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

package moe.kanon.konfig

import moe.kanon.kommons.io.paths.name
import moe.kanon.konfig.dsl.LayerContainer
import moe.kanon.konfig.layers.AbstractConfigLayer
import moe.kanon.konfig.layers.BasicLayer
import moe.kanon.konfig.layers.ConfigLayer
import moe.kanon.konfig.providers.ConfigProvider
import moe.kanon.konfig.providers.ConfigProviderFinder
import mu.KotlinLogging
import java.nio.file.Path

/**
 * A `Config` class holds various entries and layers, and allows them to be saved and loaded from files.
 *
 * TODO: Property docs
 */
data class Config @JvmOverloads constructor(
    override val name: String,
    val file: Path,
    val root: AbstractConfigLayer = BasicLayer(name),
    val settings: ConfigSettings = ConfigSettings.default,
    val provider: ConfigProvider = ConfigProviderFinder.findProvider(file, Config::class.java.classLoader).unwrap(),
    val container: LayerContainer = LayerContainer(name, layer = root)
) : ConfigLayer by root {
    companion object {
        val logger = KotlinLogging.logger {}
    }

    init {
        logger.debug { "Created new config <$name> tied to file <../${file.name}> using provider <${provider::class}>" }
        provider.config = this
    }

    fun saveToFile() {
        logger.debug { "Saving config <$name> to file <$file>.." }
        provider.saveConfigTo(file)
    }

    fun loadFromFile() {
        logger.debug { "Loading config <$name> from file <$file>.." }
        provider.populateConfigFrom(file)
    }

    /**
     * Scopes into the underlying [LayerContainer] of `this` konfig.
     *
     * This serves as a direct entry-point into the DSL for creating entries.
     */
    inline fun mutate(closure: LayerContainer.() -> Unit) {
        container.apply(closure)
    }

    /**
     * Creates and adds a [Layer] to `this` layer from the specified [name] and [closure].
     */
    inline fun addLayer(name: String, closure: LayerContainer.() -> Unit) {
        container.layer(name, closure)
    }
}