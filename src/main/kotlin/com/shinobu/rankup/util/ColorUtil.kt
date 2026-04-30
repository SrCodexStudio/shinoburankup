package com.shinobu.rankup.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.ChatColor
import java.util.regex.Pattern

/**
 * Utility class for handling colors, gradients, and text formatting.
 * Supports both MiniMessage and legacy color codes for maximum compatibility.
 *
 * SUPPORTED COLOR FORMATS:
 * - MiniMessage: <red>, <#FF0000>, <gradient:red:blue>
 * - Legacy codes: &c, &l, &r (with & or section symbol)
 * - Hex compact: &#RRGGBB (e.g., &#FB2C36)
 * - Hex Spigot/BungeeCord: &x&R&R&G&G&B&B (e.g., &x&F&B&2&C&3&6)
 * - Hex with section: §x§R§R§G§G§B§B
 *
 * All formats work in:
 * - Chat messages
 * - GUI titles and lore
 * - Tab/scoreboard placeholders
 * - Action bars and titles
 */
object ColorUtil {

    private val miniMessage = MiniMessage.miniMessage()
    private val legacySerializer = LegacyComponentSerializer.legacyAmpersand()
    private val legacySectionSerializer = LegacyComponentSerializer.legacySection()

    // Pattern for &#RRGGBB format
    private val HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})")

    // Pattern for &x&R&R&G&G&B&B format (Spigot/BungeeCord style)
    private val SPIGOT_HEX_PATTERN = Pattern.compile("&x(&[A-Fa-f0-9]){6}", Pattern.CASE_INSENSITIVE)

    // Pattern for §x§R§R§G§G§B§B format (already translated)
    private val SECTION_HEX_PATTERN = Pattern.compile("§x(§[A-Fa-f0-9]){6}", Pattern.CASE_INSENSITIVE)

    // Pattern for legacy color codes
    private val LEGACY_CODE_PATTERN = Pattern.compile("&[0-9a-fk-orx]", Pattern.CASE_INSENSITIVE)

    // Pattern for section color codes
    private val SECTION_CODE_PATTERN = Pattern.compile("§[0-9a-fk-orx]", Pattern.CASE_INSENSITIVE)

    /**
     * Parse a message supporting all color formats.
     * Automatically detects and processes:
     * - MiniMessage format (<red>, <#hex>, <gradient>)
     * - Legacy & codes (&c, &l)
     * - Hex &#RRGGBB
     * - Hex &x&R&R&G&G&B&B (Spigot/BungeeCord)
     * - Section symbol variants
     *
     * @param message The message to parse
     * @return A colored Component
     */
    fun parse(message: String): Component {
        // Convert all formats to MiniMessage, then parse
        val processed = convertAllToMiniMessage(message)
        return miniMessage.deserialize(processed)
    }

    /**
     * Convert all supported color formats to MiniMessage format.
     * This is the main conversion function that handles everything.
     */
    private fun convertAllToMiniMessage(text: String): String {
        var result = text

        // Step 1: Convert section symbols (§) to ampersand (&) for uniform processing
        result = result.replace('§', '&')

        // Step 2: Convert Spigot/BungeeCord hex format &x&R&R&G&G&B&B to &#RRGGBB
        result = convertSpigotHexToCompact(result)

        // Step 3: Convert compact hex &#RRGGBB to MiniMessage <#RRGGBB>
        result = convertCompactHexToMiniMessage(result)

        // Step 4: Convert legacy color codes to MiniMessage
        result = convertLegacyCodesToMiniMessage(result)

        return result
    }

    /**
     * Convert Spigot/BungeeCord hex format (&x&R&R&G&G&B&B) to compact format (&#RRGGBB).
     * Example: &x&F&B&2&C&3&6 -> &#FB2C36
     */
    private fun convertSpigotHexToCompact(text: String): String {
        val matcher = SPIGOT_HEX_PATTERN.matcher(text)
        val buffer = StringBuffer()

        while (matcher.find()) {
            val match = matcher.group()
            // Extract hex digits from &x&R&R&G&G&B&B format
            val hex = match.replace("&x", "", ignoreCase = true)
                .replace("&", "")
            matcher.appendReplacement(buffer, "&#$hex")
        }

        matcher.appendTail(buffer)
        return buffer.toString()
    }

    /**
     * Convert compact hex format (&#RRGGBB) to MiniMessage format (<#RRGGBB>).
     */
    private fun convertCompactHexToMiniMessage(text: String): String {
        val matcher = HEX_PATTERN.matcher(text)
        val buffer = StringBuffer()

        while (matcher.find()) {
            val hex = matcher.group(1)
            matcher.appendReplacement(buffer, "<#$hex>")
        }

        matcher.appendTail(buffer)
        return buffer.toString()
    }

    /**
     * Convert legacy color codes (&c, &l, etc.) to MiniMessage format.
     */
    private fun convertLegacyCodesToMiniMessage(text: String): String {
        var result = text

        // Color codes
        result = result.replace("&0", "<black>")
        result = result.replace("&1", "<dark_blue>")
        result = result.replace("&2", "<dark_green>")
        result = result.replace("&3", "<dark_aqua>")
        result = result.replace("&4", "<dark_red>")
        result = result.replace("&5", "<dark_purple>")
        result = result.replace("&6", "<gold>")
        result = result.replace("&7", "<gray>")
        result = result.replace("&8", "<dark_gray>")
        result = result.replace("&9", "<blue>")
        result = result.replace("&a", "<green>", ignoreCase = true)
        result = result.replace("&b", "<aqua>", ignoreCase = true)
        result = result.replace("&c", "<red>", ignoreCase = true)
        result = result.replace("&d", "<light_purple>", ignoreCase = true)
        result = result.replace("&e", "<yellow>", ignoreCase = true)
        result = result.replace("&f", "<white>", ignoreCase = true)

        // Formatting codes
        result = result.replace("&l", "<bold>", ignoreCase = true)
        result = result.replace("&o", "<italic>", ignoreCase = true)
        result = result.replace("&n", "<underlined>", ignoreCase = true)
        result = result.replace("&m", "<strikethrough>", ignoreCase = true)
        result = result.replace("&k", "<obfuscated>", ignoreCase = true)
        result = result.replace("&r", "<reset>", ignoreCase = true)

        return result
    }

    /**
     * Parse a message with placeholder replacements.
     */
    fun parse(message: String, placeholders: Map<String, String>): Component {
        var processed = message
        placeholders.forEach { (key, value) ->
            processed = processed.replace("{$key}", value)
            processed = processed.replace("%$key%", value)
        }
        return parse(processed)
    }

    /**
     * Parse legacy color codes and return as Component.
     * Use this when you specifically need legacy parsing.
     */
    fun parseLegacy(message: String): Component {
        val processed = translateAllHexToSection(message)
        val colorProcessed = ChatColor.translateAlternateColorCodes('&', processed)
        return legacySectionSerializer.deserialize(colorProcessed)
    }

    /**
     * Translate all hex formats to Bukkit section format (§x§R§R§G§G§B§B).
     * For use with Bukkit APIs that expect section symbols.
     */
    fun translateAllHexToSection(message: String): String {
        var result = message

        // Convert section to ampersand first for uniform processing
        result = result.replace('§', '&')

        // Convert Spigot format to compact
        result = convertSpigotHexToCompact(result)

        // Convert compact hex &#RRGGBB to §x§R§R§G§G§B§B
        val matcher = HEX_PATTERN.matcher(result)
        val buffer = StringBuffer()

        while (matcher.find()) {
            val hex = matcher.group(1)
            val sectionFormat = "§x" + hex.toCharArray().joinToString("") { "§$it" }
            matcher.appendReplacement(buffer, sectionFormat)
        }

        matcher.appendTail(buffer)
        return buffer.toString()
    }

    /**
     * Translate all hex formats to Spigot format (&x&R&R&G&G&B&B).
     * For use with plugins that expect this format.
     */
    fun translateAllHexToSpigot(message: String): String {
        var result = message

        // Convert section to ampersand
        result = result.replace('§', '&')

        // Convert compact hex to Spigot format
        val matcher = HEX_PATTERN.matcher(result)
        val buffer = StringBuffer()

        while (matcher.find()) {
            val hex = matcher.group(1)
            val spigotFormat = "&x" + hex.toCharArray().joinToString("") { "&$it" }
            matcher.appendReplacement(buffer, spigotFormat)
        }

        matcher.appendTail(buffer)
        return buffer.toString()
    }

    /**
     * Convert legacy codes to MiniMessage format (public wrapper).
     */
    fun legacyToMiniMessage(legacy: String): String {
        return convertAllToMiniMessage(legacy)
    }

    /**
     * Convert a Component back to legacy string format with & codes.
     * Useful for PlaceholderAPI and chat plugins that expect legacy format.
     */
    fun toLegacyString(component: Component): String {
        return legacySerializer.serialize(component)
    }

    /**
     * Convert a Component back to legacy string format with section symbols.
     * This is the format that Bukkit/Spigot uses internally.
     */
    fun toLegacySectionString(component: Component): String {
        return legacySectionSerializer.serialize(component)
    }

    /**
     * Parse a message and return it as a legacy string with & codes.
     * Useful for external plugins that don't support Adventure.
     */
    fun parseToLegacy(message: String): String {
        val component = parse(message)
        return toLegacyString(component)
    }

    /**
     * Parse a message and return it as a legacy string with section symbols.
     * This is for Bukkit API compatibility (GUI, Tab, Scoreboard, etc).
     */
    fun parseToLegacySection(message: String): String {
        val component = parse(message)
        return toLegacySectionString(component)
    }

    /**
     * Process colors for GUI/inventory items.
     * Returns a string with section symbols for Bukkit compatibility.
     */
    fun colorizeForGui(message: String): String {
        // First translate all hex formats
        var result = translateAllHexToSection(message)
        // Then translate legacy & codes to section
        result = ChatColor.translateAlternateColorCodes('&', result)
        return result
    }

    /**
     * Process colors for TAB/Scoreboard/PlaceholderAPI.
     * Returns a string with section symbols.
     */
    fun colorizeForTab(message: String): String {
        return colorizeForGui(message) // Same processing
    }

    /**
     * Process colors and return plain Bukkit colored string.
     * Universal method for any Bukkit API that accepts colored strings.
     */
    fun colorize(message: String): String {
        return colorizeForGui(message)
    }

    /**
     * Create a gradient text component.
     */
    fun gradient(text: String, startColor: String, endColor: String): Component {
        return parse("<gradient:$startColor:$endColor>$text</gradient>")
    }

    /**
     * Create a gradient text as legacy string.
     * Note: Gradients are approximated in legacy format.
     */
    fun gradientLegacy(text: String, startColor: String, endColor: String): String {
        val component = gradient(text, startColor, endColor)
        return toLegacySectionString(component)
    }

    /**
     * Create a rainbow gradient text.
     */
    fun rainbow(text: String): Component {
        return parse("<rainbow>$text</rainbow>")
    }

    /**
     * Create a rainbow text as legacy string.
     */
    fun rainbowLegacy(text: String): String {
        val component = rainbow(text)
        return toLegacySectionString(component)
    }

    /**
     * Create a progress bar component.
     */
    fun progressBar(
        progress: Double,
        length: Int = 20,
        filledChar: String = "▰",
        emptyChar: String = "▱",
        filledColor: String = "#00FF00",
        emptyColor: String = "#555555"
    ): Component {
        val filled = (progress * length).toInt().coerceIn(0, length)
        val empty = length - filled

        val filledPart = filledChar.repeat(filled)
        val emptyPart = emptyChar.repeat(empty)

        return parse("<$filledColor>$filledPart</$filledColor><$emptyColor>$emptyPart</$emptyColor>")
    }

    /**
     * Create a centered message for chat.
     */
    fun centerMessage(message: Component, chatWidth: Int = 154): Component {
        val plainText = miniMessage.serialize(message)
        val spaces = (chatWidth - plainText.length) / 2
        val padding = " ".repeat(spaces.coerceAtLeast(0))
        return parse("$padding").append(message)
    }

    /**
     * Create a box frame around text.
     */
    fun createBox(lines: List<Component>, width: Int = 40): List<Component> {
        val topBorder = parse("<gold>╔${"═".repeat(width)}╗</gold>")
        val bottomBorder = parse("<gold>╚${"═".repeat(width)}╝</gold>")

        val result = mutableListOf<Component>()
        result.add(topBorder)

        lines.forEach { line ->
            result.add(parse("<gold>║</gold> ").append(line).append(parse(" <gold>║</gold>")))
        }

        result.add(bottomBorder)
        return result
    }

    /**
     * Strip all color codes from a string.
     * Handles MiniMessage, legacy codes, hex formats, and section symbols.
     */
    fun stripColors(message: String): String {
        // First, parse the message to Component (handles all formats)
        val component = parse(message)
        // Then serialize to plain text without colors
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component)
    }

    /**
     * Strip only legacy/section color codes (preserves MiniMessage tags).
     */
    fun stripLegacyColors(message: String): String {
        var result = message
        // Remove section codes
        result = SECTION_CODE_PATTERN.matcher(result).replaceAll("")
        result = SECTION_HEX_PATTERN.matcher(result).replaceAll("")
        // Remove ampersand codes
        result = LEGACY_CODE_PATTERN.matcher(result).replaceAll("")
        result = SPIGOT_HEX_PATTERN.matcher(result).replaceAll("")
        result = HEX_PATTERN.matcher(result).replaceAll("")
        return result
    }

    /**
     * Interpolate between two colors.
     */
    fun interpolateColor(color1: TextColor, color2: TextColor, ratio: Double): TextColor {
        val r = (color1.red() + (color2.red() - color1.red()) * ratio).toInt()
        val g = (color1.green() + (color2.green() - color1.green()) * ratio).toInt()
        val b = (color1.blue() + (color2.blue() - color1.blue()) * ratio).toInt()
        return TextColor.color(r, g, b)
    }

    /**
     * Check if a string contains any color codes.
     */
    fun hasColors(message: String): Boolean {
        return LEGACY_CODE_PATTERN.matcher(message).find() ||
                SECTION_CODE_PATTERN.matcher(message).find() ||
                HEX_PATTERN.matcher(message).find() ||
                SPIGOT_HEX_PATTERN.matcher(message).find() ||
                message.contains("<") // MiniMessage tags
    }

    /**
     * Translate a hex color string to TextColor.
     * Accepts formats: #RRGGBB, RRGGBB, &#RRGGBB
     */
    fun hexToTextColor(hex: String): TextColor? {
        val cleanHex = hex.removePrefix("#").removePrefix("&#")
        return if (cleanHex.length == 6 && cleanHex.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }) {
            TextColor.fromHexString("#$cleanHex")
        } else {
            null
        }
    }
}
