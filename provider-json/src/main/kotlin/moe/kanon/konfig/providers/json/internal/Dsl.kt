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

@file:Suppress("NOTHING_TO_INLINE")

package moe.kanon.konfig.providers.json.internal

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import moe.kanon.kommons.collections.size

@DslMarker
annotation class JsonDsl

// https://github.com/mgrzeszczak/json-dsl/blob/master/src/main/kotlin/com/github/mgrzeszczak/jsondsl/Json.kt

@JsonDsl inline class JsonBuilder(val obj: JsonObject) {
    companion object {
        private val gson = GsonBuilder().create()
    }

    @JsonDsl
    inline operator fun String.invoke(scope: JsonBuilder.() -> Unit) {
        obj.add(this, JsonBuilder(JsonObject()).apply(scope).obj)
    }

    @JsonDsl
    infix fun String.to(value: Any?) {
        when (value) {
            null -> obj.add(this, JsonNull.INSTANCE)
            is String -> obj.addProperty(this, value)
            is Boolean -> obj.addProperty(this, value)
            is Number -> obj.addProperty(this, value)
            is JsonElement -> obj.add(this, value)
            else -> obj.add(this, gson.toJsonTree(value))
        }
    }

    @JsonDsl
    fun array(vararg args: Any?): JsonArray = JsonArray(args.size).also { array ->
        for (value in args) {
            when (value) {
                null -> array.add(JsonNull.INSTANCE)
                is String -> array.add(value)
                is Boolean -> array.add(value)
                is Number -> array.add(value)
                is JsonElement -> array.add(value)
                else -> array.add(gson.toJsonTree(value))
            }
        }
    }

    @JsonDsl
    fun array(iterable: Iterable<Any?>): JsonArray = JsonArray(iterable.size).also { array ->
        for (value in iterable) {
            when (value) {
                null -> array.add(JsonNull.INSTANCE)
                is String -> array.add(value)
                is Boolean -> array.add(value)
                is Number -> array.add(value)
                is JsonElement -> array.add(value)
                else -> array.add(gson.toJsonTree(value))
            }
        }
    }

    @JsonDsl
    fun array(name: String, vararg args: Any?): JsonArray = array(*args).also {
        obj.add(name, it)
    }
}

@JsonDsl inline fun json(scope: JsonBuilder.() -> Unit): JsonObject = JsonBuilder(JsonObject()).apply(scope).obj