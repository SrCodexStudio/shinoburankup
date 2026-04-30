package com.srcodex.shinobustore.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.ChatColor
import java.util.regex.Pattern

/**
 * Utility object for handling color codes and text formatting.
 * Supports both legacy color codes (&) and MiniMessage format.
 */
object ColorUtil {

    private val HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})")
    private val miniMessage = MiniMessage.miniMessage()
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()

    /**
     * Translates color codes in a string.
     * Supports:
     * - Legacy color codes (&a, &b, etc.)
     * - Hex colors (&#RRGGBB)
     */
    fun colorize(text: String): String {
        var result = text

        // Process hex colors first
        val matcher = HEX_PATTERN.matcher(result)
        val buffer = StringBuffer()
        while (matcher.find()) {
            val hexColor = matcher.group(1)
            val replacement = buildHexColor(hexColor)
            matcher.appendReplacement(buffer, replacement)
        }
        matcher.appendTail(buffer)
        result = buffer.toString()

        // Process legacy color codes
        return ChatColor.translateAlternateColorCodes('&', result)
    }

    /**
     * Colorizes a list of strings.
     */
    fun colorize(texts: List<String>): List<String> {
        return texts.map { colorize(it) }
    }

    /**
     * Converts a string to an Adventure Component.
     */
    fun toComponent(text: String): Component {
        return legacySerializer.deserialize(colorize(text))
    }

    /**
     * Converts a MiniMessage string to an Adventure Component.
     */
    fun fromMiniMessage(text: String): Component {
        return miniMessage.deserialize(text)
    }

    /**
     * Strips all color codes from a string.
     */
    fun stripColors(text: String): String {
        return ChatColor.stripColor(colorize(text)) ?: text
    }

    /**
     * Builds a hex color string for Minecraft.
     */
    private fun buildHexColor(hex: String): String {
        val builder = StringBuilder("§x")
        for (char in hex) {
            builder.append("§").append(char)
        }
        return builder.toString()
    }

    /**
     * Replaces placeholders in a string.
     */
    fun replacePlaceholders(text: String, placeholders: Map<String, String>): String {
        var result = text
        for ((key, value) in placeholders) {
            result = result.replace("{$key}", value)
        }
        return result
    }

    /**
     * Replaces placeholders and colorizes.
     */
    fun format(text: String, placeholders: Map<String, String> = emptyMap()): String {
        return colorize(replacePlaceholders(text, placeholders))
    }
}
