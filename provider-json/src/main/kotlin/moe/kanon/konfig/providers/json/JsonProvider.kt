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
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import moe.kanon.kommons.io.paths.newBufferedReader
import moe.kanon.kommons.io.paths.newBufferedWriter
import moe.kanon.kommons.io.paths.notExists
import moe.kanon.konfig.ConfigException
import moe.kanon.konfig.entries.Entry
import moe.kanon.konfig.entries.LimitedEntry
import moe.kanon.konfig.entries.LimitedStringEntry
import moe.kanon.konfig.layers.ConfigLayer
import moe.kanon.konfig.providers.json.internal.Json
import moe.kanon.konfig.providers.json.internal.kotlinTypeName
import moe.kanon.konfig.providers.ConfigProvider
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*

class JsonProvider(val settings: JsonProviderSettings = JsonProviderSettings.default) :
    ConfigProvider("application/json") {
    private val parser = JsonParser()

    val gson: Gson = GsonBuilder().apply {
        setLenient() // because who cares about the json standard lol, we hocon now.
        setPrettyPrinting()
        disableHtmlEscaping()
        serializeNulls()
        setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
        enableComplexMapKeySerialization()
    }.create()

    private fun fail(file: Path, info: String): Nothing = throw ConfigException("Enc") // TODO

    override fun populateConfigFrom(file: Path) {
        if (file.notExists) {
            saveConfigTo(file)
            return
        }

        file.newBufferedReader().use { reader ->

        }
    }

    override fun saveConfigTo(file: Path) {
        val obj = Json.obj {
            "name" to config.name
            for ((key, entry) in config.entries) key to createEntryObject(config, entry, key)
            if (config.layers.isNotEmpty()) "layers" to createLayerObject(config)
        }
        file.newBufferedWriter(CREATE, WRITE, TRUNCATE_EXISTING).use { gson.toJson(obj, it) }
    }

    private fun createLayerObject(parent: ConfigLayer): JsonObject = Json.obj {
        for ((layerKey, subLayer) in parent.layers) {
            if (subLayer.none { it.value.shouldDeserialize }) continue
            layerKey to obj {
                "path" to subLayer.path
                "name" to subLayer.name
                for ((key, entry) in subLayer.entries.asSequence().filter { it.value.value.shouldDeserialize }) {
                    key to createEntryObject(subLayer, entry, key)
                }
                val subLayerObject = createLayerObject(subLayer)
                if (subLayerObject.size() > 0) "layers" to subLayerObject
            }
        }
    }

    private fun createEntryObject(parent: ConfigLayer, entry: Entry<*>, key: String): JsonObject = Json.obj {
        "name" to entry.name
        "description" to entry.description
        "container" to obj {
            when (settings.genericPrintingStyle) {
                GenericPrintingStyle.JAVA -> "class" to entry.type.typeName
                GenericPrintingStyle.KOTLIN -> "class" to entry.type.kotlinTypeName
                GenericPrintingStyle.DISABLED -> {
                } // just don't do anything.
            }
            "type" to entry.value.name
            when (entry) {
                is LimitedEntry<*> -> "range" to gson.toJsonTree(entry.value.range)
                is LimitedStringEntry -> "range" to gson.toJsonTree(entry.value.range)
            }
            "value" to gson.toJsonTree(parent.getNullableValue(key), entry.type)
            if (entry.value.isMutable && config.settings.shouldPrintDefaultValue) {
                "default" to gson.toJsonTree(parent.getNullableDefaultValue(key), entry.type)
            }
        }
    }

    override fun stringify(value: Any?): String = gson.toJson(value)
}