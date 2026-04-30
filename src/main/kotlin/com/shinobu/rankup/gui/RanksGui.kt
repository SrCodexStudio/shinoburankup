package com.shinobu.rankup.gui

import com.shinobu.rankup.ShinobuRankup
import com.shinobu.rankup.cache.PlayerCache
import com.shinobu.rankup.cache.RankCache
import com.shinobu.rankup.config.GuiConfigManager
import com.shinobu.rankup.config.LanguageManager
import com.shinobu.rankup.data.RankData
import com.shinobu.rankup.hook.VaultHook
import com.shinobu.rankup.util.GlassColor
import com.shinobu.rankup.util.ItemBuilder
import com.shinobu.rankup.util.SoundUtil
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import kotlin.math.ceil

/**
 * GUI displaying all available ranks with their status.
 *
 * Layout (54 slots - 6 rows):
 * - Row 0: Decorative gradient border
 * - Rows 1-4: Rank items (7 per row = 28 per page)
 * - Row 5: Navigation (prev, info, page, next, close)
 *
 * Rank Status Types:
 * - COMPLETED: Green, enchant glow - ranks already obtained
 * - CURRENT: Gold, special border, glowing - current rank
 * - NEXT: Lime, highlighted - next available rank
 * - LOCKED: Red/barrier, grayed out - future ranks
 */
