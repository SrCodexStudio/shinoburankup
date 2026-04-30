package com.shinobu.rankup.gui

import com.shinobu.rankup.ShinobuRankup
import com.shinobu.rankup.cache.LeaderboardCache
import com.shinobu.rankup.cache.PlayerCache
import com.shinobu.rankup.cache.RankCache
import com.shinobu.rankup.config.GuiConfigManager
import com.shinobu.rankup.config.LanguageManager
import com.shinobu.rankup.hook.VaultHook
import com.shinobu.rankup.util.GlassColor
import com.shinobu.rankup.util.ItemBuilder
import com.shinobu.rankup.util.SoundUtil
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*
import kotlin.math.ceil

/**
 * Leaderboard GUI showing top players by rank progression.
 *
 * Layout (54 slots - 6 rows):
 * - Row 0: Decorative header with title
 * - Rows 1-4: Top player heads (10 per page)
 * - Row 5: Navigation and statistics
 *
 * Features:
 * - Player heads with actual skin textures
 * - Rank progression display
 * - Balance and total spent tracking
 * - Pagination for extended leaderboards
 */
class TopPlayersGui(
    player: Player,
    private var currentPage: Int = 0,
    private val rankCache: RankCache,
    private val playerCache: PlayerCache,
    private val leaderboardCache: LeaderboardCache,
    private val vaultHook: VaultHook
) : BaseGui(player, ShinobuRankup.getInstance().languageManager.get("gui.top.title"), 6) {

    private val lang: LanguageManager
        get() = ShinobuRankup.getInstance().languageManager

    private val guiConfig: GuiConfigManager
        get() = ShinobuRankup.getInstance().guiConfigManager

    companion object {
        /**
         * Number of players per page.
         */
        private const val PLAYERS_PER_PAGE = 10

        /**
         * Slot layout for top players.
         * Arranged for visual appeal with spacing.
         */
        private val PLAYER_SLOTS = listOf(
            // Top 3 (highlighted row)
            11, 13, 15,
            // Positions 4-7
            19, 21, 23, 25,
            // Positions 8-10
            29, 31, 33
        )

        /**
         * Medal colors for top 3 positions.
         */
        private val MEDAL_COLORS = mapOf(
            1 to "&6&l", // Gold
            2 to "&7&l", // Silver
            3 to "&c&l"  // Bronze (copper color)
        )

        /**
         * Medal symbols for top 3.
         */
        private val MEDAL_SYMBOLS = mapOf(
            1 to "🥇",
            2 to "🥈",
            3 to "🥉"
        )

        /**
         * Background colors for position highlighting.
         */
        private val POSITION_GLASS = mapOf(
            1 to GlassColor.YELLOW,  // Gold background
            2 to GlassColor.WHITE,   // Silver background
            3 to GlassColor.ORANGE   // Bronze background
        )
    }

    /**
     * Leaderboard display data - reloaded each time build() is called.
     */
    private var leaderboardData: List<LeaderboardDisplayEntry> = emptyList()

    /**
     * Total pages in the leaderboard.
     */
    private val totalPages: Int
        get() = ceil(leaderboardData.size.toDouble() / PLAYERS_PER_PAGE).toInt().coerceAtLeast(1)

    override fun build() {
        guiInventory.clear()
        clickHandlers.clear()

        // Reload leaderboard data fresh each time
        leaderboardData = loadLeaderboardData()

        // Build sections
        buildHeader()
        buildBackground()
        buildPlayerEntries()
        buildStatistics()
        buildNavigation()
    }

    /**
     * Builds the header row with title and decoration.
     * Uses configurable materials from GuiConfigManager.
     * Dark theme for consistent, elegant appearance.
     */
    private fun buildHeader() {
        // Gradient header - dark theme for consistency
        val headerGradient = listOf(
            GlassColor.GRAY,
            GlassColor.BLACK,
            GlassColor.GRAY,
            GlassColor.BLACK,
            GlassColor.GRAY,
            GlassColor.BLACK,
            GlassColor.GRAY,
            GlassColor.BLACK,
            GlassColor.GRAY
        )

        for (col in 0 until 9) {
            setItem(
                toSlot(0, col),
                ItemBuilder.glassPane(headerGradient[col]).name(" ").build()
            )
        }

        // Title item in center (configurable)
        val headerSlot = guiConfig.topHeaderSlot()
        setItem(
            headerSlot,
            ItemBuilder.of(guiConfig.topHeaderMaterial())
                .name(lang.get("gui.top.header-title"))
                .lore(lang.getList("gui.top.header-lore", mapOf("count" to leaderboardData.size.toString())))
                .hideAll()
                .apply { if (guiConfig.topHeaderGlow()) glow() }
                .build()
        )
    }

    /**
     * Builds the background decoration.
     */
    private fun buildBackground() {
        // Fill rows 1-4 background
        for (row in 1 until 5) {
            for (col in 0 until 9) {
                val slot = toSlot(row, col)
                if (slot !in PLAYER_SLOTS) {
                    setItem(slot, ItemBuilder.glassPane(GlassColor.BLACK).name(" ").build())
                }
            }
        }

        // Add decorative borders around top 3
        val topDecoSlots = listOf(10, 12, 14, 16, 2, 6)
        topDecoSlots.forEach { slot ->
            if (slot < guiInventory.size) {
                setItem(slot, ItemBuilder.glassPane(GlassColor.YELLOW).name(" ").build())
            }
        }
    }

    /**
     * Builds the player entry displays.
     */
    private fun buildPlayerEntries() {
        val startIndex = currentPage * PLAYERS_PER_PAGE
        val endIndex = (startIndex + PLAYERS_PER_PAGE).coerceAtMost(leaderboardData.size)

        val playersForPage = if (startIndex < leaderboardData.size) {
            leaderboardData.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        playersForPage.forEachIndexed { index, entry ->
            if (index < PLAYER_SLOTS.size) {
                val slot = PLAYER_SLOTS[index]
                val position = startIndex + index + 1
                val item = createPlayerHead(entry, position)

                setItem(slot, item) { _, _ ->
                    handlePlayerClick(entry)
                    true
                }

                // Add position-specific background for top 3
                if (position <= 3) {
                    val bgColor = POSITION_GLASS[position] ?: GlassColor.BLACK
                    addPositionBackground(slot, bgColor)
                }
            }
        }

        // Fill remaining slots with placeholder
        val filledCount = playersForPage.size
        for (i in filledCount until PLAYER_SLOTS.size) {
            setItem(
                PLAYER_SLOTS[i],
                ItemBuilder.of(Material.SKELETON_SKULL)
                    .name(lang.get("gui.top.empty-slot"))
                    .lore(lang.getList("gui.top.empty-slot-lore"))
                    .build()
            )
        }
    }

    /**
     * Creates a player head item for a leaderboard entry.
     */
    private fun createPlayerHead(entry: LeaderboardDisplayEntry, position: Int): ItemStack {
        val medalPrefix = MEDAL_COLORS[position] ?: "&f"
        val medalSymbol = MEDAL_SYMBOLS[position] ?: "#$position"

        val lore = mutableListOf<String>()

        // Position header
        if (position <= 3) {
            lore.add("$medalPrefix$medalSymbol ${getPositionTitle(position)}")
        } else {
            lore.add(lang.get("gui.top.player-entry.position", mapOf("position" to position.toString())))
        }

        lore.add("")
        lore.add(dividerLine("&6"))
        lore.add("")

        // Current rank
        lore.add(lang.get("gui.top.player-entry.rank", mapOf("rank" to "${entry.rankColor}${entry.rankName}")))

        // Balance
        lore.add(lang.get("gui.top.player-entry.balance", mapOf("balance" to formatMoney(entry.balance))))

        // Total spent on rankups
        lore.add(lang.get("gui.top.player-entry.total-spent", mapOf("amount" to formatMoney(entry.totalSpent))))

        // Rankups completed
        lore.add(lang.get("gui.top.player-entry.rankups-done", mapOf("count" to entry.rankupsCompleted.toString())))

        lore.add("")

        // Progress to next rank
        if (entry.nextRankName != null) {
            lore.add(lang.get("gui.top.player-entry.progress-title"))
            val progressBar = createProgressBar(
                current = entry.progressToNext,
                max = entry.nextRankCost,
                length = 10,
                filledColor = "&b",
                emptyColor = "&7"
            )
            val percent = if (entry.nextRankCost > 0) {
                ((entry.progressToNext / entry.nextRankCost) * 100).coerceIn(0.0, 100.0)
            } else 0.0

            lore.add("$progressBar &b${String.format("%.1f", percent)}%")
        } else {
            lore.add(lang.get("gui.top.player-entry.max-rank"))
        }

        lore.add("")
        lore.add(dividerLine("&6"))

        // Online status
        val onlineStatus = if (entry.isOnline) lang.get("gui.top.player-entry.online") else lang.get("gui.top.player-entry.offline")
        lore.add(onlineStatus)

        // Build the head item
        val builder = ItemBuilder.skull()
            .name("$medalPrefix${entry.playerName}")
            .lore(lore)

        // Try to get the actual player for skull owner
        val offlinePlayer = Bukkit.getOfflinePlayer(entry.playerUuid)
        builder.skullOwner(offlinePlayer)

        // Add glow for top 3
        if (position <= 3) {
            builder.glow()
        }

        return builder.build()
    }

    /**
     * Adds decorative background around a position slot.
     */
    private fun addPositionBackground(centerSlot: Int, color: GlassColor) {
        val surroundingSlots = listOf(
            centerSlot - 10, centerSlot - 9, centerSlot - 8,
            centerSlot - 1, centerSlot + 1,
            centerSlot + 8, centerSlot + 9, centerSlot + 10
        )

        surroundingSlots.forEach { slot ->
            if (slot in 0 until guiInventory.size && slot !in PLAYER_SLOTS) {
                // Don't override header row
                if (slot >= 9) {
                    setItem(slot, ItemBuilder.glassPane(color).name(" ").build())
                }
            }
        }
    }

    /**
     * Gets the title for a position.
     */
    private fun getPositionTitle(position: Int): String {
        return when (position) {
            1 -> lang.get("gui.top.position.first")
            2 -> lang.get("gui.top.position.second")
            3 -> lang.get("gui.top.position.third")
            else -> lang.get("gui.top.position.generic", mapOf("position" to position.toString()))
        }
    }

    /**
     * Builds the statistics section.
     */
    private fun buildStatistics() {
        // Player's own position indicator
        val playerPosition = leaderboardData.indexOfFirst { it.playerUuid == player.uniqueId } + 1
        val playerEntry = leaderboardData.find { it.playerUuid == player.uniqueId }

        val playerPositionText = if (playerPosition > 0) {
            lang.get("gui.top.your-position-ranked", mapOf("position" to playerPosition.toString()))
        } else {
            lang.get("gui.top.your-position-not-ranked")
        }

        val playerRankDisplay = "${playerEntry?.rankColor ?: "&7"}${playerEntry?.rankName ?: "None"}"

        setItem(
            toSlot(5, 2),
            ItemBuilder.skull()
                .skullOwner(player)
                .name(lang.get("gui.top.your-position"))
                .lore(
                    "",
                    playerPositionText,
                    "",
                    lang.get("gui.top.your-position-rank", mapOf("rank" to playerRankDisplay)),
                    lang.get("gui.top.your-position-rankups", mapOf("count" to (playerEntry?.rankupsCompleted ?: 0).toString())),
                    ""
                )
                .build()
        )

        // Server statistics
        val totalRankups = leaderboardData.sumOf { it.rankupsCompleted }
        val totalSpent = leaderboardData.sumOf { it.totalSpent }

        val topHolder = leaderboardData.firstOrNull()?.let {
            "${it.rankColor}${it.playerName}"
        } ?: lang.get("gui.top.server-stats.top-holder-none")

        setItem(
            toSlot(5, 4),
            ItemBuilder.of(Material.EMERALD)
                .name(lang.get("gui.top.server-stats-title"))
                .lore(
                    "",
                    lang.get("gui.top.server-stats.total-players", mapOf("count" to leaderboardData.size.toString())),
                    lang.get("gui.top.server-stats.total-rankups", mapOf("count" to formatNumber(totalRankups.toLong()))),
                    lang.get("gui.top.server-stats.total-spent", mapOf("amount" to formatMoney(totalSpent))),
                    "",
                    lang.get("gui.top.server-stats.top-holder", mapOf("player" to topHolder)),
                    ""
                )
                .glow()
                .build()
        )
    }

    /**
     * Builds the navigation bar.
     */
    private fun buildNavigation() {
        val bottomRow = 5

        // Fill row 5 background first
        for (col in 0 until 9) {
            if (guiInventory.getItem(toSlot(bottomRow, col)) == null) {
                setItem(
                    toSlot(bottomRow, col),
                    ItemBuilder.glassPane(GlassColor.GRAY).name(" ").build()
                )
            }
        }

        // Previous page
        val hasPrevious = currentPage > 0
        setItem(
            toSlot(bottomRow, 0),
            createNavigationButton(NavigationType.PREVIOUS_PAGE, hasPrevious)
        ) { _, _ ->
            if (hasPrevious) {
                currentPage--
                SoundUtil.playPageTurn(player)
                build()
            } else {
                SoundUtil.playDenied(player)
            }
            true
        }

        // Info button (leaderboard auto-updates every 5 minutes)
        setItem(
            toSlot(bottomRow, 6),
            ItemBuilder.of(Material.CLOCK)
                .name(lang.get("gui.top.refresh"))
                .lore(lang.getList("gui.top.refresh-lore"))
                .build()
        ) { _, _ ->
            SoundUtil.playClick(player)
            player.sendMessage(ItemBuilder.colorize(lang.get("gui.top.refresh-message")))
            true
        }

        // Close button
        setItem(
            toSlot(bottomRow, 7),
            createNavigationButton(NavigationType.CLOSE)
        ) { _, _ ->
            close()
            true
        }

        // Next page
        val hasNext = currentPage < totalPages - 1
        setItem(
            toSlot(bottomRow, 8),
            createNavigationButton(NavigationType.NEXT_PAGE, hasNext)
        ) { _, _ ->
            if (hasNext) {
                currentPage++
                SoundUtil.playPageTurn(player)
                build()
            } else {
                SoundUtil.playDenied(player)
            }
            true
        }
    }

    /**
     * Handles a click on a player entry.
     */
    private fun handlePlayerClick(entry: LeaderboardDisplayEntry) {
        SoundUtil.playClick(player)

        val playerDisplay = "${entry.rankColor}${entry.playerName}"
        val rankDisplay = "${entry.rankColor}${entry.rankName}"

        // Show detailed info message
        player.sendMessage(ItemBuilder.colorize(""))
        player.sendMessage(ItemBuilder.colorize(lang.get("gui.top.click.header")))
        player.sendMessage(ItemBuilder.colorize(""))
        player.sendMessage(ItemBuilder.colorize(lang.get("gui.top.click.title", mapOf("player" to playerDisplay))))
        player.sendMessage(ItemBuilder.colorize(""))
        player.sendMessage(ItemBuilder.colorize(lang.get("gui.top.click.rank", mapOf("rank" to rankDisplay))))
        player.sendMessage(ItemBuilder.colorize(lang.get("gui.top.click.balance", mapOf("balance" to formatMoney(entry.balance)))))
        player.sendMessage(ItemBuilder.colorize(lang.get("gui.top.click.spent", mapOf("amount" to formatMoney(entry.totalSpent)))))
        player.sendMessage(ItemBuilder.colorize(lang.get("gui.top.click.rankups", mapOf("count" to entry.rankupsCompleted.toString()))))

        if (entry.isOnline) {
            player.sendMessage(ItemBuilder.colorize(lang.get("gui.top.click.status-online")))
        } else {
            player.sendMessage(ItemBuilder.colorize(lang.get("gui.top.click.status-offline")))
        }

        player.sendMessage(ItemBuilder.colorize(""))
        player.sendMessage(ItemBuilder.colorize(lang.get("gui.top.click.header")))
        player.sendMessage(ItemBuilder.colorize(""))
    }

    /**
     * Loads leaderboard data from the LeaderboardCache.
     * Converts LeaderboardEntry to LeaderboardDisplayEntry with additional display info.
     */
    private fun loadLeaderboardData(): List<LeaderboardDisplayEntry> {
        // Get entries from the cache
        val entries = leaderboardCache.getTop(100)

        if (entries.isEmpty()) {
            return emptyList()
        }

        return entries.map { entry ->
            // Get rank data for display
            val rank = rankCache.getById(entry.rankId)
            val nextRank = rankCache.getNextRank(entry.rankId)

            // Get player balance (if online, use vault, otherwise 0)
            val offlinePlayer = Bukkit.getOfflinePlayer(entry.uuid)
            val balance = if (offlinePlayer.isOnline) {
                vaultHook.getBalance(offlinePlayer.player!!)
            } else {
                0.0
            }

            // Calculate progress to next rank
            val progressToNext = balance.coerceAtMost(nextRank?.cost ?: 0.0)

            // Extract color from rank prefix
            val rankColor = extractColorFromPrefix(rank?.prefix ?: "")

            LeaderboardDisplayEntry(
                playerUuid = entry.uuid,
                playerName = entry.name,
                rankName = rank?.displayName ?: entry.rankDisplayName,
                rankColor = rankColor,
                balance = balance,
                totalSpent = entry.totalSpent,
                rankupsCompleted = entry.rankupCount,
                progressToNext = progressToNext,
                nextRankName = nextRank?.displayName,
                nextRankCost = nextRank?.cost ?: 0.0,
                isOnline = offlinePlayer.isOnline
            )
        }
    }

    /**
     * Extract color code from rank prefix.
     */
    private fun extractColorFromPrefix(prefix: String): String {
        val colorPattern = Regex("(&[0-9a-fklmnor])+")
        val match = colorPattern.find(prefix)
        return match?.value ?: "&7"
    }
}

/**
 * Data class for leaderboard display entries.
 * Contains additional display information beyond the base LeaderboardEntry.
 */
data class LeaderboardDisplayEntry(
    val playerUuid: UUID,
    val playerName: String,
    val rankName: String,
    val rankColor: String,
    val balance: Double,
    val totalSpent: Double,
    val rankupsCompleted: Int,
    val progressToNext: Double,
    val nextRankName: String?,
    val nextRankCost: Double,
    val isOnline: Boolean
)
