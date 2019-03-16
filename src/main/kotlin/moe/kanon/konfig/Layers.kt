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

@file:Suppress("UNCHECKED_CAST", "LocalVariableName", "unused")

package moe.kanon.konfig

import moe.kanon.konfig.entries.ConstantEntry
import moe.kanon.konfig.entries.ConstantValue
import moe.kanon.konfig.entries.DynamicEntry
import moe.kanon.konfig.entries.DynamicValue
import moe.kanon.konfig.entries.Entry
import moe.kanon.konfig.entries.LazyEntry
import moe.kanon.konfig.entries.LazyValue
import moe.kanon.konfig.entries.LimitedEntry
import moe.kanon.konfig.entries.LimitedStringEntry
import moe.kanon.konfig.entries.LimitedStringValue
import moe.kanon.konfig.entries.LimitedValue
import moe.kanon.konfig.entries.NormalEntry
import moe.kanon.konfig.entries.NormalValue
import moe.kanon.konfig.entries.NullableEntry
import moe.kanon.konfig.entries.NullableValue
import moe.kanon.konfig.entries.delegates.DelegatedConstantProperty
import moe.kanon.konfig.entries.delegates.DelegatedDynamicProperty
import moe.kanon.konfig.entries.delegates.DelegatedLazyProperty
import moe.kanon.konfig.entries.delegates.DelegatedLimitedProperty
import moe.kanon.konfig.entries.delegates.DelegatedLimitedStringProperty
import moe.kanon.konfig.entries.delegates.DelegatedNormalProperty
import moe.kanon.konfig.entries.delegates.DelegatedNullableProperty
import java.util.*
import kotlin.NoSuchElementException

@Suppress("UNCHECKED_CAST")
interface Layer : Iterable<Entry<*>> {
    
    /**
     * The name of `this` layer.
     */
    val name: String
    
    /**
     * An immutable view of the underlying map of the entry storage.
     */
    val entries: Map<String, Entry<*>>
    
    /**
     * An immutable view of the underlying map of the layer storage.
     */
    val layers: Map<String, Layer>
    
    /**
     * An immutable view of the path to `this` layer.
     */
    val path: String
    
    /**
     * Adds the specified [entry] to `this` storage.
     *
     * The `entry` gets stored under it's [name][Entry.name] property.
     *
     * @param [entry] The [Entry] to add to `this` storage.
     */
    fun <V : Any> addEntry(entry: Entry<V>): Layer
    
    /**
     * Adds the specified [entry] to `this` storage.
     *
     * The `entry` gets stored under it's [name][Entry.name] property.
     *
     * @param [entry] The [Entry] to add to `this` storage.
     * @param [name] The name to store the [entry] under.
     *
     * ([entry.name][Entry.name] by default.)
     */
    fun <V : Any> addEntry(entry: Entry<V>, name: String): Layer
    
    /**
     * Adds the specified [entry] to `this` storage.
     *
     * The `entry` gets stored under it's [name][Entry.name] property.
     *
     * @param [entry] The [Entry] to add to `this` storage.
     */
    @JvmDefault
    @JvmSynthetic
    operator fun <V : Any> plusAssign(entry: Entry<V>) {
        addEntry(entry)
    }
    
    /**
     * Removes the entry stored under the specified [path] from `this` storage, or throws a [NoSuchElementException] if
     * no entry is found.
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will attempt to remove whatever
     * is stored under the `name` in the [entries] of that layer.
     *
     * @param [path] The path to the entry to remove.
     * @throws [NoSuchElementException] If no `entry` in `this` storage is stored under the specified [path].
     */
    fun removeEntry(path: String): Layer
    
    /**
     * Removes the entry stored under the specified [path] from `this` storage, or throws a [NoSuchElementException] if
     * no entry is found.
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will attempt to remove whatever
     * is stored under the `name` in the [entries] of that layer.
     *
     * @param [path] The path to the entry to remove.
     * @throws [NoSuchElementException] If no `entry` in `this` storage is stored under the specified [path].
     */
    @JvmDefault
    @JvmSynthetic
    operator fun minusAssign(path: String) {
        removeEntry(path)
    }
    
