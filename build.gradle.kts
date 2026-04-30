import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import proguard.gradle.ProGuardTask
import java.security.MessageDigest
import java.util.jar.JarFile

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.6.1")
    }
}

plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.shinobu"
version = "3.0.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

// Configuration for ProGuard library JARs
val proguardLibs: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.17.1-R0.1-SNAPSHOT")

    // Adventure MiniMessage
    implementation("net.kyori:adventure-text-minimessage:4.14.0")

    // Kotlin
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Database
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")

    // Redis (optional distributed cache)
    implementation("redis.clients:jedis:5.1.0")

    // External APIs
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.5")

    // Note: SLF4J is excluded - HikariCP works without it and uses NOP logger internally

    // ProGuard library dependencies
    proguardLibs("io.papermc.paper:paper-api:1.17.1-R0.1-SNAPSHOT")
    proguardLibs("com.github.MilkBowl:VaultAPI:1.7.1")
    proguardLibs("me.clip:placeholderapi:2.11.5")
}

kotlin {
    jvmToolchain(17)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

// Build configuration file path
val buildConfigFile = file("src/main/kotlin/com/shinobu/rankup/BuildConfig.kt")

// Function to set build type (FREE or PREMIUM)
fun setBuildType(isPremium: Boolean) {
    if (buildConfigFile.exists()) {
        var content = buildConfigFile.readText()
        // Match val IS_PREMIUM: Boolean = true/false (works with @JvmField on separate line)
        content = content.replace(
            Regex("""(val IS_PREMIUM: Boolean = )(true|false)"""),
            "$1$isPremium"
        )
        // Match the secondary compile-time constant for reflection hardening
        content = content.replace(
            Regex("""(private const val EDITION_CODE: Int = )\d"""),
            "$1${if (isPremium) 1 else 0}"
        )
        buildConfigFile.writeText(content)
        println("Build type set to: ${if (isPremium) "PREMIUM" else "FREE"}")
    }
}

tasks {
    processResources {
        val props = mapOf("version" to version, "name" to project.name)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs = listOf(
                "-Xjsr305=strict",
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
            )
        }
    }

    // Shadow JAR - intermediate file
    named<ShadowJar>("shadowJar") {
        archiveBaseName.set("ShinobuRankup")
        archiveClassifier.set("shadow")
        archiveVersion.set("")

        destinationDirectory.set(layout.buildDirectory.dir("intermediate"))

        // Relocate dependencies (ONLY internal libs, NOT Adventure/MiniMessage)
        // Adventure/MiniMessage must NOT be relocated because Component comes from Paper
        relocate("kotlin", "com.shinobu.rankup.libs.kotlin")
        relocate("kotlinx", "com.shinobu.rankup.libs.kotlinx")
        relocate("com.zaxxer.hikari", "com.shinobu.rankup.libs.hikari")
        relocate("redis.clients", "com.shinobu.rankup.libs.redis")
        relocate("org.apache.commons.pool2", "com.shinobu.rankup.libs.pool2")
        // Relocate our SLF4J stub with HikariCP so they work together
        relocate("org.slf4j", "com.shinobu.rankup.libs.slf4j")
        // DO NOT relocate: net.kyori.adventure (provided by Paper)

        // Exclude SLF4J ServiceLoader files (we use our own minimal stub)
        exclude("META-INF/services/org.slf4j.*")

        // Minimize but keep important classes
        minimize {
            exclude(dependency("org.jetbrains.kotlin:.*"))
            exclude(dependency("org.jetbrains.kotlinx:.*"))
            exclude(dependency("org.xerial:.*"))
        }

        mergeServiceFiles()
    }

    // Create directories
    register("createDirs") {
        doLast {
            layout.buildDirectory.dir("proguard").get().asFile.mkdirs()
            layout.buildDirectory.dir("libs").get().asFile.mkdirs()
        }
    }

    // ProGuard obfuscation task
    register<ProGuardTask>("proguard") {
        group = "build"
        description = "Obfuscate JAR with ProGuard"
        dependsOn(shadowJar, "createDirs")

        val shadowJarFile = named<ShadowJar>("shadowJar").get().archiveFile.get().asFile
        val obfuscatedJar = layout.buildDirectory.file("libs/ShinobuRankup.jar").get().asFile

        injars(shadowJarFile)
        outjars(obfuscatedJar)

        // Java runtime libraries
        val javaHome = System.getProperty("java.home")
        val jmodsPath = file("$javaHome/jmods")

        if (jmodsPath.exists()) {
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.base.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.logging.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.sql.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.desktop.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.management.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.naming.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.xml.jmod")
        }

        // External library dependencies
        proguardLibs.forEach { jar ->
            libraryjars(jar)
        }

        configuration(file("proguard-rules.pro"))

        printmapping(layout.buildDirectory.file("proguard/mapping.txt").get().asFile)
        printseeds(layout.buildDirectory.file("proguard/seeds.txt").get().asFile)
        printusage(layout.buildDirectory.file("proguard/usage.txt").get().asFile)
    }

    // Inject hash into JAR for Anti-Tamper verification
    register("injectHash") {
        group = "build"
        description = "Inject SHA-256 hash into JAR for integrity verification"
        dependsOn("proguard")

        doLast {
            val jarPath = layout.buildDirectory.file("libs/ShinobuRankup.jar").get().asFile
            if (!jarPath.exists()) {
                throw GradleException("JAR file not found: ${jarPath.absolutePath}")
            }

            // Calculate SHA-256 hash using same algorithm as IntegrityChecker
            // (hashes JAR entries sorted by name, excluding META-INF/shinobu.hash)
            fun calculateJarContentHash(file: File): String {
                val digest = MessageDigest.getInstance("SHA-256")
                JarFile(file).use { jar ->
                    val entries = jar.entries().toList()
                        .filter { !it.isDirectory && it.name != "META-INF/shinobu.hash" }
                        .sortedBy { it.name }

                    for (entry in entries) {
                        // Include entry name in hash for structure verification
                        digest.update(entry.name.toByteArray(Charsets.UTF_8))

                        // Include entry content
                        jar.getInputStream(entry).use { input ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                digest.update(buffer, 0, bytesRead)
                            }
                        }
                    }
                }
                return digest.digest().joinToString("") { "%02x".format(it) }
            }

            // Calculate hash of JAR contents
            val hash = calculateJarContentHash(jarPath)
            println("Calculated JAR content hash: $hash")

            // Extract JAR to temp directory
            val tempDir = layout.buildDirectory.dir("temp-jar").get().asFile
            tempDir.deleteRecursively()
            tempDir.mkdirs()

            ant.withGroovyBuilder {
                "unzip"("src" to jarPath, "dest" to tempDir)
            }

            // Create hash resource file inside JAR
            val resourceDir = File(tempDir, "META-INF")
            resourceDir.mkdirs()
            File(resourceDir, "shinobu.hash").writeText(hash)

            // Repack JAR
            jarPath.delete()
            ant.withGroovyBuilder {
                "zip"("destfile" to jarPath, "basedir" to tempDir)
            }

            // Cleanup temp directory
            tempDir.deleteRecursively()

            // Verify hash after injection (should match)
            val verifyHash = calculateJarContentHash(jarPath)
            println("Verification hash (should match): $verifyHash")

            if (hash != verifyHash) {
                println("WARNING: Hash mismatch after injection!")
            } else {
                println("SUCCESS: Hash verified after injection")
            }

            // Store hash in external file for reference
            val hashFile = layout.buildDirectory.file("libs/ShinobuRankup.sha256").get().asFile
            hashFile.writeText(hash)
        }
    }

    // ============================================
    //         FREEMIUM BUILD SYSTEM
    // ============================================
    //
    // Use the batch scripts in project root:
    //   build-free.bat    - Build FREE version (obfuscated)
    //   build-premium.bat - Build PREMIUM version (obfuscated)
    //   build-all.bat     - Build both versions
    //
    // ============================================

    // Set FREE build type (run before compiling)
    register("setFree") {
        group = "freemium"
        description = "Set BuildConfig to FREE version"
        doLast {
            setBuildType(false)
            // Force recompilation
            delete(layout.buildDirectory.dir("classes"))
            delete(layout.buildDirectory.dir("kotlin"))
            delete(layout.buildDirectory.dir("tmp"))
            delete(layout.buildDirectory.dir("intermediate"))
            println("BuildConfig set to FREE - Max 15 ranks, English only, /rankupmax disabled")
        }
    }

    // Set PREMIUM build type (run before compiling)
    register("setPremium") {
        group = "freemium"
        description = "Set BuildConfig to PREMIUM version"
        doLast {
            setBuildType(true)
            // Force recompilation
            delete(layout.buildDirectory.dir("classes"))
            delete(layout.buildDirectory.dir("kotlin"))
            delete(layout.buildDirectory.dir("tmp"))
            delete(layout.buildDirectory.dir("intermediate"))
            println("BuildConfig set to PREMIUM - Unlimited ranks, Full language support")
        }
    }

    // Enforce task ordering: setFree/setPremium must run BEFORE shadowJar
    // so that BuildConfig.kt is modified before compilation occurs
    named<ShadowJar>("shadowJar") {
        mustRunAfter("setFree", "setPremium")
    }

    // ProGuard task for FREE version
    register<ProGuardTask>("proguardFree") {
        group = "freemium"
        description = "Obfuscate FREE JAR with ProGuard"
        dependsOn("setFree", shadowJar, "createDirs")

        val shadowJarFile = named<ShadowJar>("shadowJar").get().archiveFile.get().asFile
        val obfuscatedJar = layout.buildDirectory.file("libs/ShinobuRankup-Free.jar").get().asFile

        injars(shadowJarFile)
        outjars(obfuscatedJar)

        val javaHome = System.getProperty("java.home")
        val jmodsPath = file("$javaHome/jmods")

        if (jmodsPath.exists()) {
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.base.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.logging.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.sql.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.desktop.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.management.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.naming.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.xml.jmod")
        }

        proguardLibs.forEach { jar -> libraryjars(jar) }

        configuration(file("proguard-rules.pro"))
        printmapping(layout.buildDirectory.file("proguard/mapping-free.txt").get().asFile)

        doLast {
            val jar = layout.buildDirectory.file("libs/ShinobuRankup-Free.jar").get().asFile
            if (jar.exists()) {
                println("\n" + "=".repeat(60))
                println("FREE version (OBFUSCATED): ${jar.name}")
                println("Size: ${jar.length() / 1024} KB")
                println("Limitations: Max 15 ranks, English only, /rankupmax disabled")
                println("=".repeat(60) + "\n")
            }
        }
    }

    // ProGuard task for PREMIUM version
    register<ProGuardTask>("proguardPremium") {
        group = "freemium"
        description = "Obfuscate PREMIUM JAR with ProGuard"
        dependsOn("setPremium", shadowJar, "createDirs")

        val shadowJarFile = named<ShadowJar>("shadowJar").get().archiveFile.get().asFile
        val obfuscatedJar = layout.buildDirectory.file("libs/ShinobuRankup-Premium.jar").get().asFile

        injars(shadowJarFile)
        outjars(obfuscatedJar)

        val javaHome = System.getProperty("java.home")
        val jmodsPath = file("$javaHome/jmods")

        if (jmodsPath.exists()) {
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.base.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.logging.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.sql.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.desktop.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.management.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.naming.jmod")
            libraryjars(mapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"), "$javaHome/jmods/java.xml.jmod")
        }

        proguardLibs.forEach { jar -> libraryjars(jar) }

        configuration(file("proguard-rules.pro"))
        printmapping(layout.buildDirectory.file("proguard/mapping-premium.txt").get().asFile)

        doLast {
            val jar = layout.buildDirectory.file("libs/ShinobuRankup-Premium.jar").get().asFile
            if (jar.exists()) {
                println("\n" + "=".repeat(60))
                println("PREMIUM version (OBFUSCATED): ${jar.name}")
                println("Size: ${jar.length() / 1024} KB")
                println("Features: Unlimited ranks, Full language support")
                println("=".repeat(60) + "\n")
            }
        }
    }

    // Development build (non-obfuscated, for testing)
    register<Copy>("buildDev") {
        group = "build"
        description = "Build development JAR (non-obfuscated)"
        dependsOn(shadowJar)

        from(named<ShadowJar>("shadowJar").get().archiveFile)
        into(layout.buildDirectory.dir("libs"))
        rename { "ShinobuRankup-DEV.jar" }

        doLast {
            val devJar = layout.buildDirectory.file("libs/ShinobuRankup-DEV.jar").get().asFile
            println("\n" + "=".repeat(60))
            println("Development JAR: ${devJar.name}")
            println("Size: ${devJar.length() / 1024} KB")
            println("=".repeat(60) + "\n")
        }
    }

    // Production build with ProGuard
    register("buildRelease") {
        group = "build"
        description = "Build production JAR with ProGuard + Anti-tamper"
        dependsOn("injectHash")

        doLast {
            val jar = layout.buildDirectory.file("libs/ShinobuRankup.jar").get().asFile
            if (jar.exists()) {
                println("=".repeat(60))
                println("Production JAR: ${jar.name}")
                println("Size: ${jar.length() / 1024} KB")
                val hashFile = layout.buildDirectory.file("libs/ShinobuRankup.sha256").get().asFile
                if (hashFile.exists()) {
                    println("SHA-256: ${hashFile.readText()}")
                }
                println("=".repeat(60))
            }
        }
    }

    // ============================================
    //         MAIN BUILD TASKS
    // ============================================

    build {
        dependsOn("buildRelease", "buildDev")
        doLast {
            println("\n" + "=".repeat(60))
            println("Build complete!")
            println("")
            println("Generated JARs in build/libs/:")
            println("  - ShinobuRankup.jar     (Obfuscated + Anti-tamper)")
            println("  - ShinobuRankup-DEV.jar (Non-obfuscated for testing)")
            println("")
            println("For FREEMIUM builds, use these commands:")
            println("  FREE:    ./gradlew setFree shadowJar copyFreeJar")
            println("  PREMIUM: ./gradlew setPremium shadowJar copyPremiumJar")
            println("=".repeat(60) + "\n")
        }
    }

    jar {
        enabled = false
    }
}
