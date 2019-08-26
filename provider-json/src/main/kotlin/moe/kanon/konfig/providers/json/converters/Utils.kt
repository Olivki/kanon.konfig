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

package moe.kanon.konfig.providers.json.converters

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import moe.kanon.kommons.reflection.loadServices
import kotlin.reflect.KClass

@JvmName("registerReifiedConverter")
inline fun <reified T : Any> GsonBuilder.registerConverter(converter: JsonConverter<T>) {
    val type = object : TypeToken<T>() {}.type
    this.registerTypeAdapter(type, converter)
}

fun <T : Any> GsonBuilder.registerConverter(clz: KClass<T>, converter: JsonConverter<T>) {
    this.registerTypeAdapter(clz.java, converter)
}

/**
 * Registers all the [JsonConverter] instances that have been added as services.
 */
fun GsonBuilder.registerInstalledConverters(classLoader: ClassLoader) {
    for (converter in loadServices<JsonConverter<*>>(classLoader)) {
        registerTypeAdapter(converter.type.java, converter)
    }
}