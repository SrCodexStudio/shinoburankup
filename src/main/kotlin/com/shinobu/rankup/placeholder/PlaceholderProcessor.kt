package com.shinobu.rankup.placeholder

import com.shinobu.rankup.api.ShinobuRankupAPI
import com.shinobu.rankup.hook.VaultHook
import com.shinobu.rankup.util.ColorUtil
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.text.DecimalFormat
import java.util.regex.Pattern

/**
 * Internal placeholder processor for ShinobuRankup.
 *
 * Supports two formats:
 * - Essentials style: {shinobu_rank}, {shinobu_next_rank}, etc.
 * - PlaceholderAPI style: %shinobu_rank%, %shinobu_next_rank%, etc.
 *
 * This processor works independently of PlaceholderAPI, providing
 * placeholder support even when PAPI is not installed.
 *
 * Available placeholders:
 * - shinobu_rank - Current rank display name with colors
 * - shinobu_rank_id - Current rank ID
 * - shinobu_rank_prefix - Current rank prefix with colors
 * - shinobu_rank_plain - Current rank display name without colors
 * - shinobu_next_rank - Next rank display name
 * - shinobu_next_rank_id - Next rank ID
 * - shinobu_next_cost - Cost to next rank (formatted)
 * - shinobu_next_cost_raw - Cost to next rank (raw number)
 * - shinobu_progress - Progress percentage (0-100)
 * - shinobu_progress_bar - Visual progress bar
 * - shinobu_total_spent - Total money spent on rankups
 * - shinobu_total_spent_raw - Total money spent (raw number)
 * - shinobu_rankup_count - Number of rankups done
 * - shinobu_position - Leaderboard position
 * - shinobu_can_rankup - "Yes" or "No"
 * - shinobu_money_needed - Money needed for next rank
 * - shinobu_money_needed_raw - Money needed (raw number)
 * - shinobu_balance - Current balance (formatted)
 * - shinobu_is_max_rank - "Yes" or "No"
 */
