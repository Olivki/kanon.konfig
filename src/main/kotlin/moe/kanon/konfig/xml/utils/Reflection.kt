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

@file:JvmName("KReflectionUtils")

package moe.kanon.konfig.xml.utils

import com.google.common.reflect.TypeToken
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass

inline fun <reified T> createToken(): TypeToken<T> = object : TypeToken<T>() {}

inline fun <reified T> createType(): Type = createToken<T>().type

inline val <reified T : Any> KClass<T>.token get() = createToken<T>()

inline val <reified T : Any> KClass<T>.classToken get() = TypeToken.of(this::class.java)

val KClass<*>.captureType: Type?
    get() {
        val superClass = this.java.genericSuperclass
        require(superClass is ParameterizedType) { "$superClass isn't parameterized" }
        return superClass.actualTypeArguments[0]
    }

@Suppress("UNCHECKED_CAST")
inline val <reified T : Any> KClass<T>.captureToken: TypeToken<T> get() = TypeToken.of(this.captureType!!) as TypeToken<T>

inline fun <reified T : Any> typeTokenOf(type: Type): TypeToken<T> = TypeToken.of(type) as TypeToken<T>