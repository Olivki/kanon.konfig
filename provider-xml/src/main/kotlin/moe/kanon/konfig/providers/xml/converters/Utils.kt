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

package moe.kanon.konfig.providers.xml.converters

import com.thoughtworks.xstream.XStream
import moe.kanon.kommons.reflection.loadServices
import moe.kanon.konfig.ConfigException
import kotlin.reflect.full.primaryConstructor

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