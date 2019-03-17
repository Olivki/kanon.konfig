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

@file:JvmName("KonfigDsl")
@file:Suppress("NOTHING_TO_INLINE")

package moe.kanon.konfig.dsl

import com.google.common.reflect.TypeToken
import moe.kanon.konfig.KonfigLayer
import moe.kanon.konfig.Layer
import moe.kanon.konfig.entries.ConstantEntry
import moe.kanon.konfig.entries.DynamicEntry
import moe.kanon.konfig.entries.Entry
import moe.kanon.konfig.entries.LazyEntry
import moe.kanon.konfig.entries.LimitedEntry
import moe.kanon.konfig.entries.LimitedStringEntry
import moe.kanon.konfig.entries.NormalEntry
import moe.kanon.konfig.entries.NullableEntry
import moe.kanon.konfig.settings.KonfigSettings.Companion.default
import moe.kanon.konfig.superClassTypeParameter
import java.lang.reflect.Type

@DslMarker
annotation class KonfigContainer

/**
 * A container for the creation of a [Layer] via DSL.
 *
 * @param [delegate] A [Layer] instance to work on, if `null` a new one will be created accordingly.
 *
 * @property [name] The name of `this` layer.
 * @property [parent] The parent of `this` layer container.
 */
@KonfigContainer
class LayerContainer(val name: String, val parent: LayerContainer? = null, delegate: Layer? = null) {
    
    /**
     * Returns whether or not `this` layer container has a parent.
     */
    val hasParent: Boolean = parent != null
    
    /**
     * Returns the `path` of `this` layer container.
     *
     * Note that this is the path that will be supplied to the `path` property of the [Layer] instance this container
     * creates.
     */
    val path: String = if (hasParent) "${parent!!.path}$name/" else "$name/"
    
    /**
     * The underlying [layer][Layer] of `this` layer container.
     */
    val layer: Layer = delegate ?: KonfigLayer(name).also {
        // if 'this' layer has a parent, we make sure that we set the parent of the 'KonfigLayer' for 'this' container
        // to the 'KonfigLayer' of our parent.
        if (hasParent) it.parent = parent!!.layer
    }
    
    /**
     * Creates and adds a [Layer] to `this` layer from the specified [name] and [closure].
     */
    @KonfigContainer
    inline fun addLayer(name: String, closure: LayerContainer.() -> Unit) {
        val subContainer = LayerContainer(name, parent = this).apply(closure)
        layer.addLayer(subContainer.layer)
    }
    
    /**
     * Adds the specified [delegate] to `this` layer and then scopes into it with the specified [closure].
     *
     * The `name` property of the resulting `container` is taken from the `name` of the specified [delegate].
     */
    @KonfigContainer
    inline fun addLayer(delegate: Layer, closure: LayerContainer.() -> Unit) {
        val subContainer = LayerContainer(delegate.name, parent = this, delegate = delegate).apply(closure)
        layer.addLayer(subContainer.layer)
    }
    
    // much like how the 'delegate' part of the system works, this one also makes use of reified generics and creating
    // anonymous classes to avoid the type erasure, which means that this DSL really isn't usable from the Java side,
    // and as such, it will be hidden from it. (In truth, most of this library isn't very usable from the Java side,
    // and even if some parts are, it's far from being idiomatic.)
    
    /**
     * Creates and adds a [NullableEntry] to `this` layer from the specified [name] and [closure].
     */
    @JvmSynthetic
    @KonfigContainer
    inline fun <reified V : Any> addNullable(name: String, closure: NullableEntryContainer<V>.() -> Unit) {
        val entry = (object : NullableEntryContainer<V>(this, name) {}.apply(closure)).entry
        layer.addEntry(entry)
    }
    
    /**
     * Creates and adds a [NullableEntry] to `this` layer from the specified [name], [default] and [value].
     */
    @JvmSynthetic
    @KonfigContainer
    inline fun <reified V : Any> addNullable(name: String, description: String, default: V?, value: V? = default) {
        val entry = (object : NullableEntryContainer<V>(this, name) {}.apply {
            this.description = description
            this.value = value
            this.default = default
        }).entry
        layer.addEntry(entry)
    }
    
