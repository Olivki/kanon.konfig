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
 * Registers all the custom deserializers and serializers services.
 */
/*fun ObjectMapper.registerServices() {
    val module = SimpleModule("kanon.konfig")
    for (serializer in loadServices<JsonSerializer<*>>()) module.addSerializer(serializer)
    for (deserializer in loadServices<JsonDeserializer<Any>>()) {
        module.addDeserializer(deserializer.handledType() as Class<Any>, deserializer)
    }

    this.registerModule(module)
}*/

/**
 * Converts the Java definitions of generic variance to the kotlin ones.
 *
 * `"? extends ..."` -> `"out ..."`
 *
 * `"? super ..."` -> `"in ..."`
 *
 * @receiver the [Type] instance to convert the [typeName][Type.getTypeName] of
 */
// this will be used until 'KType' can be properly converted into a java 'Type' instance, because then we will simply
// use the 'toString' of the KType, or the 'toString' of the Type.
internal val Type.kotlinTypeName: String
    get() = this.typeName
        .replace("? extends", "out")
        .replace("? super", "in")
        .replace("java.lang.Character", "kotlin.Char")
        .replace("java.lang.String", "kotlin.String")
        .replace("java.lang.Boolean", "kotlin.Boolean")
        .replace("java.lang.Byte", "kotlin.Byte")
        .replace("java.lang.Short", "kotlin.Short")
        .replace("java.lang.Integer", "kotlin.Int")
        .replace("java.lang.Long", "kotlin.Long")
        .replace("java.lang.Float", "kotlin.Float")
        .replace("java.lang.Double", "kotlin.Double")
        .replace("java.util.Collection", "kotlin.Collection")
        .replace("java.util.List", "kotlin.List")
        .replace("java.util.Set", "kotlin.Set")
        .replace("java.util.Map", "kotlin.Map")
