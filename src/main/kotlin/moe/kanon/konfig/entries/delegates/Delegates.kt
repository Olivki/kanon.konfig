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

@file:JvmName("EntryDelegates")
@file:Suppress("LocalVariableName", "MemberVisibilityCanBePrivate", "LeakingThis")

package moe.kanon.konfig.entries.delegates

import moe.kanon.konfig.KonfigLayer
import moe.kanon.konfig.entries.ConstantEntry
import moe.kanon.konfig.entries.DynamicEntry
import moe.kanon.konfig.entries.LazyEntry
import moe.kanon.konfig.entries.LimitedEntry
import moe.kanon.konfig.entries.LimitedStringEntry
import moe.kanon.konfig.entries.NormalEntry
import moe.kanon.konfig.entries.NullableEntry
import moe.kanon.konfig.superClassTypeParameter
import java.lang.reflect.Type
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

open class DelegatedNullableProperty<V : Any>(
    val value: V?,
    val default: V? = value,
    val name: String?,
    val description: String
) {
    private val type: Type = this::class.superClassTypeParameter!!
    
    operator fun provideDelegate(
        thisRef: KonfigLayer,
        property: KProperty<*>
    ): ReadWriteProperty<KonfigLayer, V?> {
        val _name = name ?: property.name
        thisRef += NullableEntry.of(type, value, default, _name, description, thisRef)
        
        return object : ReadWriteProperty<KonfigLayer, V?> {
            override fun getValue(thisRef: KonfigLayer, property: KProperty<*>): V? = thisRef.getNullable(_name)
            
            override fun setValue(thisRef: KonfigLayer, property: KProperty<*>, value: V?) {
                thisRef[_name] = value
            }
        }
    }
}

open class DelegatedNormalProperty<V : Any>(
    val value: V,
    val default: V = value,
    val name: String?,
    val description: String
) {
    private val type: Type = this::class.superClassTypeParameter!!
    
    operator fun provideDelegate(
        thisRef: KonfigLayer,
        property: KProperty<*>
    ): ReadWriteProperty<KonfigLayer, V> {
        val _name = name ?: property.name
        thisRef += NormalEntry.of(type, value, default, _name, description, thisRef)
        
        return object : ReadWriteProperty<KonfigLayer, V> {
            override fun getValue(thisRef: KonfigLayer, property: KProperty<*>): V = thisRef[_name]
            
            override fun setValue(thisRef: KonfigLayer, property: KProperty<*>, value: V) {
                thisRef[_name] = value
            }
        }
    }
}

open class DelegatedLimitedProperty<V : Comparable<V>>(
    val value: V,
    val default: V = value,
    val range: ClosedRange<V>,
    val name: String?,
    val description: String
) {
    private val type: Type = this::class.superClassTypeParameter!!
    
    operator fun provideDelegate(
        thisRef: KonfigLayer,
        property: KProperty<*>
    ): ReadWriteProperty<KonfigLayer, V> {
        val _name = name ?: property.name
        thisRef += LimitedEntry.of(type, value, default, range, _name, description, thisRef)
        
        return object : ReadWriteProperty<KonfigLayer, V> {
            override fun getValue(thisRef: KonfigLayer, property: KProperty<*>): V = thisRef[_name]
            
            override fun setValue(thisRef: KonfigLayer, property: KProperty<*>, value: V) {
                thisRef[_name] = value
            }
        }
    }
}

open class DelegatedLimitedStringProperty(
    val value: String,
    val default: String = value,
    val range: IntRange,
    val name: String?,
    val description: String
) {
    operator fun provideDelegate(
        thisRef: KonfigLayer,
        property: KProperty<*>
    ): ReadWriteProperty<KonfigLayer, String> {
        val _name = name ?: property.name
        thisRef += LimitedStringEntry.of(value, default, range, _name, description, thisRef)
        
        return object : ReadWriteProperty<KonfigLayer, String> {
            override fun getValue(thisRef: KonfigLayer, property: KProperty<*>): String = thisRef[_name]
            
            override fun setValue(thisRef: KonfigLayer, property: KProperty<*>, value: String) {
                thisRef[_name] = value
            }
        }
    }
}

open class DelegatedConstantProperty<V : Any>(
    val value: V,
    val name: String?,
    val description: String
) {
    private val type: Type = this::class.superClassTypeParameter!!
    
    operator fun provideDelegate(
        thisRef: KonfigLayer,
        property: KProperty<*>
    ): ReadOnlyProperty<KonfigLayer, V> {
        val _name = name ?: property.name
        thisRef += ConstantEntry.of(type, value, _name, description, thisRef)
        
        return object : ReadOnlyProperty<KonfigLayer, V> {
            override fun getValue(thisRef: KonfigLayer, property: KProperty<*>): V = thisRef[_name]
        }
    }
}

open class DelegatedLazyProperty<V : Any>(
    val value: () -> V,
    val name: String?,
    val description: String
) {
    private val type: Type = this::class.superClassTypeParameter!!
    
    operator fun provideDelegate(
        thisRef: KonfigLayer,
        property: KProperty<*>
    ): ReadOnlyProperty<KonfigLayer, V> {
        val _name = name ?: property.name
        thisRef += LazyEntry.of(type, value, _name, description, thisRef)
        
        return object : ReadOnlyProperty<KonfigLayer, V> {
            override fun getValue(thisRef: KonfigLayer, property: KProperty<*>): V = thisRef[_name]
        }
    }
}

open class DelegatedDynamicProperty<V : Any>(
    val value: () -> V,
    val name: String?,
    val description: String
) {
    private val type: Type = this::class.superClassTypeParameter!!
    
    operator fun provideDelegate(
        thisRef: KonfigLayer,
        property: KProperty<*>
    ): ReadOnlyProperty<KonfigLayer, V> {
        val _name = name ?: property.name
        thisRef += DynamicEntry.of(type, value, _name, description, thisRef)
        
        return object : ReadOnlyProperty<KonfigLayer, V> {
            override fun getValue(thisRef: KonfigLayer, property: KProperty<*>): V = thisRef[_name]
        }
    }
}