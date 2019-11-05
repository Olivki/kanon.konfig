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

package moe.kanon.konfig.providers.json.converters

import com.google.auto.service.AutoService
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonElement
import com.google.gson.JsonSerializationContext
import moe.kanon.konfig.providers.json.internal.json
import java.lang.reflect.Type

/*@AutoService(JsonConverter::class)
internal object IntRangeConverter : JsonConverter<IntRange>(IntRange::class) {
    override fun serialize(src: IntRange, typeOfSrc: Type, context: JsonSerializationContext): JsonElement = json {
        "start" to src.first
        "end" to src.last
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): IntRange {
        val container = json.asJsonObject
        val start = container["start"].asInt
        val end = container["end"].asInt
        return IntRange(start, end)
    }
}

@AutoService(JsonConverter::class)
internal object IntProgressionConverter : JsonConverter<IntProgression>(IntProgression::class) {
    override fun serialize(src: IntProgression, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
        json {
            "start" to src.first
            "end" to src.last
            "step" to src.step
        }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): IntProgression {
        val container = json.asJsonObject
        val start = container["start"].asInt
        val end = container["end"].asInt
        val stepAmount = container["step"].asInt
        return IntProgression.fromClosedRange(start, end, stepAmount)
    }
}

@AutoService(JsonConverter::class)
internal object LongRangeConverter : JsonConverter<LongRange>(LongRange::class) {
    override fun serialize(src: LongRange, typeOfSrc: Type, context: JsonSerializationContext): JsonElement = json {
        "start" to src.first
        "end" to src.last
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LongRange {
        val container = json.asJsonObject
        val start = container["start"].asLong
        val end = container["end"].asLong
        return LongRange(start, end)
    }
}

@AutoService(JsonConverter::class)
internal object LongProgressionConverter : JsonConverter<LongProgression>(LongProgression::class) {
    override fun serialize(src: LongProgression, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
        json {
            "start" to src.first
            "end" to src.last
            "step" to src.step
        }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LongProgression {
        val container = json.asJsonObject
        val start = container["start"].asLong
        val end = container["end"].asLong
        val stepAmount = container["step"].asLong
        return LongProgression.fromClosedRange(start, end, stepAmount)
    }
}*/