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

import moe.kanon.kommons.affirmThat
import moe.kanon.kommons.reflection.isKotlinClass
import moe.kanon.kommons.reflection.isObject
import moe.kanon.kommons.writeOut
import moe.kanon.konfig.ConfigException
import moe.kanon.konfig.entries.ConstantEntry
import moe.kanon.konfig.entries.DynamicEntry
import moe.kanon.konfig.entries.Entry
import moe.kanon.konfig.entries.LazyEntry
import moe.kanon.konfig.entries.LimitedEntry
import moe.kanon.konfig.entries.LimitedStringEntry
import moe.kanon.konfig.entries.NormalEntry
import moe.kanon.konfig.entries.NullableEntry
import moe.kanon.konfig.entries.delegates.DelegatedConstantProperty
import moe.kanon.konfig.entries.delegates.DelegatedDynamicProperty
import moe.kanon.konfig.entries.delegates.DelegatedLazyProperty
import moe.kanon.konfig.entries.delegates.DelegatedLimitedProperty
import moe.kanon.konfig.entries.delegates.DelegatedLimitedStringProperty
import moe.kanon.konfig.entries.delegates.DelegatedNormalProperty
import moe.kanon.konfig.entries.delegates.DelegatedNullableProperty
import moe.kanon.konfig.entries.values.LimitedStringValue
import moe.kanon.konfig.entries.values.LimitedValue
import moe.kanon.konfig.entries.values.NormalValue
import moe.kanon.konfig.entries.values.NullableValue
import moe.kanon.konfig.entries.values.ValueSetter
import java.lang.UnsupportedOperationException
import kotlin.reflect.KCallable
import kotlin.reflect.KClass
import kotlin.reflect.typeOf

/**
 * A [ConfigLayer] used for constructing layers from `object` instances, allowing for type-safe construction and
 * retrieval of [entries].
 *
 * An `object` that inherits from `this` may nest multiple other `ObjectLayer` implementation layers inside itself,
 * they will all be recursively collected and added to the layer as sub-layers upon creation.
 *
 * This class provides functions for creating type-safe [entries] via the use of Kotlin delegation; [nullable],
 * [normal], [limited], [constant], [lazy] and [dynamic]. The properties created using these functions can safely be
 * accessed from outside the class and can also be changed, any changes will be reflected in the actual layer.
 *
 * ```kotlin
 *  object LayerOne : ObjectLayer("layer_one") {
 *      val constantValue: String by constant(description = "This is a constant value", value = "Hello")
 *
 *      var mutableValue: Int by normal(description = "This is a mutable value", default = 42)
 *
 *      object LayerTwo : ObjectLayer("layer_two") {
 *          // etc...
 *      }
 *  }
 * ```
 *
 * @param [name] The [name][ConfigLayer.name] that the layer should use.
 *
 * If this is `null`, then the [simpleName][KClass.simpleName] of the inheriting class will be used instead.
 *
 * @throws [ConfigException] if the given `name` is `null` and the [simpleName][KClass.simpleName] of the inheriting
 * class returned `null`
 * @throws [UnsupportedOperationException] if the class inheriting `this` is not a Kotlin class and/or not an
 * [object](https://kotlinlang.org/docs/tutorials/kotlin-for-py/objects-and-companion-objects.html)
 *
 * @see [BasicLayer]
 */
@Suppress("LeakingThis")
abstract class ObjectLayer protected constructor(name: String? = null) : AbstractConfigLayer() {
    final override val name: String = name ?: this::class.simpleName
    ?: throw ConfigException("'simpleName' for inheriting class does not exist <${this::class}>")

    final override var path: String = "$name/"

    init {
        val clz = this::class
        affirmThat(clz.isKotlinClass) { "Inheritor is not a Kotlin class <${this::class}>" }
        affirmThat(clz.isObject) { "Inheritor is not an object <${this::class}>" }

        val children = clz.nestedClasses
            .asSequence()
            .mapNotNull { it.objectInstance }
            .filterIsInstance<ObjectLayer>()

        for (layer in children) this.addLayer(layer)
    }