    /**
     * Sets the value of the [Entry] stored under the specified [path] to the specified [value].
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will attempt to retrieve whatever
     * is stored under the `name` in the [entries] of that layer.
     *
     * @param [path] The path of the entry to modify the value of.
     * @param [value] The new value.
     *
     * @throws [NoSuchElementException] If there exists no [Entry] stored under the specified [path].
     * @throws [IllegalArgumentException]
     *
     * - If the found [Entry] is not a [nullable][Entry.Value.Nullable] type, but the specified [value] is `null`.
     * - If the found [Entry] is of the [limited][Entry.Value.Limited] type, but the specified [value] is not
     * [Comparable].
     * - If the found [Entry] is of a type that does not support `set` operations ([constant][Entry.Value.Constant],
     * [lazy][Entry.Value.Lazy] or [dynamic][Entry.Value.Dynamic]).
     *
     */
    @JvmDefault
    fun <V : Any> setValue(path: String, value: V?): Layer {
        val _path = path.sanitizePath(isLayerPath = false)
        val entry = getEntry<V>(_path)
        val _value = entry.value
        
        if (_value !is Entry.Value.Nullable<*> && value == null) throw IllegalArgumentException(
            "Entry <$entry> is not of a nullable type, but the provided 'value' was null."
        )
        
        when (_value) {
            is NullableValue<*> -> (_value as Entry.Value.Nullable<V>).value = value
            is NormalValue<*> -> (_value as Entry.Value.Normal<V>).value = value!! // we know that it's not null.
            is LimitedValue<*> -> {
                when (value) {
                    !is Comparable<*> -> throw IllegalArgumentException(
                        "The given value <$value> is not comparable, but an attempt to set the value of an entry of " +
                                "the 'limited' type was made with it"
                    )
                    // we need to set this value via reflection because 'V' is not a covariant of 'Comparable<V>'.
                    else -> _value::value.set(value)
                }
            }
            // we could do 'toString()' here, but that would most likely cause behaviour that wasn't intended.
            is LimitedStringValue -> _value.value = value as String
            else -> {
                throw IllegalArgumentException(
                    "The value of entry <$entry> is not mutable <${_value.name}>, but an attempt to set the value of it was made"
                )
            }
        }
        
        return this
    }
    
    /**
     * Sets the value of the [Entry] stored under the specified [path] to the specified [value].
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will attempt to retrieve whatever
     * is stored under the `name` in the [entries] of that layer.
     *
     * @param [path] The path of the entry to modify the value of.
     * @param [value] The new value.
     *
     * @throws [NoSuchElementException] If there exists no [Entry] stored under the specified [path].
     * @throws [IllegalArgumentException] If the found [Entry] is *not* a [nullable][Entry.Value.Nullable] type, but
     * the specified [value] is `null`.
     */
    @JvmDefault
    @JvmSynthetic
    operator fun <V : Any> set(path: String, value: V?) {
        setValue(path, value)
    }
    
    /**
     * Returns the raw `value` of the [entry][Entry] stored under the specified [path], or it throws a
     * [NoSuchElementException] if no `entry` is found.
     *
     * As this function is `null-safe` it is not recommended to use this to retrieve the value of an `entry` that is
     * of the [nullable][Entry.Value.Nullable] type, as it may return a value that's `null`, which will result in a
     * [IllegalArgumentException] being thrown. It is recommended to use [getNullable] for the retrieval of such items
     * instead. If you do not know what type the `entry` is before-hand, it is also recommended to use that function.
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will attempt to retrieve whatever
     * is stored under the `name` in the [entries] of that layer.
     *
     * @param [path] The path to look up the `entry` under.
     *
     * @throws [NoSuchElementException] If no [entry][Entry] is stored under the specified [path].
     * @throws [IllegalArgumentException] If the returned [entry][Entry]  is of the [nullable][Entry.Value.Nullable]
     * type.
     *
     * @see invoke
     * @see getNullable
     */
    @JvmDefault
    operator fun <V : Any> get(path: String): V =
        getNullable(path)
            ?: throw IllegalArgumentException("Entry stored under the path <$path> is of the nullable type.")
    
