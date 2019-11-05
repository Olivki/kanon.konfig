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

package moe.kanon.konfig.providers.xml

import com.google.auto.service.AutoService
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentHashSetOf
import moe.kanon.konfig.providers.ConfigProvider

@AutoService(ConfigProvider.Finder::class)
internal object XmlProviderFinder : ConfigProvider.Finder {
    override val mediaTypes: ImmutableSet<String> = persistentHashSetOf("application/xml", "text/xml")

    override fun getProvider(mediaType: String, classLoader: ClassLoader): ConfigProvider? =
        if (mediaType in mediaTypes) XmlProvider(classLoader) else null
}