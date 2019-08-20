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

import moe.kanon.kommons.lang.delegates.Delegate
import moe.kanon.konfig.layers.ConfigLayer
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class DelegatedExternalNullableProperty<T : Any?>(val layer: ConfigLayer, val path: String) : ReadWriteProperty<Any?, T?> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T? = layer.getNullableValue(path)

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        layer.setValue(path, value)
    }
}