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

import moe.kanon.konfig.entries.Entry
import java.io.IOException
import java.nio.file.Path

/**
 * Thrown to indicate that something went wrong when attempting to deserialize a file into a [Konfig] instance.
 *
 * @property [konfig] The [Konfig] instance that this exception stems from.
 * @property [file] The file that this exception stems from.
 */
open class KonfigDeserializationException @JvmOverloads constructor(
    val konfig: Konfig,
    val file: Path,
    message: String? = null,
    cause: Throwable? = null
) : IOException(message, cause) {
    companion object {
        /**
         * Creates a [KonfigDeserializationException] with a [message] populated by the specified [konfig], [file] and
         * [info].
         *
         * @param [konfig] the [Konfig] instance that the file is being loaded into
         * @param [file] the file that was being loaded
         * @param [info] any extra info regarding the failure
         */
        @JvmStatic fun of(konfig: Konfig, file: Path, info: String): KonfigDeserializationException =
            KonfigDeserializationException(
                konfig,
                file,
                "An error occurred when attempting to deserialize the file <$file> into the konfig <${konfig.name}>: $info"
            )

        /**
         * Creates a [KonfigDeserializationException] with a [message] populated by the specified [konfig], [file],
         * [info] and [cause].
         *
         * @param [konfig] the [Konfig] instance that the file is being loaded into
         * @param [file] the file that was being loaded
         * @param [info] any extra info regarding the failure
         * @param [cause] the original cause of `this` exception
         */
        @JvmStatic fun of(konfig: Konfig, file: Path, info: String, cause: Throwable): KonfigDeserializationException =
            KonfigDeserializationException(
                konfig,
                file,
                "An error occurred when attempting to deserialize the file <$file> into the konfig <${konfig.name}>: $info",
                cause
            )
    }
}

/**
 * Thrown to indicate that something went wrong when attempting to serialize a [Konfig] instance to a file.
 *
 * @property [konfig] The [Konfig] instance that this exception stems from.
 * @property [file] The file that this exception stems from.
 */
open class KonfigSerializationException @JvmOverloads constructor(
    val konfig: Konfig,
    val file: Path,
    message: String? = null,
    cause: Throwable? = null
) : IOException(message, cause) {
    companion object {
        /**
         * Creates a [KonfigSerializationException] with a [message] populated by the specified [konfig], [file] and
         * [info].
         *
         * @param [konfig] the [Konfig] instance that was being deserialized
         * @param [file] the file that the [konfig] is being serialized to
         * @param [info] any extra info regarding the failure
         */
        @JvmStatic fun of(konfig: Konfig, file: Path, info: String): KonfigSerializationException =
            KonfigSerializationException(
                konfig,
                file,
                "An error occurred when attempting to serialize the konfig <$konfig> to the file <$file>: $info"
            )

        /**
         * Creates a [KonfigSerializationException] with a [message] populated by the specified [konfig], [file],
         * [info] and [cause].
         *
         * @param [konfig] the [Konfig] instance that was being deserialized
         * @param [file] the file that the [konfig] is being serialized to
         * @param [info] any extra info regarding the failure
         * @param [cause] the original cause of `this` exception
         */
        @JvmStatic fun of(konfig: Konfig, file: Path, info: String, cause: Throwable): KonfigSerializationException =
            KonfigSerializationException(
                konfig,
                file,
                "An error occurred when attempting to serialize the konfig <$konfig> to the file <$file>: $info",
                cause
            )
    }
}

/**
 * Thrown to indicate that the system encountered an unknown entry when reading the [config file][Konfig.file].
 *
 * @param [konfig] the [Konfig] instance that `this` exception stems from.
 * @param [file] the file that `this` exception stems from.
 *
 * @property [entry] The name and some additional info regarding the unknown entry that `this` exception stems from.
 * @property [layer] The layer that the system is currently on that `this` exception stems from.
 */
