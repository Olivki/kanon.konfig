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

package moe.kanon.konfig.entries.delegates

import moe.kanon.konfig.KonfigLayer
import moe.kanon.konfig.entries.NormalEntry
import moe.kanon.konfig.internal.superClassTypeParameter
import moe.kanon.konfig.set
import java.lang.reflect.Type
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class DelegatedNormalProperty<V>(
    val value: V,
    val default: V = value,
    val name: String?,
    val description: String
) {
    private val type: Type = this::class.superClassTypeParameter!!

    operator fun provideDelegate(
        thisRef: KonfigLayer,
        property: KProperty<*>
    ): ReadWriteProperty<KonfigLayer, V> {
        val _name = name ?: property.name
        thisRef.addEntry(
            NormalEntry.of(
                type,
                value,
                default,
                _name,
                description,
                thisRef
            )
        )

        return object : ReadWriteProperty<KonfigLayer, V> {
            override fun getValue(thisRef: KonfigLayer, property: KProperty<*>): V = thisRef[_name]

            override fun setValue(thisRef: KonfigLayer, property: KProperty<*>, value: V) {
                thisRef[_name] = value
            }
        }
    }
}