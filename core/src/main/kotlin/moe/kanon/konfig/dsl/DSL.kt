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

package moe.kanon.konfig.dsl

import moe.kanon.konfig.Config
import moe.kanon.konfig.ConfigSettings
import moe.kanon.konfig.entries.ConstantEntry
import moe.kanon.konfig.entries.DynamicEntry
import moe.kanon.konfig.entries.LazyEntry
import moe.kanon.konfig.entries.LimitedEntry
import moe.kanon.konfig.entries.LimitedStringEntry
import moe.kanon.konfig.entries.NormalEntry
import moe.kanon.konfig.entries.NullableEntry
import moe.kanon.konfig.entries.values.ConstantValue
import moe.kanon.konfig.entries.values.DynamicValue
import moe.kanon.konfig.entries.values.LazyValue
import moe.kanon.konfig.entries.values.LimitedStringValue
import moe.kanon.konfig.entries.values.LimitedValue
import moe.kanon.konfig.entries.values.NormalValue
import moe.kanon.konfig.entries.values.NullableValue
import moe.kanon.konfig.entries.values.ValueSetter
import moe.kanon.konfig.internal.typeTokenOf
import moe.kanon.konfig.layers.AbstractConfigLayer
import moe.kanon.konfig.layers.BasicLayer
import moe.kanon.konfig.providers.ConfigProvider
import moe.kanon.konfig.providers.ConfigProviderFinder
import java.nio.file.Path
import kotlin.reflect.typeOf

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION)
annotation class ConfigDsl

/**
 * A container used for creating a DSL for [Config] instances.
 *
 * @property [name] The name of `this` container.
 * @property [parent] The parent of `this` container, if it has one.
 * @property [layer] The underlying [config-layer][AbstractConfigLayer] of `this` container.
 */
@ConfigDsl
class LayerContainer(
    val name: String,
    val parent: LayerContainer? = null,
    val layer: AbstractConfigLayer = parent?.let { BasicLayer(name, it.layer) } ?: BasicLayer(name)
) {
    /**
     * Returns the `path` of `this` layer container.
     *
     * Note that this is the path that will be supplied to the `path` property of the [AbstractConfigLayer] instance this
     * container creates.
     */
    val path: String = if (parent != null) "${parent.path}$name/" else "$name/"

    // -- LAYERS -- \\
    /**
     * Creates and adds a [AbstractConfigLayer] to `this` layer from the specified [name] and [closure].
     */
    @ConfigDsl
    inline fun layer(name: String, closure: LayerContainer.() -> Unit) {
        val subContainer = LayerContainer(name, this).apply(closure)
        layer.addLayer(subContainer.layer)
    }

    /**
     * Adds the specified [delegate] to `this` layer and then scopes into it with the specified [closure].
     *
     * The `name` property of the resulting `container` is taken from the `name` of the specified [delegate].
     */
    @ConfigDsl
    inline fun layer(delegate: AbstractConfigLayer, closure: LayerContainer.() -> Unit) {
        val subContainer = LayerContainer(delegate.name, this, delegate).apply(closure)
        layer.addLayer(subContainer.layer)
    }

    // -- ENTRIES -- \\
    // much like how the 'delegate' part of the system works, this one also makes use of reified generics and creating
    // anonymous classes to avoid the type erasure, which means that this DSL really isn't usable from the Java side,
    // and as such, it will be hidden from it. (In truth, most of this library isn't very usable from the Java side,
    // and even if some parts are, it's far from being idiomatic.)
    @ConfigDsl
    @UseExperimental(ExperimentalStdlibApi::class)
    @JvmSynthetic inline fun <reified T : Any> nullableValue(
        name: String,
        description: String,
        default: T?,
        value: T? = default,
        noinline setter: ValueSetter<NullableValue<T>, T?>.() -> Unit = { this.field = this.value }
    ) {
        val kotlinType = typeOf<T>()
        val javaType = typeTokenOf<T>().type
        layer += NullableEntry(name, description, kotlinType, javaType, value, default, setter)
    }

    @ConfigDsl
    @UseExperimental(ExperimentalStdlibApi::class)
    @JvmSynthetic inline fun <reified T : Any> normalValue(
        name: String,
        description: String,
        default: T,
        value: T = default,
        noinline setter: ValueSetter<NormalValue<T>, T>.() -> Unit = { this.field = this.value }
    ) {
        val kotlinType = typeOf<T>()
        val javaType = typeTokenOf<T>().type
        layer += NormalEntry(name, description, kotlinType, javaType, value, default, setter)
    }

    @ConfigDsl
    @UseExperimental(ExperimentalStdlibApi::class)
    @JvmSynthetic inline fun <reified T : Comparable<T>> limitedValue(
        name: String,
        description: String,
        default: T,
        range: ClosedRange<T>,
        value: T = default,
        noinline setter: ValueSetter<LimitedValue<T>, T>.() -> Unit = { this.field = this.value }
    ) {
        val kotlinType = typeOf<T>()
        val javaType = typeTokenOf<T>().type
        layer += LimitedEntry(name, description, kotlinType, javaType, value, default, range, setter)
    }

    @ConfigDsl
    @UseExperimental(ExperimentalStdlibApi::class)
    @JvmSynthetic fun limitedStringValue(
        name: String,
        description: String,
        default: String,
        range: IntRange,
        value: String = default,
        setter: ValueSetter<LimitedStringValue, String>.() -> Unit = { this.field = this.value }
    ) {
        layer += LimitedStringEntry(name, description, value, default, range, setter)
    }

    @ConfigDsl
    @UseExperimental(ExperimentalStdlibApi::class)
    @JvmSynthetic inline fun <reified T : Any> constantValue(name: String, description: String, value: T) {
        val kotlinType = typeOf<T>()
        val javaType = typeTokenOf<T>().type
        layer += ConstantEntry(name, description, kotlinType, javaType, value)
    }

    @ConfigDsl
    @UseExperimental(ExperimentalStdlibApi::class)
    @JvmSynthetic inline fun <reified T : Any> lazyValue(name: String, description: String, noinline value: (() -> T)) {
        val kotlinType = typeOf<T>()
        val javaType = typeTokenOf<T>().type
        layer += LazyEntry(name, description, kotlinType, javaType, value)
    }

    @ConfigDsl
    @UseExperimental(ExperimentalStdlibApi::class)
    @JvmSynthetic inline fun <reified T : Any> dynamicValue(
        name: String,
        description: String,
        noinline value: (() -> T)
    ) {
        val kotlinType = typeOf<T>()
        val javaType = typeTokenOf<T>().type
        layer += DynamicEntry(name, description, kotlinType, javaType, value)
    }
}

@ConfigDsl
@UseExperimental(ExperimentalStdlibApi::class)
fun buildConfig(
    name: String,
    file: Path,
    settings: ConfigSettings = ConfigSettings.default,
    provider: ConfigProvider = ConfigProviderFinder.findProvider(file, ClassLoader.getSystemClassLoader()).unwrap(),
    scope: LayerContainer.() -> Unit
): Config = Config(name, file, LayerContainer(name).apply(scope).layer, settings, provider)