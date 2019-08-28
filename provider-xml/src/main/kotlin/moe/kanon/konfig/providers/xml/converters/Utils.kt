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

import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriterHelper
import com.thoughtworks.xstream.io.HierarchicalStreamReader
import com.thoughtworks.xstream.io.HierarchicalStreamWriter
import moe.kanon.kommons.reflection.loadServices
import moe.kanon.konfig.ConfigException
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

private typealias Reader = HierarchicalStreamReader
private typealias Writer = HierarchicalStreamWriter

/**
 * Attempts to load all [XmlConverter] instances registered as services using the given [classLoader].
 *
 * @throws [ConfigException] if a registered [XmlConverter] services is missing a primary constructor
 */
fun XStream.registerInstalledConverters(classLoader: ClassLoader) {
    val services = loadServices<XmlConverter<*>>(classLoader) {
        val constructor = it.primaryConstructor
            ?: throw ConfigException("'XmlConverter' service <$it> does not have a primary constructor")
        constructor.call(this.mapper)
    }
    for (converter in services) this.registerConverter(converter)
}

inline fun <T> Reader.use(scope: Reader.() -> T): T =
    with(this, scope)

/**
 * Returns an `iterator` that returns `this` reader as long as `this` reader has children.
 */
operator fun Reader.iterator(): Iterator<Reader> = object : Iterator<Reader> {
    override fun hasNext(): Boolean = this@iterator.hasMoreChildren()

    override fun next(): Reader = if (!(hasNext())) throw NoSuchElementException() else this@iterator
}

fun Reader.asIterable(): Iterable<Reader> = object : Iterable<Reader> {
    override fun iterator(): Iterator<Reader> = this@asIterable.iterator()
}

/**
 * Returns the value of the attribute with the given [attrName] of the current node of `this` reader, or throws a
 * [NoSuchElementException] if none is found.
 */
operator fun Reader.get(attrName: String): String =
    getAttribute(attrName) ?: throw NoSuchElementException("Node <$nodeName> has no attribute with name <$attrName>")

/**
 * Returns the value of the attribute under the given [index] of the current node of `this` reader, or throws a
 * [IndexOutOfBoundsException] if `index` is out of bounds.
 */
operator fun Reader.get(index: Int): String =
    getAttribute(index) ?: throw IndexOutOfBoundsException("Node <$nodeName> has no attribute under index <$index>")

/**
 * Throws a [NoSuchElementException] if the given [reader] has no more children that can be consumed/read.
 */
@PublishedApi
internal fun requireChildren(reader: Reader) {
    if (!reader.hasMoreChildren()) throw NoSuchElementException("This reader has no more children!")
}

/**
 * Reads the next child in `this` reader and returns the result of invoking [action].
 *
 * Example:
 *
 * ```xml
 *  <example>
 *      <child>Text One</child>
 *      <child>Text Two</child>
 *  </example>
 * ```
 *
 * ```kotlin
 *  val firstResult = readNext { it.value } // Text One
 *  val secondResult = readNext { it.value } // Text Two
 * ```
 *
 * @throws [NoSuchElementException] if `this` reader has no more children
 */
inline fun <T> Reader.readNext(action: (Reader) -> T): T {
    requireChildren(this)
    this.moveDown()
    val result = action(this)
    this.moveUp()
    return result
}

/**
 * Returns the `value` of the next child in `this` reader.
 *
 * Example:
 *
 * ```xml
 *  <example>
 *      <child>Text One</child>
 *      <child>Text Two</child>
 *  </example>
 * ```
 *
 * ```kotlin
 *  val firstResult = readNextValue() // Text One
 *  val secondResult = readNextValue() // Text Two
 * ```
 *
 * @throws [NoSuchElementException] if `this` reader has no more children
 */
fun Reader.readNextValue(): String = readNext { it.value }

/**
 * Reads all remaining children in `this` reader and returns a list containing the results of invoking [action] on each
 * child.
 *
 * Example:
 *
 * ```xml
 *  <example>
 *      <childOne>Text One</childOne>
 *      <childTwo>Text Two</childTwo>
 *      <childThree>Text Three</childThree>
 *  </example>
 * ```
 *
 * ```kotlin
 *  val result = readAll { it.value } // [Text One, Text Two, Text Three]
 * ```
 *
 * Note that if `this` reader has no more remaining children then the returned list will be an [emptyList].
 */
inline fun <T> Reader.readAll(action: (Reader) -> T): List<T> {
    val result = mutableListOf<T>()
    while (this.hasMoreChildren()) {
        this.moveDown()
        result += action(this)
        this.moveUp()
    }
    return result.toList()
}

