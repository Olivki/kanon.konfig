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

@file:JvmName("KonfigSettingsFactory")

package moe.kanon.konfig.settings

import moe.kanon.konfig.Konfig
import moe.kanon.konfig.UnknownEntryException
import moe.kanon.konfig.entries.Entry
import moe.kanon.konfig.providers.Provider

@DslMarker
annotation class KonfigSettingsMarker

@Suppress("DataClassPrivateConstructor")
data class KonfigSettings private constructor(
    /**
     * What sort of action the system should take when encountering a unknown [entry][Entry] in the
     * [config file][Konfig.file] during the loading process.
     *
     * This setting only affects the [provider.loadFrom][Provider.loadFrom] operation, all `get` operations
     * regarding entries will still behave the same.
     *
     * ([FAIL][UnknownEntryBehaviour.FAIL] by default)
     */
    val onUnknownEntry: UnknownEntryBehaviour = UnknownEntryBehaviour.FAIL,
    /**
     * The style that the system should use for printing output of generics.
     *
     * This is mainly used for the output of the [Entry.javaType] property in the configuration file.
     *
     * Note that this is *strictly* for the JSON output mode, in the XML mode, references to what class the entry is
     * are needed, and the system will fail if they are not present.
     *
     * ([KOTLIN][GenericPrintingStyle.KOTLIN] by default)
     */
    val genericPrintingStyle: GenericPrintingStyle = GenericPrintingStyle.KOTLIN,
    /**
     * Whether or not the system should print the `default` property of the value container for entries when
     * serializing to the configuration file.
     *
     * The purpose of the `default` value in the file is purely cosmetic, it might however be good to keep it so that
     * anyone that is manually modifying the file has a point-of-reference.
     *
     * (`true` by default)
     */
    val printDefaultValue: Boolean = true
) {
    /**
     * What sort of action the system should take when encountering a unknown [entry][Entry] in the
     * [config file][Konfig.file].
     *
     * This setting only affects the [provider.loadFrom][Provider.loadFrom] operation, all `get` operations
     * regarding entries will still behave the same.
     *
     * ([IGNORE][UnknownEntryBehaviour.IGNORE] by default)
     */
    @KonfigSettingsMarker
    fun onUnknownEntry(behaviour: UnknownEntryBehaviour): KonfigSettings = this.copy(onUnknownEntry = behaviour)
    
    /**
     * The style that the system should use for printing output of generics.
     *
     * This is mainly used for the output of the [Entry.javaType] property in the configuration file.
     *
     * ([KOTLIN][GenericPrintingStyle.KOTLIN] by default)
     */
    @KonfigSettingsMarker
    fun genericPrintingStyle(style: GenericPrintingStyle): KonfigSettings = this.copy(genericPrintingStyle = style)
    
    /**
     * Whether or not the system should print the `default` property of the value container for entries when
     * serializing to the configuration file.
     *
     * The `default` value is purely cosmetic in the file, it might however be good to keep it so that anyone that is
     * manually modifying the file has a point-of-reference.
     *
     * (`true` by default)
     */
    @KonfigSettingsMarker
    fun printDefaultValue(predicate: Boolean): KonfigSettings = this.copy(printDefaultValue = predicate)
    
    companion object {
        /**
         * The default settings used by the system.
         */
        @JvmStatic
        val default: KonfigSettings = KonfigSettings()
    }
}

/**
 * Creates a [KonfigSettings] instance from the values set in the specified [closure].
 *
 * The [closure] is invoked on the [KonfigSettings.default] instance, so if no values are specified, the returned
 * `KonfigSettings` instance will be that of the `default` one.
 */
@KonfigSettingsMarker
inline fun createKonfigSettings(closure: KonfigSettings.() -> Unit = {}): KonfigSettings =
    KonfigSettings.default.apply(closure)

/**
 * Represents an action the system will take when encountering an unknown `entry` when traversing the
 * [config file][Konfig.file].
 */
enum class UnknownEntryBehaviour {
    /**
     * The system will fail loudly and throw a [UnknownEntryException] when it encounters an unknown `entry`.
     */
    FAIL,
    /**
     * The system will quietly continue on as if nothing happened when it encounters an unknown `entry`.
     */
    IGNORE,
    /**
     * The system will attempt to create a new entry from the provided data when it encounters an unknown `entry`,
     * and if successful it will add it to the layer that the entry resides in.
     *
     * TODO: This. Might be removed.
     */
    CREATE_NEW
}

/**
 * Represents a printing style for generic output.
 */
enum class GenericPrintingStyle {
    /**
     * The system will print any output of generics according to how variants and primitives look in Java.
     *
     * That means that an entry like `Map<String, Int>` will be output as
     * `"java.util.Map<java.lang.String, ? extends java.lang.Integer>"`.
     */
    JAVA,
    /**
     * The system will print any output of generics according to how variants and primitives look in Kotlin.
     *
     * That means that an entry like `Map<String, Int>` will be output as
     * `"java.util.Map<java.lang.String, out java.lang.Integer>"`.
     */
    KOTLIN,
    /**
     * The system will not print any output of generics.
     *
     * This disables the printing of the `"class"` output in the configuration file.
     */
    DISABLED
}