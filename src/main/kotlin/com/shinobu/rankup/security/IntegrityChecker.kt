package com.shinobu.rankup.security

import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.security.MessageDigest
import java.util.jar.JarFile
import java.util.logging.Level

/**
 * Anti-Tamper System - Verifies JAR integrity at runtime.
 *
 * Detects:
 * - JAR file modifications
 * - Class bytecode edits
 * - Unauthorized injections
 * - Decompilation + recompilation attempts
 *
 * SECURITY NOTE: The expected hash is stored in META-INF/shinobu.hash
 * and injected at build time by Gradle after ProGuard obfuscation.
 */
object IntegrityChecker {

    private const val HASH_RESOURCE = "META-INF/shinobu.hash"

    /**
     * Verify the integrity of the plugin JAR file.
     *
     * @param plugin The plugin instance to verify
     * @return IntegrityResult containing verification status and details
     */
    @JvmStatic
    fun verify(plugin: JavaPlugin): IntegrityResult {
        return try {
            // Get the JAR file location
            val codeSource = plugin.javaClass.protectionDomain.codeSource
            if (codeSource == null) {
                return IntegrityResult(
                    valid = false,
                    reason = "Unable to locate code source",
                    expectedHash = null,
                    actualHash = null
                )
            }

            val jarFile = File(codeSource.location.toURI())

            // Check if file exists and is readable
            if (!jarFile.exists() || !jarFile.canRead()) {
                return IntegrityResult(
                    valid = false,
                    reason = "JAR file not accessible",
                    expectedHash = null,
                    actualHash = null
                )
            }

            // Read expected hash from inside the JAR
            val expectedHash = readHashFromJar(jarFile)

            // Development mode - hash file not present
            if (expectedHash == null) {
                return IntegrityResult(
                    valid = true,
                    reason = "Development mode - integrity check skipped",
                    expectedHash = null,
                    actualHash = null,
                    isDevelopment = true
                )
            }

            // Calculate SHA-256 hash of JAR (excluding the hash file itself)
            val actualHash = calculateJarHash(jarFile)

            // Compare hashes
            val isValid = actualHash.equals(expectedHash, ignoreCase = true)

            IntegrityResult(
                valid = isValid,
                reason = if (isValid) "Integrity verified" else "Hash mismatch - JAR may have been modified",
                expectedHash = expectedHash,
                actualHash = actualHash
            )

        } catch (e: Exception) {
            IntegrityResult(
                valid = false,
                reason = "Verification failed: ${e.message}",
                expectedHash = null,
                actualHash = null,
                exception = e
            )
        }
    }

    /**
     * Perform quick integrity check - returns true if valid.
     * Use this for simple checks without detailed results.
     */
    @JvmStatic
    fun isValid(plugin: JavaPlugin): Boolean {
        return verify(plugin).valid
    }

    /**
     * Read the expected hash from META-INF/shinobu.hash inside the JAR.
     */
    private fun readHashFromJar(jarFile: File): String? {
        return try {
            JarFile(jarFile).use { jar ->
                val entry = jar.getJarEntry(HASH_RESOURCE)
                if (entry != null) {
                    jar.getInputStream(entry).bufferedReader().use { reader ->
                        reader.readLine()?.trim()
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Calculate SHA-256 hash of the JAR file contents, excluding the hash file itself.
     * This ensures the hash can be verified even after the hash file is injected.
     */
    private fun calculateJarHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")

        JarFile(file).use { jar ->
            // Get all entries sorted for consistent hashing
            val entries = jar.entries().toList()
                .filter { !it.isDirectory && it.name != HASH_RESOURCE }
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

    /**
     * Log verification result to plugin logger.
     */
    @JvmStatic
    fun logResult(plugin: JavaPlugin, result: IntegrityResult) {
        if (result.isDevelopment) {
            plugin.logger.info("[Security] Development mode - integrity check skipped")
            return
        }

        if (result.valid) {
            plugin.logger.info("[Security] Integrity verified successfully")
        } else {
            plugin.logger.log(Level.SEVERE, "[Security] INTEGRITY CHECK FAILED!")
            plugin.logger.log(Level.SEVERE, "[Security] Reason: ${result.reason}")
            if (result.actualHash != null && result.expectedHash != null) {
                plugin.logger.log(Level.SEVERE, "[Security] Expected: ${result.expectedHash}")
                plugin.logger.log(Level.SEVERE, "[Security] Actual: ${result.actualHash}")
            }
        }
    }

    /**
     * Result of integrity verification.
     */
    data class IntegrityResult(
        val valid: Boolean,
        val reason: String,
        val expectedHash: String?,
        val actualHash: String?,
        val isDevelopment: Boolean = false,
        val exception: Exception? = null
    )
}
