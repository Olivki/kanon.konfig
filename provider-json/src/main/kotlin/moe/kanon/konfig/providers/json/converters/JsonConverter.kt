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

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type
import kotlin.reflect.KClass

/**
 * A class that implements both [JsonSerializer] and [JsonDeserializer] for a specific [type][T].
 */
abstract class JsonConverter<T : Any>(val type: KClass<T>) : JsonSerializer<T>, JsonDeserializer<T> {
    abstract override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): T

    abstract override fun serialize(src: T, typeOfSrc: Type, context: JsonSerializationContext): JsonElement
}