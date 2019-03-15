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
import moe.kanon.konfig.Konfig
import moe.kanon.konfig.KonfigDeserializationException
import moe.kanon.konfig.KonfigSerializationException
import moe.kanon.konfig.Layer
import java.io.IOException
import java.nio.file.Path
import java.nio.file.StandardOpenOption

interface Provider {
    
    /**
     * The name of the format `this` provider is made for.
     */
    val format: String
    
    /**
     * The [Konfig] that this is a provider for.
     */
    val config: Konfig
    
    /**
     * The underlying [ObjectMapper] used by this provider.
     */
    val gson: Gson
    
    /**
     * Loads and sets entries from the specified [file].
     */
    @Throws(IOException::class, KonfigDeserializationException::class)
    fun loadFrom(file: Path)
    
    /**
     * Saves all the entries from the [config] to the specified [file].
     */
    @Throws(KonfigSerializationException::class)
    fun saveTo(file: Path)
    
    /**
     * Converts the specified [value] to a [String].
     */
    fun <V : Any> valueToString(value: V?): String
    
}

class JsonProvider(override val config: Konfig) : Provider {
    
    override val format: String = "JSON"
    
    override val gson: Gson = GsonBuilder().apply {
        setLenient() // because who cares about the json standard lol, we hocon now.
        setPrettyPrinting()
        disableHtmlEscaping()
        serializeNulls()
        setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
    }.create()
    
    @Throws(IOException::class, KonfigDeserializationException::class)
    override fun loadFrom(file: Path) {
        if (file.notExists) saveTo(file) // generate a config file from the default values
        
        val parser = JsonParser()
        
        val obj = parser.parse(file.newBufferedReader())
        
        if (obj["name"].asString != config.name) throw KonfigDeserializationException.create(
            config,
            "Name <${obj["name"].asString}> is not equal to the configs name <${config.name}>."
        )
        
        for (key in obj.asJsonObject.keySet()) {
            if (key == "layers") obj[key].traverseLayers(config)
            if (key !in config.entries) continue
            
            val entry = config.getEntry<Any>(key)
            val container = obj[key]["container"].asJsonObject
            val result: Any? = gson.fromJson(container["value"], entry.value.javaType)
            val currentValue = config.getNullable<Any>(key)
            
            if (currentValue != result) config[key] = result
        }
    }
    
    private fun JsonElement.traverseLayers(parentLayer: Layer) {
        for (key in this.asJsonObject.keySet()) {
            if (key !in parentLayer.layers) continue
            val layer = parentLayer.getLayer(key)
            this[key].deserializeIntoEntries(layer)
        }
    }
    
    private fun JsonElement.deserializeIntoEntries(currentLayer: Layer) {
        for (key in this.asJsonObject.keySet()) {
            if (key == "layers") this[key].traverseLayers(currentLayer)
            if (key !in currentLayer.entries) continue
        
            val entry = currentLayer.getEntry<Any>(key)
            val container = this[key]["container"].asJsonObject
            val result: Any? = gson.fromJson(container["value"], entry.value.javaType)
            val currentValue = currentLayer.getNullable<Any>(key)
        
            if (currentValue != result && entry.value.isMutable) currentLayer[key] = result
        }
    }
    
    @Throws(KonfigSerializationException::class)
    override fun saveTo(file: Path) {
        val obj = Json.obj {
            "name" to config.name
            
            for ((key, entry) in config.entries) {
                key to obj {
                    "name" to entry.name
                    "description" to entry.description
                    "container" to obj {
                        "class" to entry.value.javaType
                        "type" to entry.value.name
                        "value" to gson.toJsonTree(config.getNullable(key), entry.value.javaType)
                        if (entry.value.isMutable) {
                            "default" to gson.toJsonTree(config.getNullableDefault(key), entry.value.javaType)
                        }
                    }
                }
            }
            
            "layers" to config.createLayers()
        }
        
        val output = gson.toJson(obj)
        
        // because outputting it directly via gson didn't actually work.
        file.writeLine(output, options = *arrayOf(StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE))
    }
    
    override fun <V : Any> valueToString(value: V?): String = gson.toJson(value)
    
    private fun Layer.createLayers(): JsonObject {
        return Json.obj {
            for ((layerKey, subLayer) in this@createLayers.layers) {
                if (subLayer.none { it.value.shouldDeserialize }) continue
                layerKey to obj {
                    "path" to subLayer.path
                    "name" to subLayer.name
                    
                    for ((key, entry) in subLayer.entries) {
                        if (!entry.value.shouldDeserialize) continue
                        key to obj {
                            "name" to entry.name
                            "description" to entry.description
                            "container" to obj {
                                "class" to entry.value.javaType
                                "type" to entry.value.name
                                "value" to gson.toJsonTree(subLayer.getNullable(key), entry.value.javaType)
                                if (entry.value.isMutable) {
                                    "default" to gson.toJsonTree(
                                        subLayer.getNullableDefault(key),
                                        entry.value.javaType
                                    )
                                }
                            }
                        }
                    }
    
                    val subLayerObj = subLayer.createLayers()
                    
                    if (subLayerObj.asJsonObject.isNotEmpty()) "layers" to subLayer.createLayers()
                }
            }
        }
    }
}