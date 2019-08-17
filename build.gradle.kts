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
    kotlin("kapt").version("1.3.41")

    id("com.github.ben-manes.versions").version("0.21.0")

    `maven-publish`
}

apply(plugin = "name.remal.maven-publish-bintray")

project.group = "moe.kanon.konfig"
project.description = "A type-safe configuration system for Kotlin."
project.version = "2.0.0"
val gitUrl = "https://gitlab.com/kanondev/kanon-konfig"

repositories {
    jcenter()
}

subprojects {
    apply(plugin = "kotlin")
    apply(plugin = "maven-publish")
    apply(plugin = "name.remal.maven-publish-bintray")

    project.group = "moe.kanon.konfig"

    repositories {
        mavenCentral()
        jcenter()
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
            repositoryName = "kanon.konfig"
            packageName = this@subprojects.extra["packageName"]!!.toString()
        }
    }

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
        with(kotlinOptions) {
            freeCompilerArgs = listOf(
                "-Xuse-experimental=kotlin.Experimental",
                "-Xjvm-default=enable",
                "-Xuse-experimental=kotlin.contracts.ExperimentalContracts"
            )
        }
    }
}

// make sure that the correct sub-projects inherit from the core project
configure(subprojects.filterNot { it.name == "core" }) {
    dependencies {
        implementation(project(":core"))
    }
}