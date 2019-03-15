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

@file:JvmName("Entries")
@file:Suppress("DataClassPrivateConstructor", "LocalVariableName", "MemberVisibilityCanBePrivate")

package moe.kanon.konfig.entries

import com.thoughtworks.xstream.annotations.XStreamAlias
import com.thoughtworks.xstream.annotations.XStreamAsAttribute
import com.thoughtworks.xstream.annotations.XStreamOmitField
import moe.kanon.konfig.Konfig
import moe.kanon.konfig.Layer
import moe.kanon.konfig.superClassTypeParameter
import java.lang.reflect.Type
import java.util.*

typealias NullableValue<V> = Entry.Value.Nullable<V>
typealias NormalValue<V> = Entry.Value.Normal<V>
typealias LimitedValue<V> = Entry.Value.Limited<V>
typealias LimitedStringValue = Entry.Value.LimitedString
typealias ConstantValue<V> = Entry.Value.Constant<V>
typealias LazyValue<V> = Entry.Value.Lazy<V>
typealias DynamicValue<V> = Entry.Value.Dynamic<V>

@Suppress("LeakingThis")
sealed class Entry<V : Any>(type: Type?) {
    
    /**
     * The value type of `this` entry.
     */
    abstract val value: Value
    
    /**
     * The name of `this` entry.
     *
     * Unless explicitly stated otherwise, the `name` of an entry is what will be used as the `key` when storing it in
     * a [Layer].
     */
    abstract val name: String
    
    /**
     * The description of `this` entry.
     */
    abstract val description: String
    
    /**
     * The parent [Layer] where `this` entry is stored.
     */
    abstract val parent: Layer
    
    /**
     * The [Type] of `this` entry.
     */
    val javaType: Type = type ?: this::class.superClassTypeParameter!!
    
    /**
     * Returns the [value] property of `this` entry.
     */
    operator fun component1(): Value = value
    
    /**
     * Returns the [name] property of `this` entry.
     */
    operator fun component2(): String = name
    
    /**
     * Returns the [description] property of `this` entry.
     */
    operator fun component3(): String = description
    
