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

package moe.kanon.konfig.providers.xml

import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.XStreamException
import com.thoughtworks.xstream.io.xml.JDom2Driver
import moe.kanon.kommons.io.paths.name
import moe.kanon.kommons.io.paths.newInputStream
import moe.kanon.kommons.io.paths.newOutputStream
import moe.kanon.kommons.io.paths.notExists
import moe.kanon.kommons.writeOut
import moe.kanon.konfig.Config
import moe.kanon.konfig.ConfigException
import moe.kanon.konfig.FaultyParsedValueAction
import moe.kanon.konfig.UnknownEntryBehaviour
import moe.kanon.konfig.entries.Entry
import moe.kanon.konfig.entries.LimitedEntry
import moe.kanon.konfig.entries.LimitedStringEntry
import moe.kanon.konfig.entries.values.ConstantValue
import moe.kanon.konfig.entries.values.LimitedStringValue
import moe.kanon.konfig.entries.values.LimitedValue
import moe.kanon.konfig.entries.values.NormalValue
import moe.kanon.konfig.entries.values.NullableValue
import moe.kanon.konfig.entries.values.Value
import moe.kanon.konfig.internal.fixedName
import moe.kanon.konfig.layers.ConfigLayer
import moe.kanon.konfig.providers.ConfigProvider
import moe.kanon.konfig.providers.xml.converters.registerInstalledConverters
import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.JDOMException
import org.jdom2.input.SAXBuilder
import org.jdom2.input.sax.XMLReaders
import org.jdom2.output.Format
import org.jdom2.output.XMLOutputter
import java.io.IOException
import java.io.StringReader
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE

