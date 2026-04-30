package com.shinobu.rankup.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor

/**
 * Utility class for generating ASCII progress bars.
 *
 * Thread-safe: All methods are stateless and pure functions.
 */
object ProgressBarUtil {

    /**
     * Default progress bar characters.
     */
    object Chars {
        const val FILLED_BLOCK = "█"
        const val EMPTY_BLOCK = "░"
        const val FILLED_SQUARE = "■"
        const val EMPTY_SQUARE = "□"
        const val FILLED_CIRCLE = "●"
        const val EMPTY_CIRCLE = "○"
        const val FILLED_DIAMOND = "◆"
        const val EMPTY_DIAMOND = "◇"
        const val PIPE = "|"
        const val DASH = "-"
        const val ARROW_FILLED = "▰"
        const val ARROW_EMPTY = "▱"
        const val PROGRESS_FILLED = "━"
        const val PROGRESS_EMPTY = "─"
    }

    /**
     * Default color schemes for progress bars.
     */
    object Colors {
        val GREEN_RED = ProgressBarColors("#00FF00", "#FF0000")
        val GOLD_GRAY = ProgressBarColors("#FFD700", "#555555")
        val AQUA_DARK = ProgressBarColors("#00FFFF", "#333333")
        val GRADIENT_FIRE = ProgressBarColors(filledColor = "#FF0000", emptyColor = "#555555", gradientEnd = "#FF7F00")
        val GRADIENT_ICE = ProgressBarColors(filledColor = "#00FFFF", emptyColor = "#333333", gradientEnd = "#0080FF")
    }

    /**
     * Generate a simple ASCII progress bar.
     *
     * @param progress Progress value (0.0 to 1.0)
     * @param length Total length of the bar
     * @param filledChar Character for filled portion
     * @param emptyChar Character for empty portion
     * @return ASCII progress bar string
     */
    fun simple(
        progress: Double,
        length: Int = 20,
        filledChar: String = Chars.FILLED_BLOCK,
        emptyChar: String = Chars.EMPTY_BLOCK
    ): String {
        val safeProgress = progress.coerceIn(0.0, 1.0)
        val filledLength = (safeProgress * length).toInt()
        val emptyLength = length - filledLength

        return filledChar.repeat(filledLength) + emptyChar.repeat(emptyLength)
    }

    /**
     * Generate a colored progress bar using MiniMessage format.
     *
     * @param progress Progress value (0.0 to 1.0)
     * @param length Total length of the bar
     * @param filledChar Character for filled portion
     * @param emptyChar Character for empty portion
     * @param filledColor Color for filled portion (hex)
     * @param emptyColor Color for empty portion (hex)
     * @return MiniMessage formatted progress bar string
     */
    fun colored(
        progress: Double,
        length: Int = 20,
        filledChar: String = Chars.FILLED_BLOCK,
        emptyChar: String = Chars.EMPTY_BLOCK,
        filledColor: String = "#00FF00",
        emptyColor: String = "#555555"
    ): String {
        val safeProgress = progress.coerceIn(0.0, 1.0)
        val filledLength = (safeProgress * length).toInt()
        val emptyLength = length - filledLength

        val filledPart = filledChar.repeat(filledLength)
        val emptyPart = emptyChar.repeat(emptyLength)

        return "<$filledColor>$filledPart</$filledColor><$emptyColor>$emptyPart</$emptyColor>"
    }

    /**
     * Generate a progress bar Component.
     *
     * @param progress Progress value (0.0 to 1.0)
     * @param length Total length of the bar
     * @param filledChar Character for filled portion
     * @param emptyChar Character for empty portion
     * @param colors Color scheme to use
     * @return Component progress bar
     */
    fun component(
        progress: Double,
        length: Int = 20,
        filledChar: String = Chars.FILLED_BLOCK,
        emptyChar: String = Chars.EMPTY_BLOCK,
        colors: ProgressBarColors = Colors.GREEN_RED
    ): Component {
        return ColorUtil.parse(colored(
            progress, length, filledChar, emptyChar,
            colors.filledColor, colors.emptyColor
        ))
    }

