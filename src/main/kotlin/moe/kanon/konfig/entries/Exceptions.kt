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

open class ValueOutsideOfRangeException(message: String, val value: Any, val range: ClosedRange<*>) :
    Exception(message) {
    companion object {
        /**
         * Creates a new [ValueOutsideOfRangeException] with a [message][ValueOutsideOfRangeException.message]
         * populated by the specified [value] and [range].
         *
         * @param [value] The value that caused `this` exception to be thrown.
         * @param [range] The range that the [value] did not fit inside of.
         */
        @JvmStatic
        fun <V : Comparable<V>> create(value: V, range: ClosedRange<V>): ValueOutsideOfRangeException =
            ValueOutsideOfRangeException("The given value <$value> is outside of the set range <$range>", value, range)
        
        /**
         * Creates a new [ValueOutsideOfRangeException] with a [message][ValueOutsideOfRangeException.message]
         * populated by the specified [value] and [range].
         *
         * @param [value] The value that caused `this` exception to be thrown.
         * @param [range] The range that the [value] did not fit inside of.
         */
        @JvmStatic
        fun create(value: String, range: IntRange): ValueOutsideOfRangeException =
            ValueOutsideOfRangeException(
                "The length <${value.length}> of the given string <'$value'> is outside of the set range <$range>",
                value,
                range
            )
        
    }
}