    /**
     * Creates and adds a [NormalEntryContainer] to `this` layer from the specified [name] and [closure].
     */
    @JvmSynthetic
    @KonfigContainer
    inline fun <reified V : Any> addNormal(name: String, closure: NormalEntryContainer<V>.() -> Unit) {
        val entry = (object : NormalEntryContainer<V>(this, name) {}.apply(closure)).entry
        layer.addEntry(entry)
    }
    
    /**
     * Creates and adds a [NormalEntryContainer] to `this` layer from the specified [name], [default] and [value].
     */
    @JvmSynthetic
    @KonfigContainer
    inline fun <reified V : Any> addNormal(name: String, description: String, default: V, value: V = default) {
        val entry = (object : NormalEntryContainer<V>(this, name) {}.apply {
            this.description = description
            this.value = value
            this.default = default
        }).entry
        layer.addEntry(entry)
    }
    
    /**
     * Creates and adds a [LimitedEntryContainer] to `this` layer from the specified [name] and [closure].
     */
    @JvmSynthetic
    @KonfigContainer
    inline fun <reified V : Comparable<V>> addLimited(name: String, closure: LimitedEntryContainer<V>.() -> Unit) {
        val entry = (object : LimitedEntryContainer<V>(this, name) {}.apply(closure)).entry
        layer.addEntry(entry)
    }
    
    /**
     * Creates and adds a [LimitedEntryContainer] to `this` layer from the specified [name], [default], [value] and
     * [range].
     */
    @JvmSynthetic
    @KonfigContainer
    inline fun <reified V : Comparable<V>> addLimited(
        name: String,
        description: String,
        default: V,
        range: ClosedRange<V>,
        value: V = default
    ) {
        val entry = (object : LimitedEntryContainer<V>(this, name) {}.apply {
            this.description = description
            this.value = value
            this.default = default
            this.range = range
        }).entry
        layer.addEntry(entry)
    }
    
    /**
     * Creates and adds a [LimitedStringEntryContainer] to `this` layer from the specified [name] and [closure].
     */
    @JvmSynthetic
    @KonfigContainer
    inline fun addLimitedString(name: String, closure: LimitedStringEntryContainer.() -> Unit) {
        val entry = (object : LimitedStringEntryContainer(this, name) {}.apply(closure)).entry
        layer.addEntry(entry)
    }
    
    /**
     * Creates and adds a [LimitedStringEntryContainer] to `this` layer from the specified [name], [default], [value]
     * and [range].
     */
    @JvmSynthetic
    @KonfigContainer
    inline fun addLimitedString(
        name: String,
        description: String,
        default: String,
        range: IntRange,
        value: String = default
    ) {
        val entry = (object : LimitedStringEntryContainer(this, name) {}.apply {
            this.description = description
            this.value = value
            this.default = default
            this.range = range
        }).entry
        layer.addEntry(entry)
    }
    
    /**
     * Creates and adds a [ConstantEntryContainer] to `this` layer from the specified [name] and [closure].
     */
    @JvmSynthetic
    @KonfigContainer
    inline fun <reified V : Any> addConstant(name: String, closure: ConstantEntryContainer<V>.() -> Unit) {
        val entry = (object : ConstantEntryContainer<V>(this, name) {}.apply(closure)).entry
        layer.addEntry(entry)
    }
    
    /**
     * Creates and adds a [ConstantEntryContainer] to `this` layer from the specified [name] and [value].
     */
    @JvmSynthetic
    @KonfigContainer
    inline fun <reified V : Any> addConstant(name: String, description: String, value: V) {
        val entry = (object : ConstantEntryContainer<V>(this, name) {}.apply {
            this.description = description
            this.value = value
        }).entry
        layer.addEntry(entry)
    }
    
    /**
     * Creates and adds a [LazyEntryContainer] to `this` layer from the specified [name] and [closure].
     */
    @JvmSynthetic
    @KonfigContainer
    inline fun <reified V : Any> addLazy(name: String, closure: LazyEntryContainer<V>.() -> Unit) {
        val entry = (object : LazyEntryContainer<V>(this, name) {}.apply(closure)).entry
        layer.addEntry(entry)
    }
    
