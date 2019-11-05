group = "moe.kanon.konfig"
description = "The core of kanon.konfig"
version = "2.1.1"
extra["packageName"] = "core"

plugins {
    kotlin("kapt")
}

repositories {
    maven(url = "https://dl.bintray.com/kotlin/kotlinx") { name = "kotlinx" }
}

dependencies {
    compile(group = "moe.kanon.kommons", name = "kommons.func", version = "1.3.0")
    compile(group = "moe.kanon.kommons", name = "kommons.reflection", version = "0.4.3")
    compile(group = "moe.kanon.kommons", name = "kommons.io", version = "1.1.0")
    compile(group = "moe.kanon.kommons", name = "kommons.lang", version = "0.1.0")
    compile(group = "moe.kanon.kommons", name = "kommons.collections", version = "0.2.0")

    compile(group = "io.github.microutils", name = "kotlin-logging", version = "1.6.26")

    compileOnly(group = "com.google.auto.service", name = "auto-service", version = "1.0-rc4")
    kapt(group = "com.google.auto.service", name = "auto-service", version = "1.0-rc4")

    compile(group = "org.jetbrains.kotlinx", name = "kotlinx-collections-immutable", version = "0.2")

    // Test Dependencies
    testImplementation(group = "io.kotlintest", name = "kotlintest-runner-junit5", version = "3.1.11")
    testImplementation(group = "org.slf4j", name = "slf4j-simple", version = "1.8.0-beta2")

    testCompileOnly(group = "com.google.auto.service", name = "auto-service", version = "1.0-rc4")
    kaptTest(group = "com.google.auto.service", name = "auto-service", version = "1.0-rc4")
}