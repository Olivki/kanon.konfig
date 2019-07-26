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

import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.io.xml.JDom2Driver
import moe.kanon.kommons.io.newOutputStream
import moe.kanon.kommons.io.notExists
import moe.kanon.konfig.FaultyParsedValueException
import moe.kanon.konfig.KonfigDeserializationException
import moe.kanon.konfig.KonfigSerializationException
import moe.kanon.konfig.Layer
import moe.kanon.konfig.UnknownEntryException
import moe.kanon.konfig.entries.Entry
import moe.kanon.konfig.entries.LimitedStringValue
import moe.kanon.konfig.entries.LimitedValue
import moe.kanon.konfig.set
import moe.kanon.konfig.settings.FaultyParsedValueAction
import moe.kanon.konfig.settings.UnknownEntryBehaviour
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
        val name = source.getAttributeValue("name") ?: throw KonfigDeserializationException.of(
            konfig = config,
            file = file,
            info = "missing 'name' attribute on 'layer' element"
        )

        val layer = parentLayer.getLayer(name)

        elements("entry") { findEntries(file, layer) }
        elements("layer") { traverseLayers(file, layer) }
    }

    private fun ParserElement.findEntries(file: Path, currentLayer: Layer) {
        val name = source.getAttributeValue("name") ?: throw KonfigDeserializationException.of(
            konfig = config,
            file = file,
            info = "missing 'name' attribute on 'entry' element"
        )

        val entry: Entry<Any> = currentLayer.getEntryOrNull(name) ?: when (config.settings.onUnknownEntry) {
            UnknownEntryBehaviour.FAIL -> throw UnknownEntryException.of(
                config,
                file,
                name,
                currentLayer.path
            )
            UnknownEntryBehaviour.IGNORE -> return
        }

        if (!entry.value.isMutable) return

        if (source.getChild("container").children.isNotEmpty()) element("container") {
            val valueString = outputter.outputString(source.getChild("value"))
            val parsedValue: Any = xStream.fromXML(valueString)
            val currentValue: Any? = currentLayer.getNullable(name)
            val entryPath = "${currentLayer.path}${entry.name}"

            try {
                if (currentValue != parsedValue) {
                    config.logger.debug {
                        "Value of entry <$entryPath> has changed, default <$currentValue>, parsed <$parsedValue>"
                    }
                    currentLayer[name] = parsedValue
                }
            } catch (e: Exception) {
                when (config.settings.faultyParsedValueAction) {
                    FaultyParsedValueAction.THROW_EXCEPTION -> throw FaultyParsedValueException.of(
                        config,
                        file,
                        parsedValue,
                        entry,
                        name,
                        currentLayer,
                        e
                    )
                    FaultyParsedValueAction.FALLBACK_TO_DEFAULT -> {
                        config.logger.error {
                            "Resetting entry <$entryPath> back to default values as the parsed value <$parsedValue> was deemed faulty."
                        }
                        entry.value.reset()
                    }
                }
            }
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
            file.newOutputStream(
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.CREATE
            )
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