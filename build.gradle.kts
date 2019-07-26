import name.remal.gradle_plugins.dsl.extensions.convention
import name.remal.gradle_plugins.dsl.extensions.get
import name.remal.gradle_plugins.dsl.extensions.implementation
import name.remal.gradle_plugins.plugins.publish.bintray.RepositoryHandlerBintrayExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        jcenter()
    }

    dependencies {
        classpath("name.remal:gradle-plugins:1.0.129")
    }
}

plugins {
    kotlin("jvm").version("1.3.41")

    id("com.github.ben-manes.versions").version("0.21.0")

    `maven-publish`
}

apply(plugin = "name.remal.maven-publish-bintray")

// Project Specific Variables
project.group = "moe.kanon.konfig"
project.description = "A type-safe configuration system for Kotlin."
project.version = "1.3.0"
val artifactName = "kanon.konfig"
val gitUrl = "https://gitlab.com/kanondev/kanon-konfig"

// General Tasks
repositories {
    maven(url = "https://jitpack.io") { name = "jitpack" }
    mavenCentral()
    jcenter()
}

dependencies {
    // Normal Dependencies
    // Kotlin
    implementation(kotlin("stdlib-jdk8"))
    
    implementation(group = "com.google.code.gson", name = "gson", version = "2.8.5")
    implementation(group = "com.github.mgrzeszczak", name = "json-dsl", version = "1.1") {
        exclude(group = "org.jetbrains.kotlin")
    }
    implementation(group = "com.github.salomonbrys.kotson", name = "kotson", version = "2.5.0")
    implementation(group = "com.google.guava", name = "guava", version = "27.1-jre")
    implementation(group = "com.thoughtworks.xstream", name = "xstream", version = "1.4.11.1")
    
    // Kotlin Logging Wrapper
    implementation(group = "io.github.microutils", name = "kotlin-logging", version = "1.6.24")
    
    // Kanon
    implementation(group = "moe.kanon.kommons", name = "kanon.kommons", version = "0.6.0-alpha")
    implementation(group = "moe.kanon.xml", name = "kanon.xml", version = "2.0.0")
    
    // Test Dependencies
    testImplementation(group = "io.kotlintest", name = "kotlintest-runner-junit5", version = "3.1.11")
    testImplementation(group = "org.slf4j", name = "slf4j-simple", version = "1.8.0-beta2")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs += "-Xjvm-default=enable"
}

project.afterEvaluate {
    publishing.publications.withType<MavenPublication> {
        pom {
            name.set(project.name)
            description.set(project.description)
            url.set(gitUrl)

            licenses {
                license {
                    name.set("The Apache Software License, Version 2.0")
                    url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }

            developers {
                developer {
                    email.set("oliver@berg.moe")
                    id.set("Olivki")
                    name.set("Oliver Berg")
                }
            }

            scm {
                url.set(gitUrl)
            }
        }
    }

    publishing.repositories.convention[RepositoryHandlerBintrayExtension::class.java].bintray {
        owner = "olivki"
        repositoryName = "kanon"
        packageName = "kanon.konfig"
    }
}
