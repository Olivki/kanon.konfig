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
import moe.kanon.konfig.entries.NormalEntry
import moe.kanon.konfig.internal.TypeToken
import moe.kanon.konfig.entries.values.NormalValue
import moe.kanon.konfig.entries.values.ValueSetter
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KType

open class DelegatedNormalProperty<T : Any>(
    val value: T,
    val default: T,
    val name: String?,
    val description: String,
    val setter: ValueSetter<NormalValue<T>, T>.() -> Unit,
    val kotlinType: KType
) : TypeToken<T>() {
    operator fun provideDelegate(
        thisRef: ConfigLayer,
        property: KProperty<*>
    ): ReadWriteProperty<ConfigLayer, T> {
        val entryName = name ?: property.name
        thisRef += NormalEntry(entryName, description, kotlinType, type, value, default, setter)

        return object : ReadWriteProperty<ConfigLayer, T> {
            override fun getValue(thisRef: ConfigLayer, property: KProperty<*>): T = thisRef.getValue(entryName)

            override fun setValue(thisRef: ConfigLayer, property: KProperty<*>, value: T) {
                thisRef[entryName] = value
            }
        }
    }
}