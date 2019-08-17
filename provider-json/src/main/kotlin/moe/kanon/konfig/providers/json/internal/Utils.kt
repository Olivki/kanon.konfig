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

import java.lang.reflect.Type

/**
 * Converts the Java definitions of generic variance to the kotlin ones.
 *
 * `"? extends ..."` -> `"out ..."`
 *
 * `"? super ..."` -> `"in ..."`
 *
 * @receiver the [Type] instance to convert the [typeName][Type.getTypeName] of
 */
// not sure if there's a way to just convert a 'Type' into a 'KType', because I'm pretty sure the output would be
// properly converted automatically then.
internal val Type.kotlinTypeName: String get() = this.typeName.replace("? extends", "out").replace("? super", "in")