open class UnknownEntryException @JvmOverloads constructor(
    konfig: Konfig,
    file: Path,
    val entry: String,
    val layer: String,
    message: String? = null,
    cause: Throwable? = null
) : KonfigDeserializationException(konfig, file, message, cause) {
    companion object {
        /**
         * Creates a [UnknownEntryException] with a [message] populated by the specified [konfig], [file], [entry] and
         * [layer].
         *
         * @param [konfig] the konfig for which the file belongs to
         * @param [file] the file that the [konfig] is being loaded from
         * @param [entry] the name and some additional info regarding the unknown entry
         * @param [layer] the layer that the system is currently on
         */
        @JvmStatic fun of(konfig: Konfig, file: Path, entry: String, layer: String): UnknownEntryException =
            UnknownEntryException(
                konfig,
                file,
                entry,
                layer,
                "Encountered a unknown entry <$entry> in layer <$layer> when loading the konfig <$konfig>"
            )
    }
}

/**
 * Thrown to indicate that the system encountered a problem when trying to parse a value from a configuration file.
 *
 * @property [faultyValue] The parsed value that was deemed faulty.
 * @property [entry] The [Entry] that the value belongs to.
 * @property [entryKey] The key that the [Entry] is stored under in the [layer].
 * @property [layer] The [Layer] that the [entry] belongs to.
 */
open class FaultyParsedValueException @JvmOverloads constructor(
    konfig: Konfig,
    file: Path,
    val faultyValue: Any?,
    val entry: Entry<*>,
    val entryKey: String,
    val layer: Layer,
    message: String? = null,
    cause: Throwable? = null
) : KonfigDeserializationException(konfig, file, message, cause) {
    companion object {
        /**
         * Creates and returns a [FaultyParsedValueException] with a [message] populated by the the specified [konfig],
         * [file], [faultyValue], [entry], [entryKey] and [layer].
         *
         * @param [konfig] the konfig for which the file belongs to
         * @param [file] the file that the [konfig] is being loaded from
         * @param [faultyValue] the value that was deemed faulty by the system
         * @param [entry] the [Entry] that the [faultyValue] belongs to
         * @param [entryKey] the key that the [entry] is stored under in the specified [layer]
         * @param [layer] the [Layer] that the [entry] belongs to
         */
        @JvmStatic fun of(
            konfig: Konfig,
            file: Path,
            faultyValue: Any?,
            entry: Entry<*>,
            entryKey: String,
            layer: Layer
        ): FaultyParsedValueException = FaultyParsedValueException(
            konfig,
            file,
            faultyValue,
            entry,
            entryKey,
            layer,
            "The parsed value <$faultyValue> was deemed faulty for the entry <$entry> with the value <${layer.getNullable<Any>(
                entryKey
            )}>"
        )

        /**
         * Creates and returns a [FaultyParsedValueException] with a [message] populated by the the specified [konfig],
         * [file], [faultyValue], [entry], [entryKey], [layer] and [cause].
         *
         * @param [konfig] the konfig for which the file belongs to
         * @param [file] the file that the [konfig] is being loaded from
         * @param [faultyValue] the value that was deemed faulty by the system
         * @param [entry] the [Entry] that the [faultyValue] belongs to
         * @param [entryKey] the key that the [entry] is stored under in the specified [layer]
         * @param [layer] the [Layer] that the [entry] belongs to
         * @param [cause] the original cause of `this` exception
         */
        @JvmStatic fun of(
            konfig: Konfig,
            file: Path,
            faultyValue: Any?,
            entry: Entry<*>,
            entryKey: String,
            layer: Layer,
            cause: Throwable
        ): FaultyParsedValueException = FaultyParsedValueException(
            konfig,
            file,
            faultyValue,
            entry,
            entryKey,
            layer,
            "The parsed value <$faultyValue> was deemed faulty for the entry <$entry> with the value <${layer.getNullable<Any>(
                entryKey
            )}> cause <${cause.message}>",
            cause
        )
    }
}