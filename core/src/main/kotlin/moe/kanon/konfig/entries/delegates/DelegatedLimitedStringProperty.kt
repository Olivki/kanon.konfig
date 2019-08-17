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

import moe.kanon.konfig.layers.ConfigLayer
import moe.kanon.konfig.entries.LimitedStringEntry
import moe.kanon.konfig.internal.TypeToken
import moe.kanon.konfig.entries.values.LimitedStringValue
import moe.kanon.konfig.entries.values.ValueSetter
import moe.kanon.konfig.internal.typeTokenOf
import java.lang.reflect.Type
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class DelegatedLimitedStringProperty(
    val value: String,
    val default: String,
    val range: IntRange,
    val name: String?,
    val description: String,
    val setter: ValueSetter<LimitedStringValue, String>.() -> Unit
) {
    private val type: Type = typeTokenOf<String>().type

    operator fun provideDelegate(
        thisRef: ConfigLayer,
        property: KProperty<*>
    ): ReadWriteProperty<ConfigLayer, String> {
        val entryName = name ?: property.name
        thisRef += LimitedStringEntry(
            entryName,
            description,
            type,
            LimitedStringValue(value, default, range, type, setter)
        )

        return object : ReadWriteProperty<ConfigLayer, String> {
            override fun getValue(thisRef: ConfigLayer, property: KProperty<*>): String = thisRef.getValue(entryName)

            override fun setValue(thisRef: ConfigLayer, property: KProperty<*>, value: String) {
                thisRef[entryName] = value
            }
        }
    }
}