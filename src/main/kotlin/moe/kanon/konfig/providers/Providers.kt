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
import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.io.xml.JDom2Driver
import moe.kanon.kommons.io.newBufferedReader
import moe.kanon.kommons.io.newOutputStream
import moe.kanon.kommons.io.notExists
import moe.kanon.kommons.io.writeLine
import moe.kanon.konfig.Konfig
import moe.kanon.konfig.KonfigDeserializationException
import moe.kanon.konfig.KonfigSerializationException
import moe.kanon.konfig.Layer
import moe.kanon.konfig.UnknownEntryException
import moe.kanon.konfig.entries.Entry
import moe.kanon.konfig.entries.LimitedStringValue
import moe.kanon.konfig.entries.LimitedValue
import moe.kanon.konfig.kotlinTypeName
import moe.kanon.konfig.settings.GenericPrintingStyle.DISABLED
import moe.kanon.konfig.settings.GenericPrintingStyle.JAVA
import moe.kanon.konfig.settings.GenericPrintingStyle.KOTLIN
import moe.kanon.konfig.settings.UnknownEntryBehaviour.FAIL
import moe.kanon.konfig.settings.UnknownEntryBehaviour.IGNORE
import moe.kanon.konfig.settings.XmlRootNamePlacement
import moe.kanon.xml.ParserElement
import moe.kanon.xml.parseAsDocument
import moe.kanon.xml.xml
import org.jdom2.Element
import org.jdom2.input.SAXBuilder
import org.jdom2.input.sax.XMLReaders
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import java.io.IOException
import java.io.StringReader
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
     * Loads and sets entries from the specified [file].
     */
    @Throws(IOException::class, KonfigSerializationException::class)
    fun loadFrom(file: Path)
    
    /**
     * Saves all the entries from the [config] to the specified [file].
     */
    @Throws(KonfigDeserializationException::class)
    fun saveTo(file: Path)
    
    /**
     * Converts the specified [value] to a [String] using the specified [gson] instance.
     */
    fun <V : Any> valueToString(value: V?): String
    
}

abstract class AbstractProvider : Provider {
    
    // just because we can't call 'this' in the constructor of 'KonfigImpl'.
    @set:JvmSynthetic
    final override lateinit var config: Konfig
        internal set
    
