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

package moe.kanon.konfig.providers.json.internal

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.*

// https://github.com/mgrzeszczak/json-dsl/blob/master/src/main/kotlin/com/github/mgrzeszczak/jsondsl/Json.kt
internal class Json private constructor() {

    companion object {

        fun obj(content: Builder.() -> Unit): JsonObject {
            val builder = Builder()
            builder.content()
            return builder.obj(content)
        }

        fun array(vararg args: Any): JsonArray {
            val array = JsonArray()
            args.forEach {
                when (it) {
                    is Char -> array.add(it)
                    is Number -> array.add(it)
                    is String -> array.add(it)
                    is Boolean -> array.add(it)
                    is JsonElement -> array.add(it)
                    else -> array.add(it.toString())
                }
            }
            return array
        }

        class Builder internal constructor() {
            private val objects = Stack<JsonObject>()
            private val current: JsonObject
                get() = objects.peek()

            fun array(vararg args: Any): JsonArray {
                return Companion.array(*args)
            }

            fun obj(content: Builder.() -> Unit): JsonObject {
                val obj = JsonObject()
                objects.push(obj)
                this.content()
                objects.pop()
                return obj
            }

            infix fun String.to(value: Any) {
                when (value) {
                    is Char -> current.addProperty(this, value)
                    is Number -> current.addProperty(this, value)
                    is String -> current.addProperty(this, value)
                    is Boolean -> current.addProperty(this, value)
                    is JsonElement -> current.add(this, value)
                    else -> current.addProperty(this, value.toString())
                }
            }

            init {
                objects.add(JsonObject())
            }
        }
    }
}