    /**
     * Returns the raw `value` of the [entry][Entry] stored under the specified [path], or it throws a
     * [NoSuchElementException] if no `entry` is found.
     *
     * This function is simply here to "replicate" the behaviour of the index operator while still being able to
     * explicitly cast a generic.
     *
     * Explanation:
     *
     * ```kotlin
     *  // this will not compile, as it's not obvious to the compiler what the type of the entry under the name
     *  // "entryName" is, and it needs to be specified explicitly.
     *  val entry = config["entryName"]
     *
     *  // however, you can't specify a generic explicitly when using the index operator.
     *  // which means that this would not compile either
     *  val entry = config<String>["entryName"]
     *
     *  // which means that you would either need to do this
     *  val entry = config.get<String>("entryName")
     *  // or this
     *  val entry: String = config["entryName"]
     *
     *  // but with this function you can just do
     *  val entry = config<String>("entryName")
     * ```
     *
     * As this function is `null-safe` it is not recommended to use this to retrieve the value of an `entry` that is
     * of the [nullable][Entry.Value.Nullable] type, as it may return a value that's `null`, which will result in a
     * [IllegalArgumentException] being thrown. It is recommended to use [getNullable] for the retrieval of such items
     * instead. If you do not know what type the `entry` is before-hand, it is also recommended to use that function.
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will attempt to retrieve whatever
     * is stored under the `name` in the [entries] of that layer.
     *
     * @param [path] The path to look up the `entry` under.
     *
     * @throws [NoSuchElementException] If no [entry][Entry] is stored under the specified [path].
     * @throws [IllegalArgumentException] If the returned [entry][Entry]  is of the [nullable][Entry.Value.Nullable]
     * type.
     *
     * @see get
     * @see getNullable
     */
    @JvmDefault
    @JvmSynthetic
    operator fun <V : Any> invoke(path: String): V =
        getNullable(path)
            ?: throw IllegalArgumentException("Entry stored under the path <$path> is of the nullable type")
    
    /**
     * Returns the raw `value` of the [entry][Entry] stored under the specified [path], or `null` if none is found.
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will attempt to retrieve whatever
     * is stored under the `name` in the [entries] of that layer.
     *
     * @param [path] The path to look up the `entry` under.
     *
     * @throws [NoSuchElementException] If no [entry][Entry] is stored under the specified [path].
     *
     * @see get
     * @see invoke
     */
    @JvmDefault
    fun <V : Any> getNullable(path: String): V? {
        val _path = path.sanitizePath(isLayerPath = false)
        return when (val entry = getEntry<V>(_path).value) {
            is NullableValue<*> -> entry.value as V?
            is NormalValue<*> -> entry.value as V
            is LimitedValue<*> -> entry.value as V
            is LimitedStringValue -> entry.value as V // just keep on scrolling and don't think too much about this one
            is ConstantValue<*> -> entry.value as V
            is LazyValue<*> -> entry.value as V
            is DynamicValue<*> -> entry.value as V
        }
    }
    
    /**
     * Returns the raw `default` of the [entry][Entry] stored under the specified [path], or it throws a
     * [NoSuchElementException] if no `entry` is found.
     *
     * As this function is `null-safe` it is not recommended to use this to retrieve the `default` of an `entry` that
     * is of the [nullable][Entry.Value.Nullable] type, as it may return a value that's `null`, which will result in a
     * [IllegalArgumentException] being thrown. It is recommended to use [getNullableDefault] for the retrieval of such
     * items instead. If you do not know what type the `entry` is before-hand, it is also recommended to use that
     * function.
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will attempt to retrieve whatever
     * is stored under the `name` in the [entries] of that layer.
     *
     * @param [path] The path to look up the `entry` under.
     *
     * @throws [NoSuchElementException] If no [entry][Entry] is stored under the specified [path].
     * @throws [IllegalArgumentException]
     *
     * - If the given [path] points to an [entry][Entry] that does not have have a `default` property.
     * - If the returned [entry][Entry] is of the [nullable][Entry.Value.Nullable] type.
     *
     * @see invoke
     * @see getNullable
     */
    @JvmDefault
    fun <V : Any> getDefault(path: String): V =
        getNullableDefault(path)
            ?: throw IllegalArgumentException("Entry stored under the path <$path> is of the nullable type")
    
