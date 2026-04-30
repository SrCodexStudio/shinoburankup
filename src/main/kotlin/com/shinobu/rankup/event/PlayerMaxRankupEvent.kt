package com.shinobu.rankup.event

import com.shinobu.rankup.data.RankData
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event fired when a player attempts to use /rankupmax.
 * This event is cancellable.
 */
class PlayerMaxRankupEvent(
    val player: Player,
    val currentRank: RankData,
    val targetRank: RankData,
    val estimatedCost: Double,
    val estimatedRankups: Int
) : Event(), Cancellable {

    private var cancelled = false
    private var cancelReason: String = "Max rankup cancelled by another plugin"

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
 * Event fired after a player has successfully completed /rankupmax.
 * This event is NOT cancellable.
 */
class PlayerMaxRankupCompleteEvent(
    val player: Player,
    val startRank: RankData,
    val endRank: RankData,
    val ranksGained: Int,
    val totalCost: Double,
    val remainingBalance: Double,
    val individualRankups: List<RankupDetails>
) : Event() {

    /**
     * Data class containing details of each individual rankup.
     */
    data class RankupDetails(
        val fromRank: RankData,
        val toRank: RankData,
        val cost: Double
    )

    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