    /**
     * Generate a gradient progress bar.
     *
     * @param progress Progress value (0.0 to 1.0)
     * @param length Total length of the bar
     * @param filledChar Character for filled portion
     * @param emptyChar Character for empty portion
     * @param startColor Start color of gradient (hex)
     * @param endColor End color of gradient (hex)
     * @param emptyColor Color for empty portion (hex)
     * @return MiniMessage formatted gradient progress bar
     */
    fun gradient(
        progress: Double,
        length: Int = 20,
        filledChar: String = Chars.FILLED_BLOCK,
        emptyChar: String = Chars.EMPTY_BLOCK,
        startColor: String = "#00FF00",
        endColor: String = "#FFFF00",
        emptyColor: String = "#555555"
    ): String {
        val safeProgress = progress.coerceIn(0.0, 1.0)
        val filledLength = (safeProgress * length).toInt()
        val emptyLength = length - filledLength

        val filledPart = filledChar.repeat(filledLength)
        val emptyPart = emptyChar.repeat(emptyLength)

        return if (filledLength > 0) {
            "<gradient:$startColor:$endColor>$filledPart</gradient><$emptyColor>$emptyPart</$emptyColor>"
        } else {
            "<$emptyColor>$emptyPart</$emptyColor>"
        }
    }

    /**
     * Generate a percentage-based progress bar with brackets.
     *
     * @param progress Progress value (0.0 to 1.0)
     * @param length Total length of the bar (excluding brackets)
     * @param showPercentage Whether to show percentage after bar
     * @param colors Color scheme to use
     * @return MiniMessage formatted progress bar with percentage
     */
    fun withPercentage(
        progress: Double,
        length: Int = 20,
        showPercentage: Boolean = true,
        colors: ProgressBarColors = Colors.GREEN_RED
    ): String {
        val safeProgress = progress.coerceIn(0.0, 1.0)
        val bar = colored(
            safeProgress, length,
            Chars.FILLED_BLOCK, Chars.EMPTY_BLOCK,
            colors.filledColor, colors.emptyColor
        )
        val percentage = (safeProgress * 100).toInt()

        return if (showPercentage) {
            "<gray>[</gray>$bar<gray>] <yellow>$percentage%</yellow></gray>"
        } else {
            "<gray>[</gray>$bar<gray>]</gray>"
        }
    }

    /**
     * Generate a progress bar with a pointer/cursor showing current position.
     *
     * @param progress Progress value (0.0 to 1.0)
     * @param length Total length of the bar
     * @param trackChar Character for the track
     * @param pointerChar Character for the pointer
     * @param trackColor Color for the track
     * @param pointerColor Color for the pointer
     * @return MiniMessage formatted progress bar with pointer
     */
    fun withPointer(
        progress: Double,
        length: Int = 20,
        trackChar: String = Chars.PROGRESS_EMPTY,
        pointerChar: String = "●",
        trackColor: String = "#555555",
        pointerColor: String = "#00FF00"
    ): String {
        val safeProgress = progress.coerceIn(0.0, 1.0)
        val pointerPos = (safeProgress * (length - 1)).toInt()

        return buildString {
            append("<$trackColor>")
            repeat(length) { i ->
                if (i == pointerPos) {
                    append("</$trackColor><$pointerColor>$pointerChar</$pointerColor><$trackColor>")
                } else {
                    append(trackChar)
                }
            }
            append("</$trackColor>")
        }
    }

