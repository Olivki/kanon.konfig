import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

description = "The JSON provider for kanon.konfig"
version = "1.0.0"
extra["packageName"] = "json-provider"

plugins {
    kotlin("kapt")
}

dependencies {
    implementation(group = "com.google.code.gson", name = "gson", version = "2.8.5")
    //implementation(group = "com.fasterxml.jackson.core", name = "jackson-databind", version = "2.9.9.3")
    //implementation(group = "com.fasterxml.jackson.module", name = "jackson-module-kotlin", version = "2.9.+")

    compileOnly(group = "com.google.auto.service", name = "auto-service", version = "1.0-rc4")
    kapt(group = "com.google.auto.service", name = "auto-service", version = "1.0-rc4")
}


val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    freeCompilerArgs = listOf("-XXLanguage:+InlineClasses")
}