    /**
     * Creates and adds a [LazyEntryContainer] to `this` layer from the specified [name] and [value].
     */
    @JvmSynthetic
    @KonfigContainer
    inline fun <reified V : Any> addLazy(name: String, description: String, noinline value: (() -> V)) {
        val entry = (object : LazyEntryContainer<V>(this, name) {}.apply {
            this.description = description
            this.valueProp = value
        }).entry
        layer.addEntry(entry)
    }
    
    /**
     * Creates and adds a [DynamicEntryContainer] to `this` layer from the specified [name] and [closure].
     */
    @JvmSynthetic
    @KonfigContainer
    inline fun <reified V : Any> addDynamic(name: String, closure: DynamicEntryContainer<V>.() -> Unit) {
        val entry = (object : DynamicEntryContainer<V>(this, name) {}.apply(closure)).entry
        layer.addEntry(entry)
    }
    
    /**
     * Creates and adds a [DynamicEntryContainer] to `this` layer from the specified [name] and [value].
     */
    @JvmSynthetic
    @KonfigContainer
    inline fun <reified V : Any> addDynamic(name: String, description: String, noinline value: (() -> V)) {
        val entry = (object : DynamicEntryContainer<V>(this, name) {}.apply {
            this.description = description
            this.valueProp = value
        }).entry
        layer.addEntry(entry)
    }
}

@KonfigContainer
abstract class AbstractEntryContainer {
    
    /**
     * The underlying [type][Type] that `this` value container represents.
     */
    @KonfigContainer
    open val javaType: Type
        get() = this::class.superClassTypeParameter!!
    
    /**
     * The parent [layer container][LayerContainer] that `this` entry container is stored under.
     */
    @KonfigContainer
    abstract val parent: LayerContainer
    
    /**
     * The name of `this` entry.
     */
    @KonfigContainer
    abstract val name: String
    
    @KonfigContainer
    lateinit var description: String
    
    /**
     * Returns whether or not the [description] property has been set yet.
     */
    val isDescriptionSet: Boolean get() = this::description.isInitialized
    
    /**
     * Sets the `description` of the [entry][Entry] to the value defined inside of the [closure].
     */
    @KonfigContainer
    @JvmName("setDescription${'$'}")
    inline fun setDescription(closure: () -> String) {
        requireNoDuplicates(isDescriptionSet, "description")
        
        description = closure()
    }
    
    /**
     * Sets the `description` of the [entry][Entry] to the specified [description].
     */
    @KonfigContainer
    @JvmName("setDescription${'$'}")
    fun setDescription(description: String) {
        requireNoDuplicates(isDescriptionSet, "description")
        
        this.description = description
    }
    
    @PublishedApi
    @KonfigContainer
    internal inline fun requireNoDuplicates(predicate: Boolean, funcName: String) {
        if (predicate) throw DuplicateDslEntryException(funcName)
    }
}

@KonfigContainer
open class NullableEntryContainer<V : Any>(override val parent: LayerContainer, override val name: String) :
    AbstractEntryContainer() {
    
    // can't use 'lateinit var' on nullable types, because 'null' is how 'lateinit vars' checks if it hasn't been
    // set yet. This also means we can't properly check for duplicates on this, oh bother.
    
    @KonfigContainer
    var value: V? = null
    
    @KonfigContainer
    var default: V? = null
    
    @PublishedApi internal val entry: NullableEntry<V> by lazy {
        val value = when {
            (value != null) && (default == null) -> value
            (value == null) && (default != null) -> default
            (value != null) && (default != null) -> value
            else -> null
        }
        
        val default = when {
            (this.value != null) && (default == null) -> this.value
            (this.value == null) && (default != null) -> default
            (this.value != null) && (default != null) -> default
            else -> null
        }
        
        return@lazy NullableEntry.of(javaType, value, default, name, description, parent.layer)
    }
    
    /**
     * Sets the `value` of `this` entry to the value defined inside of the [closure].
     */
    @KonfigContainer
    @JvmName("setValue${'$'}")
    inline fun setValue(closure: () -> V?) {
        requireNoDuplicates(value != null, "value")
        
        value = closure()
    }
    
    /**
     * Sets the `value` of `this` entry to the specified [value].
     */
    @KonfigContainer
    @JvmName("setValue${'$'}")
    fun setValue(value: V?) {
        requireNoDuplicates(this.value != null, "value")
        
        this.value = value
    }
    
    /**
     * Sets the `default` of `this` entry to the value defined inside of the [closure].
     */
    @KonfigContainer
    @JvmName("setDefault${'$'}")
    inline fun setDefault(closure: () -> V?) {
        requireNoDuplicates(default != null, "value")
        
        default = closure()
    }
    
    /**
     * Sets the `default` of `this` entry to the specified [default].
     */
    @KonfigContainer
    @JvmName("setDefault${'$'}")
    fun setDefault(default: V?) {
        requireNoDuplicates(this.default != null, "value")
        
        this.default = default
    }
}