    /**
     * Returns the raw `default` of the [entry][Entry] stored under the specified [path], or `null` if none is found.
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will attempt to retrieve whatever
     * is stored under the `name` in the [entries] of that layer.
     *
     * @param [path] The path to look up the `entry` under.
     *
     * @throws [NoSuchElementException] If no [entry][Entry] is stored under the specified [path]
     * @throws [IllegalArgumentException] If the given [path] points to an [entry][Entry] that does not have have a
     * `default` property.
     *
     * @see getDefault
     */
    @JvmDefault
    fun <V : Any> getNullableDefault(path: String): V? {
        val _path = path.sanitizePath(isLayerPath = false)
        return when (val entry = getEntry<V>(_path).value) {
            is NullableValue<*> -> entry.default as V?
            is NormalValue<*> -> entry.default as V
            is LimitedValue<*> -> entry.default as V
            is LimitedStringValue -> entry.default as V
            else -> throw IllegalArgumentException(
                "Given path <$path> points to an entry that does not have a default value, as it is a <${entry.name}> value"
            )
        }
    }
    
    /**
     * Returns whether or not there's an entry stored under the specified [path] in `this` layer, or any of it's
     * sub-layers.
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will check whether or not that
     * layers' [entries] contains the `name`.
     *
     * @param [path] The path to the entry.
     */
    @JvmDefault
    operator fun contains(path: String): Boolean {
        val _path = path.sanitizePath(isLayerPath = false)
        
        return if ('/' in _path) {
            val name = path.substringAfterLast('/')
            val layer = getLayer(path.substringBeforeLast('/').sanitizePath(isLayerPath = true))
            name in layer.entries
        } else {
            _path in entries
        }
    }
    
    /**
     * Returns the [entry][Entry] stored under the specified [path], or it will throw a [NoSuchElementException] if
     * none is found.
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will attempt to retrieve whatever
     * is stored under the `name` in the [entries] of that layer.
     */
    @JvmDefault
    fun <V : Any> getEntry(path: String): Entry<V> =
        getEntryOrNull(path) ?: throw NoSuchElementException("No entry found under the path <$path> in <$this>")
    
    /**
     * Returns the [entry][Entry] stored under the specified [path], or `null` if none can be found.
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will attempt to retrieve whatever
     * is stored under the `name` in the [entries] of that layer.
     */
    @JvmDefault
    fun <V : Any> getEntryOrNull(path: String): Entry<V>? {
        val _path = path.sanitizePath(isLayerPath = false)
        
        return if ('/' in _path) {
            val name = path.substringAfterLast('/')
            val layer = getLayer(path.substringBeforeLast('/').sanitizePath(isLayerPath = true))
            layer.entries[name] as? Entry<V>
        } else {
            entries[_path] as? Entry<V>
        }
    }
    
    /**
     * Resets all the [entries] on `this` layer.
     *
     * Resetting an entry means that its `value` property is set to the value of its `default` property.
     *
     * @see resetAllEntries
     */
    @JvmDefault
    fun resetEntries() {
        for (entry in this) {
            // so that we don't cause a 'IllegalAccessException'
            if (entry.value.isMutable) entry.value.reset()
        }
    }
    
    /**
     * Resets all the [entries] on `this` layer, and the entries of all sub-layers.
     *
     * Resetting an entry means that its `value` property is set to the value of its `default` property.
     *
     * @see resetEntries
     */
    @JvmDefault
    fun resetAllEntries() {
        resetEntries()
        for ((_, subLayer) in layers) subLayer.resetAllEntries()
    }
    
    /**
     * Creates a valid path for storage and retrieval.
     *
     * This function enables the user to omit the current layers name from any operations involve the paths, this that
     * if `this` string is `"layer_two/entry_two"` then the string returned from this function would be
     * `"layer_one/layer_two/entry_two"`. *(`"layer_one"` here is just used a placeholder for the current layers name.)*
     *
     * @param [isLayerPath] Whether or not `this` string is used for retrieving layers.
     *
     * If `true` then this function will make sure that the returned string ends with the `'/'` character, to make it
     * valid as a key for layer retrieval.
     *
     * NOTE: This operation would make the returned string invalid for retrieval of values.
     */
    fun String.sanitizePath(isLayerPath: Boolean): String
    
    // layers
    
