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

@file:JvmName("DslExceptions")

package moe.kanon.konfig.dsl

class DuplicateDslEntryException(name: String) : Exception("No duplicates of <$name> is allowed in the DSL.")

class MissingFunctionsInDslException(name: String, path: String) :
    Exception("The DSL for <$name> in <$path> is faulty as it has not implemented all the required functions.")