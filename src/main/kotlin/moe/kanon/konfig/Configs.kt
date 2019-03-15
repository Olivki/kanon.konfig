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
import moe.kanon.konfig.entries.Entry
import moe.kanon.konfig.providers.JsonProvider
import moe.kanon.konfig.providers.Provider
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.reflect.KClass

interface Konfig : Layer {
    
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
     * The underlying [mapper][ObjectMapper] of `this` configuration.
     *
     * Note that this is mapper is the exact one that the [provider] uses, which means that this can be used to
     * customize the serialization process *(not the serialization of the actual entry metadata and layer parts, as
     * those are hard-wired into the system.)* for the values.
     */
    //@JvmDefault
    //val mapper: ObjectMapper
    //    get() = provider.mapper
    
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
     * [Settings.default].
     *
     * It is ***not*** recommended to change the values of this at any time except for during creation.
     */
    val settings: Settings
    
    /**
     * A container of settings for how a [Konfig] instance should behave.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    class Settings private constructor() {
        
        // the setters are all marked as "synthetic" so that they don't clutter the namespace, as they only return
        // unit, which makes them less than ideal for replicating the builder pattern from the java side.
        
        /**
         * What sort of action the system should take when encountering a unknown [entry][Entry] in the
         * [config file][file] during the loading process.
         *
         * This setting only affects the [provider.loadFrom][Provider.loadFrom] operation, all `get` operations
         * regarding entries will still behave the same.
         *
         * ([FAIL][UnknownEntryBehaviour.FAIL] by default)
         */
        @set:JvmSynthetic
        var onUnknownEntry: UnknownEntryBehaviour = UnknownEntryBehaviour.FAIL
        
        /**
         * What sort of action the system should take when encountering a unknown [entry][Entry] in the
         * [config file][file] during the loading process.
         *
         * This setting only affects the [provider.loadFrom][Provider.loadFrom] operation, all `get` operations
         * regarding entries will still behave the same.
         */
        fun onUnknownEntry(behaviour: UnknownEntryBehaviour) = apply { onUnknownEntry = behaviour }
        
        /**
         * Whether or not the system should append what type of [value][Entry.Value] each [entry][Entry] is storing.
         *
         * If the configuration file is supposed to be read and modified by a user, it is probably for the better to
         * have this set to `true`, as it can make it easier for the user to understand what types they can set the
         * value to.
         *
         * Note that this is *purely* visual, and the system does not actually look at this value when the
         * [config file][file] is being loaded.
         */
        @set:JvmSynthetic
        var shouldPrintEntryType: Boolean = true
        
        /**
         * Whether or not the system should append what type of [value][Entry.Value] each [entry][Entry] is storing.
         *
         * If the configuration file is supposed to be read and modified by a user, it is probably for the better to
         * have this set to `true`, as it can make it easier for the user to understand what types they can set the
         * value to.
         *
         * Note that this is *purely* visual, and the system does not actually look at this value when the
         * [config file][file] is being loaded.
         */
        fun shouldPrintEntryType(shouldPrintEntryType: Boolean) =
            apply { this.shouldPrintEntryType = shouldPrintEntryType }
        
        companion object {
            /**
             * The default settings used by the system.
             */
            @JvmStatic
            val default: Settings
                get() = Settings()
        }
        
