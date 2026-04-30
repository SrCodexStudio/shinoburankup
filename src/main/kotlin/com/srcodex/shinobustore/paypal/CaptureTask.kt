package com.srcodex.shinobustore.paypal

import com.srcodex.shinobustore.ShinobuStore
import com.srcodex.shinobustore.event.StoreTransactionExpiredEvent
import com.srcodex.shinobustore.transaction.CaptureResult
import kotlinx.coroutines.*
import org.bukkit.scheduler.BukkitRunnable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scheduled task that periodically checks pending transactions
 * and attempts to capture completed payments.
 */
class CaptureTask(private val plugin: ShinobuStore) : BukkitRunnable() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isProcessing = AtomicBoolean(false)

    override fun run() {
        if (!isProcessing.compareAndSet(false, true)) {
            if (plugin.configManager.debugEnabled) {
                plugin.logger.info("CaptureTask: Previous cycle still running, skipping")
            }
            return
        }
        scope.launch {
            try {
                processTransactions()
            } catch (e: Exception) {
                plugin.logger.warning("Error in capture task: ${e.message}")
            } finally {
                isProcessing.set(false)
            }
        }
    }

    /**
     * Processes all pending transactions.
     */
    private suspend fun processTransactions() {
        val pendingTransactions = plugin.transactionManager.getAllPending().toList()

        if (pendingTransactions.isEmpty()) {
            return
        }

        if (plugin.configManager.debugEnabled) {
            plugin.logger.info("Processing ${pendingTransactions.size} pending transactions...")
        }

        var captured = 0
        var expired = 0
        var pending = 0

        for (transaction in pendingTransactions) {
            // Check if expired
            if (transaction.isExpired()) {
                plugin.transactionManager.removePending(transaction.orderId)
                expired++

                if (plugin.configManager.logTransactions) {
                    plugin.logger.info("Transaction expired: ${transaction.orderId}")
                }

                // Fire custom event on main thread for plugin integration
                plugin.server.scheduler.runTask(plugin, Runnable {
                    val event = StoreTransactionExpiredEvent(
                        transaction.playerUuid, transaction.orderId, transaction.itemId, transaction.itemDisplay
                    )
                    plugin.server.pluginManager.callEvent(event)
                })

                // Notify player if online
                notifyExpiration(transaction.playerUuid)
                continue
            }

            // Attempt capture with delay between requests to avoid rate limiting
            try {
                when (val result = plugin.paypalService.captureTransaction(transaction.orderId)) {
                    is CaptureResult.Success -> {
                        captured++
                        if (plugin.configManager.debugEnabled) {
                            plugin.logger.info("Captured transaction: ${transaction.orderId}")
                        }
                    }

                    is CaptureResult.NotPaid -> {
                        pending++
                        // Still waiting for payment, do nothing
                    }

                    is CaptureResult.Pending -> {
                        pending++
                        if (plugin.configManager.debugEnabled) {
                            plugin.logger.info("Transaction pending: ${transaction.orderId} - ${result.message}")
                        }
                    }

                    is CaptureResult.Failed -> {
                        // Log error but don't remove transaction yet
                        plugin.logger.warning("Capture failed for ${transaction.orderId}: ${result.error}")
                    }
                }

                // Small delay between API calls
                delay(500)

            } catch (e: Exception) {
                plugin.logger.warning("Error processing transaction ${transaction.orderId}: ${e.message}")
            }
        }

        if (plugin.configManager.debugEnabled) {
            plugin.logger.info("Capture task complete: $captured captured, $expired expired, $pending pending")
        }
    }

    /**
     * Notifies a player that their transaction has expired.
     */
    private fun notifyExpiration(playerUUID: java.util.UUID) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            val player = plugin.server.getPlayer(playerUUID)
            if (player != null && player.isOnline) {
                player.sendMessage(plugin.configManager.getMessage("payment.expired"))

                // Play error sound
                try {
                    val sound = org.bukkit.Sound.valueOf(plugin.configManager.soundError)
                    player.playSound(player.location, sound, 1.0f, 1.0f)
                } catch (_: Exception) {}
            }
        })
    }

    /**
     * Shuts down the task.
     */
    fun shutdown() {
        scope.cancel()
        try {
            this.cancel()
        } catch (_: Exception) {}
    }

    /**
     * Alias for shutdown().
     */
    fun stop() {
        shutdown()
    }

    /**
     * Starts this capture task.
     */
    fun start() {
        val intervalTicks = plugin.configManager.checkInterval * 20L // Convert seconds to ticks

        // Run async with initial delay of 1 minute
        this.runTaskTimerAsynchronously(plugin, 1200L, intervalTicks)

        plugin.logger.info("Capture task started (interval: ${plugin.configManager.checkInterval}s)")
    }

    /**
     * Manually checks a specific transaction.
     */
    fun checkTransaction(transaction: com.srcodex.shinobustore.transaction.PendingTransaction) {
        scope.launch {
            try {
                when (val result = plugin.paypalService.captureTransaction(transaction.orderId)) {
                    is CaptureResult.Success -> {
                        plugin.logger.info("Manually captured transaction: ${transaction.orderId}")
                    }
                    is CaptureResult.NotPaid -> {
                        plugin.logger.info("Transaction not yet paid: ${transaction.orderId}")
                    }
                    is CaptureResult.Pending -> {
                        plugin.logger.info("Transaction pending: ${transaction.orderId} - ${result.message}")
                    }
                    is CaptureResult.Failed -> {
                        plugin.logger.warning("Capture failed for ${transaction.orderId}: ${result.error}")
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("Error checking transaction ${transaction.orderId}: ${e.message}")
            }
        }
    }

    companion object {
        /**
         * Creates and starts a capture task with the configured interval.
         */
        fun create(plugin: ShinobuStore): CaptureTask {
            val task = CaptureTask(plugin)
            task.start()
            return task
        }
    }
}
