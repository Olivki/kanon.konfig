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

@file:Suppress("UNCHECKED_CAST", "PropertyName")

package moe.kanon.konfig.layers

import kotlinx.collections.immutable.ImmutableMap
import moe.kanon.kommons.func.Failure
import moe.kanon.kommons.func.None
import moe.kanon.kommons.func.Option
import moe.kanon.kommons.func.Success
import moe.kanon.konfig.entries.Entry
import moe.kanon.konfig.entries.values.ConstantValue
import moe.kanon.konfig.entries.values.DynamicValue
import moe.kanon.konfig.entries.values.LazyValue
import moe.kanon.konfig.entries.values.LimitedValue
import moe.kanon.konfig.entries.values.NullableValue
import moe.kanon.konfig.internal.ConfigResult
import kotlin.properties.ReadWriteProperty
import moe.kanon.konfig.Config

/**
 * Represents a layer in a [Config].
 *
 * A layer is what holds all [entries][Entry] of a config instance, it can also hold [sub-layers][layers] essentially
 * turning it into a sort of cascading configuration format.
 *
 * @see [AbstractConfigLayer]
 */
interface ConfigLayer : Iterable<Entry<*>> {
    /**
     * The name of this layer.
     *
     * This is what's used when traversing child layers from a parent layer. TODO: More info
     */
    val name: String

    val path: String

    /**
     * The parent of this layer, or `None` if this layer does not have a parent.
     *
     * ### Implementation Notes
     * If an implementation of this allows this property to be changed, then it should also take care to make sure that
     * the [path] property is updated to properly represent the new parent.
     */
    val parent: Option<ConfigLayer>

    /**
     * Returns `true` if this layer has a parent layer, otherwise `false`.
     *
     * If this returns `true`, that means that this layer is *nested*, while if it returns `false` it means that this
     * layer is top-level.
     */
    val hasParent: Boolean

    /**
     * Returns all the entries stored in this layer.
     */
    val entries: ImmutableMap<String, Entry<*>>

    /**
     * Returns all the layer children this layer has.
     */
    val layers: ImmutableMap<String, ConfigLayer>

    /**
     * Returns a copy of this layer with any references to its [parent] removed.
     */
    val copy: ConfigLayer

    /**
     * Sets the value of the [Entry] stored under the given [path] to the specified [value].
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will attempt to retrieve whatever
     * is stored under the `name` in the [entries] of that layer.
     *
     * @param [path] the path of the entry to modify the value of
     * @param [value] the new value
     *
     * @throws [NoSuchElementException] if there exists no [Entry] stored under the specified [path].
     * @throws [IllegalArgumentException]
     *
     * - If the found [Entry] is not a [nullable][NullableValue] type, but the specified [value] is `null`
     * - If the found [Entry] is of the [limited][LimitedValue] type, but the specified [value] is not [Comparable]
     * - If the found [Entry] is of a type that does not support `set` operations *([constant][ConstantValue],
     * [lazy][LazyValue] or [dynamic][DynamicValue])*
     */
    fun <T : Any?> setValue(path: String, value: T?): ConfigLayer

    /**
     * Returns the raw `value` of the [entry][Entry] stored under the given [path], or it throws a
     * [NoSuchElementException] if no `entry` is found.
     *
     * As this function is `null-safe` it is not recommended to use this to retrieve the value of an `entry` that is
     * of the [nullable][NullableValue] type, as it may return a value that's `null`, which will result in a
     * [IllegalArgumentException] being thrown. It is recommended to use [getNullableValue] for the retrieval of such
     * entries instead. If you do not know what type the `entry` is before-hand, it is also recommended to use that
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
     * @param [path] the path to look up the `entry` under
     *
     * @throws [NoSuchElementException] if no [entry][Entry] is stored under the specified [path]
     * @throws [IllegalArgumentException] if the returned [entry][Entry]  is of the [nullable][NullableValue]
     * type
     *
     * @see [getNullableValue]
     */
    fun <T> getValue(path: String): T