    /**
     * Generate a segmented progress bar (like a health bar in games).
     *
     * @param progress Progress value (0.0 to 1.0)
     * @param segments Number of segments
     * @param filledColor Color for filled segments
     * @param emptyColor Color for empty segments
     * @param separator Character between segments
     * @return MiniMessage formatted segmented progress bar
     */
    fun segmented(
        progress: Double,
        segments: Int = 10,
        filledColor: String = "#00FF00",
        emptyColor: String = "#555555",
        separator: String = " "
    ): String {
        val safeProgress = progress.coerceIn(0.0, 1.0)
        val filledSegments = (safeProgress * segments).toInt()

        return buildString {
            repeat(segments) { i ->
                if (i > 0) append(separator)
                if (i < filledSegments) {
                    append("<$filledColor>${Chars.FILLED_SQUARE}</$filledColor>")
                } else {
                    append("<$emptyColor>${Chars.EMPTY_SQUARE}</$emptyColor>")
                }
            }
        }
    }

    /**
     * Generate a vertical progress bar (for GUIs).
     *
     * @param progress Progress value (0.0 to 1.0)
     * @param height Number of rows
     * @param filledChar Character for filled portion
     * @param emptyChar Character for empty portion
     * @return List of strings representing each row (bottom to top)
     */
    fun vertical(
        progress: Double,
        height: Int = 5,
        filledChar: String = Chars.FILLED_BLOCK,
        emptyChar: String = Chars.EMPTY_BLOCK
    ): List<String> {
        val safeProgress = progress.coerceIn(0.0, 1.0)
        val filledHeight = (safeProgress * height).toInt()

        return (0 until height).map { row ->
            if (row < filledHeight) filledChar else emptyChar
        }.reversed()
    }

    /**
     * Generate a progress bar for lore (GUI items).
     *
     * @param progress Progress value (0.0 to 1.0)
     * @param current Current value (for display)
     * @param max Maximum value (for display)
     * @param prefix Text prefix
     * @return List of formatted lore lines
     */
    fun forLore(
        progress: Double,
        current: Any,
        max: Any,
        prefix: String = "Progress"
    ): List<String> {
        val bar = colored(
            progress, 20,
            Chars.ARROW_FILLED, Chars.ARROW_EMPTY,
            "#00FF00", "#555555"
        )
        val percentage = (progress.coerceIn(0.0, 1.0) * 100).toInt()

        return listOf(
            "",
            "<gray>$prefix: <white>$current<dark_gray>/<white>$max</gray>",
            bar,
            "<gray>Completion: <yellow>$percentage%</yellow></gray>"
        )
    }

    /**
     * Create a compact progress indicator (for chat).
     *
     * @param progress Progress value (0.0 to 1.0)
     * @return Compact progress string like [████░░░░░░] 40%
     */
    fun compact(progress: Double): String {
        return withPercentage(progress, 10, true, Colors.GREEN_RED)
    }

    /**
     * Create a minimal progress indicator.
     *
     * @param progress Progress value (0.0 to 1.0)
     * @return Minimal string like "40%"
     */
    fun minimal(progress: Double): String {
        val percentage = (progress.coerceIn(0.0, 1.0) * 100).toInt()
        val color = when {
            percentage >= 75 -> "#00FF00"
            percentage >= 50 -> "#FFFF00"
            percentage >= 25 -> "#FFA500"
            else -> "#FF0000"
        }
        return "<$color>$percentage%</$color>"
    }

    /**
     * Create a boss bar style progress.
     *
     * @param progress Progress value (0.0 to 1.0)
     * @param title Title to display above the bar
     * @return List of formatted lines
     */
    fun bossBarStyle(progress: Double, title: String): List<String> {
        val bar = gradient(
            progress, 40,
            Chars.PROGRESS_FILLED, Chars.PROGRESS_EMPTY,
            "#FF0000", "#00FF00", "#333333"
        )

        return listOf(
            "<bold>$title</bold>",
            bar
        )
    }
}

/**
 * Color configuration for progress bars.
 */
data class ProgressBarColors(
    val filledColor: String,
    val emptyColor: String,
    val gradientEnd: String? = null
) {
    companion object {
        fun gradient(start: String, end: String, empty: String): ProgressBarColors {
            return ProgressBarColors(start, empty, end)
        }
    }
}