    /**
     * Returns the [javaType] property of `this` entry.
     */
    operator fun component4(): Type = javaType
    
    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is Entry<*> -> false
        value != other.value -> false
        name != other.name -> false
        description != other.description -> false
        parent != other.parent -> false
        javaType != other.javaType -> false
        else -> true
    }
    
    override fun hashCode(): Int = Objects.hash(value, name, description, parent, javaType)
    
    override fun toString(): String =
        "Entry(value=$value, name='$name', description='$description', parent=$parent, javaType=$javaType)"
    
    sealed class Value(
        @XStreamAlias("type")
        @XStreamAsAttribute val name: String,
        @XStreamOmitField val isMutable: Boolean,
        @XStreamOmitField val shouldDeserialize: Boolean,
        @XStreamOmitField val javaType: Type
    ) {
        
        /**
         * Sets the `value` property of `this` value to the value of the `default` property.
         *
         * @throws [IllegalAccessException] If invoked on a value where [isMutable] is `false`.
         */
        abstract fun reset()
        
        /**
         * A container that holds a mutable value that's nullable.
         *
         * @property [value] The `value` stored by this container.
         * @property [default] The default-value of the [value].
         */
        @XStreamAlias("container")
        class Nullable<V : Any?>(
            var value: V?,
            val default: V?,
            javaType: Type
        ) : Value(
            name = "nullable",
            isMutable = true,
            shouldDeserialize = true,
            javaType = javaType
        ) {
            
            override fun reset() {
                value = default
            }
            
            override fun equals(other: Any?): Boolean = when {
                this === other -> true
                other !is Nullable<*> -> false
                value != other.value -> false
                default != other.default -> false
                else -> true
            }
            
            override fun hashCode(): Int = Objects.hash(value, default)
            
            override fun toString(): String = "Nullable(value=$value, default=$default)"
        }
        
        /**
         * A container that holds a mutable value.
         *
         * @property [value] The `value` stored by this container.
         * @property [default] The default-value of the [value].
         */
        @XStreamAlias("container")
        class Normal<V : Any>(
            var value: V,
            val default: V,
            javaType: Type
        ) : Value(
            name = "normal",
            isMutable = true,
            shouldDeserialize = true,
            javaType = javaType
        ) {
            override fun reset() {
                value = default
            }
            
            override fun equals(other: Any?): Boolean = when {
                this === other -> true
                other !is Normal<*> -> false
                value != other.value -> false
                default != other.default -> false
                else -> true
            }
            
            override fun hashCode(): Int = Objects.hash(value, default)
            
            override fun toString(): String = "Normal(value=$value, default=$default)"
            
        }
        
        /**
         * A container that holds a mutable value which is limited to a specific [range].
         *
         * @param [value] The initial value.
         * @property [default] The default-value of the [value].
         * @property [range] The range to limit the [value] to.
         */
        @XStreamAlias("container")
        class Limited<V : Comparable<V>>(
            value: V,
            val default: V,
            @XStreamOmitField val range: ClosedRange<V>,
            javaType: Type
        ) : Value(
            name = "limited",
            isMutable = true,
            shouldDeserialize = true,
            javaType = javaType
        ) {
            
            /**
             * The value stored by this container.
             *
             * This only allows values that are inside of the specified [range] to be set, throwing a
             * [ValueOutsideOfRangeException] if the `newValue` does not fit in it.
             */
            @set:Throws(ValueOutsideOfRangeException::class)
            var value: V = value
                set(newValue) {
                    if (newValue !in range) throw ValueOutsideOfRangeException.create(newValue, range)
                    field = newValue
                }
            
            override fun reset() {
                value = default
            }
            
            /**
             * Returns the [value] property of `this` [Value].
             */
            operator fun component1(): V = value
            
            /**
             * Returns the [default] property of `this` [Value].
             */
            operator fun component2(): V = default
            
            /**
             * Returns the [range] property of `this` [Value].
             */
            operator fun component3(): ClosedRange<V> = range
            
            override fun equals(other: Any?): Boolean = when {
                this === other -> true
                other !is Limited<*> -> false
                value != other.value -> false
                default != other.default -> false
                range != other.range -> false
                else -> true
            }
            
            override fun hashCode(): Int = Objects.hash(value, default, range)
            
            override fun toString(): String = "Limited(value=$value, default=$default, range=$range)"
        }
        
        /**
         * A container that holds a mutable [String] value, which is limited to a specific [length][String.length] via
         * the specified [range].
         *
         * @param [value] The initial value.
         * @property [default] The default-value of the [value].
         * @property [range] The range to limit the [value] to.
         */
        @XStreamAlias("container")
        class LimitedString(
            value: String,
            val default: String,
            @XStreamOmitField val range: IntRange,
            javaType: Type
        ) :
            Value(
                name = "limited",
                isMutable = true,
                shouldDeserialize = true,
                javaType = javaType
            ) {
            
            /**
             * The value stored by this container.
             *
             * This only allows values that are inside of the specified [range] to be set, throwing a
             * [ValueOutsideOfRangeException] if the `newValue` does not fit in it.
             */
            @set:Throws(ValueOutsideOfRangeException::class)
            //@XStreamAlias("svalue")
            var value: String = value
                set(newValue) {
                    if (newValue.length !in range) throw ValueOutsideOfRangeException.create(newValue, range)
                    field = newValue
                }
            
            override fun reset() {
                value = default
            }
            
            /**
             * Returns the [value] property of `this` [Value].
             */
            operator fun component1(): String = value
            
            /**
             * Returns the [default] property of `this` [Value].
             */
            operator fun component2(): String = default
            
            /**
             * Returns the [range] property of `this` [Value].
             */
            operator fun component3(): IntRange = range
            
            override fun equals(other: Any?): Boolean = when {
                this === other -> true
                other !is Limited<*> -> false
                value != other.value -> false
                default != other.default -> false
                range != other.range -> false
                else -> true
            }
            
            override fun hashCode(): Int = Objects.hash(value, default, range)
            
            override fun toString(): String = "Limited(value='$value', default='$default', range=$range)"
        }
        
        /**
         * A container that holds an immutable value.
         *
         * @property [value] The `value` stored by this container.
         */
        @XStreamAlias("container")
        class Constant<V : Any>(val value: V, javaType: Type) : Value(
            name = "constant",
            isMutable = false,
            shouldDeserialize = true,
            javaType = javaType
        ) {
            override fun reset() =
                throw IllegalAccessException("Illegal attempt to invoke 'reset' on a value that is not mutable.")
            
            override fun equals(other: Any?): Boolean = when {
                this === other -> true
                other !is Constant<*> -> false
                value != other.value -> false
                else -> true
            }
            
            override fun hashCode(): Int = value.hashCode()
            
            override fun toString(): String = "Constant(value=$value)"
        }
        
        /**
         * A container that holds a value that is *lazily* evaluated upon invocation.
         *
         * Due to the nature of this container, this does *not* get saved into the [config file][Konfig.file].
         */
        @XStreamAlias("container")
        class Lazy<V : Any>(initializer: () -> V, javaType: Type) : Value(
            name = "lazy",
            isMutable = false,
            shouldDeserialize = false,
            javaType = javaType
        ) {
            @Suppress("ClassName")
            private object UNINITIALIZED_VALUE
            
            private var _initializer: (() -> V)? = initializer
            private var _value: Any? = UNINITIALIZED_VALUE
            
            val value: V
                get() {
                    if (_value == UNINITIALIZED_VALUE) {
                        _value = _initializer!!()
                        _initializer = null
                    }
                    
                    @Suppress("UNCHECKED_CAST")
                    return _value as V
                }
    
            override fun reset() =
                throw IllegalAccessException("Illegal attempt to invoke 'reset' on a value that is not mutable.")
            
            fun isInitialized(): Boolean = _value != UNINITIALIZED_VALUE
            
            override fun toString(): String =
                "Lazy(value=${if (isInitialized()) value.toString() else "Not initialized"})"
        }
        
        /**
         * A container that holds a value that gets evaluated upon every invocation.
         *
         * Due to the nature of this container, this does *not* get saved into the [config file][Konfig.file].
         */
        @XStreamAlias("container")
        class Dynamic<V : Any>(private val closure: () -> V, javaType: Type) : Value(
            name = "dynamic",
            isMutable = false,
            shouldDeserialize = false,
            javaType = javaType
        ) {
            
            /**
             * Returns the result of invoking [closure].
             */
            val value: V get() = closure()
    
            override fun reset() =
                throw IllegalAccessException("Illegal attempt to invoke 'reset' on a value that is not mutable.")
        }
    }
}

