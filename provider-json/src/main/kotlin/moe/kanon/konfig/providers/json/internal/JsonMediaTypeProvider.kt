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

package moe.kanon.konfig.providers.json.internal

import com.google.auto.service.AutoService
import moe.kanon.kommons.io.paths.name
import java.nio.file.Path
import java.nio.file.spi.FileTypeDetector

@AutoService(FileTypeDetector::class)
class JsonFileTypeDetector : FileTypeDetector() {
    override fun probeContentType(path: Path): String? =
        if (path.name.endsWith(".json", true)) "application/json" else null
}