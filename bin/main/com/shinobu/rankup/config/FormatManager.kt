package com.shinobu.rankup.config

import com.shinobu.rankup.ShinobuRankup
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.*

/**
 * Manages number, currency, time, and progress bar formatting.
 *
 * This class handles FORMAT ONLY - no text messages.
 * For messages/translations, use LanguageManager.
 */
class FormatManager(private val plugin: ShinobuRankup) {

    private lateinit var formatFile: File
    private lateinit var format: YamlConfiguration

    // Number formatters
    private val currencyFormatter = DecimalFormat("#,##0.00")
    private val shortFormatter = DecimalFormat("#,##0.##")

    // Short number suffixes (loaded from config)
    private var shortSuffixes = mapOf(
        1_000L to "K",
        1_000_000L to "M",
        1_000_000_000L to "B",
        1_000_000_000_000L to "T"
    )

    companion object {
        private const val DEFAULT_PROGRESS_BAR_LENGTH = 20
        private const val DEFAULT_PROGRESS_FILLED = "|"
        private const val DEFAULT_PROGRESS_EMPTY = "|"
    }

    // ============================================
    //              INITIALIZATION
    // ============================================

    /**
     * Initializes the format manager.
     */
    fun initialize(): Result<Unit> = runCatching {
        formatFile = File(plugin.dataFolder, "format.yml")

        if (!formatFile.exists()) {
            plugin.saveResource("format.yml", false)
        }

        format = YamlConfiguration.loadConfiguration(formatFile)
        loadSuffixes()
        plugin.logger.info("Format configuration loaded successfully!")
    }

    /**
     * Reloads format configuration from disk.
     */
    fun reload(): Result<Unit> = runCatching {
        format = YamlConfiguration.loadConfiguration(formatFile)
        loadSuffixes()
    }

    /**
     * Load short suffixes from config.
     */
    private fun loadSuffixes() {
        val thousand = format.getString("numbers.short-suffixes.thousand", "K") ?: "K"
        val million = format.getString("numbers.short-suffixes.million", "M") ?: "M"
        val billion = format.getString("numbers.short-suffixes.billion", "B") ?: "B"
        val trillion = format.getString("numbers.short-suffixes.trillion", "T") ?: "T"

        shortSuffixes = mapOf(
            1_000L to thousand,
            1_000_000L to million,
            1_000_000_000L to billion,
            1_000_000_000_000L to trillion
        )
    }

    // ============================================
    //           NUMBER FORMATTING
    // ============================================

    /**
     * Formats a number with commas and optional short format.
     */
    fun formatNumber(value: Long, useShort: Boolean = true): String {
        val useShortFormat = format.getBoolean("numbers.use-short-format", true) && useShort

        return if (useShortFormat && value >= 1000) {
            formatShort(value)
        } else {
            NumberFormat.getNumberInstance(Locale.US).format(value)
        }
    }

    /**
     * Formats a number in short format (1K, 1M, 1B, etc.).
     */
    private fun formatShort(value: Long): String {
        val decimals = format.getInt("numbers.short-decimals", 2)

        for ((threshold, suffix) in shortSuffixes.entries.sortedByDescending { it.key }) {
            if (value >= threshold) {
                val formatted = value.toDouble() / threshold
                val pattern = "#,##0.${"#".repeat(decimals)}"
                return DecimalFormat(pattern).format(formatted) + suffix
            }
        }

        return value.toString()
    }

    /**
     * Formats currency with symbol.
     */
    fun formatCurrency(value: Long): String {
        val symbol = format.getString("numbers.currency-symbol", "$") ?: "$"
        return "$symbol${formatNumber(value)}"
    }

    /**
     * Formats currency with symbol (Double version).
     */
    fun formatCurrency(value: Double): String {
        return formatCurrency(value.toLong())
    }

    // ============================================
    //          TIME FORMATTING
    // ============================================

    /**
     * Formats seconds into a human-readable duration.
     */
    fun formatTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        val parts = mutableListOf<String>()

        if (hours > 0) {
            val fmt = format.getString("time.hours", "{amount}h") ?: "{amount}h"
            parts.add(fmt.replace("{amount}", hours.toString()))
        }
        if (minutes > 0) {
            val fmt = format.getString("time.minutes", "{amount}m") ?: "{amount}m"
            parts.add(fmt.replace("{amount}", minutes.toString()))
        }
        if (secs > 0 || parts.isEmpty()) {
            val fmt = format.getString("time.seconds", "{amount}s") ?: "{amount}s"
            parts.add(fmt.replace("{amount}", secs.toString()))
        }

        return parts.joinToString(" ")
    }

    // ============================================
    //          PROGRESS BAR
    // ============================================

    /**
     * Creates a progress bar string.
     */
    fun createProgressBar(progress: Double): String {
        val length = format.getInt("progress-bar.length", DEFAULT_PROGRESS_BAR_LENGTH)
        val filledChar = format.getString("progress-bar.filled-char", DEFAULT_PROGRESS_FILLED) ?: DEFAULT_PROGRESS_FILLED
        val emptyChar = format.getString("progress-bar.empty-char", DEFAULT_PROGRESS_EMPTY) ?: DEFAULT_PROGRESS_EMPTY
        val filledColor = format.getString("progress-bar.filled-color", "&a") ?: "&a"
        val emptyColor = format.getString("progress-bar.empty-color", "&8") ?: "&8"

        val clampedProgress = progress.coerceIn(0.0, 100.0)
        val filledCount = ((clampedProgress / 100.0) * length).toInt()
        val emptyCount = length - filledCount

        val filled = filledChar.repeat(filledCount)
        val empty = emptyChar.repeat(emptyCount)

        return "$filledColor$filled$emptyColor$empty"
    }

    /**
     * Calculates progress percentage between current balance and cost.
     */
    fun calculateProgress(balance: Long, cost: Long): Double {
        if (cost <= 0) return 100.0
        return ((balance.toDouble() / cost) * 100).coerceIn(0.0, 100.0)
    }

    /**
     * Calculates progress percentage (Double version).
     */
    fun calculateProgress(balance: Double, cost: Double): Double {
        if (cost <= 0) return 100.0
        return ((balance / cost) * 100).coerceIn(0.0, 100.0)
    }

    // ============================================
    //          UTILITY METHODS
    // ============================================

    /**
     * Gets the currency symbol.
     */
    fun getCurrencySymbol(): String {
        return format.getString("numbers.currency-symbol", "$") ?: "$"
    }

    /**
     * Gets the progress bar length.
     */
    fun getProgressBarLength(): Int {
        return format.getInt("progress-bar.length", DEFAULT_PROGRESS_BAR_LENGTH)
    }
}
