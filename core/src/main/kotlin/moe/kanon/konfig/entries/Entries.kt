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

package moe.kanon.konfig.entries

import moe.kanon.konfig.layers.AbstractConfigLayer
import moe.kanon.konfig.entries.values.ConstantValue
import moe.kanon.konfig.entries.values.DynamicValue
import moe.kanon.konfig.entries.values.LazyValue
import moe.kanon.konfig.entries.values.LimitedStringValue
import moe.kanon.konfig.entries.values.LimitedValue
import moe.kanon.konfig.entries.values.NormalValue
import moe.kanon.konfig.entries.values.NullableValue
import moe.kanon.konfig.entries.values.Value
import java.lang.reflect.Type

sealed class Entry<T> {
    /**
     * The name of `this` entry.
     *
     * Unless explicitly stated otherwise, the `name` of an entry is what will be used as the `key` when storing it in
     * a [AbstractConfigLayer].
     */
    abstract val name: String

    /**
     * A description explaining what/how the [value] of this entry is used.
     */
    abstract val description: String

    /**
     * The underlying [value-class][Value] of this entry.
     */
    abstract val value: Value

    /**
     * The type of the [value] this entry is storing.
     */
    abstract val type: Type

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is Entry<*> -> false
        name != other.name -> false
        description != other.description -> false
        value != other.value -> false
        type != other.type -> false
        else -> true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }

    override fun toString(): String = "Entry(name='$name', description='$description', type=$type, value=$value)"
}

data class NullableEntry<T : Any?>(
    override val name: String,
    override val description: String,
    override val type: Type,
    override val value: NullableValue<T>
) : Entry<T>()

data class NormalEntry<T : Any>(
    override val name: String,
    override val description: String,
    override val type: Type,
    override val value: NormalValue<T>
) : Entry<T>()

data class LimitedEntry<T>(
    override val name: String,
    override val description: String,
    override val type: Type,
    override val value: LimitedValue<T>
) : Entry<T>() where T : Comparable<T>, T : Any

data class LimitedStringEntry(
    override val name: String,
    override val description: String,
    override val type: Type,
    override val value: LimitedStringValue
) : Entry<String>()

data class ConstantEntry<T : Any>(
    override val name: String,
    override val description: String,
    override val type: Type,
    override val value: ConstantValue<T>
) : Entry<T>()

data class LazyEntry<T : Any>(
    override val name: String,
    override val description: String,
    override val type: Type,
    override val value: LazyValue<T>
) : Entry<T>()

data class DynamicEntry<T : Any>(
    override val name: String,
    override val description: String,
    override val type: Type,
    override val value: DynamicValue<T>
) : Entry<T>()