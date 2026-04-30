package com.shinobu.rankup.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.text.DecimalFormat
import java.time.Duration

/**
 * Extension functions for common operations throughout the plugin.
 */

private val moneyFormat = DecimalFormat("#,##0.00")
private val compactFormat = DecimalFormat("#,##0")

/**
 * Format a number as currency.
 */
fun Double.formatMoney(): String = "$${moneyFormat.format(this)}"

/**
 * Format a number as compact currency.
 */
fun Double.formatMoneyCompact(): String = "$${compactFormat.format(this)}"

/**
 * Format a long as currency.
 */
fun Long.formatMoney(): String = "$${compactFormat.format(this)}"

/**
 * Format large numbers with K, M, B suffixes.
 */
fun Double.formatCompact(): String {
    return when {
        this >= 1_000_000_000 -> String.format("%.2fB", this / 1_000_000_000)
        this >= 1_000_000 -> String.format("%.2fM", this / 1_000_000)
        this >= 1_000 -> String.format("%.2fK", this / 1_000)
        else -> compactFormat.format(this)
    }
}

/**
 * Format a number with thousands separators.
 */
fun Int.formatWithCommas(): String = compactFormat.format(this)

fun Long.formatWithCommas(): String = compactFormat.format(this)

/**
 * Send a colored message to a player.
 */
fun Player.sendColored(message: String) {
    this.sendMessage(ColorUtil.parse(message))
}

/**
 * Send a colored message with placeholders to a player.
 */
fun Player.sendColored(message: String, placeholders: Map<String, String>) {
    this.sendMessage(ColorUtil.parse(message, placeholders))
}

/**
 * Send a title to a player with MiniMessage support.
 */
fun Player.sendTitle(
    title: String,
    subtitle: String = "",
    fadeIn: Duration = Duration.ofMillis(500),
    stay: Duration = Duration.ofSeconds(3),
    fadeOut: Duration = Duration.ofMillis(500)
) {
    val titleComponent = ColorUtil.parse(title)
    val subtitleComponent = ColorUtil.parse(subtitle)

    val times = Title.Times.of(fadeIn, stay, fadeOut)
    val titleObj = Title.title(titleComponent, subtitleComponent, times)

    this.showTitle(titleObj)
}

/**
 * Send an action bar message to a player.
 */
fun Player.sendActionBar(message: String) {
    this.sendActionBar(ColorUtil.parse(message))
}

/**
 * Send an action bar with placeholders.
 */
fun Player.sendActionBar(message: String, placeholders: Map<String, String>) {
    this.sendActionBar(ColorUtil.parse(message, placeholders))
}

/**
 * Play a sound for a player with default values.
 */
fun Player.playSound(sound: Sound, volume: Float = 1.0f, pitch: Float = 1.0f) {
    this.playSound(this.location, sound, volume, pitch)
}

/**
 * Play a sound by name (safer, handles invalid names).
 */
fun Player.playSoundSafe(soundName: String, volume: Float = 1.0f, pitch: Float = 1.0f) {
    try {
        val sound = Sound.valueOf(soundName.uppercase())
        this.playSound(sound, volume, pitch)
    } catch (e: IllegalArgumentException) {
        // Sound not found, ignore silently
    }
}

/**
 * Check if a string is a valid number.
 */
fun String.isNumeric(): Boolean = this.toDoubleOrNull() != null

/**
 * Check if a string is a valid integer.
 */
fun String.isInteger(): Boolean = this.toIntOrNull() != null

/**
 * Safely get an element from a list or return null.
 */
fun <T> List<T>.getOrNull(index: Int): T? = if (index in indices) this[index] else null

/**
 * Convert ticks to Duration.
 */
fun Int.ticksToDuration(): Duration = Duration.ofMillis(this * 50L)

/**
 * Convert Duration to ticks.
 */
fun Duration.toTicks(): Int = (this.toMillis() / 50).toInt()

/**
 * Capitalize first letter of each word.
 */
fun String.capitalizeWords(): String {
    return this.split(" ").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }
}

/**
 * Create a simple component from string.
 */
fun String.toComponent(): Component = ColorUtil.parse(this)

/**
 * Replace placeholders in a string.
 */
fun String.replacePlaceholders(placeholders: Map<String, String>): String {
    var result = this
    placeholders.forEach { (key, value) ->
        result = result.replace("{$key}", value)
        result = result.replace("%$key%", value)
    }
    return result
}

/**
 * Calculate percentage safely.
 */
fun calculatePercentage(current: Double, max: Double): Double {
    return if (max <= 0) 0.0 else (current / max * 100).coerceIn(0.0, 100.0)
}

/**
 * Calculate progress ratio (0.0 to 1.0).
 */
fun calculateProgress(current: Double, max: Double): Double {
    return if (max <= 0) 0.0 else (current / max).coerceIn(0.0, 1.0)
}
