package com.srcodex.shinobustore.event

import com.srcodex.shinobustore.transaction.StoreItem
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Fired before a purchase order is created. Can be cancelled by external plugins
 * to implement custom purchase restrictions (e.g., level requirements, cooldowns,
 * region restrictions, etc.).
 *
 * This event is called synchronously on the main thread before any PayPal
 * API call is made. Cancelling this event prevents the order from being created.
 *
 * Thread safety: This event is always fired on the main server thread.
 */
class StorePrePurchaseEvent(
    val player: Player,
    val item: StoreItem,
    val cost: Double,
    val total: Double
) : Event(), Cancellable {

    private var cancelled = false
    private var cancelReason = ""

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    /**
     * Cancels this event with a reason that will be shown to the player.
     * @param cancel whether to cancel the event
     * @param reason the reason to display to the player
     */
    fun setCancelled(cancel: Boolean, reason: String) {
        cancelled = cancel
        cancelReason = reason
    }

    /**
     * Gets the reason why this event was cancelled.
     * @return the cancel reason, or empty string if not set
     */
    fun getCancelReason(): String = cancelReason

    companion object {
        @JvmStatic
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }

    override fun getHandlers(): HandlerList = Companion.getHandlerList()
}

/**
 * Fired after a purchase is completed and reward commands have been executed.
 * This is a read-only event for logging, analytics, and external integrations.
 *
 * Note: [player] may be null if the player went offline between payment and
 * capture. In that case, [playerUUID] and [playerName] are always available.
 *
 * Thread safety: This event is always fired on the main server thread.
 */
class StorePurchaseCompleteEvent(
    val player: Player?,
    val playerUUID: UUID,
    val playerName: String,
    val itemId: String,
    val itemDisplay: String,
    val orderId: String,
    val captureId: String,
    val cost: Double,
    val total: Double,
    val commandsExecuted: List<String>
) : Event() {

    companion object {
        @JvmStatic
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }

    override fun getHandlers(): HandlerList = Companion.getHandlerList()
}

/**
 * Fired when a pending transaction expires without receiving payment.
 * Useful for logging, notifying admins, or triggering cleanup in external systems.
 *
 * This event may be fired asynchronously during the periodic cleanup task.
 * Listeners should not call Bukkit API directly without scheduling back
 * to the main thread.
 *
 * Thread safety: This event may be fired on an async thread.
 */
class StoreTransactionExpiredEvent(
    val playerUUID: UUID,
    val orderId: String,
    val itemId: String,
    val itemDisplay: String
) : Event(true) {

    companion object {
        @JvmStatic
        private val handlers = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }

    override fun getHandlers(): HandlerList = Companion.getHandlerList()
}
