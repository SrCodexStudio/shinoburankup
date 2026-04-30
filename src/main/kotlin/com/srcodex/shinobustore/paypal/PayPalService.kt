package com.srcodex.shinobustore.paypal

import com.srcodex.shinobustore.ShinobuStore
import com.srcodex.shinobustore.transaction.CaptureResult
import com.srcodex.shinobustore.transaction.PendingTransaction
import com.srcodex.shinobustore.transaction.StoreItem
import com.srcodex.shinobustore.util.ColorUtil
import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * Service layer for PayPal operations.
 * Handles order creation, capture, and transaction management.
 */
class PayPalService(private val plugin: ShinobuStore, private val client: PayPalClient) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Per-order capture lock to prevent duplicate capture attempts (idempotency). */
    private val capturingOrders = ConcurrentHashMap.newKeySet<String>()

    /** Locale-safe decimal formatting to avoid comma separators in non-US locales. */
    private fun formatDecimal(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)

    /**
     * Result of creating a purchase order.
     */
    sealed class PurchaseResult {
        data class Success(val transaction: PendingTransaction) : PurchaseResult()
        data class Error(val message: String) : PurchaseResult()
    }

    /**
     * Creates a purchase order for a player.
     */
    suspend fun createPurchase(
        player: Player,
        item: StoreItem
    ): PurchaseResult = withContext(Dispatchers.IO) {
        try {
            // Calculate cost
            val cost = item.cost
            val total = item.calculateTotal()

            // Create description
            val description = "${item.display} for ${player.name}"
            val customId = "${player.uniqueId}_${item.id}_${System.currentTimeMillis()}"

            // Create PayPal order
            val result = client.createOrder(
                amount = total,
                currency = plugin.configManager.paypalCurrency,
                description = ColorUtil.stripColors(description),
                customId = customId
            )

            when (result) {
                is PayPalClient.ApiResult.Success -> {
                    val orderResponse = result.data
                    val fee = total - cost

                    // Create pending transaction
                    val transaction = PendingTransaction(
                        orderId = orderResponse.orderId,
                        playerUuid = player.uniqueId,
                        playerName = player.name,
                        itemId = item.id,
                        itemDisplay = ColorUtil.stripColors(item.display),
                        cost = cost,
                        fee = fee,
                        total = total,
                        createdAt = System.currentTimeMillis(),
                        expiresAt = System.currentTimeMillis() + plugin.configManager.expireAfter,
                        checkoutUrl = orderResponse.approveUrl,
                        commands = item.commands
                    )

                    // Store transaction
                    plugin.transactionManager.addPending(transaction)

                    // Log
                    plugin.logger.info(buildString {
                        append("\n")
                        append("═══════════════════════════════════════\n")
                        append("  NEW PURCHASE ORDER\n")
                        append("═══════════════════════════════════════\n")
                        append("  Player: ${player.name}\n")
                        append("  UUID: ${player.uniqueId}\n")
                        append("  Item: ${item.id}\n")
                        append("  Cost: ${plugin.configManager.currencySymbol}${formatDecimal(cost)}\n")
                        append("  Total: ${plugin.configManager.currencySymbol}${formatDecimal(total)}\n")
                        append("  Order ID: ${orderResponse.orderId}\n")
                        append("═══════════════════════════════════════")
                    })

                    PurchaseResult.Success(transaction)
                }

                is PayPalClient.ApiResult.Error -> {
                    plugin.logger.warning("PayPal order creation failed: ${result.message}")
                    PurchaseResult.Error(result.message)
                }
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Error creating purchase", e)
            PurchaseResult.Error("Internal error: ${e.message}")
        }
    }

    /**
     * Attempts to capture a pending transaction.
     * Uses per-order idempotency guard to prevent duplicate capture attempts.
     */
    suspend fun captureTransaction(orderId: String): CaptureResult = withContext(Dispatchers.IO) {
        // Idempotency check: prevent duplicate capture attempts for the same order
        if (!capturingOrders.add(orderId)) {
            return@withContext CaptureResult.Pending(orderId, "Capture already in progress")
        }

        try {
            val pending = plugin.transactionManager.getPending(orderId)
                ?: return@withContext CaptureResult.Failed(orderId, "Transaction not found")

            // Check if expired
            if (pending.isExpired()) {
                plugin.transactionManager.removePending(orderId)
                return@withContext CaptureResult.Failed(orderId, "Transaction expired")
            }

            // Attempt capture
            when (val result = client.captureOrder(orderId)) {
                is PayPalClient.ApiResult.Success -> {
                    val captureResponse = result.data

                    when (captureResponse.status) {
                        "COMPLETED" -> {
                            // Verify captured amount matches expected total
                            val tolerance = 0.01
                            if (captureResponse.capturedAmount > 0 && Math.abs(captureResponse.capturedAmount - pending.total) > tolerance) {
                                plugin.logger.severe("AMOUNT MISMATCH for order $orderId! Expected ${pending.total}, got ${captureResponse.capturedAmount}")
                                return@withContext CaptureResult.Failed(orderId, "Amount mismatch: expected ${pending.total}, captured ${captureResponse.capturedAmount}")
                            }

                            // Execute commands AND THEN complete (fix race condition)
                            executeCommandsAndComplete(pending, orderId, captureResponse.captureId)
                            CaptureResult.Success(orderId, captureResponse.captureId)
                        }
                        else -> {
                            CaptureResult.Pending(orderId, "Status: ${captureResponse.status}")
                        }
                    }
                }

                is PayPalClient.ApiResult.Error -> {
                    when {
                        result.message.contains("ORDER_NOT_APPROVED", ignoreCase = true) -> {
                            CaptureResult.NotPaid
                        }
                        result.statusCode == 422 -> {
                            CaptureResult.NotPaid
                        }
                        else -> {
                            CaptureResult.Failed(orderId, result.message)
                        }
                    }
                }
            }
        } finally {
            capturingOrders.remove(orderId)
        }
    }

    /**
     * Executes reward commands for a completed transaction and then marks
     * the transaction as complete INSIDE the main-thread runTask callback.
     *
     * This fixes the race condition where completePending was called from
     * the IO thread before commands had actually executed on the main thread.
     */
    private fun executeCommandsAndComplete(
        transaction: PendingTransaction,
        orderId: String,
        captureId: String
    ) {
        val commands = transaction.commands.ifEmpty {
            plugin.configManager.items[transaction.itemId]?.commands ?: emptyList()
        }
        val item = plugin.configManager.items[transaction.itemId]
        val player = Bukkit.getPlayer(transaction.playerUuid)

        plugin.server.scheduler.runTask(plugin, Runnable {
            val executedCommands = mutableListOf<String>()
            val playerName = player?.name ?: transaction.playerName

            for (command in commands) {
                val processedCommand = command
                    .replace("{player}", playerName)
                    .replace("{uuid}", transaction.playerUuid.toString())
                    .replace("{item}", transaction.itemId)
                    .replace("{cost}", formatDecimal(transaction.cost))
                    .replace("{total}", formatDecimal(transaction.total))
                    .replace("{order_id}", transaction.orderId)

                try {
                    plugin.server.dispatchCommand(plugin.server.consoleSender, processedCommand)
                    executedCommands.add(processedCommand)
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to execute command: $processedCommand")
                }
            }

            // NOW complete the transaction AFTER commands executed on the main thread
            plugin.transactionManager.completePending(orderId, captureId, executedCommands)

            // Notify player if online
            if (player != null && player.isOnline) {
                player.sendMessage(plugin.configManager.getMessage("payment.completed"))
                player.sendMessage(plugin.configManager.getMessage("payment.completed-item",
                    mapOf("item" to (item?.display ?: transaction.itemDisplay))))

                // Play sound
                try {
                    val sound = org.bukkit.Sound.valueOf(plugin.configManager.soundPurchaseComplete)
                    player.playSound(player.location, sound, 1.0f, 1.0f)
                } catch (_: Exception) {}
            }

            // Log completion
            plugin.logger.info(buildString {
                append("\n═══════════════════════════════════════\n")
                append("  PURCHASE COMPLETED\n")
                append("═══════════════════════════════════════\n")
                append("  Player: ${transaction.playerName}\n")
                append("  Item: ${transaction.itemId}\n")
                append("  Order ID: ${transaction.orderId}\n")
                append("  Commands: ${executedCommands.size} executed\n")
                append("═══════════════════════════════════════")
            })
        })
    }

    /**
     * Validates PayPal configuration.
     */
    fun validateConfiguration(): Boolean {
        return client.validateConfiguration()
    }

    /**
     * Gets the formatted cost string.
     */
    fun formatCost(cost: Double): String = "${plugin.configManager.currencySymbol}${formatDecimal(cost)}"

    /**
     * Gets the formatted total string (with fees).
     */
    fun formatTotal(total: Double): String = "${plugin.configManager.currencySymbol}${formatDecimal(total)}"

    /**
     * Shuts down the service.
     */
    fun shutdown() {
        scope.cancel()
        client.shutdown()
    }
}