        /**
         * Represents an action the system will take when encountering an unknown `entry` when loading the
         * configuration from the [file].
         */
        enum class UnknownEntryBehaviour {
            /**
             * The system will fail loudly and throw a [UnknownEntryException] when it encounters an unknown `entry` in
             * the [config file][file].
             */
            FAIL,
            /**
             * The system will quietly continue on as if nothing happened when it encounters an unknown `entry` in the
             * [config file][file].
             */
            IGNORE,
            /**
             * The system will attempt to create a wholly new `entry` from the available entry data in the
             * [config file][file] when it encounters an unknown `entry`.
             *
             * If the `entry` is stored under an unknown `layer`, then that `layer` will also be created.
             */
            CREATE_NEW
        }
    }
    
    companion object {
        /**
         * Creates a new [Konfig] from the specified [name].
         *
         * @param [name] The name of the configuration.
         *
         * As this function does not ask for a explicit [root] layer, one will be created from this name.
         */
        @JvmStatic
        @JvmName("create")
        operator fun invoke(name: String, file: Path): Konfig = KonfigImpl(name, file)
        
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
        @JvmStatic
        @JvmName("create")
        operator fun invoke(name: String, file: Path, settings: Settings): Konfig =
            KonfigImpl(name, file, settings = settings)
        
        /**
         * Creates a new [Konfig] from the specified [name], and applies the specified [settings] to the newly
         * created instance.
         *
         * @param [name] The name of the configuration.
         *
         * As this function does not ask for a explicit [root] layer, one will be created from this name.
         * @param [settings] The custom settings to apply.
         */
        @JvmSynthetic
        @JvmName("create")
        operator fun invoke(name: String, file: Path, settings: Settings.() -> Unit): Konfig =
            KonfigImpl(name, file, settings = Settings.default.apply(settings))
        
        /**
         * Creates a new [Konfig] which inherits everything from the specified [layer].
         *
         * @param [layer] The [layer][Layer] to act as a [root] `layer` for the newly created configuration.
         *
         * The newly created configuration will inherit everything from this `layer`, even it's name and any sub-layers
         * already connected to it.
         */
        @JvmStatic
        @JvmName("from")
        operator fun invoke(layer: Layer, file: Path): Konfig = KonfigImpl(layer.name, file, layer)
        
        /**
         * Creates a new [Konfig] which inherits everything from the specified [layer], and applies the specified
         * [settings] to the newly created instance.
         *
         * This function is intended to be used from the Java side, utilizing the builder pattern, example;
         *
         * ```java
         *  final Layer layer = ...
         *  final Konfig.Settings settings = Konfig.Settings.getDefault()
         *                                                  .shouldPrintEntryType(false)
         *                                                  .onUnknownEntry(CREATE_NEW);
         *  final Konfig config = Konfig.from(layer, settings);
         * ```
         *
         * @param [layer] The [layer][Layer] to act as a [root] `layer` for the newly created configuration.
         *
         * The newly created configuration will inherit everything from this `layer`, even it's name and any sub-layers
         * already connected to it.
         * @param [settings] The custom settings to apply.
         */
        @JvmStatic
        @JvmName("from")
        operator fun invoke(layer: Layer, file: Path, settings: Settings): Konfig =
            KonfigImpl(layer.name, file, layer, settings)
        
        /**
         * Creates a new [Konfig] which inherits everything from the specified [layer], and applies the specified
         * [settings] to the newly created instance.
         *
         * @param [layer] The [layer][Layer] to act as a [root] `layer` for the newly created configuration.
         *
         * The newly created configuration will inherit everything from this `layer`, even it's name and any sub-layers
         * already connected to it.
         * @param [settings] The custom settings to apply.
         */
        @JvmSynthetic
        @JvmName("from")
        operator fun invoke(layer: Layer, file: Path, settings: Settings.() -> Unit): Konfig =
            KonfigImpl(layer.name, file, layer, Settings.default.apply(settings))
    }
}

/**
 * Creates a new [Konfig] from the specified [name].
 *
 * @param [name] The name of the configuration.
 *
 * As this function does not ask for a explicit [root] layer, one will be created from this name.
 */
fun configOf(name: String, file: Path): Konfig = Konfig(name, file)

/**
 * Creates a new [Konfig] from the specified [name], and applies the specified [settings] to it.
 *
 * @param [name] The name of the configuration.
 *
 * As this function does not ask for a explicit [root] layer, one will be created from this name.
 * @param [settings] The custom settings to create the config with.
 */
fun configOf(name: String, file: Path, settings: Konfig.Settings.() -> Unit): Konfig =
    Konfig(name, file, settings = Konfig.Settings.default.apply(settings))

/**
 * Creates a new [Konfig] which inherits everything from the specified [layer].
 *
 * @param [layer] The [layer][Layer] to act as a [root] `layer` for the newly created configuration.
 *
 * The newly created configuration will inherit everything from this `layer`, even it's name and any sub-layers
 * already connected to it.
 */
fun configFrom(layer: Layer, file: Path): Konfig = Konfig(layer, file)

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
fun configFrom(layer: Layer, file: Path, settings: Konfig.Settings.() -> Unit): Konfig =
    Konfig(layer, file, settings = Konfig.Settings.default.apply(settings))

/**
 * An implementation of [Konfig].
 *
 * This is the class that is used for all the factory methods that `Konfig` has.
 */
class KonfigImpl @JvmOverloads constructor(
    override val name: String,
    override val file: Path,
    override val root: Layer = KonfigLayer(name),
    override val settings: Konfig.Settings = Konfig.Settings.default
) : Konfig, Layer by root {
    
    // TODO: Make it changeable or something.
    override val provider: Provider = JsonProvider(this)
    
    init {
        //mapper.registerKotlinModule()
    }
    
    override fun toString(): String = "Konfig(name='$name', file='$file', root=$root)"
    
    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is KonfigImpl -> false
        name != other.name -> false
        file != other.file -> false
        root != other.root -> false
        settings != other.settings -> false
        provider != other.provider -> false
        else -> true
    }
    
    override fun hashCode(): Int = Objects.hash(name, file, root, settings, provider)
    
}

internal val Class<*>.isKotlinClass: Boolean
    get() = this.declaredAnnotations.any {
        it.annotationClass.qualifiedName == "kotlin.Metadata"
    }

internal val KClass<*>.superClassTypeParameter: Type? get() = this.java.superClassTypeParameter

internal val Class<*>.superClassTypeParameter: Type?
    get() {
        val superclass = genericSuperclass
        if (superclass is Class<*>) throw RuntimeException("Missing type parameter.")
        val parameterized = superclass as ParameterizedType
        return `$Gson$Types`.canonicalize(parameterized.actualTypeArguments[0])
    }