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

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import moe.kanon.kommons.io.paths.contentType
import moe.kanon.kommons.io.paths.notExists
import moe.kanon.kommons.reflection.loadServices
import moe.kanon.kommons.writeOut
import moe.kanon.konfig.internal.ConfigResult
import moe.kanon.konfig.internal.failure
import moe.kanon.konfig.internal.toSuccess
import java.nio.file.Path

internal object ConfigProviderFinder {
    val finders: ImmutableSet<ConfigProvider.Finder> by lazy { loadServices<ConfigProvider.Finder>().toImmutableSet() }

    fun findProvider(file: Path): ConfigResult<ConfigProvider> {
        val mediaType = file.contentType ?: return failure("Could not find a media-type for file <$file>")
        return finders.asSequence().mapNotNull { it.getProvider(mediaType) }.firstOrNull()?.let { it.toSuccess() }
            ?: failure("Could not find provider for file <$file> with media-type <$mediaType>")
    }
}