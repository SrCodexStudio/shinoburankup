package com.shinobu.rankup.event

import com.shinobu.rankup.data.RankData
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event fired when a player attempts to rank up.
 * This event is cancellable, allowing other plugins to prevent rankups.
 */
class PlayerRankupEvent(
    val player: Player,
    val previousRank: RankData,
    val newRank: RankData,
    val cost: Double,
    val isFreeRankup: Boolean = false
) : Event(), Cancellable {

    private var cancelled = false
    private var cancelReason: String = "Rankup cancelled by another plugin"

    /**
     * Get the reason for cancellation.
     */
    fun getCancelReason(): String = cancelReason

    /**
     * Set the cancellation reason.
     */
    fun setCancelReason(reason: String) {
        this.cancelReason = reason
    }

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        this.cancelled = cancel
    }

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}

/**
 * Event fired after a player has successfully ranked up.
 * This event is NOT cancellable - use PlayerRankupEvent to prevent rankups.
 */
class PlayerRankupCompleteEvent(
    val player: Player,
    val previousRank: RankData,
    val newRank: RankData,
    val cost: Double,
    val isFreeRankup: Boolean = false,
    val totalRankups: Int,
    val totalSpent: Double
) : Event() {

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
