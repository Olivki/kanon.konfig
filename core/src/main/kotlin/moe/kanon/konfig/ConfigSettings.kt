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

@file:Suppress("DataClassPrivateConstructor")

package moe.kanon.konfig

import moe.kanon.konfig.entries.Entry
import moe.kanon.konfig.providers.ConfigProvider

data class ConfigSettings private constructor(
    /**
     * What sort of action the system should take when encountering a unknown [entry][Entry] in the
     * [config file][Config.file] during the loading process.
     *
     * This setting only affects the [ConfigProvider.populateConfigFrom]/[Config.loadFromFile] operations, all `get`
     * operations regarding entries will still behave the same.
     */
    val onUnknownEntry: UnknownEntryBehaviour,
    /**
     * Determines what action the system should take when it encounters a faulty value when parsing the configuration
     * file.
     */
    val faultyParsedValueAction: FaultyParsedValueAction,
    /**
     * Whether or not the system should print the `default` property of the value container for entries when
     * serializing to the configuration file.
     *
     * The purpose of the `default` value in the file is purely cosmetic, it might however be good to keep it so that
     * anyone that is manually modifying the file has a point-of-reference.
     */
    val shouldPrintDefaultValue: Boolean
) {
    companion object {
        /**
         * Returns a new [ConfigSettings] instance with all the values set to their default values.
         */
        val default: ConfigSettings get() = Builder().build()

        val builder: Builder get() = Builder()

        inline operator fun invoke(scope: Builder.() -> Unit = {}): ConfigSettings = builder.apply(scope).build()
    }

    data class Builder internal constructor(
        var onUnknownEntry: UnknownEntryBehaviour = UnknownEntryBehaviour.FAIL,
        var faultyParsedValueAction: FaultyParsedValueAction = FaultyParsedValueAction.FALLBACK_TO_DEFAULT,
        var shouldPrintDefaultValue: Boolean = true
    ) {
        fun build(): ConfigSettings = ConfigSettings(onUnknownEntry, faultyParsedValueAction, shouldPrintDefaultValue)
    }
}

/**
 * Represents an action the system will take when encountering an unknown `entry` when traversing the
 * [config file][Konfig.file].
 */
enum class UnknownEntryBehaviour {
    /**
     * The system will fail loudly and throw a [ConfigException] when it encounters an unknown `entry`.
     */
    FAIL,
    /**
     * The system will quietly continue on as if nothing happened when it encounters an unknown `entry`.
     */
    IGNORE
}

/**
 * Represents a action that the system will take when it encounters a faulty value when parsing the configuration file.
 */
enum class FaultyParsedValueAction {
    /**
     * The system will fail loudly and throw an exception when it encounters a faulty value.
     */
    THROW_EXCEPTION,
    /**
     * The system will set the value of the parsed entry back to its default value when it encounters a faulty value.
     */
    FALLBACK_TO_DEFAULT
}