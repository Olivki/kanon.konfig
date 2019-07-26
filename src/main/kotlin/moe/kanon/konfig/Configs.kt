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

@file:JvmName("Konfigs")

package moe.kanon.konfig

import com.google.gson.internal.`$Gson$Types`
import moe.kanon.konfig.dsl.KonfigContainer
import moe.kanon.konfig.dsl.LayerContainer
import moe.kanon.konfig.providers.AbstractProvider
import moe.kanon.konfig.providers.JsonProvider
import moe.kanon.konfig.providers.Provider
import moe.kanon.konfig.settings.KonfigSettings
import mu.KLogger
import mu.KotlinLogging
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.KClass

interface Konfig : Layer {
    companion object {
        /**
         * Creates a new [Konfig] from the specified [name].
         *
         * @param [name] The name of the configuration.
         *
         * As this function does not ask for a explicit [root] layer, one will be created from this name.
         */
        @JvmName("create")
        @JvmStatic operator fun invoke(name: String, file: Path, provider: Provider = JsonProvider()): AbstractKonfig =
            KonfigImpl(name, file, provider = provider)

        /**
         * Creates a new [Konfig] from the specified [name] and [settings].
         *
         * This function is intended to be used from the Java side, utilizing the builder pattern, example;
         *
         * ```java
         *  final Konfig.Settings settings = Konfig.Settings.getDefault()
         *                                                  .shouldPrintEntryType(false)
         *                                                  .onUnknownEntry(CREATE_NEW);
         *  final Konfig config = Konfig.create("config", settings);
         * ```
         *
         * @param [name] The name of the configuration.
         *
         * As this function does not ask for a explicit [root] layer, one will be created from this name.
         * @param [settings] The custom settings to apply.
         */
        @JvmName("create")
        @JvmStatic operator fun invoke(
            name: String,
            file: Path,
            settings: KonfigSettings = KonfigSettings.default,
            provider: Provider = JsonProvider()
        ): AbstractKonfig = KonfigImpl(name, file, settings = settings, provider = provider)

        /**
         * Creates a new [Konfig] which inherits everything from the specified [layer].
         *
         * @param [layer] The [layer][Layer] to act as a [root] `layer` for the newly created configuration.
         *
         * The newly created configuration will inherit everything from this `layer`, even it's name and any sub-layers
         * already connected to it.
         */
        @JvmName("from")
        @JvmStatic operator fun invoke(layer: Layer, file: Path, provider: Provider = JsonProvider()): AbstractKonfig =
            KonfigImpl(layer.name, file, layer, provider = provider)

        /**
         * Creates a new [Konfig] which inherits everything from the specified [layer], and applies the specified
         * [settings] to the newly created instance.
         *
         * This function is intended to be used from the Java side, utilizing the builder pattern, example;
         *
         * ```java
         *  final Layer layer = ...
         *  final Konfig.Settings settings = Konfig.Settings.getDefault()
         *                                                  .printDefaultValue(false)
         *                                                  .onUnknownEntry(IGNORE);
         *  final Konfig config = Konfig.from(layer, settings);
         * ```
         *
         * @param [layer] The [layer][Layer] to act as a [root] `layer` for the newly created configuration.
         *
         * The newly created configuration will inherit everything from this `layer`, even it's name and any sub-layers
         * already connected to it.
         * @param [settings] The custom settings to apply.
         */
        @JvmName("from")
        @JvmStatic operator fun invoke(
            layer: Layer,
            file: Path,
            settings: KonfigSettings = KonfigSettings.default,
            provider: Provider = JsonProvider()
        ): AbstractKonfig = KonfigImpl(layer.name, file, layer, settings, provider)
    }

    /**
     * The underlying logger for `this` config.
     */
    val logger: KLogger
    
    /**
     * The provider for `this` config.
     *
     * The provider is the component that actually saves and loads *(serializes/deserializes)* all the entries and the
     * metadata regarding them.
     */
    val provider: Provider
    
    /**
     * The underlying file of `this` configuration.
     *
     * This file is where all the data will be saved and loaded from, if the given [path][Path] does
     * [not exist][Files.notExists] then a file will be created populated with all the default values.
     */
    val file: Path
    
