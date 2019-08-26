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

package moe.kanon.konfig.entries.values

import moe.kanon.kommons.UNSUPPORTED
import moe.kanon.konfig.ConfigException
import moe.kanon.konfig.internal.TypeToken
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

data class ValueSetter<V : Value, T>(val ref: V, var field: T, val value: T)

sealed class Value(val valueType: String, val isMutable: Boolean, val shouldDeserialize: Boolean) {
    abstract val javaType: Type

    /**
     * Sets the `value` of this value-class to its `default` value, if it has one, otherwise does nothing.
     */
    open fun resetValue() {
        UNSUPPORTED("Can't reset value of a '$valueType' value")
    }

    protected inline fun validateType(valueClass: KClass<*>, topClass: KClass<*>) {
        if (!valueClass.isSubclassOf(topClass)) {
            throw ConfigException(
                """
                    
                Failed to set value of this <$this> value class because of type mismatch.
                The given value <$valueClass> is NOT a sub-type of the type of the value this class stores, <$topClass>.
                Value classes will ONLY store values that correspond to the type given at creation.
                """.trimIndent()
            )
        }
    }
}

class NullableValue<T : Any?>(
    value: T?,
    val default: T?,
    override val javaType: Type,
    private val setter: ValueSetter<NullableValue<T>, T?>.() -> Unit
) : Value("nullable", isMutable = true, shouldDeserialize = true) {
    var value: T? = value
        internal set(value) {
            field = if (value != null) {
                validateType(
                    (value?.let { it as Any } ?: throw ConfigException())::class,
                    (TypeToken.of(javaType).rawType as Class<*>).kotlin
                )
                ValueSetter(this, field, value).apply(setter).field
            } else {
                ValueSetter(this, field, value).apply(setter).field
            }
        }

    override fun resetValue() {
        value = default
    }

    override fun toString(): String = "NullableValue(value=$value, default=$default, type=$javaType)"
}

class NormalValue<T : Any>(
    value: T,
    val default: T,
    override val javaType: Type,
    private val setter: ValueSetter<NormalValue<T>, T>.() -> Unit
) : Value("normal", isMutable = true, shouldDeserialize = true) {
    var value: T = value
        internal set(value) {
            validateType(value::class, field::class)
            field = ValueSetter(this, field, value).apply(setter).field
        }

    override fun resetValue() {
        value = default
    }

    override fun toString(): String = "NormalValue(value=$value, default=$default, type=$javaType)"
}

class LimitedValue<T>(
    value: T,
    val default: T,
    val range: ClosedRange<T>,
    override val javaType: Type,
    private val setter: ValueSetter<LimitedValue<T>, T>.() -> Unit
) : Value("limited", isMutable = true, shouldDeserialize = true) where T : Any, T : Comparable<T> {
    var value: T = value
        @Throws(ValueOutsideOfRangeException::class)
        internal set(value) {
            validateType(value::class, field::class)
            if (value !in range) throw ValueOutsideOfRangeException(this, "<$value> is outside of the set range <$range>")
            field = ValueSetter(this, field, value).apply(setter).field
        }

    override fun resetValue() {
        value = default
    }

    override fun toString(): String = "LimitedValue(value=$value, default=$default, range=$range, type=$javaType)"
}

class LimitedStringValue(
    value: String,
    val default: String,
    val range: IntRange,
    override val javaType: Type,
    private val setter: ValueSetter<LimitedStringValue, String>.() -> Unit
) : Value("limited", isMutable = true, shouldDeserialize = true) {
    var value: String = value
        @Throws(ValueOutsideOfRangeException::class)
        internal set(value) {
            validateType(value::class, field::class)
            if (value.length !in range) throw ValueOutsideOfRangeException(this, "<$value> is not in range <$range>")
            field = ValueSetter(this, field, value).apply(setter).field
        }

    override fun resetValue() {
        value = default
    }

    override fun toString(): String = "LimitedStringValue(value='$value', default='$default', range=$range, type=$javaType)"
}

data class ConstantValue<T : Any>(
    val value: T,
    override val javaType: Type
) : Value("constant", isMutable = false, shouldDeserialize = true)

class LazyValue<T : Any>(
    initializer: () -> T,
    override val javaType: Type
) : Value("lazy", isMutable = false, shouldDeserialize = false) {
    @Suppress("ClassName")
    private object UNINITIALIZED_VALUE

    private var _initializer: (() -> T)? = initializer
    private var _value: Any? = UNINITIALIZED_VALUE

    val value: T
        get() {
            if (_value == UNINITIALIZED_VALUE) {
                _value = _initializer!!()
                _initializer = null
            }

            @Suppress("UNCHECKED_CAST")
            return _value as T
        }

    val isInitialized: Boolean get() = _value != UNINITIALIZED_VALUE

    override fun toString(): String =
        "LazyValue(value=${if (isInitialized) value.toString() else "Not initialized"}, type=$javaType)"
}

class DynamicValue<T : Any>(
    private val closure: () -> T,
    override val javaType: Type
) : Value("dynamic", isMutable = false, shouldDeserialize = false) {
    val value: T get() = closure()

    override fun toString(): String = "DynamicValue(value=$closure, type=$javaType)"
}