    /**
     * Returns the raw nullable `value` of the [entry][Entry] stored under the given [path].
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will attempt to retrieve whatever
     * is stored under the `name` in the [entries] of that layer.
     *
     * @param [path] the path to look up the `entry` under
     *
     * @throws [NoSuchElementException] if no [entry][Entry] is stored under the specified [path]
     *
     * @see [getValue]
     */
    fun <T> getNullableValue(path: String): T?

    /**
     * Returns the raw `default` of the [entry][Entry] stored under the given [path], or it throws a
     * [NoSuchElementException] if no `entry` is found.
     *
     * As this function is `null-safe` it is not recommended to use this to retrieve the `default` of an `entry` that
     * is of the [nullable][NullableValue] type, as it may return a value that's `null`, which will result in a
     * [IllegalArgumentException] being thrown. It is recommended to use [getNullableDefaultValue] for the retrieval of
     * such entries instead. If you do not know what type the `entry` is before-hand, it is also recommended to use
     * that function.
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will attempt to retrieve whatever
     * is stored under the `name` in the [entries] of that layer.
     *
     * @param [path] the path to look up the `entry` under
     *
     * @throws [NoSuchElementException] if no [entry][Entry] is stored under the specified [path]
     * @throws [IllegalArgumentException]
     *
     * - If the given [path] points to an [entry][Entry] that does not have have a `default` property.
     * - If the returned [entry][Entry] is of the [nullable][NullableValue] type.
     *
     * @see [getNullableDefaultValue]
     */
    fun <T> getDefaultValue(path: String): T

    /**
     * Returns the raw nullable `default` of the [entry][Entry] stored under the specified [path].
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will attempt to retrieve whatever
     * is stored under the `name` in the [entries] of that layer.
     *
     * @param [path] the path to look up the `entry` under
     *
     * @throws [NoSuchElementException] if no [entry][Entry] is stored under the specified [path]
     * @throws [IllegalArgumentException] if the given [path] points to an [entry][Entry] that does not have have a
     * `default` property
     *
     * @see [getDefaultValue]
     */
    fun <T> getNullableDefaultValue(path: String): T?

    /**
     * Adds the specified [entry] to this layer.
     *
     * The `entry` gets stored under it's [name][Entry.name] property.
     *
     * @param [entry] the [Entry] to add to this layer
     *
     * @throws [IllegalArgumentException] if the [name][Entry.name] of the `entry` contains any `'/'` characters
     */
    fun addEntry(entry: Entry<*>): ConfigLayer

    /**
     * Adds the specified [entry] to this layer.
     *
     * @param [entry] the [Entry] to add to this layer
     * @param [name] the name to store the [entry] under
     *
     * @throws [IllegalArgumentException] if [name] contains any `'/'` characters
     */
    fun addEntry(name: String, entry: Entry<*>): ConfigLayer

    /**
     * Adds all the given [entries] to this layer.
     */
    fun addEntries(entries: Iterable<Entry<*>>): ConfigLayer

    /**
     * Removes the entry stored under the given [path] from this layer.
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will attempt to remove whatever
     * is stored under the `name` in the [entries] of that layer.
     *
     * @param [path] the path to the entry to remove
     *
     * @return [success][Success] containing the removed value if one was successfully found and removed, or
     * [failure][Failure] if none could be found
     *
     * @throws [NoSuchElementException] if no `entry` in this layer *(or any child layers)* is stored under the given
     * [path]
     */
    fun removeEntry(path: String): ConfigResult<Entry<*>>

    /**
     * Returns the [entry][Entry] stored under the given [path], or throws a [NoSuchElementException] if none is
     * found.
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will attempt to retrieve whatever
     * is stored under the `name` in the [entries] of that layer.
     */
    fun <T> getEntry(path: String): Entry<T>

