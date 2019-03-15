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

package moe.kanon.konfig.xml

import com.google.common.reflect.TypeToken
import moe.kanon.konfig.Konfig
import moe.kanon.konfig.xml.deserializer.KonfigDeserializer
import moe.kanon.konfig.xml.deserializer.newDeserializer
import moe.kanon.konfig.xml.serializer.KonfigSerializer
import moe.kanon.konfig.xml.serializer.newSerializer
import moe.kanon.xml.XmlDocumentContainer
import org.jdom2.Element

class KonfigXml(val konfig: Konfig) {
    
    private val serializers: MutableMap<TypeToken<*>, KonfigSerializer<*>> = HashMap()
    
    private val deserializers: MutableMap<TypeToken<*>, KonfigDeserializer<*>> = HashMap()
    
    fun <K : Any> Map<TypeToken<*>, K>.hasToken(tokenString: String): Boolean =
        this.keys.any { it.toString() == tokenString }
    
    init {
        //provideSet<Boolean> {
        //    serializer<Boolean> { text("bool") { it } }
        //    deserializer<Boolean> { it, _ -> it.getChildText("string") }
        //}
        provideSet<String> {
            serializer<String> { text("string") { it } }
            deserializer<String> { it, _ -> it.getChildText("string") }
        }
        provideSet<Int> {
            serializer<Int> { text("int") { it } }
            deserializer<Int> { it, _ -> it.getChildText("string").toInt() }
        }
    }
    
    fun <T : Any> addSerializer(serializer: KonfigSerializer<T>) {
        require(serializer.token !in serializers) { "There already exists a serializer for the token <${serializer.token}>" }
        
        serializers[serializer.token] = serializer
    }
    
    fun <T : Any> addDeserializer(deserializer: KonfigDeserializer<T>) {
        require(deserializer.token !in deserializers) { "There already exists a deserializer for the token <${deserializer.token}>" }
        
        deserializers[deserializer.token] = deserializer
    }
    
    inline fun <reified T : Any> toXml(item: T): String {
        
        TODO()
    }
    
    inline fun <reified T : Any> provideSet(closure: ConverterSet<T>.() -> Unit): ConverterSet<T> =
        ConverterSet<T>(this).apply(closure)
    
}

class ConverterSet<T : Any>(val xmlHandler: KonfigXml) {
    
    inline fun <reified T : Any> serializer(noinline closure: XmlDocumentContainer.(item: T) -> XmlDocumentContainer) =
        xmlHandler.addSerializer(newSerializer(closure))
    
    inline fun <reified T : Any> deserializer(crossinline closure: (element: Element, token: TypeToken<T>) -> T) =
        xmlHandler.addDeserializer(newDeserializer(closure))
    
}