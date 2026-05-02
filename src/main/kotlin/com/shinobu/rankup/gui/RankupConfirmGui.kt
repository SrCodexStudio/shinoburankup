package com.shinobu.rankup.gui

import com.shinobu.rankup.ShinobuRankup
import com.shinobu.rankup.config.GuiConfigManager
import com.shinobu.rankup.config.LanguageManager
import com.shinobu.rankup.data.RankData
import com.shinobu.rankup.service.RankupResult
import com.shinobu.rankup.util.GlassColor
import com.shinobu.rankup.util.ItemBuilder
import com.shinobu.rankup.util.SoundUtil
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * Confirmation GUI displayed before ranking up.
 *
 * Layout (27 slots - 3 rows):
 * - Row 0: Decorative border with current -> next rank display
 * - Row 1: Cost display, confirm (green), cancel (red) buttons
 * - Row 2: Rewards/permissions preview
 *
 * Features:
 * - Clear cost display
 * - Confirm/Cancel buttons with visual distinction
 * - Preview of what the player will receive
 * - Balance check with visual feedback
 * - Full integration with RankupService
 */
class RankupConfirmGui(
    player: Player,
    private val targetRankId: String
) : BaseGui(player, ShinobuRankup.getInstance().languageManager.get("gui.confirm.title"), 5) {

    private val plugin = ShinobuRankup.getInstance()
    private val confirming = java.util.concurrent.atomic.AtomicBoolean(false)

    private val lang: LanguageManager
        get() = plugin.languageManager

    private val guiConfig: GuiConfigManager
        get() = plugin.guiConfigManager

    /**
     * The rank data for the target rank loaded from RankCache.
     */
    private val targetRank: RankData? by lazy {
        plugin.rankCache.getById(targetRankId)
    }

    /**
     * The player's current rank data loaded from services.
     */
    private val currentRank: RankData? by lazy {
        loadCurrentRankFromService()
    }

    /**
     * The player's current balance from Vault.
     */
    private val playerBalance: Double by lazy {
        plugin.hookManager.vault.getBalance(player)
    }

    override fun build() {
        guiInventory.clear()
        clickHandlers.clear()

        val rank = targetRank
        if (rank == null) {
            buildErrorDisplay()
            return
        }

        // Fill background
        fillBackground()

        // Build the main content
        buildRankTransition()
        buildCostDisplay()
        buildConfirmButton()
        buildCancelButton()
        buildRewardsPreview()
    }

    /**
     * Loads the player's current rank from the service.
     */
    private fun loadCurrentRankFromService(): RankData? {
        val playerData = plugin.playerCache.get(player.uniqueId) ?: return null
        return plugin.rankCache.getById(playerData.currentRankId)
    }

    /**
     * Fills the background with a gradient pattern.
     * Layout for 5 rows (45 slots):
     * Row 0: Top border
     * Row 1: Rank display (current -> arrow -> next)
     * Row 2: Cost info center
     * Row 3: Confirm / Rewards / Cancel buttons
     * Row 4: Bottom border
     */
    private fun fillBackground() {
        // Top row (row 0) - border
        for (col in 0 until 9) {
            setItem(
                toSlot(0, col),
                ItemBuilder.glassPane(GlassColor.GRAY).name(" ").build()
            )
        }

        // Row 1 - sides only (content slots: 11, 13, 15)
        setItem(toSlot(1, 0), ItemBuilder.glassPane(GlassColor.GRAY).name(" ").build())
        setItem(toSlot(1, 8), ItemBuilder.glassPane(GlassColor.GRAY).name(" ").build())

        // Row 2 - sides only (content slot: 22)
        setItem(toSlot(2, 0), ItemBuilder.glassPane(GlassColor.GRAY).name(" ").build())
        setItem(toSlot(2, 8), ItemBuilder.glassPane(GlassColor.GRAY).name(" ").build())

        // Row 3 - sides only (content slots: 29, 31, 33)
        setItem(toSlot(3, 0), ItemBuilder.glassPane(GlassColor.GRAY).name(" ").build())
        setItem(toSlot(3, 8), ItemBuilder.glassPane(GlassColor.GRAY).name(" ").build())

        // Bottom row (row 4) - border
        for (col in 0 until 9) {
            setItem(
                toSlot(4, col),
                ItemBuilder.glassPane(GlassColor.GRAY).name(" ").build()
            )
        }

        // Fill empty slots in middle rows with lighter glass
        val emptySlots = listOf(
            10, 12, 14, 16,  // Row 1 fillers
            19, 20, 21, 23, 24, 25,  // Row 2 fillers
            28, 30, 32, 34  // Row 3 fillers
        )
        for (slot in emptySlots) {
            setItem(slot, ItemBuilder.glassPane(GlassColor.LIGHT_GRAY).name(" ").build())
        }
    }

    /**
     * Builds the current -> next rank transition display.
     * Row 1: [border] [filler] [CURRENT] [filler] [ARROW] [filler] [NEXT] [filler] [border]
     */
    private fun buildRankTransition() {
        val rank = targetRank ?: return
        val current = currentRank

        val currentRankDisplay = "${current?.prefix ?: "&7"}${current?.displayName ?: "None"}"
        val nextRankDisplay = "${rank.prefix}${rank.displayName}"

        // Current rank (row 1, col 2 = slot 11)
        setItem(
            toSlot(1, 2),
            ItemBuilder.of(Material.BOOK)
                .name("${current?.prefix ?: "&7"}&l${lang.get("gui.confirm.current-rank")}")
                .lore(lang.getList("gui.confirm.current-rank-lore", mapOf("rank_display" to currentRankDisplay)))
                .build()
        )

        // Arrow indicator (row 1, col 4 = slot 13)
        setItem(
            toSlot(1, 4),
            ItemBuilder.of(Material.ARROW)
                .name(lang.get("gui.confirm.arrow-title"))
                .lore(lang.getList("gui.confirm.arrow-lore", mapOf(
                    "current_rank" to "${current?.prefix ?: "&7"}${current?.displayName ?: "None"}",
                    "next_rank" to "${rank.prefix}${rank.displayName}"
                )))
                .glow()
                .build()
        )

        // Target rank (row 1, col 6 = slot 15)
        setItem(
            toSlot(1, 6),
            ItemBuilder.of(Material.ENCHANTED_BOOK)
                .name("${rank.prefix}&l${lang.get("gui.confirm.next-rank")}")
                .lore(lang.getList("gui.confirm.next-rank-lore", mapOf("rank_display" to nextRankDisplay)))
                .glow()
                .build()
        )
    }

    /**
     * Builds the cost display in the center.
     * Uses configurable materials from GuiConfigManager.
     */
    private fun buildCostDisplay() {
        val rank = targetRank ?: return

        val canAfford = playerBalance >= rank.cost
        val balanceColor = if (canAfford) "&a" else "&c"
        val costStatus = if (canAfford) lang.get("gui.confirm.cost-sufficient") else lang.get("gui.confirm.cost-insufficient")

        val lore = mutableListOf<String>()
        lore.add(dividerLine())
        lore.add("")
        lore.add(lang.get("gui.confirm.cost-balance", mapOf("color" to balanceColor, "balance" to formatMoney(playerBalance))))
        lore.add(lang.get("gui.confirm.cost-amount", mapOf("cost" to formatMoney(rank.cost))))
        lore.add("")
        if (canAfford) {
            lore.add(lang.get("gui.confirm.cost-after", mapOf("remaining" to formatMoney(playerBalance - rank.cost))))
        } else {
            lore.add(lang.get("gui.confirm.cost-need", mapOf("needed" to formatMoney(rank.cost - playerBalance))))
        }
        lore.add("")
        lore.add(costStatus)
        lore.add("")
        lore.add(dividerLine())

        // Use configurable materials
        val costMaterial = if (canAfford) guiConfig.confirmCostCanAffordMaterial() else guiConfig.confirmCostCannotAffordMaterial()

        setItem(
            guiConfig.confirmCostInfoSlot(),
            ItemBuilder.of(costMaterial)
                .name(lang.get("gui.confirm.cost-title", mapOf("cost" to formatMoney(rank.cost))))
                .lore(lore)
                .apply { if (canAfford) glow() }
                .build()
        )
    }

    /**
     * Builds the confirm button.
     * Uses configurable materials from GuiConfigManager.
     */
    private fun buildConfirmButton() {
        val rank = targetRank ?: return
        val canAfford = playerBalance >= rank.cost

        val rankDisplay = "${rank.prefix}&l${rank.displayName}"

        // Use configurable materials
        val confirmMaterial = if (canAfford) guiConfig.confirmButtonCanAffordMaterial() else guiConfig.confirmButtonCannotAffordMaterial()

        val confirmItem = if (canAfford) {
            ItemBuilder.of(confirmMaterial)
                .name(lang.get("gui.confirm.confirm-button"))
                .lore(lang.getList("gui.confirm.confirm-lore", mapOf(
                    "rank_display" to rankDisplay,
                    "cost" to formatMoney(rank.cost)
                )))
                .glow()
                .build()
        } else {
            ItemBuilder.of(confirmMaterial)
                .name(lang.get("gui.confirm.cannot-afford-button"))
                .lore(lang.getList("gui.confirm.cannot-afford-lore", mapOf(
                    "balance" to formatMoney(playerBalance),
                    "cost" to formatMoney(rank.cost),
                    "needed" to formatMoney(rank.cost - playerBalance)
                )))
                .build()
        }

        val confirmSlot = guiConfig.confirmButtonSlot()
        setItem(confirmSlot, confirmItem) { _, _ ->
            if (canAfford) {
                handleConfirm()
            } else {
                SoundUtil.playDenied(player)
                player.sendMessage(ItemBuilder.colorize(lang.get("gui.confirm.no-money")))
            }
            true
        }
    }

    /**
     * Builds the cancel button.
     * Uses configurable materials from GuiConfigManager.
     */
    private fun buildCancelButton() {
        val cancelItem = ItemBuilder.of(guiConfig.confirmCancelMaterial())
            .name(lang.get("gui.confirm.cancel-button"))
            .lore(lang.getList("gui.confirm.cancel-lore"))
            .build()

        val cancelSlot = guiConfig.confirmCancelSlot()
        setItem(cancelSlot, cancelItem) { _, _ ->
            handleCancel()
            true
        }
    }

    /**
     * Builds the rewards preview section.
     */
    private fun buildRewardsPreview() {
        val rank = targetRank ?: return

        val lore = mutableListOf<String>()
        lore.add(dividerLine("&6"))
        lore.add("")
        lore.add(lang.get("gui.confirm.rewards-header"))
        lore.add("")

        // Commands as rewards preview
        if (rank.commands.isNotEmpty()) {
            lore.add(lang.get("gui.confirm.rewards-commands"))
            rank.commands.take(3).forEach { cmd ->
                // Sanitize command display (hide sensitive parts)
                val displayCmd = cmd.replace("{player}", player.name)
                    .replace("%player%", player.name)
                lore.add(" &8• &a$displayCmd")
            }
            if (rank.commands.size > 3) {
                lore.add(lang.get("gui.confirm.rewards-more", mapOf("count" to (rank.commands.size - 3).toString())))
            }
            lore.add("")
        }

        // Broadcast message preview (if configured)
        rank.broadcastMessage?.takeIf { it.isNotEmpty() }?.let { broadcastMsg ->
            lore.add(lang.get("gui.confirm.rewards-broadcast"))
            lore.add(" &8• &b${broadcastMsg.replace("{player}", player.name)}")
            lore.add("")
        }

        lore.add(dividerLine("&6"))

        setItem(
            toSlot(2, 4),
            ItemBuilder.of(Material.CHEST)
                .name(lang.get("gui.confirm.rewards-title"))
                .lore(lore)
                .glow()
                .build()
        )
    }

    /**
     * Builds an error display when rank data is not found.
     */
    private fun buildErrorDisplay() {
        fillSlots(0 until guiInventory.size, ItemBuilder.glassPane(GlassColor.RED).name(" ").build())

        setItem(
            13,
            ItemBuilder.of(Material.BARRIER)
                .name(lang.get("gui.confirm.error-title"))
                .lore(lang.getList("gui.confirm.error-lore", mapOf("rank_id" to targetRankId)))
                .build()
        ) { _, _ ->
            close()
            true
        }
    }

    /**
     * Handles the confirm action using the real RankupService.
     */
    private fun handleConfirm() {
        if (!confirming.compareAndSet(false, true)) return
        val rank = targetRank ?: return

        // Double-check balance using fresh data
        val currentBalance = plugin.hookManager.vault.getBalance(player)
        if (currentBalance < rank.cost) {
            SoundUtil.playDenied(player)
            player.sendMessage(ItemBuilder.colorize(lang.get("gui.confirm.no-money")))
            return
        }

        // Close GUI first to prevent any exploits
        close()

        // Process the rankup using the coroutine scope
        plugin.coroutineScope.launchAsync {
            processRankupAsync()
        }
    }

    /**
     * Processes the actual rankup using RankupService.
     */
    private suspend fun processRankupAsync() {
        val result = plugin.rankupService.performRankup(player)

        when (result) {
            is RankupResult.Success -> {
                val rankDisplay = result.newRank.displayName

                // Play success sounds
                plugin.server.scheduler.runTask(plugin, Runnable {
                    SoundUtil.playRankupSuccess(player)

                    // Send success message
                    player.sendMessage(ItemBuilder.colorize(""))
                    player.sendMessage(ItemBuilder.colorize(lang.get("gui.confirm.success-divider")))
                    player.sendMessage(ItemBuilder.colorize(""))
                    player.sendMessage(ItemBuilder.colorize("  ${lang.get("gui.confirm.success-title")}"))
                    player.sendMessage(ItemBuilder.colorize(""))
                    player.sendMessage(ItemBuilder.colorize("  ${lang.get("gui.confirm.success-chat-message", mapOf("rank" to rankDisplay))}"))
                    player.sendMessage(ItemBuilder.colorize("  ${lang.get("gui.confirm.success-chat-cost", mapOf("cost" to formatMoney(result.cost)))}"))
                    player.sendMessage(ItemBuilder.colorize(""))
                    player.sendMessage(ItemBuilder.colorize(lang.get("gui.confirm.success-divider")))
                    player.sendMessage(ItemBuilder.colorize(""))
                })
            }

            is RankupResult.Failure -> {
                plugin.server.scheduler.runTask(plugin, Runnable {
                    SoundUtil.playDenied(player)

                    val errorMessage = when (result) {
                        is RankupResult.Failure.InsufficientFunds -> {
                            lang.get("rankup.fail-no-money", mapOf(
                                "cost" to formatMoney(result.required),
                                "balance" to formatMoney(result.current)
                            ))
                        }
                        is RankupResult.Failure.AlreadyMaxRank -> {
                            lang.get("rankup.fail-max-rank")
                        }
                        is RankupResult.Failure.EconomyError -> {
                            "&cEconomy error: ${result.message}"
                        }
                        is RankupResult.Failure.CancelledByEvent -> {
                            "&cRankup cancelled: ${result.reason}"
                        }
                        else -> {
                            lang.get("rankup.error")
                        }
                    }

                    player.sendMessage(ItemBuilder.colorize(errorMessage))
                })
            }
        }
    }

    /**
     * Handles the cancel action.
     */
    private fun handleCancel() {
        SoundUtil.playClick(player)
        guiManager.openRanksGui(player)
    }
}