    /**
     * Returns the [entry][Entry] stored under the given [path], or [None] if none can be found.
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will attempt to retrieve whatever
     * is stored under the `name` in the [entries] of that layer.
     */
    fun <T> getEntryOrNone(path: String): Option<Entry<T>>

    /**
     * Returns whether or not there's an entry stored under the given [path] in this layer, or any of it' sub-layers.
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will check whether or not that
     * layers' [entries] contains the `name`.
     *
     * @param [path] the path to the entry
     */
    operator fun contains(path: String): Boolean

    /**
     * Creates a new [AbstractConfigLayer] from the given [path] and then adds it to this layer.
     *
     * This function will create new layers from *all* the non-existent layers in the given [path].
     *
     * Note that the [path] `string` should ***not*** start with the [name] of the layer that it is being added to, as
     * the system assumes that all layers given in the `path` parameter are *new* entries, i.e;
     *
     * Suppose you're invoking this function on a layer called `"green_things"` and you invoke this function with the
     * `path` parameter set to `"green_things/fruits/apples/"` then the following layers will be created:
     *
     * - `"green_things"` which is a child of the current layer which is also called `"green_things"`
     * - `"fruits"` which is a child of the `"green_things"` layer created above
     * - `"apples"` which is a child of the `"fruits"` layer
     *
     * this means that in the end we have a hierarchy looking like this `"green_things"` -> `"green_things"` ->
     * `"fruits"` -> `"apples"`, where `->` denotes that the previous `layer` is the `parent` of the next layer.
     *
     * @param [path] The `string` to create layers according to
     *
     * Note that this `string` ***needs*** to end with the `'/'` character, or an [IllegalArgumentException] will be
     * thrown.
     *
     * @return [success][Success] containing the last layer in the given [path] if all layers were successfully created,
     * otherwise [failure][Failure]
     *
     * @throws [IllegalArgumentException] if the given [path] does ***not*** end with the `'/'` character, or if
     * the specified `path` is not a valid path
     */
    fun addLayer(path: String): ConfigResult<ConfigLayer>

    /**
     * Creates new layers from the given [paths] and then adds all of them to this layer.
     *
     * This function will create new layers from *all* the non-existent layers in the given [paths].
     *
     * Note that the `strings` defined in the specified [paths] should ***not*** start with the [name] of the layer
     * that it is being added to, as the system assumes that all layers given in the `path` parameter are *new*
     * entries, i.e;
     *
     * Suppose you're invoking this function on a layer called `"green_things"` and you invoke this function with the
     * `path` parameter set to `"green_things/fruits/apples/"` then the following layers will be created:
     *
     * - `"green_things"` which is a child of the current layer which is also called `"green_things"`
     * - `"fruits"` which is a child of the `"green_things"` layer created above
     * - `"apples"` which is a child of the `"fruits"` layer
     *
     * this means that in the end we have a hierarchy looking like this `"green_things"` -> `"green_things"` ->
     * `"fruits"` -> `"apples"`, where `->` denotes that the previous `layer` is the `parent` of the next layer.
     *
     * @param [paths] The `strings` to create layers according to.
     *
     * Note that every single `string` contained in this array ***need*** to end with the `'/'` character, or an
     * [IllegalArgumentException] will be thrown.
     *
     * @return `this` layer
     *
     * @throws [IllegalArgumentException] if one of the `strings` in the specified [paths] does ***not*** end with the
     * `'/'` character, or if one of the `strings` is not a valid path
     */
    fun addLayers(vararg paths: String): ConfigLayer

