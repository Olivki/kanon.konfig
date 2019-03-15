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

package moe.kanon.konfig.xml.serializer

import com.google.common.reflect.TypeToken
import moe.kanon.konfig.xml.utils.captureType
import moe.kanon.konfig.xml.utils.token
import moe.kanon.konfig.xml.utils.typeTokenOf
import moe.kanon.xml.XmlDocumentContainer
import moe.kanon.xml.expandWithDsl
import org.jdom2.Document
import org.jdom2.Element

interface KonfigSerializer<V : Any> {
    
    val token: TypeToken<V>
    
    fun serialize(element: Element, item: V): Element
    
}

inline fun <reified T : Any> newSerializer(
    noinline closure: XmlDocumentContainer.(item: T) -> XmlDocumentContainer
): KonfigSerializer<T> {
    return object : KonfigSerializer<T> {
        
        override val token: TypeToken<T> = typeTokenOf(this::class.captureType!!)
        
        override fun serialize(element: Element, item: T): Element =
            Document(element).expandWithDsl { closure(item) }.detachRootElement()
    }
}

class StringSerializer : KonfigSerializer<String> {
    
    override val token: TypeToken<String> = String::class.token
    
    override fun serialize(element: Element, item: String): Element {
        TODO("not implemented")
    }
}