class PlaceholderProcessor(
    private val apiProvider: () -> ShinobuRankupAPI?,
    private val vaultHook: VaultHook
) {

    private val numberFormat = DecimalFormat("#,##0.##")
    private val percentFormat = DecimalFormat("#.##")

    // Patterns for both placeholder styles
    private val essentialsPattern = Pattern.compile("\\{(shinobu_[a-zA-Z0-9_]+)\\}")
    private val papiPattern = Pattern.compile("%(shinobu_[a-zA-Z0-9_]+)%")

    // Progress bar configuration
    private val defaultBarLength = 10
    private val filledChar = '\u2588' // Full block
    private val emptyChar = '\u2591'  // Light shade
    private val filledColor = "&a"
    private val emptyColor = "&7"

    /**
     * Process all ShinobuRankup placeholders in a string.
     * Supports both {shinobu_*} and %shinobu_*% formats.
     *
     * @param text The text to process
     * @param player The player to get data for
     * @return The text with placeholders replaced
     */
    fun process(text: String, player: OfflinePlayer): String {
        var result = text

        // Process Essentials style {placeholder}
        result = processPattern(result, essentialsPattern, player)

        // Process PAPI style %placeholder%
        result = processPattern(result, papiPattern, player)

        return result
    }

    /**
     * Process all ShinobuRankup placeholders in a list of strings.
     */
    fun process(texts: List<String>, player: OfflinePlayer): List<String> {
        return texts.map { process(it, player) }
    }

    /**
     * Process placeholders using a specific pattern.
     */
    private fun processPattern(text: String, pattern: Pattern, player: OfflinePlayer): String {
        val matcher = pattern.matcher(text)
        val buffer = StringBuffer()

        while (matcher.find()) {
            val placeholder = matcher.group(1)
            val value = getPlaceholderValue(placeholder, player) ?: matcher.group(0)
            matcher.appendReplacement(buffer, value.replace("$", "\\$"))
        }
        matcher.appendTail(buffer)

        return buffer.toString()
    }

    /**
     * Get the value for a specific placeholder.
     *
     * @param placeholder The placeholder name (without delimiters)
     * @param player The player to get data for
     * @return The placeholder value, or null if not found
     */
    fun getPlaceholderValue(placeholder: String, player: OfflinePlayer): String? {
        val api = apiProvider() ?: return null

        return when (placeholder.lowercase()) {
            // Current rank placeholders
            "shinobu_rank" -> {
                val rank = api.getPlayerRank(player)
                if (rank != null) ColorUtil.parseToLegacySection(rank.displayName) else "No Rank"
            }
            "shinobu_rank_id" -> {
                api.getPlayerRank(player)?.id ?: "none"
            }
            "shinobu_rank_prefix", "shinobu_prefix" -> {
                val rank = api.getPlayerRank(player)
                if (rank != null) ColorUtil.parseToLegacySection(rank.prefix) else ""
            }
            "shinobu_rank_plain" -> {
                val rank = api.getPlayerRank(player)
                if (rank != null) ColorUtil.stripColors(rank.displayName) else "No Rank"
            }
            "shinobu_rank_order" -> {
                api.getPlayerRank(player)?.order?.toString() ?: "0"
            }
            "shinobu_rank_cost" -> {
                val rank = api.getPlayerRank(player)
                vaultHook.format(rank?.cost ?: 0.0)
            }

            // Next rank placeholders
            "shinobu_next_rank", "shinobu_next" -> {
                val nextRank = api.getNextRank(player)
                if (nextRank != null) {
                    ColorUtil.parseToLegacySection(nextRank.displayName)
                } else {
                    ColorUtil.parseToLegacySection("&6&lMax Rank")
                }
            }
            "shinobu_next_rank_id" -> {
                api.getNextRank(player)?.id ?: "MAX"
            }
            "shinobu_next_rank_plain" -> {
                val nextRank = api.getNextRank(player)
                if (nextRank != null) ColorUtil.stripColors(nextRank.displayName) else "Max Rank"
            }
            "shinobu_next_cost" -> {
                val nextRank = api.getNextRank(player)
                vaultHook.format(nextRank?.cost ?: 0.0)
            }
            "shinobu_next_cost_raw" -> {
                api.getNextRank(player)?.cost?.toString() ?: "0"
            }

            // Progress placeholders
            "shinobu_progress" -> {
                percentFormat.format(calculateProgress(player, api))
            }
            "shinobu_progress_bar" -> {
                createProgressBar(calculateProgress(player, api), defaultBarLength)
            }

            // Player stats placeholders
            "shinobu_total_spent" -> {
                val data = api.getPlayerData(player)
                vaultHook.format(data?.totalSpent ?: 0.0)
            }
            "shinobu_total_spent_raw" -> {
                api.getPlayerData(player)?.totalSpent?.toString() ?: "0"
            }
            "shinobu_rankup_count", "shinobu_rankups" -> {
                api.getPlayerData(player)?.rankupCount?.toString() ?: "0"
            }
            "shinobu_position" -> {
                getLeaderboardPosition(player, api)
            }

            // Utility placeholders
            "shinobu_can_rankup" -> {
                val nextRank = api.getNextRank(player) ?: return "No"
                val balance = vaultHook.getBalance(player)
                if (balance >= nextRank.cost) "Yes" else "No"
            }
            "shinobu_money_needed" -> {
                val nextRank = api.getNextRank(player) ?: return vaultHook.format(0.0)
                val balance = vaultHook.getBalance(player)
                val needed = (nextRank.cost - balance).coerceAtLeast(0.0)
                vaultHook.format(needed)
            }
            "shinobu_money_needed_raw" -> {
                val nextRank = api.getNextRank(player) ?: return "0"
                val balance = vaultHook.getBalance(player)
                val needed = (nextRank.cost - balance).coerceAtLeast(0.0)
                needed.toString()
            }
            "shinobu_balance" -> {
                vaultHook.format(vaultHook.getBalance(player))
            }
            "shinobu_is_max_rank", "shinobu_max_rank" -> {
                if (api.getNextRank(player) == null) "Yes" else "No"
            }

            // Playtime requirement placeholders
            "shinobu_playtime" -> {
                val onlinePlayer = player.player ?: return "0s"
                val ticks = onlinePlayer.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE)
                formatPlaytime(ticks / 20L)
            }
            "shinobu_playtime_required" -> {
                val nextRank = api.getNextRank(player) ?: return "0s"
                formatPlaytime(nextRank.requirements.minPlaytime)
            }
            "shinobu_playtime_progress" -> {
                val nextRank = api.getNextRank(player) ?: return "100.0"
                val required = nextRank.requirements.minPlaytime
                if (required <= 0) return "100.0"
                val onlinePlayer = player.player ?: return "0.0"
                val current = onlinePlayer.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20L
                percentFormat.format(((current.toDouble() / required) * 100).coerceIn(0.0, 100.0))
            }
            "shinobu_playtime_progress_bar" -> {
                val nextRank = api.getNextRank(player) ?: return createProgressBar(100.0, defaultBarLength)
                val required = nextRank.requirements.minPlaytime
                if (required <= 0) return createProgressBar(100.0, defaultBarLength)
                val onlinePlayer = player.player ?: return createProgressBar(0.0, defaultBarLength)
                val current = onlinePlayer.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20L
                val progress = ((current.toDouble() / required) * 100).coerceIn(0.0, 100.0)
                createProgressBar(progress, defaultBarLength)
            }

            else -> null
        }
    }

    /**
     * Calculate progress percentage towards next rank.
     */
    private fun calculateProgress(player: OfflinePlayer, api: ShinobuRankupAPI): Double {
        val nextRank = api.getNextRank(player) ?: return 100.0
        val balance = vaultHook.getBalance(player)
        val cost = nextRank.cost

        if (cost <= 0) return 100.0

        return ((balance / cost) * 100).coerceIn(0.0, 100.0)
    }

    /**
     * Get player's position in leaderboard.
     * Uses the O(1) position lookup from LeaderboardCache via the API,
     * instead of fetching and scanning the full top-1000 list.
     */
    private fun getLeaderboardPosition(player: OfflinePlayer, api: ShinobuRankupAPI): String {
        val position = api.getLeaderboardPosition(player)
        return if (position > 0) position.toString() else "N/A"
    }

    /**
     * Create a visual progress bar.
     */
    private fun createProgressBar(progress: Double, length: Int): String {
        val filledCount = ((progress / 100.0) * length).toInt().coerceIn(0, length)
        val emptyCount = length - filledCount

        return buildString {
            append(filledColor)
            repeat(filledCount) { append(filledChar) }
            append(emptyColor)
            repeat(emptyCount) { append(emptyChar) }
        }
    }

    /**
     * Format seconds into a human-readable playtime string.
     */
    private fun formatPlaytime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    companion object {
        /**
         * List of all available placeholder names (without delimiters).
         */
        val AVAILABLE_PLACEHOLDERS = listOf(
            "shinobu_rank",
            "shinobu_rank_id",
            "shinobu_rank_prefix",
            "shinobu_rank_plain",
            "shinobu_rank_order",
            "shinobu_rank_cost",
            "shinobu_next_rank",
            "shinobu_next",
            "shinobu_next_rank_id",
            "shinobu_next_rank_plain",
            "shinobu_next_cost",
            "shinobu_next_cost_raw",
            "shinobu_progress",
            "shinobu_progress_bar",
            "shinobu_total_spent",
            "shinobu_total_spent_raw",
            "shinobu_rankup_count",
            "shinobu_rankups",
            "shinobu_position",
            "shinobu_can_rankup",
            "shinobu_money_needed",
            "shinobu_money_needed_raw",
            "shinobu_balance",
            "shinobu_is_max_rank",
            "shinobu_max_rank"
        )
    }
}
