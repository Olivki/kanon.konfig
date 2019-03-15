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

package moe.kanon.konfig.xml.deserializer

import com.google.common.reflect.TypeToken
import moe.kanon.konfig.xml.utils.captureType
import moe.kanon.konfig.xml.utils.typeTokenOf
import org.jdom2.Element
import java.lang.reflect.Type

interface KonfigDeserializer<T : Any> {
    
    val token: TypeToken<T>
    
    fun deserialize(element: Element, token: TypeToken<T>, type: Type): T
    
}

inline fun <reified T : Any> newDeserializer(
    crossinline closure: (element: Element, token: TypeToken<T>) -> T
): KonfigDeserializer<T> {
    return object : KonfigDeserializer<T> {
        
        override val token: TypeToken<T> = typeTokenOf(this::class.captureType!!)
        
        override fun deserialize(element: Element, token: TypeToken<T>, type: Type): T = closure(element, token)
    }
}