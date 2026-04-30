package com.shinobu.rankup.data

import java.time.Instant
import java.util.UUID

/**
 * Represents player progression data.
 * Thread-safe immutable data class.
 */
data class PlayerData(
    val uuid: UUID,
    val name: String,
    val currentRankId: String,
    val totalSpent: Double = 0.0,
    val rankupCount: Int = 0,
    val firstJoin: Instant = Instant.now(),
    val lastRankup: Instant? = null,
    val lastSeen: Instant = Instant.now(),
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * Create a copy with updated rank after successful rankup.
     */
    fun withRankup(newRankId: String, cost: Double): PlayerData {
        return copy(
            currentRankId = newRankId,
            totalSpent = totalSpent + cost,
            rankupCount = rankupCount + 1,
            lastRankup = Instant.now()
        )
    }

    /**
     * Create a copy with updated last seen timestamp.
     */
    fun withLastSeen(): PlayerData {
        return copy(lastSeen = Instant.now())
    }

    /**
     * Create a copy with updated name (for name changes).
     */
    fun withName(newName: String): PlayerData {
        return copy(name = newName)
    }

    /**
     * Add or update metadata.
     */
    fun withMetadata(key: String, value: String): PlayerData {
        return copy(metadata = metadata + (key to value))
    }
}

/**
 * Result of a max rankup operation.
 */
data class MaxRankupResult(
    val success: Boolean,
    val ranksGained: Int,
    val totalCost: Double,
    val fromRankId: String,
    val toRankId: String,
    val finalRankDisplay: String = "",
    val errorMessage: String? = null
) {
    companion object {
        fun success(ranksGained: Int, totalCost: Double, fromRankId: String, toRankId: String, finalRankDisplay: String): MaxRankupResult {
            return MaxRankupResult(
                success = true,
                ranksGained = ranksGained,
                totalCost = totalCost,
                fromRankId = fromRankId,
                toRankId = toRankId,
                finalRankDisplay = finalRankDisplay
            )
        }

        fun failure(reason: String, currentRankId: String): MaxRankupResult {
            return MaxRankupResult(
                success = false,
                ranksGained = 0,
                totalCost = 0.0,
                fromRankId = currentRankId,
                toRankId = currentRankId
            )
        }
    }
}

/**
 * Leaderboard entry for top players.
 */
data class LeaderboardEntry(
    val position: Int,
    val uuid: UUID,
    val name: String,
    val rankId: String,
    val rankDisplayName: String,
    val totalSpent: Double,
    val rankupCount: Int
)
