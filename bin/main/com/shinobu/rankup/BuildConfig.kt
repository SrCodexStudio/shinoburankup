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
    val IS_PREMIUM: Boolean = true

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
    const val VERSION: String = "3.0.0"

    /**
     * Check if running free version.
     * Uses direct field access to avoid inlining issues.
     */
    @JvmStatic
    fun isFreeVersion(): Boolean = !IS_PREMIUM

    /**
     * Check if running premium version.
     * Uses direct field access to avoid inlining issues.
     */
    @JvmStatic
    fun isPremiumVersion(): Boolean = IS_PREMIUM
}
