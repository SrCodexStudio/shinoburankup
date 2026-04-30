package com.shinobu.rankup.hook

import com.shinobu.rankup.api.ShinobuRankupAPI
import com.shinobu.rankup.data.RankData
import com.shinobu.rankup.util.ColorUtil
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.ChatColor
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin
import java.text.DecimalFormat

/**
 * PlaceholderAPI expansion for ShinobuRankup.
 *
 * Registered placeholders:
 * - %shinobu_rank% - Current rank display name with colors
 * - %shinobu_rank_id% - Current rank ID
 * - %shinobu_rank_prefix% - Current rank prefix with colors
 * - %shinobu_rank_plain% - Current rank display name without colors
 * - %shinobu_next_rank% - Next rank display name
 * - %shinobu_next_rank_id% - Next rank ID
 * - %shinobu_next_cost% - Cost to next rank (formatted)
 * - %shinobu_next_cost_raw% - Cost to next rank (raw number)
 * - %shinobu_progress% - Progress percentage (0-100)
 * - %shinobu_progress_bar% - Visual progress bar
 * - %shinobu_progress_bar_<length>% - Custom length progress bar
 * - %shinobu_total_spent% - Total money spent on rankups
 * - %shinobu_total_spent_raw% - Total money spent (raw number)
 * - %shinobu_rankup_count% - Number of rankups done
 * - %shinobu_position% - Leaderboard position
 * - %shinobu_can_rankup% - "Yes" or "No"
 * - %shinobu_money_needed% - Money needed for next rank
 * - %shinobu_money_needed_raw% - Money needed (raw number)
 * - %shinobu_balance% - Current balance (formatted)
 * - %shinobu_is_max_rank% - "Yes" or "No"
 */
