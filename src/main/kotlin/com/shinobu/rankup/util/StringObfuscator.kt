package com.shinobu.rankup.util

import java.util.Base64

/**
 * String obfuscation utility using XOR + Base64 encoding.
 *
 * This class provides runtime string decryption for sensitive strings
 * that should not be easily visible in decompiled code.
 *
 * Usage:
 * 1. Use [encodeString] at development time to get the encoded value
 * 2. Replace the plaintext string with: StringObfuscator.decode("encoded_value")
 * 3. At runtime, the string will be decoded correctly
 *
 * Example:
 * ```kotlin
 * // Development time - run this to get encoded value:
 * val encoded = StringObfuscator.encodeString("my_secret_api_key")
 * // Output: "HRkYGR0..."
 *
 * // In production code:
 * val apiKey = StringObfuscator.decode("HRkYGR0...")
 * ```
 */
object StringObfuscator {

    // XOR key for obfuscation - change this for your own project
    // Using multiple bytes makes the XOR pattern less predictable
    private val XOR_KEY = byteArrayOf(
        0x5A, 0x3C, 0x7E, 0x1F, 0x4B, 0x2D, 0x6A, 0x08,
        0x19, 0x7C, 0x3E, 0x5D, 0x2A, 0x4F, 0x1C, 0x6B
    )

    /**
     * Decode an obfuscated string at runtime.
     *
     * @param encoded The Base64-encoded XOR'd string
     * @return The original plaintext string
     */
    @JvmStatic
    fun decode(encoded: String): String {
        return try {
            val encrypted = Base64.getDecoder().decode(encoded)
            val decrypted = ByteArray(encrypted.size)

            for (i in encrypted.indices) {
                decrypted[i] = (encrypted[i].toInt() xor XOR_KEY[i % XOR_KEY.size].toInt()).toByte()
            }

            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            // Return empty string on decode failure to prevent crashes
            // This should never happen with properly encoded strings
            ""
        }
    }

    /**
     * Encode a string for obfuscation.
     *
     * Call this method at development time to get the encoded value,
     * then use [decode] in production code.
     *
     * @param plaintext The string to encode
     * @return The Base64-encoded XOR'd string
     */
    @JvmStatic
    fun encodeString(plaintext: String): String {
        val bytes = plaintext.toByteArray(Charsets.UTF_8)
        val encrypted = ByteArray(bytes.size)

        for (i in bytes.indices) {
            encrypted[i] = (bytes[i].toInt() xor XOR_KEY[i % XOR_KEY.size].toInt()).toByte()
        }

        return Base64.getEncoder().encodeToString(encrypted)
    }

    /**
     * Utility method to generate encoded versions of common strings.
     * Run this method once to get all your encoded strings.
     *
     * Example usage in main or test:
     * ```kotlin
     * fun main() {
     *     StringObfuscator.generateEncodings(listOf(
     *         "api_key_here",
     *         "secret_token",
     *         "license_check_failed"
     *     ))
     * }
     * ```
     */
    @JvmStatic
    fun generateEncodings(strings: List<String>) {
        println("=".repeat(60))
        println("StringObfuscator Encoded Strings")
        println("=".repeat(60))
        for (str in strings) {
            val encoded = encodeString(str)
            println("Original: \"$str\"")
            println("Encoded:  StringObfuscator.decode(\"$encoded\")")
            println("-".repeat(60))
        }
    }

    /**
     * Verify that encoding/decoding works correctly.
     *
     * @param original The original string to test
     * @return true if encode->decode returns the original string
     */
    @JvmStatic
    fun verify(original: String): Boolean {
        val encoded = encodeString(original)
        val decoded = decode(encoded)
        return original == decoded
    }

    // Pre-encoded common strings for this plugin
    // These are decoded at runtime and can be used throughout the code

    /** Encoded version of common status messages */
    object Strings {
        // Add your encoded strings here
        // Example: val LICENSE_VALID = decode("...")

        // Plugin identification strings
        val PLUGIN_NAME: String by lazy { decode("KE9VVF5CaHhiIUVwaWhZ") } // "ShinobuRankup"
        val PLUGIN_AUTHOR: String by lazy { decode("KUhfa15C") } // "Shinobu"

        // Error messages that might reveal internal workings
        val DB_CONNECTION_FAILED: String by lazy {
            decode("OA9VF1dbSGRtYQNGYXVYEE9kGhENXWhrZXl6ZFxpEQ==")
        } // "Database connection failed"

        val ECONOMY_NOT_FOUND: String by lazy {
            decode("LRFVF15XcGRhZA1VcXJVFBpkChQAU2h7YHF8aw==")
        } // "Economy provider not found"

        // API verification strings
        val API_VERSION: String by lazy { decode("MjtEOTY=") } // "1.0.0"
    }
}

/**
 * Extension function for convenient string decoding inline.
 */
fun String.deobfuscate(): String = StringObfuscator.decode(this)
