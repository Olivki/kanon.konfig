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

@file:Suppress("DataClassPrivateConstructor")

package moe.kanon.konfig.providers.xml

import moe.kanon.konfig.Config
import moe.kanon.konfig.ConfigSettings
import moe.kanon.konfig.entries.Entry

data class XmlProviderSettings private constructor(
    /**
     * Determines how the [Config.name] property should be printed in the configuration file.
     */
    val rootNamePlacement: RootNamePlacement,
    /**
     * The style that the system should use for printing output of generics.
     *
     * This is mainly used for the output of the [Entry.javaType] property in the configuration file.
     */
    val genericPrintingStyle: XmlGenericPrintingStyle
) {
    companion object {
        /**
         * Returns a new [XmlProviderSettings] instance with all the values set to their default values.
         */
        val default: XmlProviderSettings get() = Builder().build()

        val builder: Builder get() = Builder()

        inline operator fun invoke(scope: Builder.() -> Unit = {}): XmlProviderSettings =
            builder.apply(scope).build()
    }

    data class Builder internal constructor(
        var rootNamePlacement: RootNamePlacement = RootNamePlacement.IN_TAG,
        var genericPrintingStyle: XmlGenericPrintingStyle = XmlGenericPrintingStyle.DISABLED
    ) {
        fun build(): XmlProviderSettings =
            XmlProviderSettings(rootNamePlacement, genericPrintingStyle)
    }
}

enum class RootNamePlacement {
    /**
     * The `name` will be set as the name of the tag, i.e;
     *
     * A [Config] with the name of `"module"` will have a root element that looks like `"<module>...</module>"`
     */
    IN_TAG,
    /**
     * The `name` will be set as a attribute, i.e;
     *
     * A [Config] with the name of `"module"` will have a root element that looks like `"<root name="module">...</root>"`
     */
    IN_ATTRIBUTE
}

enum class XmlGenericPrintingStyle {
    /**
     * The system will print any output of generics according to how variants and primitives look in Java.
     *
     * That means that an entry like `Map<String, Int>` will be output as
     * `"java.util.Map<java.lang.String, ? extends java.lang.Integer>"`.
     */
    JAVA,
    /**
     * The system will print any output of generics according to how variants and primitives look in Kotlin.
     *
     * That means that an entry like `Map<String, Int>` will be output as
     * `"java.util.Map<java.lang.String, out java.lang.Integer>"`.
     */
    KOTLIN,
    /**
     * The system will not print any output of generics.
     *
     * This disables the printing of the `"class"` output in the configuration file.
     */
    DISABLED
}