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
import moe.kanon.konfig.entries.LimitedStringEntry
import moe.kanon.konfig.set
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class DelegatedLimitedStringProperty(
    val value: String,
    val default: String = value,
    val range: IntRange,
    val name: String?,
    val description: String
) {
    operator fun provideDelegate(
        thisRef: KonfigLayer,
        property: KProperty<*>
    ): ReadWriteProperty<KonfigLayer, String> {
        val _name = name ?: property.name
        thisRef.addEntry(
            LimitedStringEntry.of(
                value,
                default,
                range,
                _name,
                description,
                thisRef
            )
        )

        return object : ReadWriteProperty<KonfigLayer, String> {
            override fun getValue(thisRef: KonfigLayer, property: KProperty<*>): String = thisRef[_name]

            override fun setValue(thisRef: KonfigLayer, property: KProperty<*>, value: String) {
                thisRef[_name] = value
            }
        }
    }
}