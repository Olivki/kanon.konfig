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

@file:JvmName("EntriesDsl")

package moe.kanon.konfig.dsl

import moe.kanon.konfig.Layer
import moe.kanon.konfig.entries.Entry
import moe.kanon.konfig.entries.LimitedEntry
import moe.kanon.konfig.entries.NormalEntry
import moe.kanon.konfig.superClassTypeParameter
import java.lang.reflect.Type

@KonfigDsl
open class NormalEntryContainer<V : Any>(private val name: String, private val parent: Layer) {
    
    @PublishedApi
    @field:JvmSynthetic
    @set:JvmSynthetic
    @get:JvmSynthetic
    internal lateinit var valueProp: V
    
    @PublishedApi
    @field:JvmSynthetic
    @set:JvmSynthetic
    @get:JvmSynthetic
    internal lateinit var descriptionProp: String
    
    @PublishedApi
    @field:JvmSynthetic
    @set:JvmSynthetic
    @get:JvmSynthetic
    internal lateinit var defaultProp: V
    
    @Suppress("LeakingThis")
    private val type: Type = this::class.superClassTypeParameter!!
    
    @PublishedApi
    @get:JvmSynthetic internal open val entry: Entry<V> by lazy {
        val value = when {
            isValuePropInitialized() && !isDefaultPropInitialized() -> valueProp
            !isValuePropInitialized() && isDefaultPropInitialized() -> defaultProp
            isValuePropInitialized() && isDefaultPropInitialized() -> valueProp
            else -> throw Exception("Faulty DSL.")
        }
        
        val default = when {
            isValuePropInitialized() && !isDefaultPropInitialized() -> valueProp
            !isValuePropInitialized() && isDefaultPropInitialized() -> defaultProp
            isValuePropInitialized() && isDefaultPropInitialized() -> defaultProp
            else -> throw Exception("Faulty DSL.")
        }
        
        NormalEntry.of(type, value, default, name, descriptionProp, parent)
    }
    
    @PublishedApi
    @JvmSynthetic
    internal fun isValuePropInitialized(): Boolean = this::defaultProp.isInitialized
    
    @PublishedApi
    @JvmSynthetic
    internal fun isDescriptionPropInitialized(): Boolean = this::defaultProp.isInitialized
    
    @PublishedApi
    @JvmSynthetic
    internal fun isDefaultPropInitialized(): Boolean = this::defaultProp.isInitialized
    
    /**
     * Sets the `value` of the [Entry] to the value returned in the specified [closure].
     */
    @KonfigDsl
    inline fun value(closure: () -> V): NormalEntryContainer<V> {
        if (isValuePropInitialized()) throw DuplicateDslEntryException("value(...)")
        valueProp = closure.invoke()
        return this
    }
    
    /**
     * Sets the `description` of the [Entry] to the value returned in the specified [closure].
     */
    @KonfigDsl
    inline fun description(closure: () -> String): NormalEntryContainer<V> {
        if (isDescriptionPropInitialized()) throw DuplicateDslEntryException("description(...)")
        descriptionProp = closure.invoke()
        return this
    }
    
    /**
     * Sets the `defaultValue` of the [Entry] to the value returned in the specified [closure].
     */
    @KonfigDsl
    inline fun default(closure: () -> V): NormalEntryContainer<V> {
        if (isDefaultPropInitialized()) throw DuplicateDslEntryException("default(...)")
        defaultProp = closure.invoke()
        return this
    }
    
}

class LimitedEntryContainer<V : Comparable<V>>(name: String, parent: Layer) :
    NormalEntryContainer<V>(name, parent) {
    
    @PublishedApi
    @field:JvmSynthetic
    @set:JvmSynthetic
    @get:JvmSynthetic
    internal lateinit var rangeProp: ClosedRange<*>
    
    /*
    @PublishedApi
    @get:JvmSynthetic override val entry: LimitedEntry<V> by lazy {
        val default = if (isDefaultPropInitialized()) defaultProp else valueProp
        return@lazy when (rangeProp.start) {
            is Int -> LimitedIntEntry(valueProp, name, descriptionProp, default, rangeProp as IntRange)
            is Long -> LimitedLongEntry(valueProp, name, descriptionProp, default, rangeProp as LongRange)
            is Float -> LimitedFloatEntry(
                valueProp,
                name,
                descriptionProp,
                default,
                rangeProp as ClosedFloatingPointRange<Float>
            )
            is Double -> LimitedDoubleEntry(
                valueProp,
                name,
                descriptionProp,
                default,
                rangeProp as ClosedFloatingPointRange<Double>
            )
            else -> throw IllegalArgumentException("<${rangeProp.start::class}> is not a supported range type.")
        }
    }*/
    
    @PublishedApi
    @JvmSynthetic
    internal fun isRangePropInitialized(): Boolean = this::rangeProp.isInitialized
    
    /**
     * Sets the `range` of the [LimitedEntry] to the [range][ClosedRange] returned in the specified [closure].
     */
    @KonfigDsl
    inline fun range(closure: () -> ClosedRange<*>): LimitedEntryContainer<V> {
        if (isRangePropInitialized()) throw DuplicateDslEntryException("range(...)")
        rangeProp = closure.invoke()
        return this
    }
}