class XmlProvider @JvmOverloads constructor(
    override val classLoader: ClassLoader = ClassLoader.getSystemClassLoader(),
    val settings: XmlProviderSettings = XmlProviderSettings.default
) : ConfigProvider("json") {
    private val builder = SAXBuilder(XMLReaders.NONVALIDATING)
    private val writer = XMLOutputter(Format.getPrettyFormat())
    val serializer = XStream(JDom2Driver())

    init {
        serializer.apply {
            allowTypesByRegExp(arrayOf(".*"))
            autodetectAnnotations(true)
            Value::class.java.also {
                alias("container", it)
                omitField(it, "valueType")
                omitField(it, "isMutable")
                omitField(it, "shouldDeserialize")
                omitField(it, "kotlinType")
                omitField(it, "javaType")
            }
            NullableValue::class.java.also {
                alias("container", it)
                omitField(it, "setter")
                omitField(it, "kotlinType")
                omitField(it, "javaType")
            }
            NormalValue::class.java.also {
                alias("container", it)
                omitField(it, "setter")
                omitField(it, "kotlinType")
                omitField(it, "javaType")
            }
            LimitedValue::class.java.also {
                alias("container", it)
                omitField(it, "range")
                omitField(it, "setter")
                omitField(it, "kotlinType")
                omitField(it, "javaType")
            }
            LimitedStringValue::class.java.also {
                alias("container", it)
                omitField(it, "range")
                omitField(it, "setter")
                omitField(it, "kotlinType")
                omitField(it, "javaType")
            }
            ConstantValue::class.java.also {
                alias("container", it)
                omitField(it, "kotlinType")
                omitField(it, "javaType")
            }
        }
        // load converters
        serializer.registerInstalledConverters(classLoader)
    }

    override fun populateConfigFrom(file: Path) {
        if (file.notExists) {
            Config.logger.debug { "File <../${file.name}> for config '${config.name}' does not exist, creating a default one.." }
            saveConfigTo(file)
            return
        }

        file.newInputStream().use { input ->
            try {
                builder.build(input).rootElement.also { root ->
                    for (entryElement in root.getChildren("entry")) populateEntries(entryElement, file, config)
                    for (layerElement in root.getChildren("layer")) traverseLayers(layerElement, file, config)
                }
            } catch (e: IOException) {
                throw ConfigException("Error when loading config <${config.name}> from file <$file>", e)
            } catch (e: JDOMException) {
                throw ConfigException("Error when loading config <${config.name}> from file <$file>", e)
            }
        }
    }

    private fun fail(
        file: Path,
        element: Element,
        currentLayer: ConfigLayer,
        info: String,
        cause: Throwable? = null
    ): Nothing = throw ConfigException(
        """
        Error encountered when parsing a config file for config <${config.name}>
        -----------DETAILS------------
        File = $file
        Element = $element
        Current Layer = "${currentLayer.path}"
        Info = "$info"
        Cause = "${cause?.message ?: "No cause"}"
        -----------------------------
        """.trimIndent(),
        cause
    )

    private fun populateEntries(element: Element, file: Path, currentLayer: ConfigLayer) {
        val name = element.getAttributeValue("name") ?: fail(file, element, currentLayer, "missing 'name' attribute")
        val entry = currentLayer.getEntryOrNone<Any?>(name).orNull() ?: when (config.settings.onUnknownEntry) {
            UnknownEntryBehaviour.FAIL -> fail(file, element, currentLayer, "unknown entry '$name'")
            UnknownEntryBehaviour.IGNORE -> {
                Config.logger.warn { "Encountered unknown entry '$name' at element <$element> in file <$file>" }
                return
            }
        }

        if (!(entry.value.isMutable)) {
            Config.logger.error { "Encountered non-mutable entry <$entry> when loading config file <$file>, this should not happen." }
            return
        }

        element.getChild("container")?.also { container ->
            val currentValue: Any? = currentLayer.getNullableValue(name)
            val entryPath = "${currentLayer.path}${entry.name}"
            if (container.getChild("value") == null) {
                setValue(name, null, currentValue, entryPath, currentLayer, file, element, entry)
                return
            }
            val valueString = writer.outputString(container.getChild("value"))
            val (shouldSetValue, parsedValue: Any?) = try {
                true to serializer.fromXML(valueString)
            } catch (e: Exception) {
                when (config.settings.faultyParsedValueAction) {
                    FaultyParsedValueAction.THROW_EXCEPTION -> {
                        fail(file, element, currentLayer, "value in element could not be deserialized", e)
                    }
                    FaultyParsedValueAction.FALLBACK_TO_DEFAULT -> {
                        Config.logger.error { "Resetting value of entry <$entryPath> as the value in element <$element> could not be deserialized; ${e.message}" }
                        false to entry.value.resetValue()
                    }
                }
            }

            if (shouldSetValue) setValue(name, parsedValue, currentValue, entryPath, currentLayer, file, element, entry)
        } ?: fail(file, element, currentLayer, "missing 'container' element")
    }

    private fun setValue(
        name: String,
        parsedValue: Any?,
        currentValue: Any?,
        entryPath: String,
        currentLayer: ConfigLayer,
        file: Path,
        element: Element,
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
                    fail(file, element, currentLayer, "parsed value <$parsedValue> is faulty", e)
                }
                FaultyParsedValueAction.FALLBACK_TO_DEFAULT -> {
                    Config.logger.error { "Resetting value of entry <$entryPath> as the parsed value <$parsedValue> is faulty; ${e.message}" }
                    entry.value.resetValue()
                }
            }
        }
    }

    private fun traverseLayers(element: Element, file: Path, parent: ConfigLayer) {
        val name = element.getAttributeValue("name") ?: fail(file, element, parent, "missing 'name' attribute")
        val layer = parent.getLayerOrNone(name).orElse { fail(file, element, parent, "unknown layer '$name'") }

        for (entryElement in element.getChildren("entry")) populateEntries(entryElement, file, layer)
        for (layerElement in element.getChildren("layer")) traverseLayers(layerElement, file, layer)
    }

    override fun saveConfigTo(file: Path) {
        val isRootAttribute = settings.rootNamePlacement == RootNamePlacement.IN_ATTRIBUTE

        val doc = Document(Element(if (isRootAttribute) "root" else config.name)).rootElement.also { root ->
            if (isRootAttribute) root.setAttribute("name", config.name)

            for ((key, entry) in config.entries.asSequence().filter { it.value.value.shouldDeserialize }) {
                try {
                    root.addContent(createEntryElement(key, entry))
                } catch (e: Exception) {
                    throw ConfigException("Error when creating element for entry <$entry> in config layer", e)
                }
            }

            config.layers
                .asSequence()
                .map { createLayerElement(it.value) }
                .filter { it.children.isNotEmpty() }
                .forEach { root.addContent(it) }
        }

        file.newOutputStream().use { writer.output(doc, it) }
    }

    private fun createLayerElement(parent: ConfigLayer): Element = Element("layer").also { root ->
        root.setAttribute("name", parent.name)

        for ((key, entry) in parent.entries.asSequence().filter { it.value.value.shouldDeserialize }) {
            try {
                root.addContent(createEntryElement(key, entry))
            } catch (e: Exception) {
                throw ConfigException("Error when creating element for entry <$entry> in layer <$parent>", e)
            }
        }

        parent.layers
            .asSequence()
            .map { createLayerElement(it.value) }
            .filter { it.children.isNotEmpty() }
            .forEach { root.addContent(it) }
    }

    private fun createEntryElement(key: String, entry: Entry<*>): Element = Element("entry").also { root ->
        root.setAttribute("name", key)
        root.setAttribute("valueType", entry.value.valueType)

        root.addContent(Element("description").setText(entry.description))

        when (entry) {
            is LimitedEntry<*> -> root.addContent(Element("range").setText(entry.value.range.toString()))
            is LimitedStringEntry -> root.addContent(Element("range").setText(entry.value.range.toString()))
        }

        val valueString = stringify(entry.value)
        val valueElement = builder.build(StringReader(valueString)).detachRootElement()

        if (entry.value is LimitedStringValue) {
            for (child in valueElement.children) child.setAttribute("class", "string")
        }

        if (!(config.settings.shouldPrintDefaultValue) && valueElement.children.any { it.name == "default" }) {
            valueElement.removeChild("default")
        }

        when (settings.genericPrintingStyle) {
            XmlGenericPrintingStyle.JAVA -> valueElement.setAttribute("type", entry.javaType.fixedName)
            XmlGenericPrintingStyle.KOTLIN -> valueElement.setAttribute("type", entry.kotlinType.toString())
            XmlGenericPrintingStyle.DISABLED -> {
            } // just don't do anything.
        }

        root.addContent(valueElement)
    }

    override fun stringify(value: Any?): String = serializer.toXML(value)
}