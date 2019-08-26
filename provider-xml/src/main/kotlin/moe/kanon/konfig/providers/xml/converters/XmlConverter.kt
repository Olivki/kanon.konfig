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

@file:Suppress("NOTHING_TO_INLINE")

package moe.kanon.konfig.providers.xml.converters

import com.thoughtworks.xstream.converters.Converter
import com.thoughtworks.xstream.converters.MarshallingContext
import com.thoughtworks.xstream.converters.UnmarshallingContext
import com.thoughtworks.xstream.core.util.HierarchicalStreams
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriterHelper
import com.thoughtworks.xstream.io.HierarchicalStreamReader
import com.thoughtworks.xstream.io.HierarchicalStreamWriter
import com.thoughtworks.xstream.mapper.Mapper
import kotlin.reflect.KClass

abstract class XmlConverter<T> : Converter {
    abstract val mapper: Mapper

    @Suppress("UNCHECKED_CAST")
    final override fun marshal(source: Any, writer: HierarchicalStreamWriter, context: MarshallingContext) {
        writeSource(source as T, writer, context)
    }

    final override fun unmarshal(reader: HierarchicalStreamReader, context: UnmarshallingContext): Any =
        readSource(reader, context) as Any

    final override fun canConvert(type: Class<*>): Boolean = isSupported(type.kotlin)

    abstract fun isSupported(type: KClass<*>): Boolean

    abstract fun writeSource(source: T, writer: HierarchicalStreamWriter, context: MarshallingContext)

    abstract fun readSource(reader: HierarchicalStreamReader, context: UnmarshallingContext): T

    // writeItem..
    protected fun writeCompleteItem(item: Any?, writer: HierarchicalStreamWriter, context: MarshallingContext) {
        if (item == null) {
            writeNullItem(writer, context)
        } else {
            val name = mapper.serializedClass(item.javaClass)
            ExtendedHierarchicalStreamWriterHelper.startNode(writer, name, item.javaClass)
            writeBareItem(item, writer, context)
            writer.endNode()
        }
    }

    protected fun writeBareItem(item: Any, writer: HierarchicalStreamWriter, context: MarshallingContext) {
        context.convertAnother(item)
    }

    protected fun writeNullItem(writer: HierarchicalStreamWriter, context: MarshallingContext) {
        val name = mapper.serializedClass(null)
        ExtendedHierarchicalStreamWriterHelper.startNode(writer, name, Mapper.Null::class.java)
        writer.endNode()
    }

    // readItem..
    protected fun readBareItem(reader: HierarchicalStreamReader, context: UnmarshallingContext, current: Any?): Any {
        val type = HierarchicalStreams.readClassType(reader, mapper)
        return context.convertAnother(current, type)
    }

    protected fun readCompleteItem(
        reader: HierarchicalStreamReader,
        context: UnmarshallingContext,
        current: Any?
    ): Any {
        reader.moveDown()
        val result = readBareItem(reader, context, current)
        reader.moveUp()
        return result
    }

    // DSL
    // read
    protected inline fun <T> HierarchicalStreamReader.use(scope: HierarchicalStreamReader.() -> T): T =
        with(this, scope)

    protected inline fun HierarchicalStreamReader.forAll(action: () -> Unit) {
        while (this.hasMoreChildren()) {
            this.moveDown()
            action()
            this.moveUp()
        }
    }

    // write
    protected inline fun HierarchicalStreamWriter.use(scope: HierarchicalStreamWriter.() -> Unit): Unit {
        this.apply(scope)
    }

    @JvmName("writeClassNode")
    protected inline fun <reified T : Any> HierarchicalStreamWriter.writeNode(
        name: String,
        scope: HierarchicalStreamWriter.() -> Unit = {}
    ) {
        ExtendedHierarchicalStreamWriterHelper.startNode(this, name, T::class.java)
        this.apply(scope)
        this.endNode()
    }

    protected inline fun HierarchicalStreamWriter.writeClassNode(
        name: String,
        clz: KClass<*>,
        scope: HierarchicalStreamWriter.() -> Unit = {}
    ) {
        ExtendedHierarchicalStreamWriterHelper.startNode(this, name, clz.java)
        this.apply(scope)
        this.endNode()
    }

    protected inline fun HierarchicalStreamWriter.writeNode(
        name: String,
        scope: HierarchicalStreamWriter.() -> Unit = {}
    ) {
        this.startNode(name)
        this.apply(scope)
        this.endNode()
    }

    protected inline fun HierarchicalStreamWriter.writeNode(name: String, body: Any) {
        this.startNode(name)
        this.setValue(body.toString())
        this.endNode()
    }

    protected inline fun HierarchicalStreamWriter.writeAttribute(key: String, value: () -> Any) {
        addAttribute(key, value().toString())
    }

    protected inline fun HierarchicalStreamWriter.writeAttribute(key: String, value: Any) {
        addAttribute(key, value.toString())
    }
}