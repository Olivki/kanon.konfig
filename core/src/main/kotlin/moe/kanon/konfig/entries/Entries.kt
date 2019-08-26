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

@file:Suppress("DataClassPrivateConstructor")

package moe.kanon.konfig.entries

import moe.kanon.konfig.entries.values.ConstantValue
import moe.kanon.konfig.entries.values.DynamicValue
import moe.kanon.konfig.entries.values.LazyValue
import moe.kanon.konfig.entries.values.LimitedStringValue
import moe.kanon.konfig.entries.values.LimitedValue
import moe.kanon.konfig.entries.values.NormalValue
import moe.kanon.konfig.entries.values.NullableValue
import moe.kanon.konfig.entries.values.Value
import moe.kanon.konfig.entries.values.ValueSetter
import moe.kanon.konfig.internal.typeTokenOf
import moe.kanon.konfig.layers.AbstractConfigLayer
import java.lang.reflect.Type
import kotlin.reflect.KType
import kotlin.reflect.typeOf

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
     * The kotlin-type of the [value] this entry is storing.
     */
    abstract val kotlinType: KType

    /**
     * The java-type of the [value] this entry is storing.
     */
    abstract val javaType: Type

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is Entry<*> -> false
        name != other.name -> false
        description != other.description -> false
        value != other.value -> false
        javaType != other.javaType -> false
        else -> true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + value.hashCode()
        result = 31 * result + javaType.hashCode()
        return result
    }

    override fun toString(): String = "Entry(name='$name', description='$description', type=$javaType, value=$value)"
}

data class NullableEntry<T : Any?> private constructor(
    override val name: String,
    override val description: String,
    override val kotlinType: KType,
    override val javaType: Type,
    override val value: NullableValue<T>
) : Entry<T>() {
    constructor(
        name: String,
        description: String,
        kotlinType: KType,
        javaType: Type,
        value: T?,
        default: T?,
        setter: ValueSetter<NullableValue<T>, T?>.() -> Unit
    ) : this(name, description, kotlinType, javaType, NullableValue(value, default, kotlinType, javaType, setter))
}

data class NormalEntry<T : Any> private constructor(
    override val name: String,
    override val description: String,
    override val kotlinType: KType,
    override val javaType: Type,
    override val value: NormalValue<T>
) : Entry<T>() {
    constructor(
        name: String,
        description: String,
        kotlinType: KType,
        javaType: Type,
        value: T,
        default: T,
        setter: ValueSetter<NormalValue<T>, T>.() -> Unit
    ) : this(name, description, kotlinType, javaType, NormalValue(value, default, kotlinType, javaType, setter))
}

data class LimitedEntry<T> private constructor(
    override val name: String,
    override val description: String,
    override val kotlinType: KType,
    override val javaType: Type,
    override val value: LimitedValue<T>
) : Entry<T>() where T : Comparable<T>, T : Any {
    constructor(
        name: String,
        description: String,
        kotlinType: KType,
        javaType: Type,
        value: T,
        default: T,
        range: ClosedRange<T>,
        setter: ValueSetter<LimitedValue<T>, T>.() -> Unit
    ) : this(name, description, kotlinType, javaType, LimitedValue(value, default, range, kotlinType, javaType, setter))
}

data class LimitedStringEntry private constructor(
    override val name: String,
    override val description: String,
    override val kotlinType: KType,
    override val javaType: Type,
    override val value: LimitedStringValue
) : Entry<String>() {
    companion object {
        @UseExperimental(ExperimentalStdlibApi::class)
        operator fun invoke(
            name: String,
            description: String,
            value: String,
            default: String,
            range: IntRange,
            setter: ValueSetter<LimitedStringValue, String>.() -> Unit
        ): LimitedStringEntry {
            val kotlinType = typeOf<String>()
            val javaType = typeTokenOf<String>().type
            return LimitedStringEntry(
                name,
                description,
                kotlinType,
                javaType,
                LimitedStringValue(value, default, range, kotlinType, javaType, setter)
            )
        }
    }
}

data class ConstantEntry<T : Any> private constructor(
    override val name: String,
    override val description: String,
    override val kotlinType: KType,
    override val javaType: Type,
    override val value: ConstantValue<T>
) : Entry<T>() {
    constructor(
        name: String,
        description: String,
        kotlinType: KType,
        javaType: Type,
        value: T
    ) : this(name, description, kotlinType, javaType, ConstantValue(value, kotlinType, javaType))
}

data class LazyEntry<T : Any> private constructor(
    override val name: String,
    override val description: String,
    override val kotlinType: KType,
    override val javaType: Type,
    override val value: LazyValue<T>
) : Entry<T>() {
    constructor(
        name: String,
        description: String,
        kotlinType: KType,
        javaType: Type,
        value: () -> T
    ) : this(name, description, kotlinType, javaType, LazyValue(value, kotlinType, javaType))
}

data class DynamicEntry<T : Any> private constructor(
    override val name: String,
    override val description: String,
    override val kotlinType: KType,
    override val javaType: Type,
    override val value: DynamicValue<T>
) : Entry<T>() {
    constructor(
        name: String,
        description: String,
        kotlinType: KType,
        javaType: Type,
        value: () -> T
    ) : this(name, description, kotlinType, javaType, DynamicValue(value, kotlinType, javaType))
}