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

package moe.kanon.konfig.providers

import moe.kanon.konfig.Konfig
import moe.kanon.konfig.KonfigDeserializationException
import moe.kanon.konfig.KonfigSerializationException
import java.io.IOException
import java.nio.file.Path

interface Provider {

    /**
     * The name of the format `this` provider is made for.
     */
    val format: String

    /**
     * The [Konfig] that this is a provider for.
     */
    val config: Konfig

    /**
     * Loads and sets entries from the specified [file].
     */
    @Throws(IOException::class, KonfigSerializationException::class)
    fun loadFrom(file: Path)

    /**
     * Saves all the entries from the [config] to the specified [file].
     */
    @Throws(KonfigDeserializationException::class)
    fun saveTo(file: Path)

    /**
     * Converts the specified [value] to a [String] using the specified [gson] instance.
     */
    fun <V : Any> valueToString(value: V?): String
}

abstract class AbstractProvider : Provider {

    // just because we can't call 'this' in the constructor of 'KonfigImpl'.
    final override lateinit var config: Konfig
        @JvmSynthetic internal set

    // if we included the entire 'Konfig.toString' we'd be stuck in an infinite loop.
    override fun toString(): String = "AbstractProvider(format='$format', config='${config.name}')"

}