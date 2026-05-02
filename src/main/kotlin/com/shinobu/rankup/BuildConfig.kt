package com.shinobu.rankup

/**
 * Build configuration for ShinobuRankup.
 *
 * This file controls freemium limitations.
 * Change IS_PREMIUM to true for the premium version.
 *
 * FREE VERSION LIMITATIONS:
 * - Maximum 15 ranks
 * - Language locked to English (users must edit en.yml manually)
 * - /rankupmax command disabled
 *
 * PREMIUM VERSION:
 * - Unlimited ranks
 * - Full language support (configurable in config.yml)
 */
object BuildConfig {

    /**
     * Set to true for premium build, false for free build.
     * This is modified by the build script.
     *
     * IMPORTANT: Using @JvmField instead of const to prevent
     * compile-time inlining that breaks freemium switching.
     */
    @JvmField
    val IS_PREMIUM: Boolean = false

    /**
     * Secondary build edition check -- const val is inlined at compile time,
     * making it immune to Java reflection attacks that modify IS_PREMIUM at runtime.
     * 0 = FREE, 1 = PREMIUM. Modified by the build script alongside IS_PREMIUM.
     */
    private const val EDITION_CODE: Int = 0

    /**
     * Maximum ranks allowed in free version.
     */
    const val FREE_MAX_RANKS: Int = 15

    /**
     * Default language for free version.
     */
    const val FREE_DEFAULT_LANGUAGE: String = "en"

    /**
     * Plugin version.
     */
    const val VERSION: String = "3.5.1"

    /**
     * Check if running free version.
     * Validates BOTH the runtime field and the compile-time constant.
     * Even if IS_PREMIUM is changed via reflection, EDITION_CODE is inlined
     * by the Kotlin compiler and cannot be modified at runtime.
     */
    @JvmStatic
    fun isFreeVersion(): Boolean = !IS_PREMIUM && EDITION_CODE == 0

    /**
     * Check if running premium version.
     * Validates BOTH the runtime field and the compile-time constant.
     */
    @JvmStatic
    fun isPremiumVersion(): Boolean = IS_PREMIUM && EDITION_CODE == 1
}