    /**
     * Adds the given [layer] to this layer as a sub-layer.
     *
     * The `layer` is added to this layer using its [name] as the key.
     *
     * Note that when adding an already existing [ConfigLayer] instance to another, the [parent] of the given `layer`
     * will be changed to `this` layer, and any instance of `layer` in its old `parent` will be removed, this is to
     * make sure that the layer hierarchy is properly preserved.
     *
     * Whether or not a `ConfigLayer` can *actually* be added to another is highly implementation dependant, and there
     * is no guarantee that one layer can be added to another.
     *
     * @return the given `layer`
     *
     * @throws [IllegalArgumentException] if the [parent] of the given `layer` is ***not*** `this` or `None`
     */
    fun addLayer(layer: ConfigLayer): ConfigLayer

    /**
     * Removes the the [layer][AbstractConfigLayer] stored under the given [path] from this layer.
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the layer, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"]`, it will then scope into each `layer`
     * recursively until it reaches a dead-end, at which point it will attempt to remove the layer it is currently on
     * if its [name] matches the last entry of the sequence.
     *
     * @return [success][Success] containing the removed layer if one was successfully found and removed, or
     * [failure][Failure] if none could be found
     *
     * @throws [IllegalArgumentException] if `path` does ***not*** end with the `'/'` character
     */
    fun removeLayer(path: String): ConfigResult<ConfigLayer>

    /**
     * Removes the given [layer] from this layer.
     *
     * @return [success][Success] containing the removed layer if one was successfully found and removed, or
     * [failure][Failure] if none could be found
     */
    fun removeLayer(layer: ConfigLayer): ConfigResult<ConfigLayer>

    /**
     * Returns the [layer][AbstractConfigLayer] stored under the given [path], or throws a [NoSuchElementException] if none is
     * found.
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the layer, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two"`, that string will then be split up into
     * the following sequence; `layers:["layer_one", "layer_two"]`, it will then scope into each `layer` recursively
     * until it reaches a dead-end, at which point it will attempt to retrieve the current layer it is, if  its [name]
     * matches the last entry of the sequence, otherwise failing in some implementation specific fashion.
     */
    fun getLayer(path: String): ConfigLayer

    /**
     * Returns the [layer][AbstractConfigLayer] stored under the given [path], or [None] if none is found.
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the layer, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two"`, that string will then be split up into
     * the following sequence; `layers:["layer_one", "layer_two"]`, it will then scope into each `layer` recursively
     * until it reaches a dead-end, at which point it will attempt to retrieve the current layer it is, if  its [name]
     * matches the last entry of the sequence, otherwise failing in some implementation specific fashion.
     */
    fun getLayerOrNone(path: String): Option<ConfigLayer>

    // -- OPERATORS -- \\
    // i've opted to put the operators in the actual class rather than extensions because IntelliJ still doesn't play
    // *too* nicely with actually importing certain operators, which means that it can be quite a pain in the ass
    // to actually use operators if they're located in outside of the class that's already imported.
    /**
     * Sets the value of the [Entry] stored under the given [path] to the specified [value].
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will attempt to retrieve whatever
     * is stored under the `name` in the [entries] of that layer.
     *
     * @param [path] the path of the entry to modify the value of
     * @param [value] the new value
     *
     * @throws [NoSuchElementException] if there exists no [Entry] stored under the specified [path].
     * @throws [IllegalArgumentException]
     *
     * - If the found [Entry] is not a [nullable][NullableValue] type, but the specified [value] is `null`
     * - If the found [Entry] is of the [limited][LimitedValue] type, but the specified [value] is not [Comparable]
     * - If the found [Entry] is of a type that does not support `set` operations *([constant][ConstantValue],
     * [lazy][LazyValue] or [dynamic][DynamicValue])*
     */
    @JvmDefault @JvmSynthetic operator fun <T : Any?> set(path: String, value: T?) {
        this.setValue(path, value)
    }