@KonfigContainer
open class NormalEntryContainer<V : Any>(override val parent: LayerContainer, override val name: String) :
    AbstractEntryContainer() {
    
    @KonfigContainer
    lateinit var value: V
    
    /**
     * Returns whether or not the [setValue] property has been set yet.
     */
    val isValueSet: Boolean get() = this::value.isInitialized
    
    @KonfigContainer
    lateinit var default: V
    
    /**
     * Returns whether or not the [default] property has been set yet.
     */
    val isDefaultSet: Boolean get() = this::default.isInitialized
    
    @PublishedApi internal val entry: NormalEntry<V> by lazy {
        val value = when {
            isValueSet && !isDefaultSet -> value
            !isValueSet && isDefaultSet -> default
            isValueSet && isDefaultSet -> value
            else -> throw MissingFunctionsInDslException(name, parent.path)
        }
        
        val default = when {
            isValueSet && !isDefaultSet -> this.value
            !isValueSet && isDefaultSet -> default
            isValueSet && isDefaultSet -> default
            else -> throw MissingFunctionsInDslException(name, parent.path)
        }
        
        return@lazy NormalEntry.of(javaType, value, default, name, description, parent.layer)
    }
    
    /**
     * Sets the `value` of `this` entry to the value defined inside of the [closure].
     */
    @KonfigContainer
    @JvmName("setValue${'$'}")
    inline fun setValue(closure: () -> V) {
        requireNoDuplicates(isValueSet, "value")
        
        value = closure()
    }
    
    /**
     * Sets the `value` of `this` entry to the specified [value].
     */
    @KonfigContainer
    @JvmName("setValue${'$'}")
    fun setValue(value: V) {
        requireNoDuplicates(isValueSet, "value")
        
        this.value = value
    }
    
    /**
     * Sets the `default` of `this` entry to the value defined inside of the [closure].
     */
    @KonfigContainer
    @JvmName("setDefault${'$'}")
    inline fun setDefault(closure: () -> V) {
        requireNoDuplicates(isDefaultSet, "default")
        
        default = closure()
    }
    
    /**
     * Sets the `default` of `this` entry to the specified [default].
     */
    @KonfigContainer
    @JvmName("setDefault${'$'}")
    fun setDefault(default: V) {
        requireNoDuplicates(isDefaultSet, "default")
        
        this.default = default
    }
}

