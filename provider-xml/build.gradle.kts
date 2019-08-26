description = "The XML provider for kanon.konfig"
version = "1.1.1"
extra["packageName"] = "xml-provider"

plugins {
    kotlin("kapt")
}

dependencies {
    implementation(group = "org.jdom", name = "jdom2", version = "2.0.6")
    implementation(group = "com.thoughtworks.xstream", name = "xstream", version = "1.4.11.1")

    compileOnly(group = "com.google.auto.service", name = "auto-service", version = "1.0-rc4")
    kapt(group = "com.google.auto.service", name = "auto-service", version = "1.0-rc4")
}