/**
 * Returns a list containing the `value` of all the remaining children in `this` reader.
 *
 * Example:
 *
 * ```xml
 *  <example>
 *      <childOne>Text One</childOne>
 *      <childTwo>Text Two</childTwo>
 *      <childThree>Text Three</childThree>
 *  </example>
 * ```
 *
 * ```kotlin
 *  val result = readAllValues() // [Text One, Text Two, Text Three]
 * ```
 *
 * Note that if `this` reader has no more remaining children then the returned list will be an [emptyList].
 */
fun Reader.readAllValues(): List<String> = readAll { it.value }

/**
 * Reads [n] children in `this` reader and returns a list containing the results of invoking [action] on each one.
 *
 * Example:
 *
 * ```xml
 *  <example>
 *      <childOne>Text One</childOne>
 *      <childTwo>Text Two</childTwo>
 *      <childThree>Text Three</childThree>
 *  </example>
 * ```
 *
 * ```kotlin
 *  val result = readNextN(2) { it.value } // [Text One, Text Two]
 * ```
 *
 * Note that if `this` reader has no more remaining children then the returned list will be an [emptyList].
 */
inline fun <T> Reader.readNextN(n: Int, action: (Reader) -> T): List<T> {
    val result = mutableListOf<T>()
    repeat(n) {
        this.moveDown()
        result += action(this)
        this.moveUp()
    }
    return result.toList()
}

/**
 * Returns a list containing the `value` of [n] children in `this` reader.
 *
 * Example:
 *
 * ```xml
 *  <example>
 *      <childOne>Text One</childOne>
 *      <childTwo>Text Two</childTwo>
 *      <childThree>Text Three</childThree>
 *  </example>
 * ```
 *
 * ```kotlin
 *  val result = readNextNValues(2) // [Text One, Text Two]
 * ```
 *
 * Note that if `this` reader has no more remaining children then the returned list will be an [emptyList].
 */
fun Reader.readNextNValues(n: Int): List<String> = readNextN(n) { it.value }

/**
 * Consumes the next child in `this` reader, and invokes the given [action].
 *
 * This function should mainly be used when there's a child in an XML document that has no real value to the conversion
 * process, meaning that it can just be skipped over rather than stored into a variable by using something like
 * [readNext].
 *
 * @throws [NoSuchElementException] if `this` reader has no more children
 */
inline fun Reader.consumeNext(action: (Reader) -> Unit = {}) {
    requireChildren(this)
    this.moveDown()
    action(this)
    this.moveUp()
}

/**
 * Consumes all the remaining children in `this` reader, and invokes [action] on each consummation.
 *
 * This function should mainly be used when there's a child in an XML document that has no real value to the conversion
 * process, meaning that it can just be skipped over rather than stored into a variable by using something like
 * [readNext].
 *
 * Note that if `this` reader has no more remaining children then this function will simply fail silently, and `action`
 * will never be invoked.
 */
inline fun Reader.consumeAll(action: (Reader) -> Unit = {}) {
    while (this.hasMoreChildren()) {
        this.moveDown()
        action(this)
        this.moveUp()
    }
}

/**
 * Consumes [n] children in `this` reader, and invokes [action] on each consummation.
 *
 * This function should mainly be used when there's a child in an XML document that has no real value to the conversion
 * process, meaning that it can just be skipped over rather than stored into a variable by using something like
 * [readNext].
 *
 * @throws [NoSuchElementException] if `this` reader has no more children
 */
inline fun Reader.consumeNextN(n: Int, action: (Reader) -> Unit = {}) {
    repeat(n) { consumeNext(action) }
}

// write
inline fun Writer.use(scope: Writer.() -> Unit) {
    this.apply(scope)
}

@JvmName("writeClassNode")
inline fun <reified T : Any> Writer.writeNode(name: String, scope: Writer.() -> Unit = {}) {
    ExtendedHierarchicalStreamWriterHelper.startNode(this, name, T::class.java)
    this.apply(scope)
    this.endNode()
}

inline fun Writer.writeClassNode(name: String, clz: KClass<*>, scope: Writer.() -> Unit = {}) {
    ExtendedHierarchicalStreamWriterHelper.startNode(this, name, clz.java)
    this.apply(scope)
    this.endNode()
}

inline fun Writer.writeNode(name: String, scope: Writer.() -> Unit = {}) {
    this.startNode(name)
    this.apply(scope)
    this.endNode()
}

inline fun <T> Writer.writeNodes(parentName: String, childName: String, elements: Iterable<T>, value: (T) -> Any) {
    writeNode(parentName) {
        for (element in elements) writeNode(childName, value(element))
    }
}

inline fun Writer.writeNode(name: String, body: Any) {
    this.startNode(name)
    this.setValue(body.toString())
    this.endNode()
}

inline fun Writer.writeAttribute(key: String, value: () -> Any) {
    addAttribute(key, value().toString())
}

inline fun Writer.writeAttribute(key: String, value: Any) {
    addAttribute(key, value.toString())
}