@KonfigContainer
open class LimitedEntryContainer<V : Comparable<V>>(override val parent: LayerContainer, override val name: String) :
    AbstractEntryContainer() {
    
    @KonfigContainer
    lateinit var value: V
    
    /**
     * Returns whether or not the [value] property has been set yet.
     */
    val isValueSet: Boolean get() = this::value.isInitialized
    
    @KonfigContainer
    lateinit var default: V
    
    /**
     * Returns whether or not the [default] property has been set yet.
     */
    val isDefaultSet: Boolean get() = this::default.isInitialized
    
    @KonfigContainer
    lateinit var range: ClosedRange<V>
    
    /**
     * Returns whether or not the [range] property has been set yet.
     */
    val isRangeSet: Boolean get() = this::range.isInitialized
    
    @PublishedApi internal val entry: LimitedEntry<V> by lazy {
        val value = when {
            isValueSet && !isDefaultSet -> value
            !isValueSet && isDefaultSet -> default
            isValueSet && isDefaultSet -> value
            else -> throw MissingFunctionsInDslException(name, parent.path)
        }
        
        val default = when {
            isValueSet && !isDefaultSet -> this.value
            !isValueSet && isDefaultSet -> default
            isValueSet && isDefaultSet -> default
            else -> throw MissingFunctionsInDslException(name, parent.path)
        }
        
        if (!isRangeSet) throw MissingFunctionsInDslException(name, parent.path)
        
        return@lazy LimitedEntry.of(javaType, value, default, range, name, description, parent.layer)
    }
    
    /**
     * Sets the `value` of `this` entry to the value defined inside of the [closure].
     */
    @KonfigContainer
    @JvmName("setValue${'$'}")
    inline fun setValue(closure: () -> V) {
        requireNoDuplicates(isValueSet, "value")
        
        value = closure()
    }
    
    /**
     * Sets the `value` of `this` entry to the specified [value].
     */
    @KonfigContainer
    @JvmName("setValue${'$'}")
    fun setValue(value: V) {
        requireNoDuplicates(isValueSet, "value")
        
        this.value = value
    }
    
    /**
     * Sets the `default` of `this` entry to the value defined inside of the [closure].
     */
    @KonfigContainer
    @JvmName("setDefault${'$'}")
    inline fun setDefault(closure: () -> V) {
        requireNoDuplicates(isDefaultSet, "default")
        
        default = closure()
    }
    
    /**
     * Sets the `default` of `this` entry to the specified [default].
     */
    @KonfigContainer
    @JvmName("setDefault${'$'}")
    fun setDefault(default: V) {
        requireNoDuplicates(isDefaultSet, "default")
        
        this.default = default
    }
    
    /**
     * Sets the `range` of `this` entry to the value defined inside of the [closure].
     */
    @KonfigContainer
    @JvmName("setRange${'$'}")
    inline fun setRange(closure: () -> ClosedRange<V>) {
        requireNoDuplicates(isRangeSet, "range")
        
        range = closure()
    }
    
    /**
     * Sets the `range` of `this` entry to the specified [setRange].
     */
    @KonfigContainer
    @JvmName("setRange${'$'}")
    fun setRange(default: ClosedRange<V>) {
        requireNoDuplicates(isRangeSet, "range")
        
        range = default
    }
}

@KonfigContainer
open class LimitedStringEntryContainer(override val parent: LayerContainer, override val name: String) :
    AbstractEntryContainer() {
    
    override val javaType: Type get() = TypeToken.of(String::class.java).type
    
    @KonfigContainer
    lateinit var value: String
    
    /**
     * Returns whether or not the [value] property has been set yet.
     */
    val isValueSet: Boolean get() = this::value.isInitialized
    
    @KonfigContainer
    lateinit var default: String
    
    /**
     * Returns whether or not the [default] property has been set yet.
     */
    val isDefaultSet: Boolean get() = this::default.isInitialized
    
    @KonfigContainer
    lateinit var range: IntRange
    
    /**
     * Returns whether or not the [range] property has been set yet.
     */
    val isRangeSet: Boolean get() = this::range.isInitialized
    
    @PublishedApi internal val entry: LimitedStringEntry by lazy {
        val value = when {
            isValueSet && !isDefaultSet -> value
            !isValueSet && isDefaultSet -> default
            isValueSet && isDefaultSet -> value
            else -> throw MissingFunctionsInDslException(name, parent.path)
        }
        
        val default = when {
            isValueSet && !isDefaultSet -> this.value
            !isValueSet && isDefaultSet -> default
            isValueSet && isDefaultSet -> default
            else -> throw MissingFunctionsInDslException(name, parent.path)
        }
        
        if (!isRangeSet) throw MissingFunctionsInDslException(name, parent.path)
        
        return@lazy LimitedStringEntry.of(value, default, range, name, description, parent.layer)
    }
    
    /**
     * Sets the `value` of `this` entry to the value defined inside of the [closure].
     */
    @KonfigContainer
    @JvmName("setValue${'$'}")
    inline fun setValue(closure: () -> String) {
        requireNoDuplicates(isValueSet, "value")
        
        value = closure()
    }
    
    /**
     * Sets the `value` of `this` entry to the specified [value].
     */
    @KonfigContainer
    @JvmName("setValue${'$'}")
    fun setValue(value: String) {
        requireNoDuplicates(isValueSet, "value")
        
        this.value = value
    }
    
    /**
     * Sets the `default` of `this` entry to the value defined inside of the [closure].
     */
    @KonfigContainer
    @JvmName("setDefault${'$'}")
    inline fun setDefault(closure: () -> String) {
        requireNoDuplicates(isDefaultSet, "default")
        
        default = closure()
    }
    
    /**
     * Sets the `default` of `this` entry to the specified [default].
     */
    @KonfigContainer
    @JvmName("setDefault${'$'}")
    fun setDefault(default: String) {
        requireNoDuplicates(isDefaultSet, "default")
        
        this.default = default
    }
    
    /**
     * Sets the `range` of `this` entry to the value defined inside of the [closure].
     */
    @KonfigContainer
    @JvmName("setRange${'$'}")
    inline fun setRange(closure: () -> IntRange) {
        requireNoDuplicates(isRangeSet, "range")
        
        range = closure()
    }
    
    /**
     * Sets the `range` of `this` entry to the specified [setRange].
     */
    @KonfigContainer
    @JvmName("setRange${'$'}")
    fun setRange(default: IntRange) {
        requireNoDuplicates(isRangeSet, "range")
        
        range = default
    }
    
}

