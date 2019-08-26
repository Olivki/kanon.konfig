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

package moe.kanon.konfig.providers.json

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import moe.kanon.kommons.io.paths.name
import moe.kanon.kommons.io.paths.newBufferedReader
import moe.kanon.kommons.io.paths.newBufferedWriter
import moe.kanon.kommons.io.paths.notExists
import moe.kanon.konfig.Config
import moe.kanon.konfig.ConfigException
import moe.kanon.konfig.FaultyParsedValueAction
import moe.kanon.konfig.UnknownEntryBehaviour
import moe.kanon.konfig.entries.Entry
import moe.kanon.konfig.entries.LimitedEntry
import moe.kanon.konfig.entries.LimitedStringEntry
import moe.kanon.konfig.layers.ConfigLayer
import moe.kanon.konfig.providers.ConfigProvider
import moe.kanon.konfig.providers.json.converters.registerInstalledConverters
import moe.kanon.konfig.providers.json.internal.json
import moe.kanon.konfig.providers.json.internal.kotlinTypeName
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

class JsonProvider(
    override val classLoader: ClassLoader = ClassLoader.getSystemClassLoader(),
    val settings: JsonProviderSettings = JsonProviderSettings.default
) : ConfigProvider("json") {
    private val parser = JsonParser()

    val gson: Gson = GsonBuilder().apply {
        setLenient()
        setPrettyPrinting()
        disableHtmlEscaping()
        serializeNulls()
        setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
        enableComplexMapKeySerialization()
        registerInstalledConverters(classLoader)
    }.create()

    private fun parseFail(node: JsonElement, file: Path, info: String, cause: Throwable? = null): Nothing =
        throw ConfigException(
            """
            Error encountered when parsing a config file for config <${config.name}>
            -----------DETAILS------------
            File = $file
            Node = $node
            Info = "$info"
            Cause = "${cause?.message ?: "No cause"}"
            -----------------------------
            """.trimIndent(),
            cause
        )

    override fun populateConfigFrom(file: Path) {
        if (file.notExists) {
            Config.logger.debug { "File <../${file.name}> for config '${config.name}' does not exist, creating a default one.." }
            saveConfigTo(file)
            return
        }

        file.newBufferedReader().use { reader ->
            val obj = parser.parse(reader).asJsonObject

            val name = obj["name"].asString

            if (name != config.name) {
                parseFail(obj["name"], file, "contents <$name> is not equal to config name <${config.name}>")
            }

            val entriesNode = obj["entries"]

            loop@ for ((key, node) in entriesNode.asJsonObject.entrySet()) {
                if (key !in config.entries) {
                    when (config.settings.onUnknownEntry) {
                        UnknownEntryBehaviour.FAIL -> parseFail(node, file, "unknown entry '$key'")
                        UnknownEntryBehaviour.IGNORE -> {
                            Config.logger.warn { "Encountered unknown entry '$key' at node <$node> in file <$file>" }
                            continue@loop
                        }
                    }
                }

                val currentNode = node.asJsonObject
                val entry = config.getEntry<Any?>(key)
                val container: JsonElement = currentNode["container"].asJsonObject["value"]
                val result: Any? = gson.fromJson(container, entry.type)
                val currentValue = config.getNullableValue<Any?>(key)
                val entryPath = "${config.path}${entry.name}"

                if (!entry.value.isMutable) continue@loop

                if (!entry.value.shouldDeserialize) {
                    Config.logger.error { "Node <$node> in file <$file> references an entry that should not be serialized" }
                }

                setValue(key, result, currentValue, entryPath, config, file, node, entry)
            }

            if (obj.has("layers")) traverseLayers(obj["layers"], file, config)
        }
    }

    private fun populateEntries(parentNode: JsonElement, file: Path, currentLayer: ConfigLayer) {
        loop@ for ((key, node) in parentNode.asJsonObject.entrySet()) {
            if (key !in currentLayer.entries) {
                when (config.settings.onUnknownEntry) {
                    UnknownEntryBehaviour.FAIL -> parseFail(node, file, "unknown entry '$key'")
                    UnknownEntryBehaviour.IGNORE -> {
                        Config.logger.warn { "Encountered unknown entry '$key' at node <$node> in file <$file>" }
                        continue@loop
                    }
                }
            }

            val currentNode = node.asJsonObject
            val entry = currentLayer.getEntry<Any?>(key)
            val container = currentNode["container"].asJsonObject["value"]
            val result: Any? = gson.fromJson(container, entry.type)
            val currentValue = currentLayer.getNullableValue<Any?>(key)
            val entryPath = "${currentLayer.path}${entry.name}"

            if (!entry.value.isMutable) continue@loop

            if (!entry.value.shouldDeserialize) {
                Config.logger.error { "Node <$node> in file <$file> references an entry that should not be serialized" }
            }

            setValue(key, result, currentValue, entryPath, currentLayer, file, node, entry)
        }
    }

    private fun traverseLayers(parentNode: JsonElement, file: Path, parentLayer: ConfigLayer) {
        for ((key, node) in parentNode.asJsonObject.entrySet()) {
            if (key !in parentLayer.layers) {
                Config.logger.warn { "Node <$node> in file <$file> references unknown layer '$key'" }
                continue
            }

            val currentNode = node.asJsonObject

            val layer = parentLayer.getLayer(key)
            populateEntries(currentNode["entries"], file, layer)

            if (currentNode.has("layers")) traverseLayers(currentNode["layers"], file, config)
        }
    }

    private fun setValue(
        name: String,
        parsedValue: Any?,
        currentValue: Any?,
        entryPath: String,
        currentLayer: ConfigLayer,
        file: Path,
        node: JsonElement,
        entry: Entry<*>
    ) {
        try {
            if (currentValue != parsedValue) {
                Config.logger.debug { "Value of entry <$entryPath> has changed; <$currentValue> -> <$parsedValue>" }
                currentLayer[name] = parsedValue
            }
        } catch (e: Exception) {
            when (config.settings.faultyParsedValueAction) {
                FaultyParsedValueAction.THROW_EXCEPTION -> {
                    parseFail(
                        node,
                        file,
                        "Could not set value of entry <$entryPath> with the parsed value <$parsedValue>",
                        e
                    )
                }
                FaultyParsedValueAction.FALLBACK_TO_DEFAULT -> {
                    Config.logger.error { "Resetting value of entry <$entryPath> as the parsed value <$parsedValue> is faulty; ${e.message}" }
                    entry.value.resetValue()
                }
            }
        }
    }

    override fun saveConfigTo(file: Path) {
        val obj = json {
            "path" to config.path
            "name" to config.name
            "entries" {
                for ((key, entry) in config.entries) key to createEntryNode(config, entry, key)
            }
            if (config.layers.isNotEmpty()) "layers" to createLayerNode(config)
        }

        file.newBufferedWriter(CREATE, WRITE, TRUNCATE_EXISTING).use { gson.toJson(obj, it) }
    }

    private fun createEntryNode(parent: ConfigLayer, entry: Entry<*>, key: String): JsonObject = json {
        "name" to entry.name
        "description" to entry.description
        "container" {
            when (settings.genericPrintingStyle) {
                GenericPrintingStyle.JAVA -> "class" to entry.type.typeName
                GenericPrintingStyle.KOTLIN -> "class" to entry.type.kotlinTypeName
                GenericPrintingStyle.DISABLED -> {
                } // just don't do anything.
            }
            "type" to entry.value.name
            when (entry) {
                is LimitedEntry<*> -> "range" to entry.value.range.toString()
                is LimitedStringEntry -> "range" to entry.value.range.toString()
            }
            "value" to parent.getNullableValue(key)
            if (entry.value.isMutable && config.settings.shouldPrintDefaultValue) {
                "default" to parent.getNullableDefaultValue(key)
            }
        }
    }

    private fun createLayerNode(layer: ConfigLayer): JsonObject = json {
        for ((layerKey, subLayer) in layer.layers) {
            if (subLayer.none { it.value.shouldDeserialize }) continue
            layerKey {
                "path" to subLayer.path
                "name" to subLayer.name
                "entries" {
                    for ((key, entry) in subLayer.entries.asSequence().filter { it.value.value.shouldDeserialize }) {
                        key to createEntryNode(subLayer, entry, key)
                    }
                }
                val subLayerObject = createLayerNode(subLayer)
                if (subLayerObject.size() > 0) "layers" to subLayerObject
            }
        }
    }

    override fun stringify(value: Any?): String = gson.toJson(value)
}