    // -- DELEGATES -- \\
    /**
     * Creates a new [NullableEntry] using the given parameters, and then delegates the invoking property to its
     * [value][Entry.value].
     *
     * If the given `name` is `null` *(or simply not defined)* then the [name][KCallable.name] of the invoking property
     * will be used.
     */
    @UseExperimental(ExperimentalStdlibApi::class)
    protected inline fun <reified T : Any> nullable(
        name: String? = null,
        description: String,
        default: T?,
        value: T? = default,
        noinline setter: ValueSetter<NullableValue<T>, T?>.() -> Unit = { this.field = this.value }
    ) = object : DelegatedNullableProperty<T>(value, default, name, description, setter, typeOf<T>()) {}

    /**
     * Creates a new [NormalEntry] using the given parameters, and then delegates the invoking property to its
     * [value][Entry.value].
     *
     * If the given `name` is `null` *(or simply not defined)* then the [name][KCallable.name] of the invoking property
     * will be used.
     */
    @UseExperimental(ExperimentalStdlibApi::class)
    protected inline fun <reified T : Any> normal(
        name: String? = null,
        description: String,
        default: T,
        value: T = default,
        noinline setter: ValueSetter<NormalValue<T>, T>.() -> Unit = { this.field = this.value }
    ) = object : DelegatedNormalProperty<T>(value, default, name, description, setter, typeOf<T>()) {}

    /**
     * Creates a new [LimitedEntry] using the given parameters, and then delegates the invoking property to its
     * [value][Entry.value].
     *
     * If the given `name` is `null` *(or simply not defined)* then the [name][KCallable.name] of the invoking property
     * will be used.
     */
    @UseExperimental(ExperimentalStdlibApi::class)
    protected inline fun <reified T> limited(
        name: String? = null,
        description: String,
        range: ClosedRange<T>,
        default: T,
        value: T = default,
        noinline setter: ValueSetter<LimitedValue<T>, T>.() -> Unit = { this.field = this.value }
    ) where T : Comparable<T>, T : Any =
        object : DelegatedLimitedProperty<T>(value, default, range, name, description, setter, typeOf<T>()) {}

    /**
     * Creates a new [LimitedStringEntry] using the given parameters, and then delegates the invoking property to its
     * [value][Entry.value].
     *
     * If the given `name` is `null` *(or simply not defined)* then the [name][KCallable.name] of the invoking property
     * will be used.
     */
    @UseExperimental(ExperimentalStdlibApi::class)
    protected fun limited(
        name: String? = null,
        description: String,
        range: IntRange,
        default: String,
        value: String = default,
        setter: ValueSetter<LimitedStringValue, String>.() -> Unit = { this.field = this.value }
    ) = object : DelegatedLimitedStringProperty(value, default, range, name, description, setter) {}

    /**
     * Creates a new [ConstantEntry] using the given parameters, and then delegates the invoking property to its
     * [value][Entry.value].
     *
     * If the given `name` is `null` *(or simply not defined)* then the [name][KCallable.name] of the invoking property
     * will be used.
     */
    @UseExperimental(ExperimentalStdlibApi::class)
    protected inline fun <reified T : Any> constant(
        name: String? = null,
        description: String,
        value: T
    ) = object : DelegatedConstantProperty<T>(value, name, description, typeOf<T>()) {}

    /**
     * Creates a new [LazyEntry] using the given parameters, and then delegates the invoking property to its
     * [value][Entry.value].
     *
     * If the given `name` is `null` *(or simply not defined)* then the [name][KCallable.name] of the invoking property
     * will be used.
     */
    @UseExperimental(ExperimentalStdlibApi::class)
    protected inline fun <reified T : Any> lazy(
        name: String? = null,
        description: String,
        noinline closure: () -> T
    ) = object : DelegatedLazyProperty<T>(closure, name, description, typeOf<T>()) {}

    /**
     * Creates a new [DynamicEntry] using the given parameters, and then delegates the invoking property to its
     * [value][Entry.value].
     *
     * If the given `name` is `null` *(or simply not defined)* then the [name][KCallable.name] of the invoking property
     * will be used.
     */
    @UseExperimental(ExperimentalStdlibApi::class)
    protected inline fun <reified T : Any> dynamic(
        name: String? = null,
        description: String,
        noinline closure: () -> T
    ) = object : DelegatedDynamicProperty<T>(closure, name, description, typeOf<T>()) {}
}