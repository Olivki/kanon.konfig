/*
 * Copyright 2019 Oliver Berg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package moe.kanon.konfig.layers

import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap
import moe.kanon.kommons.collections.asUnmodifiable
import moe.kanon.kommons.func.Failure
import moe.kanon.kommons.func.None
import moe.kanon.kommons.func.Option
import moe.kanon.kommons.func.Success
import moe.kanon.kommons.func.getValueOrNone
import moe.kanon.kommons.func.toOption
import moe.kanon.kommons.requireThat
import moe.kanon.konfig.ConfigException
import moe.kanon.konfig.entries.Entry
import moe.kanon.konfig.entries.delegates.DelegatedExternalNullableProperty
import moe.kanon.konfig.entries.delegates.DelegatedExternalProperty
import moe.kanon.konfig.entries.values.ConstantValue
import moe.kanon.konfig.entries.values.DynamicValue
import moe.kanon.konfig.entries.values.LazyValue
import moe.kanon.konfig.entries.values.LimitedStringValue
import moe.kanon.konfig.entries.values.LimitedValue
import moe.kanon.konfig.entries.values.NormalValue
import moe.kanon.konfig.entries.values.NullableValue
import moe.kanon.konfig.internal.ConfigResult
import moe.kanon.konfig.internal.TypeToken
import moe.kanon.konfig.internal.clz
import moe.kanon.konfig.internal.failure
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.isSuperclassOf

/**
 * An abstract implementation of a [ConfigLayer].
 *
 * If one wants to implement their own `ConfigLayer` then it's highly recommended to inherit from this, as it already
 * handles essentially all the required logic for a layer implementation to work as expected.
 */
abstract class AbstractConfigLayer : ConfigLayer {
    abstract override val name: String

    abstract override var path: String
        protected set

    override var parent: Option<AbstractConfigLayer> = None
        protected set(value) {
            field = value
            path = value.fold({ "$name/" }, { "${it.path}$name/" })
        }

    override val hasParent: Boolean get() = parent.isPresent

    protected val _entries: MutableMap<String, Entry<*>> = LinkedHashMap()

    override val entries: ImmutableMap<String, Entry<*>> get() = _entries.toImmutableMap()

    protected val _layers: MutableMap<String, AbstractConfigLayer> = LinkedHashMap()

    override val layers: ImmutableMap<String, AbstractConfigLayer> get() = _layers.toImmutableMap()

    final override val copy: AbstractConfigLayer
        get() = BasicLayer(name).also { newLayer ->
            newLayer.parent = parent
            newLayer._entries.putAll(this._entries)
            newLayer._layers.putAll(this._layers)
        }

    // -- VALUES -- \\
    final override fun <T : Any?> setValue(path: String, value: T?): AbstractConfigLayer = apply {
        val sanitizedPath = path.sanitizePath()
        val entry = getEntry<T>(sanitizedPath)
        val entryValue = entry.value

        if (entryValue is NullableValue<*>) (entryValue as NullableValue<T>).value = value

        requireThat(value != null) { "'path' <$path> points towards a non-nullable entry, but 'value' was null" }

        when (entryValue) {
            is NormalValue<*> -> (entryValue as NormalValue<Any>).value = value
            is LimitedValue<*> -> {
                requireThat(value is Comparable<*>) {
                    "'path' <$path> points towards a limited-entry, but 'value' is not comparable"
                }
                // we need to set this value via reflection because 'V' is not a covariant of 'Comparable<V>'.
                entryValue::value.set(value)
            }
            // we could do 'toString()' here, but that would most likely cause behaviour that wasn't intended.
            is LimitedStringValue -> entryValue.value = value as String
            else -> throw IllegalArgumentException("'path' <$path> points towards a non-mutable entry")
        }
    }

    final override fun <T> getValue(path: String): T {
        val def = getNullableValue<T>(path)
        requireThat(def != null) { "'path' <$path> points towards a nullable entry" }
        return def
    }

