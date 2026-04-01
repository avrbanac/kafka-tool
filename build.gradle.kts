import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
}

group = "com.kafkatool"
version = "0.3.0"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.apache.kafka:kafka-clients:3.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.slf4j:slf4j-simple:2.0.13")
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.AppImage)
            packageName = "kafka-tool"
            packageVersion = "0.3.0"
            description = "Desktop GUI tool for Apache Kafka"
            vendor = "kafka-tool"
            modules("java.management", "java.naming", "java.security.sasl")
            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
        }
    }
}