@KonfigContainer
open class ConstantEntryContainer<V : Any>(override val parent: LayerContainer, override val name: String) :
    AbstractEntryContainer() {
    
    @KonfigContainer
    lateinit var value: V
    
    /**
     * Returns whether or not the [value] property has been set yet.
     */
    val isValueSet: Boolean get() = this::value.isInitialized
    
    @PublishedApi internal val entry: ConstantEntry<V> by lazy {
        val value = when {
            isValueSet -> value
            else -> throw MissingFunctionsInDslException(name, parent.path)
        }
        
        return@lazy ConstantEntry.of(javaType, value, name, description, parent.layer)
    }
    
    /**
     * Sets the `value` of `this` entry to the value defined inside of the [closure].
     */
    @KonfigContainer
    @JvmName("setValue${'$'}")
    inline fun setValue(closure: () -> V) {
        requireNoDuplicates(isValueSet, "value")
        
        value = closure()
    }
    
    /**
     * Sets the `value` of `this` entry to the specified [value].
     */
    @KonfigContainer
    @JvmName("setValue${'$'}")
    fun setValue(value: V) {
        requireNoDuplicates(isValueSet, "value")
        
        this.value = value
    }
}

@KonfigContainer
open class LazyEntryContainer<V : Any>(override val parent: LayerContainer, override val name: String) :
    AbstractEntryContainer() {
    
    @KonfigContainer
    @PublishedApi internal lateinit var valueProp: (() -> V)
    
    /**
     * Returns whether or not the [valueProp] property has been set yet.
     */
    val isValueSet: Boolean get() = this::valueProp.isInitialized
    
    @PublishedApi internal val entry: LazyEntry<V> by lazy {
        val value = when {
            isValueSet -> valueProp
            else -> throw MissingFunctionsInDslException(name, parent.path)
        }
        
        return@lazy LazyEntry.of(javaType, value, name, description, parent.layer)
    }
    
    /**
     * Sets the `value` of `this` entry to the value defined inside of the [closure].
     */
    @KonfigContainer
    @JvmName("setValue${'$'}")
    inline fun setValue(noinline closure: () -> V) {
        requireNoDuplicates(isValueSet, "value")
        
        valueProp = closure
    }
}

@KonfigContainer
open class DynamicEntryContainer<V : Any>(override val parent: LayerContainer, override val name: String) :
    AbstractEntryContainer() {
    
    @KonfigContainer
    @PublishedApi internal lateinit var valueProp: (() -> V)
    
    /**
     * Returns whether or not the [valueProp] property has been set yet.
     */
    val isValueSet: Boolean get() = this::valueProp.isInitialized
    
    @PublishedApi internal val entry: DynamicEntry<V> by lazy {
        val value = when {
            isValueSet -> valueProp
            else -> throw MissingFunctionsInDslException(name, parent.path)
        }
        
        return@lazy DynamicEntry.of(javaType, value, name, description, parent.layer)
    }
    
    /**
     * Sets the `value` of `this` entry to the value defined inside of the [closure].
     */
    @KonfigContainer
    @JvmName("setValue${'$'}")
    inline fun setValue(noinline closure: () -> V) {
        requireNoDuplicates(isValueSet, "value")
        
        valueProp = closure
    }
}