class PlaceholderAPIHook(
    private val plugin: JavaPlugin,
    private val apiProvider: () -> ShinobuRankupAPI?,
    private val vaultHook: VaultHook
) {

    @Volatile
    private var expansion: ShinobuRankupExpansion? = null

    @Volatile
    private var isRegistered: Boolean = false

    /**
     * Setup PlaceholderAPI integration.
     *
     * @return true if PlaceholderAPI is available and expansion registered
     */
    fun setup(): Boolean {
        if (isRegistered) {
            return expansion != null
        }

        if (!isPlaceholderAPIPresent()) {
            plugin.logger.info("PlaceholderAPI not found. Placeholders will not be available.")
            isRegistered = true
            return false
        }

        try {
            expansion = ShinobuRankupExpansion(plugin, apiProvider, vaultHook)
            val registered = expansion!!.register()

            if (registered) {
                plugin.logger.info("PlaceholderAPI expansion 'shinobu' registered successfully")
            } else {
                plugin.logger.warning("Failed to register PlaceholderAPI expansion - register() returned false")
                plugin.logger.warning("Try reloading PlaceholderAPI: /papi reload")
                expansion = null
            }

            isRegistered = true
            return registered
        } catch (e: Exception) {
            plugin.logger.warning("Exception during PlaceholderAPI expansion registration: ${e.message}")
            e.printStackTrace()
            expansion = null
            isRegistered = true
            return false
        }
    }

    /**
     * Check if PlaceholderAPI plugin is present.
     */
    private fun isPlaceholderAPIPresent(): Boolean {
        return try {
            plugin.server.pluginManager.getPlugin("PlaceholderAPI") != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if the expansion is registered and working.
     */
    fun isEnabled(): Boolean = expansion != null && isRegistered

    /**
     * Unregister the expansion (call on plugin disable).
     */
    fun unregister() {
        expansion?.unregister()
        expansion = null
        isRegistered = false
    }
}

/**
 * The actual PlaceholderAPI expansion implementation.
 */
class ShinobuRankupExpansion(
    private val plugin: JavaPlugin,
    private val apiProvider: () -> ShinobuRankupAPI?,
    private val vaultHook: VaultHook
) : PlaceholderExpansion() {

    private val percentFormat = DecimalFormat("#.##")
    private val numberFormat = DecimalFormat("#,##0.##")

    // Debug mode - set to true to enable verbose logging
    private var debugMode = false

    init {
        // Check if debug mode is enabled in config
        debugMode = plugin.config.getBoolean("debug.placeholders", false)
    }

    private fun debug(message: String) {
        if (debugMode) {
            plugin.logger.info("[PAPI Debug] $message")
        }
    }

    // Progress bar configuration
    private val defaultBarLength = 10
    private val filledChar = '\u2588' // Full block
    private val emptyChar = '\u2591'  // Light shade
    private val filledColor = "&a"
    private val emptyColor = "&7"

    override fun getIdentifier(): String = "shinobu"

    override fun getAuthor(): String = plugin.description.authors.firstOrNull() ?: "Unknown"

    override fun getVersion(): String = plugin.description.version

    override fun persist(): Boolean = true

    override fun canRegister(): Boolean {
        // Check if we can register - PlaceholderAPI must be present
        val canReg = plugin.server.pluginManager.getPlugin("PlaceholderAPI") != null
        plugin.logger.fine("PlaceholderAPI canRegister check: $canReg")
        return canReg
    }

    /**
     * Test the expansion with a dummy request.
     * Used for debugging.
     */
    fun testExpansion(): String {
        val api = try { apiProvider() } catch (e: Exception) { null }
        return if (api != null) {
            "Expansion OK - API available"
        } else {
            "Expansion FAIL - API is null"
        }
    }

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        debug("onRequest called with params: '$params' for player: ${player?.name}")

        if (player == null) {
            debug("Player is null, returning null")
            return null
        }

        // Safely get API with error handling
        val api = try {
            apiProvider()
        } catch (e: Exception) {
            plugin.logger.warning("Failed to get API for placeholder: ${e.message}")
            null
        }

        if (api == null) {
            debug("API is null! Returning empty string")
            // Return empty string instead of null to prevent raw placeholder showing
            // This can happen if API is not yet initialized
            return ""
        }

        debug("API obtained successfully")

        // Split params for parameterized placeholders
        val parts = params.lowercase().split("_")
        val mainParam = parts.first()
        debug("Main param: '$mainParam', parts: $parts")

        return try {
            val result = when (mainParam) {
                "rank" -> handleRankPlaceholder(player, parts, api)
                "next" -> handleNextRankPlaceholder(player, parts, api)
                "progress" -> handleProgressPlaceholder(player, parts, api)
                "total" -> handleTotalPlaceholder(player, parts, api)
                "rankup" -> handleRankupCountPlaceholder(player, api)
                "position" -> getLeaderboardPosition(player, api)
                "can" -> handleCanRankupPlaceholder(player, api)
                "money" -> handleMoneyNeededPlaceholder(player, parts, api)
                "balance" -> vaultHook.format(vaultHook.getBalance(player))
                "prefix" -> handleRankPlaceholder(player, listOf("rank", "prefix"), api)
                "playtime" -> handlePlaytimePlaceholder(player, parts, api)
                "is" -> handleIsPlaceholder(player, parts, api)
                else -> {
                    debug("Unknown placeholder param: '$mainParam'")
                    null
                }
            }
            debug("Result for '$params': '$result'")
            result
        } catch (e: Exception) {
            plugin.logger.warning("Error processing placeholder 'shinobu_$params': ${e.message}")
            e.printStackTrace()
            ""
        }
    }

    private fun handleRankPlaceholder(
        player: OfflinePlayer,
        parts: List<String>,
        api: ShinobuRankupAPI
    ): String {
        val rank = api.getPlayerRank(player)

        // If player has no rank, return a default value
        if (rank == null) {
            return when (parts.getOrNull(1)) {
                "id" -> "none"
                "prefix" -> ""
                "order" -> "0"
                "cost" -> vaultHook.format(0.0)
                "plain" -> "No Rank"
                else -> "No Rank"
            }
        }

        return when (parts.getOrNull(1)) {
            "id" -> rank.id
            "prefix" -> ColorUtil.parseToLegacySection(rank.prefix)
            "order" -> rank.order.toString()
            "cost" -> vaultHook.format(rank.cost)
            "plain" -> ColorUtil.stripColors(rank.displayName)
            else -> ColorUtil.parseToLegacySection(rank.displayName)
        }
    }

    private fun handleNextRankPlaceholder(
        player: OfflinePlayer,
        parts: List<String>,
        api: ShinobuRankupAPI
    ): String? {
        val nextRank = api.getNextRank(player)

        if (nextRank == null) {
            return when (parts.getOrNull(1)) {
                "rank" -> when (parts.getOrNull(2)) {
                    "id" -> "MAX"
                    "plain" -> "Max Rank"
                    else -> ColorUtil.parseToLegacySection("<gradient:#FFD700:#FF6B6B>Max Rank</gradient>")
                }
                "cost" -> when (parts.getOrNull(2)) {
                    "raw" -> "0"
                    else -> vaultHook.format(0.0)
                }
                else -> ColorUtil.parseToLegacySection("<gradient:#FFD700:#FF6B6B>Max Rank</gradient>")
            }
        }

        return when (parts.getOrNull(1)) {
            "rank" -> when (parts.getOrNull(2)) {
                "id" -> nextRank.id
                "plain" -> ColorUtil.stripColors(nextRank.displayName)
                else -> ColorUtil.parseToLegacySection(nextRank.displayName)
            }
            "cost" -> when (parts.getOrNull(2)) {
                "raw" -> nextRank.cost.toString()
                else -> vaultHook.format(nextRank.cost)
            }
            else -> ColorUtil.parseToLegacySection(nextRank.displayName)
        }
    }

    private fun handleProgressPlaceholder(
        player: OfflinePlayer,
        parts: List<String>,
        api: ShinobuRankupAPI
    ): String? {
        val progress = calculateProgress(player, api)

        return when (parts.getOrNull(1)) {
            "bar" -> {
                val length = parts.getOrNull(2)?.toIntOrNull() ?: defaultBarLength
                createProgressBar(progress, length.coerceIn(1, 50))
            }
            else -> percentFormat.format(progress)
        }
    }

    private fun handleTotalPlaceholder(
        player: OfflinePlayer,
        parts: List<String>,
        api: ShinobuRankupAPI
    ): String? {
        val playerData = api.getPlayerData(player) ?: return "0"

        return when (parts.getOrNull(1)) {
            "spent" -> when (parts.getOrNull(2)) {
                "raw" -> playerData.totalSpent.toString()
                else -> vaultHook.format(playerData.totalSpent)
            }
            else -> vaultHook.format(playerData.totalSpent)
        }
    }

    private fun handleRankupCountPlaceholder(
        player: OfflinePlayer,
        api: ShinobuRankupAPI
    ): String {
        val playerData = api.getPlayerData(player) ?: return "0"
        return playerData.rankupCount.toString()
    }

    private fun getLeaderboardPosition(
        player: OfflinePlayer,
        api: ShinobuRankupAPI
    ): String {
        val position = api.getLeaderboardPosition(player)
        return if (position > 0) position.toString() else "N/A"
    }

    private fun handleCanRankupPlaceholder(
        player: OfflinePlayer,
        api: ShinobuRankupAPI
    ): String {
        val nextRank = api.getNextRank(player) ?: return "No"
        val balance = vaultHook.getBalance(player)
        return if (balance >= nextRank.cost) "Yes" else "No"
    }

    private fun handleMoneyNeededPlaceholder(
        player: OfflinePlayer,
        parts: List<String>,
        api: ShinobuRankupAPI
    ): String? {
        val nextRank = api.getNextRank(player) ?: return vaultHook.format(0.0)
        val balance = vaultHook.getBalance(player)
        val needed = (nextRank.cost - balance).coerceAtLeast(0.0)

        return when (parts.getOrNull(1)) {
            "needed" -> when (parts.getOrNull(2)) {
                "raw" -> needed.toString()
                else -> vaultHook.format(needed)
            }
            else -> vaultHook.format(needed)
        }
    }

    private fun handlePlaytimePlaceholder(
        player: OfflinePlayer,
        parts: List<String>,
        api: ShinobuRankupAPI
    ): String? {
        return when (parts.getOrNull(1)) {
            "required" -> {
                val nextRank = api.getNextRank(player) ?: return "0s"
                formatPlaytime(nextRank.requirements.minPlaytime)
            }
            "progress" -> when (parts.getOrNull(2)) {
                "bar" -> {
                    val nextRank = api.getNextRank(player) ?: return createProgressBar(100.0, defaultBarLength)
                    val required = nextRank.requirements.minPlaytime
                    if (required <= 0) return createProgressBar(100.0, defaultBarLength)
                    val onlinePlayer = player.player ?: return createProgressBar(0.0, defaultBarLength)
                    val current = onlinePlayer.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20L
                    val progress = ((current.toDouble() / required) * 100).coerceIn(0.0, 100.0)
                    createProgressBar(progress, defaultBarLength)
                }
                else -> {
                    val nextRank = api.getNextRank(player) ?: return "100.0"
                    val required = nextRank.requirements.minPlaytime
                    if (required <= 0) return "100.0"
                    val onlinePlayer = player.player ?: return "0.0"
                    val current = onlinePlayer.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20L
                    percentFormat.format(((current.toDouble() / required) * 100).coerceIn(0.0, 100.0))
                }
            }
            else -> {
                val onlinePlayer = player.player ?: return "0s"
                val ticks = onlinePlayer.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE)
                formatPlaytime(ticks / 20L)
            }
        }
    }

    private fun formatPlaytime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun handleIsPlaceholder(
        player: OfflinePlayer,
        parts: List<String>,
        api: ShinobuRankupAPI
    ): String? {
        return when (parts.getOrNull(1)) {
            "max" -> when (parts.getOrNull(2)) {
                "rank" -> {
                    val isMax = api.getNextRank(player) == null
                    if (isMax) "Yes" else "No"
                }
                else -> null
            }
            else -> null
        }
    }

    /**
     * Calculate progress percentage towards next rank.
     */
    private fun calculateProgress(player: OfflinePlayer, api: ShinobuRankupAPI): Double {
        val currentRank = api.getPlayerRank(player) ?: return 0.0
        val nextRank = api.getNextRank(player) ?: return 100.0

        val balance = vaultHook.getBalance(player)
        val cost = nextRank.cost

        if (cost <= 0) return 100.0

        return ((balance / cost) * 100).coerceIn(0.0, 100.0)
    }

    /**
     * Create a visual progress bar.
     *
     * @param progress Progress percentage (0-100)
     * @param length Total length of the bar
     * @return Formatted progress bar string with color codes
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
}
