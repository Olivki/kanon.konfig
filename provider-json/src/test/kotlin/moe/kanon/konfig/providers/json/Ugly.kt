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

package moe.kanon.konfig.providers.json

import kotlinx.collections.immutable.persistentListOf
import moe.kanon.kommons.io.paths.pathOf
import moe.kanon.kommons.writeOut
import moe.kanon.konfig.Config
import moe.kanon.konfig.ConfigSettings
import moe.kanon.konfig.FaultyParsedValueAction
import moe.kanon.konfig.dsl.buildConfig
import moe.kanon.konfig.layers.ObjectLayer
import java.time.LocalDateTime

fun main() {
    val dir = pathOf("H:", "Programming", "JVM", "Kotlin", "Data", "kanon.konfig", "json")
    val dslFile = dir.resolve("test2.json")

    val dslConfig = buildConfig("cars", dslFile) {
        normalValue("epic string entry", description = "It's a string", default = "stevie wonder")
        normalValue("string entry", "it's a love story", "love", "stevie wonder")
        normalValue("faulty entry", "dab", 42, 1337)
        limitedStringValue("limited string test", "tiger baby", default = "tiger", range = 1..3)
        nullableValue("enum entry", "I love cheese", TestEnum.ENUM_CONSTANT_ONE)
        layer("childLayer") {
            constantValue("L33T", "Deep and fluffy space love", 1337)
        }
    }

    //dslConfig.saveToFile()
    writeOut(dslConfig["string entry"])
    dslConfig.loadFromFile()
    writeOut(dslConfig["string entry"])

    writeOut()

    writeOut(dslConfig["faulty entry"])
    dslConfig["faulty entry"] = 69
    writeOut(dslConfig["faulty entry"])

    dslConfig.saveToFile()

    val objectFile = dir.resolve("test1.json")
    val objectConfig = Config("things", objectFile, LayerOne)
    objectConfig.loadFromFile()

    writeOut(objectConfig["layerOne_Child/limitedLongEntry"])
}

object CarsLayer : ObjectLayer("cars") {
    var epicStringEntry by normal("epic string entry", "It's a string", "stevie wonder")

    var stringEntry by normal("string entry", "it's a love story", "love", "stevie wonder")

    var limitedStringTest by limited("limited string test", "tiger baby", default = "tiger", range = 1..3)

    var enumEntry by nullable("enum entry", "I love cheese", TestEnum.ENUM_CONSTANT_ONE)

    object ChildLayer : ObjectLayer("childLayer") {
        val leet by constant("L33T", "Deep and fluffy space love", 1337)
    }
}

data class TestData(
    val name: String,
    val pair: Pair<Boolean, Int>,
    val triple: Triple<Double, Float, String>,
    val range: IntRange,
    val list: List<String>
)

enum class TestEnum(val string: String, val bool: Boolean) {
    ENUM_CONSTANT_ONE("string one", true),
    OTHER_ENUM_CONSTANT("string fifty", false)
}

object LayerOne : ObjectLayer("layerOne") {
    val enumEntry by normal(
        default = TestEnum.ENUM_CONSTANT_ONE,
        description = "A test enum entry"
    )

    val testData by normal(
        default = TestData(
            "test_data",
            false to 69,
            Triple(13.37, 13.337F, "LEET"),
            13..37,
            listOf("grand chase", "big bad", "epic win")
        ),
        description = "A test of using a data class."
    )

    val testPersistentList by normal(
        default = persistentListOf("hello", "i'm", "a", "persistent", "list"),
        description = "A persistent list test"
    )

    val currentDate by constant(value = LocalDateTime.now(), description = "The current local date and time")

    val immutableConstantEntry by constant(value = "This is a constant", description = "An immutable constant entry.")

    var mutableNullableEntry by nullable<Int>(default = null, description = "A mutable nullable entry.")

    val testList by normal(
        default = listOf(
            "String One",
            "String Two",
            "String Three"
        ),
        description = "A list of strings."
    )

    val testMap by normal(
        default = mapOf(
            "String_Key_One" to 1,
            "String_Key_Two" to 2,
            "String_Key_Three" to 3,
            "String_Key_Four" to 4
        ),
        description = "A map of things."
    )

    object NestedOne : ObjectLayer("nested_1") {
        val temp by normal(default = 1, description = "1")

        object NestedTwo : ObjectLayer("nested_2") {
            val temp by normal(default = 2, description = "2")

            object NestedThree : ObjectLayer("nested_3") {
                val temp by normal(default = 3, description = "3")
            }
        }
    }

    object LayerTwo : ObjectLayer("layerOne_Child") {
        var limitedLongEntry by limited(
            default = 12L,
            range = 5L..25L,
            description = "A limited long entry."
        )

        var limitedStringEntry by limited(
            default = "I'm a string",
            range = 0..15,
            description = "A limited string entry."
        )

        object LayerThree : ObjectLayer("layerOne_Child_Child") {
            val lazyEntry by lazy("custom name lazy entry", "A lazy entry with a custom name.") {
                System.currentTimeMillis()
            }

            val dynamicEntry by dynamic(description = "A dynamic entry.") { System.currentTimeMillis() }
        }
    }
}