package com.srcodex.shinobustore.service

import com.srcodex.shinobustore.ShinobuStore
import com.srcodex.shinobustore.menu.CategoryMenu
import com.srcodex.shinobustore.util.RateLimiter
import com.srcodex.shinobustore.util.TimeUtil
import org.bukkit.entity.Player

/**
 * Shared store actions used by multiple commands.
 * Eliminates code duplication between StoreCommand and ShinobuStoreCommand.
 */
class StoreActions(private val plugin: ShinobuStore) {

    /**
     * Opens the store for a player with all necessary checks.
     * @return true if store was opened, false if blocked
     */
    fun openStore(player: Player): Boolean {
        // Rate limit check
        if (plugin.configManager.rateLimitEnabled && !player.hasPermission("shinobustore.bypass.cooldown")) {
            when (val result = plugin.rateLimiter.check(
                player.uniqueId,
                RateLimiter.ACTION_STORE_OPEN,
                plugin.configManager.storeOpenCooldown
            )) {
                is RateLimiter.RateLimitResult.Cooldown -> {
                    player.sendMessage(plugin.configManager.getMessage("rate-limit.cooldown",
                        mapOf("time" to TimeUtil.formatMillis(result.remainingMillis))))
                    return false
                }
                is RateLimiter.RateLimitResult.TooManyRequests -> {
                    player.sendMessage(plugin.configManager.getMessage("rate-limit.too-many-requests"))
                    return false
                }
                is RateLimiter.RateLimitResult.Banned -> {
                    player.sendMessage(plugin.configManager.getMessage("rate-limit.temporarily-blocked",
                        mapOf("time" to TimeUtil.formatMillis(result.remainingMillis))))
                    return false
                }
                is RateLimiter.RateLimitResult.Allowed -> { /* Continue */ }
            }
        }

        // Pending transaction check
        if (plugin.transactionManager.hasPending(player.uniqueId)) {
            player.sendMessage(plugin.configManager.getMessage("store.pending-exists"))
            player.sendMessage(plugin.configManager.getMessage("store.pending-exists-hint"))
            return false
        }

        // PayPal config check
        if (!plugin.configManager.isPayPalConfigured()) {
            player.sendMessage(plugin.configManager.getMessage("errors.paypal-not-configured"))
            return false
        }

        // Open store
        CategoryMenu(plugin, player).open()
        return true
    }

    /**
     * Cancels all pending transactions for a player.
     * @return number of cancelled transactions
     */
    fun cancelPending(player: Player): Int {
        if (!plugin.transactionManager.hasPending(player.uniqueId)) {
            player.sendMessage(plugin.configManager.getMessage("cancel.no-pending"))
            return 0
        }

        val removed = plugin.transactionManager.removePendingForPlayer(player.uniqueId)
        if (removed.isNotEmpty()) {
            player.sendMessage(plugin.configManager.getMessage("cancel.success"))
            plugin.logger.info("${player.name} cancelled ${removed.size} pending transaction(s)")
        } else {
            player.sendMessage(plugin.configManager.getMessage("cancel.no-pending"))
        }
        return removed.size
    }

    /**
     * Shows transaction timer for a player.
     */
    fun showTimer(player: Player) {
        val pending = plugin.transactionManager.getPendingForPlayer(player.uniqueId)

        if (pending.isEmpty()) {
            player.sendMessage(plugin.configManager.getMessage("timer.no-pending"))
            return
        }

        player.sendMessage(plugin.configManager.getMessage("timer.header"))
        for (tx in pending) {
            if (tx.isExpired()) {
                player.sendMessage(plugin.configManager.getMessage("timer.expired",
                    mapOf("item" to tx.itemDisplay)))
            } else {
                player.sendMessage(plugin.configManager.getMessage("timer.remaining",
                    mapOf("item" to tx.itemDisplay, "time" to TimeUtil.formatMillis(tx.getTimeRemaining()))))
            }
        }
        player.sendMessage(plugin.configManager.getMessage("timer.footer"))
    }
}
