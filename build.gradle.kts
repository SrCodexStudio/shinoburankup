import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.srcodex"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    // Paper API - Compatible with 1.17.1 - 1.21.x
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")

    // Kotlin
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // HTTP Client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON Processing
    implementation("com.google.code.gson:gson:2.10.1")

    // Adventure API (provided by Paper at runtime)
    compileOnly("net.kyori:adventure-api:4.15.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.15.0")

    // PlaceholderAPI
    compileOnly("me.clip:placeholderapi:2.11.5")
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = listOf("-Xjvm-default=all", "-opt-in=kotlin.RequiresOptIn")
        }
    }

    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("ShinobuStore-${project.version}.jar")

        // Relocate dependencies to avoid conflicts
        relocate("kotlin", "com.srcodex.shinobustore.libs.kotlin")
        relocate("kotlinx", "com.srcodex.shinobustore.libs.kotlinx")
        relocate("okhttp3", "com.srcodex.shinobustore.libs.okhttp3")
        relocate("okio", "com.srcodex.shinobustore.libs.okio")
        relocate("com.google.gson", "com.srcodex.shinobustore.libs.gson")

        minimize {
            exclude(dependency("org.jetbrains.kotlin:.*"))
            exclude(dependency("org.jetbrains.kotlinx:.*"))
        }
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}