    final override fun <T> getNullableValue(path: String): T? {
        val sanitizedPath = path.sanitizePath()
        return when (val entry = getEntry<T>(sanitizedPath).value) {
            is NullableValue<*> -> entry.value as T?
            is NormalValue<*> -> entry.value as T
            is LimitedValue<*> -> entry.value as T
            is LimitedStringValue -> entry.value as T // just keep on scrolling and don't think too much about this one
            is ConstantValue<*> -> entry.value as T
            is LazyValue<*> -> entry.value as T
            is DynamicValue<*> -> entry.value as T
        }
    }

    final override fun <T> getDefaultValue(path: String): T {
        val def = getNullableDefaultValue<T>(path)
        requireThat(def != null) { "'path' <$path> points towards a nullable entry" }
        return def
    }

    final override fun <T> getNullableDefaultValue(path: String): T? {
        val sanitizedPath = path.sanitizePath()
        return when (val entry = getEntry<T>(sanitizedPath).value) {
            is NullableValue<*> -> entry.default as T?
            is NormalValue<*> -> entry.default as T
            is LimitedValue<*> -> entry.default as T
            is LimitedStringValue -> entry.default as T
            else -> throw IllegalArgumentException("'path' <$path> points to an entry without a default value")
        }
    }

    // -- ENTRIES -- \\
    final override fun addEntry(entry: Entry<*>): AbstractConfigLayer = addEntry(entry.name, entry)

    final override fun addEntry(name: String, entry: Entry<*>): AbstractConfigLayer = apply {
        requireThat('/' !in name) { "'name' <$name> contains invalid character '/'" }
        _entries[name] = entry
    }

    final override fun addEntries(entries: Iterable<Entry<*>>): AbstractConfigLayer = apply {
        for (entry in entries) addEntry(entry)
    }

    final override fun removeEntry(path: String): ConfigResult<Entry<*>> {
        val sanitizedPath = path.sanitizePath()

        val result = if ('/' in sanitizedPath) {
            val name = path.substringAfterLast('/')
            val layer = getLayer(path.substringBeforeLast('/').sanitizePath())
            val entry = layer.getEntryOrNone<Any?>(name)
            layer._entries -= name
            entry
        } else {
            val entry = getEntryOrNone<Any?>(sanitizedPath)
            _entries -= sanitizedPath
            entry
        }

        return result.fold({ failure("No entry found under path <$path>") }, { Success(it) })
    }

    final override fun <T> getEntry(path: String): Entry<T> =
        getEntryOrNone<T>(path).orThrow { NoSuchElementException("No entry found under the path <$path> in <$this>") }

    @Suppress("UNCHECKED_CAST")
    final override fun <T> getEntryOrNone(path: String): Option<Entry<T>> {
        val sanitizedPath = path.sanitizePath()

        return if ('/' in sanitizedPath) {
            val name = path.substringAfterLast('/')
            val layer = getLayer(path.substringBeforeLast('/').sanitizePath())
            layer._entries.getValueOrNone(name) as Option<Entry<T>>
        } else entries.getValueOrNone(sanitizedPath) as Option<Entry<T>>
    }

    final override operator fun contains(path: String): Boolean {
        val sanitizedPath = path.sanitizePath()

        return if ('/' in sanitizedPath) {
            val name = path.substringAfterLast('/')
            val layer = getLayer(path.substringBeforeLast('/').sanitizePath())
            name in layer.entries
        } else sanitizedPath in entries
    }

    // -- LAYERS -- \\
    final override fun addLayer(path: String): ConfigResult<AbstractConfigLayer> {
        val sanitizedPath = path.sanitizePath()

        return when {
            sanitizedPath.isBlank() -> failure("Path <$path> became blank after sanitation")
            '/' in sanitizedPath -> {
                val firstLayer = sanitizedPath.substringBefore('/')
                val remainingLayers = sanitizedPath.substringAfter('/')
                return when {
                    // we know that this is safe because of the check above.
                    firstLayer in _layers -> _layers.getValue(firstLayer).addLayer(remainingLayers)
                    '/' in remainingLayers -> {
                        val subLayer = BasicLayer(firstLayer)
                        addLayer(subLayer) // this function takes a 'Layer' instance
                        subLayer.addLayer(remainingLayers) // and this is 'this' function, called recursively
                        return Success(subLayer)
                    }
                    remainingLayers.isBlank() -> {
                        val subLayer = BasicLayer(firstLayer)
                        addLayer(subLayer)
                        return Success(subLayer)
                    }
                    else -> failure("Error in layer handling logic, firstLayer <$firstLayer>, remainingLayers <$remainingLayers>")
                }
            }
            else -> {
                val subLayer = BasicLayer(sanitizedPath)
                addLayer(subLayer)
                return Success(subLayer)
            }
        }
    }

