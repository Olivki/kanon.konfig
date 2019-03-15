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
            
            if (currentValue != result) {
                println("Entry <$key> has been changed, old <$currentValue> new <$result>")
                config[key] = result
                println()
            }
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
        
            if (currentValue != result && entry.value.isMutable) {
                println("Entry <$key> has been changed, old <$currentValue> new <$result>")
                currentLayer[key] = result
                println()
            }
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

/*class XmlProvider(override val config: Konfig) : Provider {
    
    override val format: String = "XML"
    
    private val module: JacksonXmlModule = JacksonXmlModule()
    
    init {
        module.setDefaultUseWrapper(true)
    }
    
    override val mapper: ObjectMapper = XmlMapper(module)
    
    @Throws(IOException::class, KonfigDeserializationException::class)
    override fun loadFrom(file: Path) {
        file.requireExistence()
        
        file.parseAsDocument(validator = XMLReaders.NONVALIDATING) {
            elements("entry") {
                val name = source.getChildText("name")
                
                if (name.isBlank()) throw KonfigSerializationException.create(
                    file = file,
                    info = "the 'name' element has no text."
                )
                
                val entry: Entry<Any> = config.getEntryOrNull(name) ?: when (config.settings.onUnknownEntry) {
                    FAIL -> throw UnknownEntryException.create(name, config)
                    IGNORE -> return
                    CREATE_NEW -> TODO()
                }
                
                /*val input = XMLOutputter(Format.getCompactFormat()).outputString(source.getChild("container"))
                println(input)
                println(
                    TypeFactory.defaultInstance().constructParametricType(
                        entry.value::class.java,
                        entry.value.javaType
                    )
                )
                val type = TypeFactory.defaultInstance().constructParametricType(
                    entry.value::class.java, entry.value.javaType
                )
                
                val value: Any? = mapper.readValue(input, type)
                println("$value")*/
                val input = XMLOutputter(Format.getCompactFormat()).outputString(
                    source.getChild("container").getChild("value").children.first()
                )
                
                val value: Any? = mapper.readValue(input, entry.value.javaType)
                println(value)
            }
        }
    }
    
    @Throws(KonfigSerializationException::class)
    override fun saveTo(file: Path) {
        file.deleteIfExists()
        val document = xml("root") {
            attributes("name" to config.name)
            
            // adding the root level entries to the document
            root.addContent(config.createEntries().cloneContent())
            
            // adding all sub-layers and their respective entries to the document
            val layers = config.createLayers()
            if (!layers.children.isEmpty()) root.addContent(layers)
        }
        
        document.saveTo(file.parent, file.name)
    }
    
    private fun Layer.createLayers(): Element {
        return xml("layer") {
            attributes("name" to this@createLayers.name)
            
            root.addContent(this@createLayers.createEntries().cloneContent())
            
            for ((_, subLayer) in this@createLayers.layers) {
                val layers = subLayer.createLayers()
                if (!layers.children.isEmpty()) root.addContent(layers)
            }
            
        }.document.detachRootElement()
    }
    
    private fun Layer.createEntries(): Element {
        val builder = SAXBuilder()
        return xml(name) {
            for ((key, entry) in entries) {
                if (!entry.value.shouldDeserialize) continue
                element("entry") {
                    attributes("type" to entry.value.name)
                    
                    text("name") { entry.name }
                    text("description") { entry.description }
                    if (entry.value is LimitedValue<*>)
                        text("range") { (entry.value as LimitedValue<*>).range.toString() }
                    if (entry.value is LimitedStringValue)
                        text("range") { (entry.value as LimitedStringValue).range.toString() }
                    
                    element("container") {
                        attributes {
                            attribute("class") { entry.value.javaType.toCanonical() }
                            attribute("type") { entry.value.name }
                        }
                        
                        element("value") {
                            val valueXml = StringReader(valueToString<Any>(entry.parent.getNullable(key)))
                            source.addContent(builder.build(valueXml).detachRootElement())
                        }
                        
                        if (entry.value.isMutable) element("default") {
                            val defaultXml = StringReader(valueToString<Any>(entry.parent.getNullableDefault(key)))
                            source.addContent(builder.build(defaultXml).detachRootElement())
                        }
                    }
                    
                    //val container = StringReader(valueToString(entry.value))
                    
                    //println(valueToString(entry.value::class.memberProperties.first { it.name == "value" }.getter.call(entry.value)))
                    
                    //source.addContent(builder.build(container).detachRootElement())
                }
            }
        }.document.detachRootElement()
    }
    
    override fun <V : Any> valueToString(value: V?): String =
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)
    
    override fun toString(): String = "XmlProvider(config=$config, format='$format', mapper=$mapper)"
}*/