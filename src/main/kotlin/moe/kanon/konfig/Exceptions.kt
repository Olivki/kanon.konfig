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

package moe.kanon.konfig

import java.io.IOException
import java.nio.file.Path

/**
 * Thrown to indicate that something went wrong when attempting to serialize a file into a [Konfig] instance.
 */
open class KonfigSerializationException : IOException {
    
    constructor(message: String) : super(message)
    
    constructor(message: String, cause: Throwable) : super(message, cause)
    
    constructor(cause: Throwable) : super(cause)
    
    companion object {
        /**
         * Creates a [KonfigSerializationException] with a [message] populated by the specified [file] and [info].
         *
         * @param [file] The file that was being loaded.
         * @param [info] Any extra info regarding the failure.
         */
        @JvmStatic
        fun create(file: Path, info: String): KonfigSerializationException =
            KonfigSerializationException(
                "An error occurred when attempting to serialize the file <$file> into a konfig instance: $info"
            )
        
        /**
         * Creates a [KonfigSerializationException] with a [message] populated by the specified [file] and [info].
         *
         * @param [file] The file that was being loaded.
         * @param [info] Any extra info regarding the failure.
         * @param [cause] The original cause of this exception.
         */
        @JvmStatic
        fun create(file: Path, info: String, cause: Throwable): KonfigSerializationException =
            KonfigSerializationException(
                "An error occurred when attempting to serialize the file <$file> into a konfig instance: $info",
                cause
            )
    }
}

/**
 * Thrown to indicate that something went wrong when attempting to deserialize a [Konfig] instance to a file.
 */
open class KonfigDeserializationException : IOException {
    
    constructor(message: String) : super(message)
    
    constructor(message: String, cause: Throwable) : super(message, cause)
    
    constructor(cause: Throwable) : super(cause)
    
    companion object {
        /**
         * Creates a [KonfigDeserializationException] with a [message] populated by the specified [config] and [info].
         *
         * @param [config] The [Konfig] instance that was being deserialized.
         * @param [info] Any extra info regarding the failure.
         */
        @JvmStatic
        fun create(config: Konfig, info: String): KonfigDeserializationException =
            KonfigDeserializationException(
                "An error occurred when attempting to deserialize konfig <$config> into the file <${config.file}>: $info"
            )
        
        /**
         * Creates a [KonfigDeserializationException] with a [message] populated by the specified [config] and [info].
         *
         * @param [config] The [Konfig] instance that was being deserialized.
         * @param [info] Any extra info regarding the failure.
         * @param [cause] The original cause of this exception.
         */
        @JvmStatic
        fun create(config: Konfig, info: String, cause: Throwable): KonfigDeserializationException =
            KonfigDeserializationException(
                "An error occurred when attempting to deserialize konfig <$config> into the file <${config.file}>: $info",
                cause
            )
    }
}

/**
 * Thrown to indicate that the system encountered an unknown entry when reading the [config file][Konfig.file].
 */
open class UnknownEntryException(message: String) : KonfigSerializationException(message) {
    companion object {
        /**
         * Creates a [UnknownEntryException] with a [message] populated by the specified [entry] and [konfig].
         *
         * @param [entry] The name and some additional info regarding the unknown entry.
         * @param [layer] The layer that the system is currently on
         * @param [konfig] The konfig for which the file belongs to.
         */
        @JvmStatic
        fun create(entry: String, layer: String, konfig: Konfig): UnknownEntryException = UnknownEntryException(
            "Encountered a unknown entry <$entry> in layer <$layer> when loading the konfig <$konfig>"
        )
    }
}