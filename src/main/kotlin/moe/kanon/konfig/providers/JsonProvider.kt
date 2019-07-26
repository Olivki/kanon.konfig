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

import com.github.mgrzeszczak.jsondsl.Json
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.isNotEmpty
import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import moe.kanon.kommons.io.newBufferedReader
import moe.kanon.kommons.io.notExists
import moe.kanon.kommons.io.writeLine
import moe.kanon.konfig.FaultyParsedValueException
import moe.kanon.konfig.KonfigDeserializationException
import moe.kanon.konfig.KonfigSerializationException
import moe.kanon.konfig.Layer
import moe.kanon.konfig.UnknownEntryException
import moe.kanon.konfig.entries.Entry
import moe.kanon.konfig.entries.LimitedStringValue
import moe.kanon.konfig.entries.LimitedValue
import moe.kanon.konfig.internal.kotlinTypeName
import moe.kanon.konfig.set
import moe.kanon.konfig.settings.FaultyParsedValueAction
import moe.kanon.konfig.settings.GenericPrintingStyle
import moe.kanon.konfig.settings.UnknownEntryBehaviour
import java.io.IOException
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class JsonProvider : AbstractProvider() {

    override val format: String = "JSON"

    val gson: Gson = GsonBuilder().apply {
        setLenient() // because who cares about the json standard lol, we hocon now.
        setPrettyPrinting()
        disableHtmlEscaping()
        serializeNulls()
        setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
        enableComplexMapKeySerialization()
    }.create()

    @Throws(IOException::class, KonfigSerializationException::class)
    override fun loadFrom(file: Path) {
        if (file.notExists) {
            saveTo(file)
            return
        }

        val parser = JsonParser()

        val obj = parser.parse(file.newBufferedReader())

        if (obj["name"].asString != config.name) throw KonfigSerializationException.of(
            config,
            file,
            "Name <${obj["name"].asString}> is not equal to the configs name <${config.name}>."
        )

        loop@ for (key in obj.asJsonObject.keySet()) {
            if (key == "layers") obj[key].traverseLayers(file, config)
            if (key !in config.entries) {
                if (key == "name" && obj[key].isJsonPrimitive) continue@loop // it's just the name entry.
                when (config.settings.onUnknownEntry) {
                    UnknownEntryBehaviour.FAIL -> throw UnknownEntryException.of(
                        config,
                        file,
                        key,
                        config.path
                    )
                    UnknownEntryBehaviour.IGNORE -> continue@loop
                }
            }

            val entry = config.getEntry<Any>(key)
            val container = obj[key]["container"].asJsonObject
            val result: Any? = gson.fromJson(container["value"], entry.javaType)
            val currentValue = config.getNullable<Any>(key)
            val entryPath = "${config.path}${entry.name}"

            if (!entry.value.isMutable) continue@loop

            try {
                if (currentValue != result) {
                    config.logger.debug {
                        "Value of entry <$entryPath> has changed, default <$currentValue>, parsed <$result>"
                    }
                    config[key] = result
                }
            } catch (e: Exception) {
                when (config.settings.faultyParsedValueAction) {
                    FaultyParsedValueAction.THROW_EXCEPTION -> throw FaultyParsedValueException.of(
                        config,
                        file,
                        result,
                        entry,
                        key,
                        config,
                        e
                    )
                    FaultyParsedValueAction.FALLBACK_TO_DEFAULT -> {
                        config.logger.error {
                            "Resetting entry <$entry> back to default values as the parsed value <$result> was deemed faulty."
                        }
                        entry.value.reset()
                    }
                }
            }
        }
    }

    private fun JsonElement.traverseLayers(file: Path, parentLayer: Layer) {
        for (key in this.asJsonObject.keySet()) {
            if (key !in parentLayer.layers) continue
            val layer = parentLayer.getLayer(key)
            this[key].deserializeIntoEntries(file, layer)
        }
    }

    private fun JsonElement.deserializeIntoEntries(file: Path, currentLayer: Layer) {
        loop@ for (key in this.asJsonObject.keySet()) {
            if (key == "layers") this[key].traverseLayers(file, currentLayer)
            if (key == "name" && this[key].isJsonPrimitive) continue@loop // it's just the name entry.
            if (key !in currentLayer.entries) {
                when (config.settings.onUnknownEntry) {
                    UnknownEntryBehaviour.FAIL -> throw UnknownEntryException.of(
                        config,
                        file,
                        key,
                        currentLayer.path
                    )
                    UnknownEntryBehaviour.IGNORE -> continue@loop
                }
            }

            val entry = currentLayer.getEntry<Any>(key)
            val container = this[key]["container"].asJsonObject
            val result: Any? = gson.fromJson(container["value"], entry.value.javaType)
            val currentValue = currentLayer.getNullable<Any>(key)
            val entryPath = "${currentLayer.path}${entry.name}"

            if (!entry.value.isMutable) continue@loop

            try {
                if (currentValue != result) {
                    config.logger.debug {
                        "Value of entry <$entryPath> has changed, default <$currentValue>, parsed <$result>"
                    }
                    currentLayer[key] = result
                }
            } catch (e: Exception) {
                when (config.settings.faultyParsedValueAction) {
                    FaultyParsedValueAction.THROW_EXCEPTION -> throw FaultyParsedValueException.of(
                        config,
                        file,
                        result,
                        entry,
                        key,
                        currentLayer,
                        e
                    )
                    FaultyParsedValueAction.FALLBACK_TO_DEFAULT -> {
                        config.logger.error {
                            "Resetting entry <$entry> back to default values as the parsed value <$result> was deemed faulty."
                        }
                        entry.value.reset()
                    }
                }
            }
        }
    }

    @Throws(KonfigDeserializationException::class)
    override fun saveTo(file: Path) {
        val obj = Json.obj {
            "name" to config.name

            for ((key, entry) in config.entries) {
                key to entry.createEntry(key)
            }

            if (config.layers.isNotEmpty()) "layers" to config.createLayers()
        }

        val output = gson.toJson(obj)

        // because outputting it directly via gson didn't actually work.
        file.writeLine(output, options = *arrayOf(
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.CREATE
        ))
    }

    private fun Layer.createLayers(): JsonObject {
        return Json.obj {
            for ((layerKey, subLayer) in this@createLayers.layers) {
                if (subLayer.none { it.value.shouldDeserialize }) continue
                layerKey to obj {
                    "path" to subLayer.path
                    "name" to subLayer.name

                    for ((key, entry) in subLayer.entries) {
                        if (!entry.value.shouldDeserialize) continue
                        key to entry.createEntry(key)
                    }

                    val subLayerObj = subLayer.createLayers()

                    if (subLayerObj.asJsonObject.isNotEmpty()) "layers" to subLayer.createLayers()
                }
            }
        }
    }

    private fun Entry<*>.createEntry(key: String): JsonObject =
        Json.obj {
            "name" to name
            "description" to description
            "container" to obj {
                when (config.settings.genericPrintingStyle) {
                    GenericPrintingStyle.JAVA -> "class" to javaType.typeName
                    GenericPrintingStyle.KOTLIN -> "class" to javaType.kotlinTypeName
                    GenericPrintingStyle.DISABLED -> {
                    } // just don't do anything.
                }
                "type" to value.name
                if (value is LimitedValue<*>) "range" to gson.toJsonTree((value as LimitedValue<*>).range)
                if (value is LimitedStringValue) "range" to gson.toJsonTree((value as LimitedStringValue).range)
                "value" to gson.toJsonTree(parent.getNullable(key), javaType)
                if (value.isMutable && config.settings.printDefaultValue) "default" to gson.toJsonTree(
                    parent.getNullableDefault(key),
                    javaType
                )
            }
        }

    override fun <V : Any> valueToString(value: V?): String = gson.toJson(value)
}