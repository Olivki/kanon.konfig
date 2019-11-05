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

import io.kotlintest.fail
import io.kotlintest.matchers.types.shouldBeSameInstanceAs
import io.kotlintest.shouldBe
import io.kotlintest.shouldFail
import io.kotlintest.shouldThrow
import io.kotlintest.specs.ExpectSpec
import moe.kanon.konfig.layers.ObjectLayer

object LayerTestOne : ObjectLayer("layer_test_one") {
    val testValueOne: String by constant(description = "Test value one", value = "Test One")

    val testValueTwo: Int by constant(description = "Test value two", value = 42)

    object LayerTestTwo : ObjectLayer("layer_test_two") {
        object LayerTestThree : ObjectLayer("layer_test_three") {

        }
    }
}

class LayerRetrievalTest : ExpectSpec({
    context("attempting to retrieve 'LayerTestTwo' from 'LayerTestOne' using its path") {
        expect("that it should return 'LayerTestTwo' without any issues") {
            val layer = LayerTestOne.getLayer("layer_test_two/")
            layer.shouldBeSameInstanceAs(LayerTestOne.LayerTestTwo)
        }
    }
})