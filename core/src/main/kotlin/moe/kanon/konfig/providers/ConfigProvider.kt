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

package moe.kanon.konfig.providers

import moe.kanon.kommons.affirmThat
import moe.kanon.kommons.requireThat
import moe.kanon.konfig.Config
import java.nio.file.Path

/**
 * An implementation of this class handles the saving and loading of a [Config] instance to a specific file format
 * *(e.g; XML, JSON, etc..)*.
 *
 * @property [mediaTypes] the media-types that this class can work on, generally used in conjunction with an
 * implementation of [ConfigProvider.Finder] to automatically fetch the appropriate provider for an unknown file.
 */
abstract class ConfigProvider(vararg val mediaTypes: String) {
    init {
        requireThat(mediaTypes.isNotEmpty()) { "'mediaTypes' is empty" }
    }

    /**
     * The [Config] instance that this provider is tied to.
     *
     * Note that this property does not get set until an instance of a `ConfigProvider` is passed to a `Config`
     * instance, meaning that it's generally *not* safe to access this property in an `init` block, or any time before
     * you know that the corresponding `Config` instance has been fully created.
     */
    lateinit var config: Config
        @JvmSynthetic internal set

    /**
     * Attempts to populate the [config] tied to this provider with the contents of the given [file].
     */
    abstract fun populateConfigFrom(file: Path)

    /**
     * Attempts to save the contents of the [config] tied to this provider to the given [file].
     */
    abstract fun saveConfigTo(file: Path)

    /**
     * Returns a string of the given [value], represented in a way matching the file-format this provider is wrapped
     * around.
     */
    abstract fun stringify(value: Any?): String

    /**
     * An implementation of this class handles the automatic locating of providers when loading from config file
     * where the needed provider is unknown.
     */
    interface Finder {
        /**
         * Returns an appropriate [ConfigProvider] based on the given [mediaType], or `null` if none can be found.
         */
        fun getProvider(mediaType: String): ConfigProvider?
    }
}