class NullableEntry<V : Any> private constructor(
    type: Type,
    override val value: NullableValue<V>,
    override val name: String,
    override val description: String,
    override val parent: Layer
) : Entry<V>(type) {
    companion object {
        @JvmStatic
        fun <V : Any> of(
            type: Type,
            value: NullableValue<V>,
            name: String,
            description: String,
            parent: Layer
        ): NullableEntry<V> = NullableEntry(type, value, name, description, parent)
        
        @JvmStatic
        fun <V : Any> of(
            type: Type,
            value: V?,
            default: V?,
            name: String,
            description: String,
            parent: Layer
        ): NullableEntry<V> = NullableEntry(type, NullableValue(value, default, type), name, description, parent)
        
        @JvmStatic
        fun <V : Any> of(
            type: Type,
            default: V?,
            name: String,
            description: String,
            parent: Layer
        ): NullableEntry<V> = NullableEntry(type, NullableValue(default, default, type), name, description, parent)
    }
}

class NormalEntry<V : Any> private constructor(
    type: Type,
    override val value: NormalValue<V>,
    override val name: String,
    override val description: String,
    override val parent: Layer
) : Entry<V>(type) {
    companion object {
        @JvmStatic
        fun <V : Any> of(
            type: Type,
            value: NormalValue<V>,
            name: String,
            description: String,
            parent: Layer
        ): NormalEntry<V> = NormalEntry(type, value, name, description, parent)
        
        @JvmStatic
        fun <V : Any> of(
            type: Type,
            value: V,
            default: V,
            name: String,
            description: String,
            parent: Layer
        ): NormalEntry<V> = NormalEntry(type, NormalValue(value, default, type), name, description, parent)
        
        @JvmStatic
        fun <V : Any> of(
            type: Type,
            default: V,
            name: String,
            description: String,
            parent: Layer
        ): NormalEntry<V> = NormalEntry(type, NormalValue(default, default, type), name, description, parent)
    }
}

