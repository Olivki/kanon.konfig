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
import java.lang.reflect.Type

data class ValueSetter<V : Value, T>(val ref: V, var field: T, val value: T)

sealed class Value(val name: String, val isMutable: Boolean, val shouldDeserialize: Boolean) {
    abstract val type: Type

    /**
     * Sets the `value` of this value-class to its `default` value, if it has one, otherwise does nothing.
     */
    open fun resetValue() {
        UNSUPPORTED("Can't reset value of a '$name'")
    }
}

class NullableValue<T : Any?>(
    value: T?,
    val default: T?,
    override val type: Type,
    val setter: ValueSetter<NullableValue<T>, T?>.() -> Unit
) : Value("nullable", isMutable = true, shouldDeserialize = true) {
    var value: T? = value
        set(value) {
            field = ValueSetter(this, field, value).apply(setter).field
        }

    override fun resetValue() {
        value = default
    }

    override fun toString(): String = "NullableValue(value=$value, default=$default, type=$type)"
}

class NormalValue<T>(
    value: T,
    val default: T,
    override val type: Type,
    val setter: ValueSetter<NormalValue<T>, T>.() -> Unit
) : Value("normal", isMutable = true, shouldDeserialize = true) {
    var value: T = value
        set(value) {
            field = ValueSetter(this, field, value).apply(setter).field
        }

    override fun resetValue() {
        value = default
    }

    override fun toString(): String = "NormalValue(value=$value, default=$default, type=$type)"
}

class LimitedValue<T : Comparable<T>>(
    value: T,
    val default: T,
    val range: ClosedRange<T>,
    override val type: Type,
    val setter: ValueSetter<LimitedValue<T>, T>.() -> Unit
) : Value("limited", isMutable = true, shouldDeserialize = true) {
    var value: T = value
        @Throws(ValueOutsideOfRangeException::class)
        set(value) {
            if (value !in range) throw ValueOutsideOfRangeException(this, "<$value> is not in range <$range>")
            field = ValueSetter(this, field, value).apply(setter).field
        }

    override fun resetValue() {
        value = default
    }

    override fun toString(): String = "LimitedValue(value=$value, default=$default, range=$range, type=$type)"
}

class LimitedStringValue(
    value: String,
    val default: String,
    val range: IntRange,
    override val type: Type,
    val setter: ValueSetter<LimitedStringValue, String>.() -> Unit
) : Value("limited", isMutable = true, shouldDeserialize = true) {
    var value: String = value
        @Throws(ValueOutsideOfRangeException::class)
        set(value) {
            if (value.length !in range) throw ValueOutsideOfRangeException(this, "<$value> is not in range <$range>")
            field = ValueSetter(this, field, value).apply(setter).field
        }

    override fun resetValue() {
        value = default
    }

    override fun toString(): String = "LimitedStringValue(value='$value', default='$default', range=$range, type=$type)"
}

data class ConstantValue<T>(
    val value: T,
    override val type: Type
) : Value("constant", isMutable = false, shouldDeserialize = true)

class LazyValue<T>(
    initializer: () -> T,
    override val type: Type
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
        "LazyValue(value=${if (isInitialized) value.toString() else "Not initialized"}, type=$type)"
}

class DynamicValue<T>(
    private val closure: () -> T,
    override val type: Type
) : Value("dynamic", isMutable = false, shouldDeserialize = false) {
    val value: T get() = closure()

    override fun toString(): String = "DynamicValue(value=$closure, type=$type)"
}