    /**
     * Adds the specified [layer] to `this` layer storage.
     *
     * When a layer gets added to another layer, the parent of that layer is forcefully changed to the layer that it's
     * being added to, i.e;
     *
     * If `{Layer E}` is originally a sub-layer of `{Layer D}` which means that the parent of `{Layer E}` is
     * `{Layer D}`, if `{Layer E}` then gets added to `{Layer A}` the parent of `{Layer E}` changes from `{Layer D}`
     * to `{Layer A}`. However, any sub-layers of `{Layer E}` will still keep their parent, but their `paths` will all
     * be updated to be correct.
     */
    fun addLayer(layer: KonfigLayer): Layer
    
    /**
     * Removes the specified [layer] from `this` layer, or throws a [NoSuchElementException] if `layer` is not a
     * sub-layer of `this` layer.
     *
     * @throws [NoSuchElementException] If [layer] is not a sub-layer of `this` layer.
     */
    @JvmDefault
    fun removeLayer(layer: KonfigLayer): Layer {
        removeLayer(layer.name)
        return this
    }
    
    /**
     * Removes the the [layer][Layer] stored under the specified [path] from `this` layer, or throws a
     * [NoSuchElementException] if there is no `layer` stored under the specified [path].
     *
     * @throws [NoSuchElementException] If there is no [layer][Layer] stored under the specified [path] in `this`
     * layer.
     */
    fun removeLayer(path: String): Layer
    
    // TODO: Better the documentation with explanation that you can traverse sub-layers.
    
    /**
     * Returns the [layer][Layer] stored under the specified [path], or throws a [NoSuchElementException] if none is
     * found.
     */
    @JvmDefault
    fun getLayer(path: String): Layer =
        getLayerOrNull(path) ?: throw NoSuchElementException("No layer found under the path <$path> in <$this>")
    
    /**
     * Returns the [layer][Layer] stored under the specified [path], or `null` if none exists.
     */
    @JvmDefault
    fun getLayerOrNull(path: String): Layer? {
        val _path = path.substringAfter("$name/")
        
        return when {
            _path.isBlank() -> this
            '/' in _path -> {
                val key = _path.substringBefore('/')
                layers[key]?.getLayerOrNull(_path.substringAfter('/'))
            }
            else -> layers[_path]
        }
    }
    
    /**
     * Updates the path of any sub-layers of `this` layer.
     */
    fun updatePaths()
}

/**
 * A config layer.
 */
@Suppress("LeakingThis")
open class KonfigLayer(override val name: String) : Layer, Iterable<Entry<*>> {
    
    /**
     * The path of `this` layer.
     */
    private var _path: String = "$name/"
    
    final override val path: String get() = _path
    
    /**
     * The underlying map of `this` layer storage.
     */
    private val _layers: MutableMap<String, KonfigLayer> = LinkedHashMap()
    
    final override val layers: Map<String, Layer> get() = _layers.toMap()
    
    /**
     * The underlying map of `this` entry storage.
     */
    private val _entries: MutableMap<String, Entry<*>> = LinkedHashMap()
    
    final override val entries: Map<String, Entry<*>> get() = _entries.toMap()
    
    /**
     * The parent [layer][KonfigLayer] of `this` layer, this will return `null` if `this` layer has no parent.
     */
    lateinit var parent: KonfigLayer
        private set
    
    /**
     * Returns whether or not `this` layer has a parent layer.
     *
     * If this returns `true`, that means that this layer is *nested*, while if it returns `false` it means that this
     * layer is top-level.
     */
    val hasParent: Boolean get() = this::parent.isInitialized
    
    // init
    init {
        if (javaClass.isKotlinClass) {
            val nestedClasses = this::class.nestedClasses
            val children = nestedClasses.mapNotNull { it.objectInstance }.filterIsInstance<KonfigLayer>()
            
            for (layer in children) {
                layer.parent = this
                layer._path = "${layer.parent._path}${layer._path}"
                addLayer(layer)
            }
        }
    }
    
    // entries
    
    final override fun <V : Any> addEntry(entry: Entry<V>): Layer = addEntry(entry, entry.name)
    
    final override fun <V : Any> addEntry(entry: Entry<V>, name: String): Layer {
        _entries[name.sanitizePath(isLayerPath = false)] = entry
        
        return this
    }
    
