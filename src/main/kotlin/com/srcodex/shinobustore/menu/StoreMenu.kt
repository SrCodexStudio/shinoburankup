package com.srcodex.shinobustore.menu

import com.srcodex.shinobustore.ShinobuStore
import com.srcodex.shinobustore.paypal.PayPalService
import com.srcodex.shinobustore.transaction.StoreItem
import com.srcodex.shinobustore.util.ClickableMessage
import com.srcodex.shinobustore.util.ColorUtil
import com.srcodex.shinobustore.util.RateLimiter
import com.srcodex.shinobustore.util.TimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent

/**
 * Menu displaying items in a specific category.
 */
class StoreMenu(
    plugin: ShinobuStore,
    player: Player,
    private val categoryId: String,
    categoryDisplay: String,
    rows: Int
) : BaseMenu(
    plugin,
    player,
    categoryDisplay,
    rows
) {

    private val categoryItems: List<StoreItem> = plugin.configManager.getItemsForCategory(categoryId)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Item ID mapped to slot for quick lookup
    private val slotToItem = mutableMapOf<Int, StoreItem>()

    override fun build() {
        // Back button in slot 0
        val backItem = createItem(
            Material.ARROW,
            "&c&lBack",
            listOf("&7Click to go back to categories")
        )
        setItem(0, backItem) {
            goBack()
        }

        // Add items for this category
        for (item in categoryItems) {
            // Skip invalid positions
            if (item.position < 0 || item.position >= menuInventory.size) {
                plugin.logger.warning("Invalid position ${item.position} for item ${item.id}")
                continue
            }

            val storeItem = createItem(
                materialString = item.material,
                name = item.display,
                lore = item.lore
            )

            slotToItem[item.position] = item
            setItem(item.position, storeItem)
        }
    }

    override fun onClick(event: InventoryClickEvent) {
        // Cancel event to prevent item extraction
        event.isCancelled = true

        val slot = event.rawSlot

        // Check if slot is in our menu
        if (slot < 0 || slot >= menuInventory.size) {
            return
        }

        // Check if interactive slot
        if (!interactiveSlots.contains(slot)) {
            return
        }

        // Play click sound
        playClickSound()

        // Process click handlers first (back button)
        if (processClick(slot)) {
            return
        }

        // Check if it's an item slot
        val item = slotToItem[slot] ?: return

        // Handle purchase
        handlePurchase(item)
    }

    /**
     * Handles a purchase request by validating preconditions
     * and opening a confirmation menu if all checks pass.
     */
    private fun handlePurchase(item: StoreItem) {
        // Close inventory first
        player.closeInventory()

        // Check rate limit
        if (plugin.configManager.rateLimitEnabled) {
            if (!player.hasPermission("shinobustore.bypass.cooldown")) {
                when (val result = plugin.rateLimiter.check(
                    player.uniqueId,
                    RateLimiter.ACTION_PURCHASE,
                    plugin.configManager.purchaseCooldown
                )) {
                    is RateLimiter.RateLimitResult.Cooldown -> {
                        player.sendMessage(plugin.configManager.getMessage(
                            "rate-limit.cooldown",
                            mapOf("time" to TimeUtil.formatMillis(result.remainingMillis))
                        ))
                        playErrorSound()
                        return
                    }
                    is RateLimiter.RateLimitResult.TooManyRequests -> {
                        player.sendMessage(plugin.configManager.getMessage("rate-limit.too-many-requests"))
                        playErrorSound()
                        return
                    }
                    is RateLimiter.RateLimitResult.Banned -> {
                        player.sendMessage(plugin.configManager.getMessage(
                            "rate-limit.temporarily-blocked",
                            mapOf("time" to TimeUtil.formatMillis(result.remainingMillis))
                        ))
                        playErrorSound()
                        return
                    }
                    is RateLimiter.RateLimitResult.Allowed -> { /* Continue */ }
                }
            }
        }

        // Check for existing pending transaction
        val maxPending = plugin.configManager.maxPendingPerPlayer
        if (plugin.transactionManager.getPendingCount(player.uniqueId) >= maxPending) {
            player.sendMessage(plugin.configManager.getMessage("store.pending-exists"))
            player.sendMessage(plugin.configManager.getMessage("store.pending-exists-hint"))
            playErrorSound()
            return
        }

        // Check PayPal configuration
        if (!plugin.configManager.isPayPalConfigured()) {
            player.sendMessage(plugin.configManager.getMessage("errors.paypal-not-configured"))
            playErrorSound()
            return
        }

        // All checks passed -- open confirmation menu
        ConfirmMenu(plugin, player, item, categoryId).open()
    }

    /**
     * Goes back to the category menu.
     */
    private fun goBack() {
        player.closeInventory()
        CategoryMenu(plugin, player).open()
    }

    /**
     * Called when menu is closed.
     */
    override fun onClose(event: org.bukkit.event.inventory.InventoryCloseEvent) {
        super.onClose(event)
        scope.cancel()
    }

    companion object {

        /**
         * Executes the actual PayPal purchase flow.
         * Called from [ConfirmMenu] after the player confirms the purchase.
         *
         * This is a static entry point so the purchase can proceed
         * even after the StoreMenu inventory has been closed.
         */
        fun executePurchase(plugin: ShinobuStore, player: Player, item: StoreItem) {
            player.sendMessage(plugin.configManager.getMessage("payment.creating-order"))

            // Play purchase start sound
            try {
                val sound = org.bukkit.Sound.valueOf(plugin.configManager.soundPurchaseStart)
                player.playSound(player.location, sound, 1.0f, 1.0f)
            } catch (_: Exception) {}

            // Create a dedicated scope for this purchase -- cancelled after result is handled
            val purchaseScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            purchaseScope.launch {
                val result = plugin.paypalService.createPurchase(player, item)

                // Switch back to main thread via scheduler (never use Dispatchers.Main in Bukkit)
                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (player.isOnline) {
                        handlePurchaseResult(plugin, player, result, item)
                    }
                    purchaseScope.cancel()
                })
            }
        }

        /**
         * Handles the PayPal purchase result on the main thread.
         */
        private fun handlePurchaseResult(
            plugin: ShinobuStore,
            player: Player,
            result: PayPalService.PurchaseResult,
            item: StoreItem
        ) {
            when (result) {
                is PayPalService.PurchaseResult.Success -> {
                    val transaction = result.transaction

                    // Send success messages
                    player.sendMessage(plugin.configManager.getMessage("payment.order-created"))
                    player.sendMessage(plugin.configManager.getMessage(
                        "payment.cost-info",
                        mapOf(
                            "cost" to plugin.paypalService.formatCost(transaction.cost),
                            "total" to plugin.paypalService.formatTotal(transaction.total)
                        )
                    ))

                    // Send clickable buttons
                    val linkButton = plugin.configManager.getMessage("payment.link-button")
                    val linkHover = plugin.configManager.getMessage("payment.link-hover")
                    val cancelButton = plugin.configManager.getMessage("payment.cancel-button")
                    val cancelHover = plugin.configManager.getMessage("payment.cancel-hover")
                    val timerButton = plugin.configManager.getMessage("payment.timer-button")
                    val timerHover = plugin.configManager.getMessage("payment.timer-hover")

                    ClickableMessage.create(linkButton)
                        .hover(linkHover)
                        .link(transaction.checkoutUrl)
                        .add(" ")
                        .add(cancelButton)
                        .hover(cancelHover)
                        .command("/cancelitem")
                        .add(" ")
                        .add(timerButton)
                        .hover(timerHover)
                        .command("/timer")
                        .send(player)
                }

                is PayPalService.PurchaseResult.Error -> {
                    player.sendMessage(plugin.configManager.getMessage("errors.paypal-error"))
                    if (plugin.configManager.debugEnabled) {
                        player.sendMessage(ColorUtil.colorize("&cDebug: ${result.message}"))
                    }

                    try {
                        val sound = org.bukkit.Sound.valueOf(plugin.configManager.soundError)
                        player.playSound(player.location, sound, 1.0f, 1.0f)
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