    /**
     * Returns the raw `value` of the [entry][Entry] stored under the given [path], or it throws a
     * [NoSuchElementException] if no `entry` is found.
     *
     * As this function is `null-safe` it is not recommended to use this to retrieve the value of an `entry` that is
     * of the [nullable][NullableValue] type, as it may return a value that's `null`, which will result in a
     * [IllegalArgumentException] being thrown. It is recommended to use [getNullableValue] for the retrieval of such
     * entries instead. If you do not know what type the `entry` is before-hand, it is also recommended to use that
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
     * @param [path] the path to look up the `entry` under
     *
     * @throws [NoSuchElementException] if no [entry][Entry] is stored under the specified [path]
     * @throws [IllegalArgumentException] if the returned [entry][Entry]  is of the [nullable][NullableValue]
     * type
     *
     * @see [getNullableValue]
     */
    @JvmDefault @JvmSynthetic operator fun <T : Any> get(path: String): T = getValue(path)

    /**
     * Returns the raw `value` of the [entry][Entry] stored under the given [path], or it throws a
     * [NoSuchElementException] if no `entry` is found.
     *
     * As this function is `null-safe` it is not recommended to use this to retrieve the value of an `entry` that is
     * of the [nullable][NullableValue] type, as it may return a value that's `null`, which will result in a
     * [IllegalArgumentException] being thrown. It is recommended to use [getNullableValue] for the retrieval of such
     * entries instead. If you do not know what type the `entry` is before-hand, it is also recommended to use that
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
     * @param [path] the path to look up the `entry` under
     *
     * @throws [NoSuchElementException] if no [entry][Entry] is stored under the specified [path]
     * @throws [IllegalArgumentException] if the returned [entry][Entry]  is of the [nullable][NullableValue]
     * type
     *
     * @see [getNullableValue]
     */
    @JvmDefault @JvmSynthetic operator fun <T : Any> invoke(path: String): T = getValue(path)

    /**
     * Adds the specified [entry] to this layer.
     *
     * The `entry` gets stored under it's [name][Entry.name] property.
     *
     * @param [entry] the [Entry] to add to this layer
     *
     * @throws [IllegalArgumentException] if the [name][Entry.name] of the `entry` contains any `'/'` characters
     */
    @JvmDefault @JvmSynthetic operator fun plusAssign(entry: Entry<*>) {
        this.addEntry(entry)
    }

    /**
     * Removes the entry stored under the given [path] from this layer.
     *
     * This function works in the way that it delimits the given `path` based on the `/` character, and it assumes that
     * the last entry of the delimited array is the name of the entry, i.e;
     *
     * Suppose you invoke this function with `path` as `"layer_one/layer_two/entry_one"`, that string will then be
     * split up into the following sequence; `layers:["layer_one", "layer_two"], name:"entry_one"`, it will then scope
     * into each `layer` recursively until it reaches a dead-end, at which point it will attempt to remove whatever
     * is stored under the `name` in the [entries] of that layer.
     *
     * @param [path] the path to the entry to remove
     *
     * @return [success][Success] containing the removed value if one was successfully found and removed, or
     * [failure][Failure] if none could be found
     *
     * @throws [NoSuchElementException] if no `entry` in this layer *(or any child layers)* is stored under the given
     * [path]
     */
    @JvmDefault @JvmSynthetic operator fun minusAssign(path: String) {
        this.removeEntry(path)
    }

    // -- DELEGATES -- \\
    /**
     * Delegates a property to the entry stored under the given [path].
     *
     * ```kotlin
     *  val config: Config = ...
     *
     *  val prop: String by config.delegateTo("entry/name")
     * ```
     */
    fun <T : Any> delegateTo(path: String): ReadWriteProperty<ConfigLayer, T>

    /**
     * Delegates a property to the nullable entry stored under the given [path].
     *
     * ```kotlin
     *  val config: Config = ...
     *
     *  val prop: String by config.delegateToNullable("entry/name")
     * ```
     */
    fun <T : Any?> delegateToNullable(path: String): ReadWriteProperty<ConfigLayer, T?>

    /**
     * Returns an `iterator` that iterates over the [entries] contained in this layer.
     */
    override fun iterator(): Iterator<Entry<*>>
}