    final override fun removeEntry(path: String): Layer {
        val _path = path.sanitizePath(isLayerPath = false)
        
        if ('/' in _path) {
            val name = path.substringAfterLast('/')
            val layer = getLayer(path.substringBeforeLast('/').sanitizePath(isLayerPath = true)) as KonfigLayer
            layer._entries -= name
        } else {
            _entries -= _path
        }
        
        return this
    }
    
    final override fun String.sanitizePath(isLayerPath: Boolean): String {
        var _str = replace("/+".toRegex(), "/")
        
        if (_str.startsWith("./")) _str = "$_path${_str.substringAfter("./")}"
        if (_str.startsWith('/')) _str = _str.substringAfter('/')
        if (!_str.endsWith('/') && isLayerPath) _str = "$_str/"
        
        return _str
    }
    
    // layers
    
    final override fun addLayer(layer: KonfigLayer): Layer {
        layer.parent = this
        
        _layers[layer.name] = layer
        
        updatePaths()
        
        return this
    }
    
    final override fun removeLayer(layer: KonfigLayer): Layer = removeLayer(layer._path)
    
    final override fun removeLayer(path: String): Layer {
        _layers -= path.sanitizePath(isLayerPath = true)
        
        return this
    }
    
    override fun updatePaths() {
        for ((_, subLayer) in _layers) {
            subLayer._path = "$path${subLayer.name}/"
            subLayer.updatePaths()
        }
    }
    
    // iterable
    final override fun iterator(): Iterator<Entry<*>> = entries.values.iterator()
    
    // delegate functions
    /**
     * A delegates function for creating a [NullableEntry] and automagically adding it to `this` layer.
     */
    protected inline fun <reified V : Any> nullable(name: String? = null, default: V?, description: String) =
        object : DelegatedNullableProperty<V>(
            value = default,
            name = name,
            description = description
        ) {}
    
    /**
     * A delegates function for creating a [NormalEntry] and automagically adding it to `this` layer.
     */
    protected inline fun <reified V : Any> normal(name: String? = null, default: V, description: String) =
        object : DelegatedNormalProperty<V>(
            value = default,
            name = name,
            description = description
        ) {}
    
    /**
     * A delegates function for creating a [LimitedEntry] and automagically adding it to `this` layer.
     */
    protected inline fun <reified V : Comparable<V>> limited(
        name: String? = null,
        default: V,
        range: ClosedRange<V>,
        description: String
    ) = object : DelegatedLimitedProperty<V>(
        value = default,
        range = range,
        name = name,
        description = description
    ) {}
    
    /**
     * A delegates function for creating a [LimitedStringEntry] and automagically adding it to `this` layer.
     */
    protected fun limited(name: String? = null, default: String, range: IntRange, description: String) =
        object : DelegatedLimitedStringProperty(
            value = default,
            range = range,
            name = name,
            description = description
        ) {}
    
    /**
     * A delegates function for creating a [ConstantEntry] and automagically adding it to `this` layer.
     */
    protected inline fun <reified V : Any> constant(name: String? = null, value: V, description: String) =
        object : DelegatedConstantProperty<V>(
            value = value,
            name = name,
            description = description
        ) {}
    
    /**
     * A delegates function for creating a [LazyEntry] and automagically adding it to `this` layer.
     */
    protected inline fun <reified V : Any> lazy(name: String? = null, description: String, noinline closure: () -> V) =
        object : DelegatedLazyProperty<V>(
            value = closure,
            name = name,
            description = description
        ) {}
    
    /**
     * A delegates function for creating a [DynamicEntry] and automagically adding it to `this` layer.
     */
    protected inline fun <reified V : Any> dynamic(
        name: String? = null,
        description: String,
        noinline closure: () -> V
    ) = object : DelegatedDynamicProperty<V>(
        value = closure,
        name = name,
        description = description
    ) {}
    
    final override fun toString(): String = "ConfigLayer(name='$name', path='$_path')"
    
    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is KonfigLayer -> false
        name != other.name -> false
        _path != other._path -> false
        _layers != other._layers -> false
        _entries != other._entries -> false
        (hasParent && !other.hasParent) || (!hasParent && other.hasParent) -> false
        (hasParent && other.hasParent) && parent != other.parent -> false
        else -> true
    }
    
    override fun hashCode(): Int = Objects.hash(name, _path, _layers, _entries, parent)
    
}