    final override fun addLayers(vararg paths: String): AbstractConfigLayer = apply {
        for (path in paths) addLayer(path)
    }

    final override fun addLayer(layer: ConfigLayer): AbstractConfigLayer {
        requireThat(layer is AbstractConfigLayer) { "Expected 'layer' to be a 'AbstractConfigLayer' but it was not" }
        layer.parent = this.toOption()
        _layers[layer.name] = layer
        return layer
    }

    final override fun removeLayer(path: String): ConfigResult<AbstractConfigLayer> {
        val sanitizedPath = path.substringAfter("$name/").sanitizePath()

        return when {
            sanitizedPath.isBlank() -> failure("Can not remove a layer from itself")
            '/' in sanitizedPath -> {
                val key = sanitizedPath.substringBefore('/')
                val layer = _layers.getValueOrNone(key)
                    .flatMap { it.getLayerOrNone(sanitizedPath.substringAfter('/')) }
                layer.ifPresent { removeLayer(it) }
                layer.fold({ failure("No layer found under key <$key>") }, { Success(it) })
            }
            else -> _layers.getValueOrNone(sanitizedPath).fold(
                { failure("No layer found under path <$path>") },
                { Success(it) }
            )
        }
    }

    final override fun removeLayer(layer: ConfigLayer): ConfigResult<AbstractConfigLayer> {
        requireThat(layer is AbstractConfigLayer) { "Expected 'layer' to be a 'AbstractConfigLayer' but it was not" }
        val toRemove = _layers.getValueOrNone(layer.name)
        toRemove.ifPresent {
            layer.parent = None
            _layers -= it.name
        }
        return toRemove.fold(
            { Failure(ConfigException("Layer <$toRemove> does not belong to this <$this> layer")) },
            { Success(it) }
        )
    }

    final override fun getLayer(path: String): AbstractConfigLayer =
        getLayerOrNone(path).orThrow { NoSuchElementException("No layer found under the path <$path> in <$this>") }

    final override fun getLayerOrNone(path: String): Option<AbstractConfigLayer> {
        val sanitizedPath = path.substringAfter("$name/").sanitizePath()

        return when {
            sanitizedPath.isBlank() -> this.toOption()
            '/' in sanitizedPath -> {
                val key = sanitizedPath.substringBefore('/')
                _layers.getValueOrNone(key).flatMap { it.getLayerOrNone(sanitizedPath.substringAfter('/')) }
            }
            else -> _layers.getValueOrNone(sanitizedPath)
        }
    }

    // -- DELEGATES -- \\
    final override fun <T : Any> delegateTo(path: String): ReadWriteProperty<ConfigLayer, T> =
        DelegatedExternalProperty(path)

    final override fun <T : Any?> delegateToNullable(path: String): ReadWriteProperty<ConfigLayer, T?> =
        DelegatedExternalNullableProperty(path)

    // -- MISC -- \\
    protected fun String.sanitizePath(): String {
        var string = this.replace("/+".toRegex(), "/")

        if (string.startsWith("./")) string = "$path${string.substringAfter("./")}"
        if (string.startsWith('/')) string = string.substringAfter('/')

        return string
    }

    final override fun iterator(): Iterator<Entry<*>> = _entries.values.iterator().asUnmodifiable()

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is AbstractConfigLayer -> false
        name != other.name -> false
        path != other.path -> false
        parent != other.parent -> false
        _entries != other._entries -> false
        _layers != other._layers -> false
        else -> true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + parent.hashCode()
        result = 31 * result + _entries.hashCode()
        result = 31 * result + _layers.hashCode()
        return result
    }

    override fun toString(): String = "ConfigLayer(name='$name', path='$path')"
}