    /**
     * The underlying root [layer][Layer] of `this` configuration.
     *
     * If not explicitly defined at the creation, the root layer will be created from the specified [name] of `this`
     * configuration.
     *
     * Note that all layer related functions available on `this` configuration are all provided by this root layer via
     * delegation, that means that doing `config["entryName"]` is the exact same as doing `config.root["entryName"]`.
     */
    val root: Layer
    
    /**
     * The underlying settings of `this` configuration.
     *
     * This allows the user to customize various parts of how the system interacts with all the data.
     *
     * Unless explicitly stated during the creation of `this` configuration, this will have been set to
     * [KonfigSettings.default].
     *
     * It is ***not*** recommended to change the values of this at any time except for during creation.
     */
    val settings: KonfigSettings
    
    /**
     * Attempts to load and set any changed entries from the [file] tied to `this` config.
     *
     * If [file] does not exist, a file will be created at its path filled with default values.
     */
    @JvmDefault fun loadFromFile() = provider.loadFrom(file)
    
    /**
     * Attempts to save all the entries and values of `this` config to the [file] tied to `this` config.
     */
    @JvmDefault fun saveToFile() = provider.saveTo(file)
}

/**
 * Creates a new [Konfig] from the specified [name], and applies the specified [settings] to it.
 *
 * @param [name] The name of the configuration.
 *
 * As this function does not ask for a explicit [root] layer, one will be created from this name.
 * @param [settings] The custom settings to create the config with.
 */
@JvmOverloads fun configOf(
    name: String,
    file: Path,
    provider: Provider = JsonProvider(),
    settings: KonfigSettings = KonfigSettings.default
): AbstractKonfig = Konfig(name, file, settings = settings, provider = provider)

/**
 * Creates a new [Konfig] which inherits everything from the specified [layer], and applies the specified [settings] to
 * it.
 *
 * @param [layer] The [layer][Layer] to act as a [root] `layer` for the newly created configuration.
 *
 * The newly created configuration will inherit everything from this `layer`, even it's name and any sub-layers
 * already connected to it.
 * @param [settings] The custom settings to create the config with.
 */
@JvmOverloads fun configFrom(
    layer: Layer,
    file: Path,
    provider: Provider = JsonProvider(),
    settings: KonfigSettings = KonfigSettings.default
): Konfig = Konfig(layer, file, settings = settings, provider = provider)

@JvmOverloads @KonfigContainer inline fun createConfig(
    name: String,
    file: Path,
    settings: KonfigSettings = KonfigSettings.default,
    provider: Provider = JsonProvider(),
    closure: LayerContainer.() -> Unit
): AbstractKonfig {
    val container = LayerContainer(name).apply(closure)
    return KonfigImpl(
        name,
        file,
        root = container.layer,
        settings = settings,
        provider = provider,
        delegateContainer = container
    )
}

abstract class AbstractKonfig : Konfig {
    /**
     * The underlying [LayerContainer] of the [root] layer of `this` konfig.
     */
    abstract val container: LayerContainer
    
    /**
     * Scopes into the underlying [LayerContainer] of `this` konfig.
     *
     * This serves as a direct entry-point into the DSL for creating entries.
     */
    @JvmSynthetic inline fun modify(closure: LayerContainer.() -> Unit) {
        container.apply(closure)
    }
    
    /**
     * Creates and adds a [Layer] to `this` layer from the specified [name] and [closure].
     */
    @JvmSynthetic inline fun addLayer(name: String, closure: LayerContainer.() -> Unit) {
        container.addLayer(name, closure)
    }
}

/**
 * An implementation of [Konfig].
 *
 * This is the class that is used for all the factory methods that `Konfig` has.
 */
data class KonfigImpl @JvmOverloads constructor(
    override val name: String,
    override val file: Path,
    override val root: Layer = KonfigLayer(name),
    override val settings: KonfigSettings = KonfigSettings.default,
    override val provider: Provider = JsonProvider(),
    private val delegateContainer: LayerContainer? = null
) : AbstractKonfig(), Layer by root {
    
    override val logger: KLogger = KotlinLogging.logger { }
    
    override val container: LayerContainer = delegateContainer ?: LayerContainer(name, delegate = this)
    
    init {
        // a provider class should always be inheriting from the AbstractProvider, and by doing this we provide
        // the user with an immutable view of the 'config' property.
        (provider as AbstractProvider).config = this
    }
}