class RanksGui(
    player: Player,
    private var currentPage: Int = 0,
    private val rankCache: RankCache,
    private val playerCache: PlayerCache,
    private val vaultHook: VaultHook
) : BaseGui(player, ShinobuRankup.getInstance().languageManager.get("gui.ranks.title"), 6) {

    private val lang: LanguageManager
        get() = ShinobuRankup.getInstance().languageManager

    private val guiConfig: GuiConfigManager
        get() = ShinobuRankup.getInstance().guiConfigManager

    companion object {
        /**
         * Number of rank slots per page (rows 1-4, columns 1-7).
         */
        private const val RANKS_PER_PAGE = 28

        /**
         * Slot indices for rank items.
         */
        private val RANK_SLOTS = listOf(
            // Row 1 (index 0): slots 10-16
            10, 11, 12, 13, 14, 15, 16,
            // Row 2 (index 1): slots 19-25
            19, 20, 21, 22, 23, 24, 25,
            // Row 3 (index 2): slots 28-34
            28, 29, 30, 31, 32, 33, 34,
            // Row 4 (index 3): slots 37-43
            37, 38, 39, 40, 41, 42, 43
        )

        /**
         * Gradient colors for the top border.
         * Uses dark theme for consistent, elegant appearance.
         */
        private val TOP_GRADIENT = listOf(
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
    }

    /**
     * Cache of all ranks for display.
     */
    private val allRanks: List<RankDisplayData> by lazy {
        loadRanks()
    }

    /**
     * Total number of pages.
     */
    private val totalPages: Int
        get() = ceil(allRanks.size.toDouble() / RANKS_PER_PAGE).toInt().coerceAtLeast(1)

    override fun build() {
        // Clear the inventory first
        guiInventory.clear()
        clickHandlers.clear()

        // Fill top row with gradient
        fillTopWithGradient()

        // Fill side borders
        fillSideBorders()

        // Place custom decorative items (before rank slots so ranks take priority)
        placeCustomItems()

        // Fill rank display area
        fillRankSlots()

        // Setup navigation bar
        setupNavigationBar()
    }

    /**
     * Places custom decorative items defined in gui/ranks.yml.
     * Supports PLAYER_HEAD with custom skull textures.
     */
    private fun placeCustomItems() {
        val customItems = guiConfig.ranksCustomItems()
        for (item in customItems) {
            if (item.slot < 0 || item.slot >= guiInventory.size) continue
            val builder = ItemBuilder.of(item.material)
                .name(item.name)
                .lore(item.lore)
                .hideAll()
            if (item.headTexture != null && item.material == Material.PLAYER_HEAD) {
                builder.skullTexture(item.headTexture)
            }
            guiInventory.setItem(item.slot, builder.build())
        }
    }

    /**
     * Fills the top row with a rainbow gradient.
     */
    private fun fillTopWithGradient() {
        for (col in 0 until 9) {
            val color = TOP_GRADIENT[col % TOP_GRADIENT.size]
            setItem(
                toSlot(0, col),
                ItemBuilder.glassPane(color).name(" ").build()
            )
        }
    }

    /**
     * Fills the left and right borders.
     */
    private fun fillSideBorders() {
        // Rows 1-4 (not 0 and 5)
        for (row in 1 until 5) {
            // Left border (column 0)
            setItem(
                toSlot(row, 0),
                ItemBuilder.glassPane(GlassColor.GRAY).name(" ").build()
            )
            // Right border (column 8)
            setItem(
                toSlot(row, 8),
                ItemBuilder.glassPane(GlassColor.GRAY).name(" ").build()
            )
        }
    }

    /**
     * Fills the rank display slots with rank items.
     */
    private fun fillRankSlots() {
        val startIndex = currentPage * RANKS_PER_PAGE
        val endIndex = (startIndex + RANKS_PER_PAGE).coerceAtMost(allRanks.size)
        val ranksForPage = if (startIndex < allRanks.size) {
            allRanks.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        // Place ranks in their slots
        ranksForPage.forEachIndexed { index, rank ->
            if (index < RANK_SLOTS.size) {
                val slot = RANK_SLOTS[index]
                val item = createRankItem(rank)
                setItem(slot, item) { _, _ ->
                    handleRankClick(rank)
                    true
                }
            }
        }

        // Fill remaining slots with glass panes
        val filledSlots = ranksForPage.size
        for (i in filledSlots until RANK_SLOTS.size) {
            setItem(
                RANK_SLOTS[i],
                ItemBuilder.glassPane(GlassColor.BLACK).name(" ").build()
            )
        }
    }

    /**
     * Creates an item representing a rank based on its status.
     * Uses configurable materials from GuiConfigManager.
     */
    private fun createRankItem(rank: RankDisplayData): ItemStack {
        val statusInfo = when (rank.status) {
            RankStatus.COMPLETED -> StatusInfo(
                material = guiConfig.ranksCompletedMaterial(),
                glowing = guiConfig.ranksCompletedGlow(),
                statusText = lang.get("gui.ranks.status.unlocked"),
                statusColor = "&a",
                borderColor = GlassColor.GREEN
            )
            RankStatus.CURRENT -> StatusInfo(
                material = guiConfig.ranksCurrentMaterial(),
                glowing = guiConfig.ranksCurrentGlow(),
                statusText = lang.get("gui.ranks.status.current"),
                statusColor = "&e",
                borderColor = GlassColor.YELLOW
            )
            RankStatus.NEXT -> StatusInfo(
                material = guiConfig.ranksAvailableMaterial(),
                glowing = guiConfig.ranksAvailableGlow(),
                statusText = lang.get("gui.ranks.status.available"),
                statusColor = "&a",
                borderColor = GlassColor.LIME
            )
            RankStatus.LOCKED -> StatusInfo(
                material = guiConfig.ranksLockedMaterial(),
                glowing = guiConfig.ranksLockedGlow(),
                statusText = lang.get("gui.ranks.status.locked"),
                statusColor = "&c",
                borderColor = GlassColor.RED
            )
        }

        val lore = buildRankLore(rank, statusInfo)

        // Apply glow: either from rank config OR from status (current rank glows)
        val shouldGlow = rank.glow || statusInfo.glowing

        // Resolve head texture: per-rank override > per-status default from gui config
        val headTexture = rank.headTexture ?: when (rank.status) {
            RankStatus.COMPLETED -> guiConfig.ranksCompletedHeadTexture()
            RankStatus.CURRENT -> guiConfig.ranksCurrentHeadTexture()
            RankStatus.NEXT -> guiConfig.ranksAvailableHeadTexture()
            RankStatus.LOCKED -> guiConfig.ranksLockedHeadTexture()
        }

        // Use PLAYER_HEAD if a texture is available, otherwise use the status material
        val itemMaterial = if (headTexture != null) Material.PLAYER_HEAD else statusInfo.material

        return ItemBuilder.of(itemMaterial)
            .name("${rank.displayColor}&l${rank.displayName}")
            .lore(lore)
            .apply {
                if (headTexture != null) skullTexture(headTexture)
                if (shouldGlow) glow()
            }
            .hideAll()
            .build()
    }

    /**
     * Builds the lore for a rank item using the template-based system.
     *
     * Template resolution order:
     * 1. Per-rank lore override from ranks.yml (rank.lore)
     * 2. Status-specific template from gui/ranks.yml (lore-templates.<status>)
     * 3. Hardcoded fallback in GuiConfigManager
     *
     * Supports single-line placeholders ({cost}, {status}, etc.) and
     * multi-line placeholders ({description}, {requirements}, {rewards})
     * that expand to 0+ lines or are removed when empty.
     */
    private fun buildRankLore(rank: RankDisplayData, statusInfo: StatusInfo): List<String> {
        // Determine status key for template lookup
        val statusKey = when (rank.status) {
            RankStatus.COMPLETED -> "completed"
            RankStatus.CURRENT -> "current"
            RankStatus.NEXT -> "available"
            RankStatus.LOCKED -> "locked"
        }

        // Pick template: per-rank override OR default from config
        val template = rank.loreOverride.ifEmpty {
            guiConfig.getLoreTemplate(statusKey)
        }

        // Calculate progress values
        val progress = if (rank.cost > 0) ((rank.playerProgress / rank.cost) * 100).coerceIn(0.0, 100.0) else 0.0
        val progressBar = createProgressBar(
            current = rank.playerProgress,
            max = rank.cost,
            length = 10,
            filledColor = statusInfo.statusColor,
            emptyColor = "&7"
        )

        // Get player balance safely
        val balance = try { vaultHook.getBalance(player) } catch (_: Exception) { 0.0 }

        // Action text based on status
        val actionText = when (rank.status) {
            RankStatus.NEXT -> lang.get("gui.ranks.action.click-rankup")
            RankStatus.CURRENT -> lang.get("gui.ranks.action.current-rank")
            RankStatus.COMPLETED -> lang.get("gui.ranks.action.already-achieved")
            RankStatus.LOCKED -> lang.get("gui.ranks.action.complete-previous")
        }

        // Playtime progress calculation
        val playerPlaytime = try {
            player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20L
        } catch (_: Exception) { 0L }
        val requiredPlaytime = rank.minPlaytime
        val playtimeProgress = if (requiredPlaytime > 0) {
            ((playerPlaytime.toDouble() / requiredPlaytime) * 100).coerceIn(0.0, 100.0)
        } else 100.0
        val playtimeBar = createProgressBar(
            current = playtimeProgress,
            max = 100.0,
            length = 10,
            filledColor = if (playtimeProgress >= 100.0) "&a" else "&e",
            emptyColor = "&7"
        )

        // Build single-line placeholders
        val placeholders = mapOf(
            "{rank_display}" to rank.displayName,
            "{rank_id}" to rank.id,
            "{prefix}" to rank.displayColor,
            "{cost}" to formatMoney(rank.cost),
            "{cost_raw}" to rank.cost.toString(),
            "{order}" to rank.order.toString(),
            "{status}" to lang.get("gui.ranks.item.status", mapOf("status" to statusInfo.statusText)),
            "{balance}" to formatMoney(balance),
            "{remaining}" to formatMoney((rank.cost - balance).coerceAtLeast(0.0)),
            "{progress}" to String.format("%.1f", progress),
            "{progress_bar}" to progressBar,
            "{player}" to player.name,
            "{action}" to actionText,
            "{status_color}" to statusInfo.statusColor,
            "{playtime}" to formatPlaytime(playerPlaytime),
            "{playtime_required}" to formatPlaytime(requiredPlaytime),
            "{playtime_progress}" to String.format("%.1f", playtimeProgress),
            "{playtime_progress_bar}" to playtimeBar
        )

        // Build multi-line placeholders

        // Description lines
        val descriptionLines = rank.description.map { ItemBuilder.colorize(it) }

        // Requirements lines
        val requirementsLines = mutableListOf<String>()
        if (rank.requirements.isNotEmpty() && rank.status != RankStatus.COMPLETED) {
            requirementsLines.add(lang.get("gui.ranks.item.requirements"))
            rank.requirements.take(3).forEach { req ->
                val checkmark = if (req.met) "&a\u2713" else "&c\u2717"
                requirementsLines.add(" $checkmark &7${req.description}")
            }
            if (rank.requirements.size > 3) {
                requirementsLines.add(lang.get("gui.ranks.item.requirements-more",
                    mapOf("count" to (rank.requirements.size - 3).toString())))
            }
        }

        // Rewards lines
        val rewardsLines = mutableListOf<String>()
        if (rank.rewards.isNotEmpty()) {
            rewardsLines.add(lang.get("gui.ranks.item.rewards"))
            rank.rewards.take(3).forEach { reward ->
                rewardsLines.add(" &8\u2022 &f$reward")
            }
            if (rank.rewards.size > 3) {
                rewardsLines.add(lang.get("gui.ranks.item.rewards-more",
                    mapOf("count" to (rank.rewards.size - 3).toString())))
            }
        }

        val multiLine = mapOf(
            "{description}" to descriptionLines,
            "{requirements}" to requirementsLines,
            "{rewards}" to rewardsLines
        )

        // Process template and return
        return LoreTemplateProcessor.processTemplate(template, placeholders, multiLine)
    }

    /**
     * Handles a click on a rank item.
     */
    private fun handleRankClick(rank: RankDisplayData) {
        when (rank.status) {
            RankStatus.NEXT -> {
                // Open confirmation GUI
                SoundUtil.playSuccess(player)
                guiManager.openRankupConfirmGui(player, rank.id)
            }
            RankStatus.CURRENT -> {
                SoundUtil.playNotification(player)
                player.sendMessage(ItemBuilder.colorize(lang.get("gui.ranks.click.current")))
            }
            RankStatus.COMPLETED -> {
                SoundUtil.playSuccess(player)
                player.sendMessage(ItemBuilder.colorize(lang.get("gui.ranks.click.completed")))
            }
            RankStatus.LOCKED -> {
                SoundUtil.playDenied(player)
                player.sendMessage(ItemBuilder.colorize(lang.get("gui.ranks.click.locked")))
            }
        }
    }

    /**
     * Sets up the navigation bar (row 5).
     */
    private fun setupNavigationBar() {
        val bottomRow = 5

        // Fill with dark glass first
        for (col in 0 until 9) {
            setItem(
                toSlot(bottomRow, col),
                ItemBuilder.glassPane(GlassColor.BLACK).name(" ").build()
            )
        }

        // Previous page (slot 45 - col 0)
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

        // Player info (slot 47 - col 2)
        setItem(
            toSlot(bottomRow, 2),
            createPlayerInfoItem()
        )

        // Page indicator (slot 49 - col 4)
        val pagePlaceholders = mapOf(
            "current" to (currentPage + 1).toString(),
            "total" to totalPages.toString(),
            "total_ranks" to allRanks.size.toString()
        )
        setItem(
            toSlot(bottomRow, 4),
            ItemBuilder.of(Material.BOOK)
                .name(lang.get("gui.ranks.page-title", pagePlaceholders))
                .lore(lang.getList("gui.ranks.page-lore", pagePlaceholders))
                .build()
        )

        // Refresh button (slot 51 - col 6)
        setItem(
            toSlot(bottomRow, 6),
            ItemBuilder.of(Material.SUNFLOWER)
                .name(lang.get("gui.ranks.refresh-title"))
                .lore(lang.getList("gui.ranks.refresh-lore"))
                .build()
        ) { _, _ ->
            SoundUtil.playClick(player)
            build()
            true
        }

        // Next page (slot 53 - col 8)
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

        // Close button (slot 52 - col 7)
        setItem(
            toSlot(bottomRow, 7),
            createNavigationButton(NavigationType.CLOSE)
        ) { _, _ ->
            close()
            true
        }
    }

    /**
     * Creates the player info item showing current rank progress.
     */
    private fun createPlayerInfoItem(): ItemStack {
        val currentRank = getCurrentPlayerRank()
        val nextRank = getNextPlayerRank()

        val currentRankDisplay = "${currentRank?.displayColor ?: "&7"}${currentRank?.displayName ?: "None"}"

        val lore = mutableListOf<String>()
        lore.add(dividerLine())
        lore.add("")
        lore.add(lang.get("gui.ranks.player-info.current-rank", mapOf("rank" to currentRankDisplay)))

        if (nextRank != null) {
            val nextRankDisplay = "${nextRank.displayColor}${nextRank.displayName}"
            lore.add(lang.get("gui.ranks.player-info.next-rank", mapOf("rank" to nextRankDisplay)))
            lore.add("")
            lore.add(lang.get("gui.ranks.player-info.progress-title"))
            val progressBar = createProgressBarWithPercent(
                nextRank.playerProgress,
                nextRank.cost,
                10,
                "&a"
            )
            lore.add(progressBar)
            lore.add("")
            lore.add(lang.get("gui.ranks.player-info.balance", mapOf("balance" to formatMoney(getPlayerBalance()))))
            lore.add(lang.get("gui.ranks.player-info.need", mapOf("amount" to formatMoney((nextRank.cost - nextRank.playerProgress).coerceAtLeast(0.0)))))
        } else {
            lore.add("")
            lore.add(lang.get("gui.ranks.player-info.max-rank"))
        }

        lore.add("")
        lore.add(dividerLine())

        return ItemBuilder.skull()
            .skullOwner(player)
            .name("&6&l${player.name}")
            .lore(lore)
            .build()
    }

    /**
     * Loads all ranks for display from the RankCache.
     * Calculates the status of each rank based on the player's current rank.
     */
    private fun loadRanks(): List<RankDisplayData> {
        val allRanks = rankCache.getAllSorted()
        if (allRanks.isEmpty()) {
            return emptyList()
        }

        // Get player data to determine current rank
        val playerData = playerCache.get(player.uniqueId)
        val currentRankId = playerData?.currentRankId ?: rankCache.getDefault()?.id ?: allRanks.first().id
        val currentRank = rankCache.getById(currentRankId)
        val currentRankOrder = currentRank?.order ?: 0

        // Get player's balance for progress calculation
        val playerBalance = getPlayerBalance()

        // Get next rank for progress calculation
        val nextRank = rankCache.getNextRank(currentRankId)

        return allRanks.map { rank ->
            val status = calculateRankStatus(rank, currentRankId, currentRankOrder)

            // Calculate player progress towards this rank
            val playerProgress = when (status) {
                RankStatus.COMPLETED -> rank.cost
                RankStatus.CURRENT -> rank.cost
                RankStatus.NEXT -> playerBalance.coerceAtMost(rank.cost)
                RankStatus.LOCKED -> 0.0
            }

            // Build requirements list
            val requirements = buildRequirementsList(rank, status, playerBalance)

            // Build rewards list from commands (simplified display)
            val rewards = buildRewardsList(rank)

            RankDisplayData(
                id = rank.id,
                displayName = rank.displayName,
                displayColor = extractColorFromPrefix(rank.prefix),
                order = rank.order,
                cost = rank.cost,
                playerProgress = playerProgress,
                status = status,
                requirements = requirements,
                rewards = rewards,
                description = rank.description,
                glow = rank.glow,
                loreOverride = rank.lore,
                minPlaytime = rank.requirements.minPlaytime,
                headTexture = rank.headTexture
            )
        }
    }

    /**
     * Calculate the status of a rank based on player's current rank.
     */
    private fun calculateRankStatus(rank: RankData, currentRankId: String, currentRankOrder: Int): RankStatus {
        return when {
            rank.id == currentRankId -> RankStatus.CURRENT
            rank.order < currentRankOrder -> RankStatus.COMPLETED
            rank.order == currentRankOrder + 1 -> RankStatus.NEXT
            else -> RankStatus.LOCKED
        }
    }

    /**
     * Build requirements list for display.
     */
    private fun buildRequirementsList(rank: RankData, status: RankStatus, playerBalance: Double): List<RequirementData> {
        if (status == RankStatus.COMPLETED || status == RankStatus.CURRENT) {
            return emptyList()
        }

        val requirements = mutableListOf<RequirementData>()

        // Money requirement
        val hasMoney = playerBalance >= rank.cost
        requirements.add(RequirementData("Have ${formatMoney(rank.cost)}", hasMoney))

        // Playtime requirement
        if (rank.requirements.minPlaytime > 0) {
            val hours = rank.requirements.minPlaytime / 3600
            requirements.add(RequirementData("Play for $hours hours", false)) // Would need actual playtime check
        }

        return requirements
    }

    /**
     * Build rewards list from rank commands for display.
     */
    private fun buildRewardsList(rank: RankData): List<String> {
        val rewards = mutableListOf<String>()

        // Add commands as rewards (simplified display)
        if (rank.commands.isNotEmpty()) {
            rewards.add(lang.get("gui.ranks.item.commands-reward", mapOf("count" to rank.commands.size.toString())))
        }

        // Add broadcast message hint if present
        if (rank.broadcastMessage != null) {
            rewards.add(lang.get("gui.ranks.item.broadcast-reward"))
        }

        return rewards
    }

    /**
     * Extract color code from rank prefix.
     */
    private fun formatPlaytime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun extractColorFromPrefix(prefix: String): String {
        // Try to extract color codes from prefix (e.g., "&a[Member]" -> "&a")
        val colorPattern = Regex("(&[0-9a-fklmnor])+")
        val match = colorPattern.find(prefix)
        return match?.value ?: "&7"
    }

    /**
     * Gets the player's current rank.
     */
    private fun getCurrentPlayerRank(): RankDisplayData? {
        return allRanks.find { it.status == RankStatus.CURRENT }
    }

    /**
     * Gets the player's next available rank.
     */
    private fun getNextPlayerRank(): RankDisplayData? {
        return allRanks.find { it.status == RankStatus.NEXT }
    }

    /**
     * Gets the player's current balance from Vault economy.
     */
    private fun getPlayerBalance(): Double {
        return vaultHook.getBalance(player)
    }
}

/**
 * Data class for rank display information.
 * Includes per-rank customization fields for GUI rendering.
 */
data class RankDisplayData(
    val id: String,
    val displayName: String,
    val displayColor: String,
    val order: Int,
    val cost: Double,
    val playerProgress: Double,
    val status: RankStatus,
    val requirements: List<RequirementData>,
    val rewards: List<String>,
    /** Custom description lines from ranks.yml */
    val description: List<String> = emptyList(),
    /** Whether to apply enchantment glow */
    val glow: Boolean = false,
    /** Per-rank lore template override from ranks.yml */
    val loreOverride: List<String> = emptyList(),
    /** Min playtime requirement in seconds (0 = no requirement) */
    val minPlaytime: Long = 0L,
    /** Base64 skull texture for custom player head in GUI */
    val headTexture: String? = null
)

/**
 * Data class for requirement information.
 */
data class RequirementData(
    val description: String,
    val met: Boolean
)

/**
 * Enum representing the status of a rank for a player.
 */
enum class RankStatus {
    /** Rank has been achieved */
    COMPLETED,
    /** Player's current rank */
    CURRENT,
    /** Next available rank to achieve */
    NEXT,
    /** Rank is locked (future ranks) */
    LOCKED
}

/**
 * Helper class for status-specific display information.
 */
private data class StatusInfo(
    val material: Material,
    val glowing: Boolean,
    val statusText: String,
    val statusColor: String,
    val borderColor: GlassColor
)
