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

package moe.kanon.konfig.internal

import moe.kanon.kommons.func.Failure
import moe.kanon.kommons.func.Success
import moe.kanon.kommons.func.Try
import moe.kanon.konfig.ConfigException

typealias ConfigResult<T> = Try<T>

@PublishedApi internal fun failure(message: String, cause: Throwable? = null): Failure =
    Failure(ConfigException(message, cause))

@PublishedApi internal fun <T> T.toSuccess(): Success<T> = Success(this)