    // if we included the entire 'Konfig.toString' we'd be stuck in an infinite loop.
    override fun toString(): String = "AbstractProvider(format='$format', config='${config.name}')"
    
}

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
        
        if (obj["name"].asString != config.name) throw KonfigSerializationException.create(
            config,
            file,
            "Name <${obj["name"].asString}> is not equal to the configs name <${config.name}>."
        )
        
        loop@ for (key in obj.asJsonObject.keySet()) {
            if (key == "layers") obj[key].traverseLayers(file, config)
            if (key !in config.entries) {
                if (key == "name" && obj[key].isJsonPrimitive) continue@loop // it's just the name entry.
                when (config.settings.onUnknownEntry) {
                    FAIL -> throw UnknownEntryException.create(config, file, key, config.path)
                    IGNORE -> continue@loop
                }
            }
            
            val entry = config.getEntry<Any>(key)
            val container = obj[key]["container"].asJsonObject
            val result: Any? = gson.fromJson(container["value"], entry.javaType)
            val currentValue = config.getNullable<Any>(key)
            
            if (currentValue != result) config[key] = result
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
                    FAIL -> throw UnknownEntryException.create(config, file, key, currentLayer.path)
                    IGNORE -> continue@loop
                }
            }
            
            val entry = currentLayer.getEntry<Any>(key)
            val container = this[key]["container"].asJsonObject
            val result: Any? = gson.fromJson(container["value"], entry.value.javaType)
            val currentValue = currentLayer.getNullable<Any>(key)
            
            if (currentValue != result && entry.value.isMutable) currentLayer[key] = result
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
        file.writeLine(output, options = *arrayOf(StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE))
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
    
    private fun Entry<*>.createEntry(key: String): JsonObject = Json.obj {
        "name" to name
        "description" to description
        "container" to obj {
            when (config.settings.genericPrintingStyle) {
                JAVA -> "class" to javaType.typeName
                KOTLIN -> "class" to javaType.kotlinTypeName
                DISABLED -> {
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

// scrapped for now because no API can handle converting between JSON and XML very well.
class XmlProvider : AbstractProvider() {
    
    override val format: String = "XML"
    
    private val builder = SAXBuilder(XMLReaders.NONVALIDATING)
    
    private val outputter = XMLOutputter(Format.getPrettyFormat())
    
    val xStream = XStream(JDom2Driver())
    
    init {
        //xStream.allowTypesByRegExp(arrayOf(".*")) // to make xStream be quiet
        xStream.alias("pair", Pair::class.java)
        xStream.alias("triple", Triple::class.java)
        xStream.alias("int-range", IntRange::class.java)
        xStream.alias("long-range", LongRange::class.java)
        xStream.autodetectAnnotations(true)
    }
    
    @Throws(IOException::class, KonfigSerializationException::class)
    override fun loadFrom(file: Path) {
        if (file.notExists) {
            saveTo(file)
            return
        }
        
        file.parseAsDocument(validator = XMLReaders.NONVALIDATING) {
            elements("entry") { findEntries(file, config) }
            elements("layer") { traverseLayers(file, config) }
        }
    }
    
    private fun ParserElement.traverseLayers(file: Path, parentLayer: Layer) {
        val name = source.getAttributeValue("name") ?: throw KonfigDeserializationException.create(
            konfig = config,
            file = file,
            info = "missing 'name' attribute on 'layer' element"
        )
        
        val layer = parentLayer.getLayer(name)
        
        elements("entry") { findEntries(file, layer) }
        elements("layer") { traverseLayers(file, layer) }
    }
    
    private fun ParserElement.findEntries(file: Path, currentLayer: Layer) {
        val name = source.getAttributeValue("name") ?: throw KonfigDeserializationException.create(
            konfig = config,
            file = file,
            info = "missing 'name' attribute on 'entry' element"
        )
        
        val entry: Entry<Any> = currentLayer.getEntryOrNull(name) ?: when (config.settings.onUnknownEntry) {
            FAIL -> throw UnknownEntryException.create(config, file, name, currentLayer.path)
            IGNORE -> return
        }
        
        if (!entry.value.isMutable) return
        
        if (source.getChild("container").children.isNotEmpty()) element("container") {
            val valueString = outputter.outputString(source.getChild("value"))
            val parsedValue: Any = xStream.fromXML(valueString)
            val currentValue: Any? = currentLayer.getNullable(name)
            
            if (currentValue != parsedValue) currentLayer[name] = parsedValue
        }
    }
    
    @Throws(KonfigDeserializationException::class)
    override fun saveTo(file: Path) {
        val isRootAttribute = config.settings.xmlRootNamePlacement == XmlRootNamePlacement.IN_ATTRIBUTE
        val document = xml(if (isRootAttribute) "root" else config.name) {
            if (isRootAttribute) attributes("name" to config.name)
            
            // adding the root level entries to the document
            root.addContent(config.createEntries().cloneContent())
            
            // adding all sub-layers and their respective entries to the document
            for ((_, subLayer) in config.layers) {
                val layers = subLayer.createLayers()
                if (!layers.children.isEmpty()) root.addContent(layers)
            }
        }.document
        
        outputter.output(
            document,
            file.newOutputStream(StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)
        )
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
        return xml(name) {
            for ((key, entry) in entries) {
                if (!entry.value.shouldDeserialize) continue
                element("entry") {
                    attributes("name" to entry.name)
                    
                    text("description") { entry.description }
                    if (entry.value is LimitedValue<*>)
                        text("range") { (entry.value as LimitedValue<*>).range.toString() }
                    if (entry.value is LimitedStringValue)
                        text("range") { (entry.value as LimitedStringValue).range.toString() }
                    
                    val valueXml = xStream.toXML(entry.parent.getEntry<Any>(key).value)
                    val valueElement = builder.build(StringReader(valueXml)).detachRootElement()
                    
                    if (entry.value is LimitedStringValue) for (child in valueElement.children) child.setAttribute(
                        "class",
                        "string"
                    )
                    
                    if (!config.settings.printDefaultValue && valueElement.children.any { it.name == "default" })
                        valueElement.removeChild("default")
                    
                    source.addContent(valueElement)
                }
            }
        }.document.detachRootElement()
    }
    
    override fun <V : Any> valueToString(value: V?): String = xStream.toXML(value)
}