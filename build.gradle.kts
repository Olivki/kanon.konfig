import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm").version("1.3.21")
    
    id("com.adarshr.test-logger").version("1.6.0") // For pretty-printing for tests.
    id("com.jfrog.bintray").version("1.8.4") // For publishing to BinTray.
    id("org.jetbrains.dokka").version("0.9.17") // The KDoc engine.
    id("com.github.ben-manes.versions").version("0.20.0") // For checking for new dependency versions.

    `maven-publish`
}

// Project Specific Variables
project.group = "moe.kanon.konfig"
project.description = "A type-safe configuration system for Kotlin."
project.version = "0.1.0"
val artifactName = "kanon.konfig"
val gitUrl = "https://gitlab.com/kanondev/kanon-konfig"

// General Tasks
repositories {
    maven(url = "https://jitpack.io") { setName("jitpack") }
    mavenCentral()
    jcenter()
}

dependencies {
    // Normal Dependencies
    // Kotlin
    implementation(kotlin("stdlib-jdk8"))
    
    implementation(group = "com.google.code.gson", name = "gson", version = "2.8.5")
    implementation(group = "com.github.mgrzeszczak", name = "json-dsl", version = "1.1")
    implementation(group = "com.github.salomonbrys.kotson", name = "kotson", version = "2.5.0")
    implementation(group = "com.google.guava", name = "guava", version = "27.1-jre")
    implementation(group = "com.thoughtworks.xstream", name = "xstream", version = "1.4.11.1")
    
    // Kanon
    implementation(group = "moe.kanon.kommons", name = "kanon.kommons", version = "0.6.0-alpha")
    implementation(group = "moe.kanon.xml", name = "kanon.xml", version = "2.0.0")
    
    // Test Dependencies
    testImplementation(group = "io.kotlintest", name = "kotlintest-runner-junit5", version = "3.1.11")
    testImplementation(group = "org.slf4j", name = "slf4j-simple", version = "1.8.0-beta2")
}

subprojects {
    buildscript {
        repositories {
            mavenCentral()
        }
        
        dependencies {
            classpath(kotlin("gradle-plugin", version = "1.3.21"))
        }
    }
    
    apply(plugin = "java")
    apply(plugin = "kotlin")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs += "-Xjvm-default=enable"
}

tasks.withType<Wrapper> {
    gradleVersion = "4.10"
    distributionType = Wrapper.DistributionType.BIN
}

// Dokka Tasks
val dokkaJavaDoc by tasks.creating(DokkaTask::class) {
    outputFormat = "javadoc"
    outputDirectory = "$buildDir/javadoc"
    inputs.dir("src/main/kotlin")
    includeNonPublic = false
    skipEmptyPackages = true
    jdkVersion = 8
}

// Test Tasks
testlogger {
    setTheme("mocha")
}

val test by tasks.getting(Test::class) {
    useJUnitPlatform { }
}

// Artifact Tasks
val sourcesJar by tasks.creating(Jar::class) {
    dependsOn(JavaPlugin.CLASSES_TASK_NAME)
    description = "Assembles the sources of this project into a *-sources.jar file."
    classifier = "sources"
    
    from(project.sourceSets["main"].allSource)
}

val javaDocJar by tasks.creating(Jar::class) {
    description = "Creates a *-javadoc.jar from the generated dokka output."
    classifier = "javadoc"
    
    from(dokkaJavaDoc)
}

artifacts {
    add("archives", sourcesJar)
    add("archives", javaDocJar)
}

// Publishing Tasks
// BinTray
bintray {
    // Credentials.
    user = getVariable("BINTRAY_USER")
    key = getVariable("BINTRAY_KEY")
    
    // Whether or not the "package" should automatically be published.
    publish = true
    
    // Sets the publication to our created maven publication instance.
    setPublications("mavenPublication")
    
    // Details for the actual package that's going up on BinTray.
    with(pkg) {
        repo = "kanon"
        desc = project.description
        name = artifactName
        websiteUrl = gitUrl
        vcsUrl = "$gitUrl.git"
        publicDownloadNumbers = true
        setLicenses("Apache-2.0")
        setLabels("kotlin")
        
        with(version) {
            name = project.version.toString()
            desc = project.version.toString()
            released = `java.util`.Date().toString()
        }
    }
}

// Maven Tasks
publishing {
    publications.invoke {
        register("mavenPublication", MavenPublication::class.java) {
            from(components["java"])

            afterEvaluate {
                // General project information.
                groupId = project.group.toString()
                version = project.version.toString()
                artifactId = artifactName

                // Any extra artifacts that need to be added, ie: sources & javadoc jars.
                artifact(sourcesJar)
                artifact(javaDocJar)

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
        }
    }
}

// Misc Functions & Properties
fun getVariable(name: String) = System.getenv(name)!!