class LimitedEntry<V : Comparable<V>> private constructor(
    type: Type,
    override val value: LimitedValue<V>,
    override val name: String,
    override val description: String,
    override val parent: Layer
) : Entry<V>(type) {
    companion object {
        @JvmStatic
        fun <V : Comparable<V>> of(
            type: Type,
            value: LimitedValue<V>,
            name: String,
            description: String,
            parent: Layer
        ): LimitedEntry<V> = LimitedEntry(type, value, name, description, parent)
        
        @JvmStatic
        fun <V : Comparable<V>> of(
            type: Type,
            value: V,
            default: V,
            range: ClosedRange<V>,
            name: String,
            description: String,
            parent: Layer
        ): LimitedEntry<V> = LimitedEntry(type, LimitedValue(value, default, range, type), name, description, parent)
        
        @JvmStatic
        fun <V : Comparable<V>> of(
            type: Type,
            default: V,
            range: ClosedRange<V>,
            name: String,
            description: String,
            parent: Layer
        ): LimitedEntry<V> = LimitedEntry(type, LimitedValue(default, default, range, type), name, description, parent)
    }
}

class LimitedStringEntry private constructor(
    override val value: LimitedStringValue,
    override val name: String,
    override val description: String,
    override val parent: Layer
) : Entry<String>(com.google.common.reflect.TypeToken.of(String::class.java).type) {
    companion object {
        @JvmStatic
        fun of(value: LimitedStringValue, name: String, description: String, parent: Layer): LimitedStringEntry =
            LimitedStringEntry(value, name, description, parent)
        
        @JvmStatic
        fun of(default: String, range: IntRange, name: String, description: String, parent: Layer): LimitedStringEntry =
            LimitedStringEntry(
                LimitedStringValue(
                    default,
                    default,
                    range,
                    com.google.common.reflect.TypeToken.of(String::class.java).type
                ), name, description, parent
            )
        
        @JvmStatic
        fun of(
            value: String,
            default: String,
            range: IntRange,
            name: String,
            description: String,
            parent: Layer
        ): LimitedStringEntry = LimitedStringEntry(
            LimitedStringValue(
                value,
                default,
                range,
                com.google.common.reflect.TypeToken.of(String::class.java).type
            ), name, description, parent
        )
    }
}

class ConstantEntry<V : Any> private constructor(
    type: Type,
    override val value: Value.Constant<V>,
    override val name: String,
    override val description: String,
    override val parent: Layer
) : Entry<V>(type) {
    companion object {
        @JvmStatic
        fun <V : Any> of(
            type: Type,
            value: Value.Constant<V>,
            name: String,
            description: String,
            parent: Layer
        ): ConstantEntry<V> = ConstantEntry(type, value, name, description, parent)
        
        @JvmStatic
        fun <V : Any> of(type: Type, value: V, name: String, description: String, parent: Layer): ConstantEntry<V> =
            ConstantEntry(type, ConstantValue(value, type), name, description, parent)
    }
}

class LazyEntry<V : Any> private constructor(
    type: Type,
    override val value: LazyValue<V>,
    override val name: String,
    override val description: String,
    override val parent: Layer
) : Entry<V>(type) {
    companion object {
        @JvmStatic
        fun <V : Any> of(
            type: Type,
            value: LazyValue<V>,
            name: String,
            description: String,
            parent: Layer
        ): LazyEntry<V> = LazyEntry(type, value, name, description, parent)
        
        @JvmStatic
        fun <V : Any> of(
            type: Type,
            value: () -> V,
            name: String,
            description: String,
            parent: Layer
        ): LazyEntry<V> = LazyEntry(type, LazyValue(value, type), name, description, parent)
    }
}

class DynamicEntry<V : Any> private constructor(
    type: Type,
    override val value: DynamicValue<V>,
    override val name: String,
    override val description: String,
    override val parent: Layer
) : Entry<V>(type) {
    companion object {
        @JvmStatic
        fun <V : Any> of(
            type: Type,
            value: DynamicValue<V>,
            name: String,
            description: String,
            parent: Layer
        ): DynamicEntry<V> = DynamicEntry(type, value, name, description, parent)
        
        @JvmStatic
        fun <V : Any> of(
            type: Type,
            value: () -> V,
            name: String,
            description: String,
            parent: Layer
        ): DynamicEntry<V> = DynamicEntry(type, DynamicValue(value, type), name, description, parent)
    }
}