package com.shinobu.rankup.api

import com.shinobu.rankup.data.LeaderboardEntry
import com.shinobu.rankup.data.MaxRankupResult
import com.shinobu.rankup.data.PlayerData
import com.shinobu.rankup.data.RankData
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Public API for ShinobuRankup.
 *
 * This interface provides methods for other plugins to interact with ShinobuRankup.
 * All methods are thread-safe and can be called from any thread.
 *
 * Usage example:
 * ```kotlin
 * val api = ShinobuRankupProvider.getAPI()
 * val playerRank = api?.getPlayerRank(player)
 * ```
 */
interface ShinobuRankupAPI {

    // ==================== Rank Information ====================

    /**
     * Get the current rank of a player.
     *
     * @param player The player to check
     * @return The player's current rank, or null if not found
     */
    fun getPlayerRank(player: OfflinePlayer): RankData?

    /**
     * Get the current rank of a player by UUID.
     *
     * @param uuid The player's UUID
     * @return The player's current rank, or null if not found
     */
    fun getPlayerRankByUUID(uuid: UUID): RankData?

    /**
     * Get the next rank for a player (the rank they would upgrade to).
     *
     * @param player The player to check
     * @return The next rank, or null if player is at max rank
     */
    fun getNextRank(player: OfflinePlayer): RankData?

    /**
     * Get the next rank for a player by UUID.
     *
     * @param uuid The player's UUID
     * @return The next rank, or null if player is at max rank
     */
    fun getNextRankByUUID(uuid: UUID): RankData?

    /**
     * Set a player's rank directly.
     *
     * @param player The player to modify
     * @param rankId The ID of the rank to set
     * @return true if the rank was set successfully
     */
    fun setPlayerRank(player: OfflinePlayer, rankId: String): Boolean

    /**
     * Set a player's rank directly by UUID.
     *
     * @param uuid The player's UUID
     * @param rankId The ID of the rank to set
     * @return true if the rank was set successfully
     */
    fun setPlayerRankByUUID(uuid: UUID, rankId: String): Boolean

    // ==================== Rank Registry ====================

    /**
     * Get all available ranks, sorted by order.
     *
     * @return List of all ranks
     */
    fun getAllRanks(): List<RankData>

    /**
     * Get a specific rank by its ID.
     *
     * @param id The rank ID
     * @return The rank, or null if not found
     */
    fun getRankById(id: String): RankData?

    /**
     * Get the default (starting) rank.
     *
     * @return The default rank
     */
    fun getDefaultRank(): RankData?

    /**
     * Get the maximum (highest) rank.
     *
     * @return The max rank
     */
    fun getMaxRank(): RankData?

    /**
     * Get the rank after the specified rank.
     *
     * @param currentRankId The current rank ID
     * @return The next rank, or null if this is the max rank
     */
    fun getRankAfter(currentRankId: String): RankData?

    /**
     * Get the rank before the specified rank.
     *
     * @param currentRankId The current rank ID
     * @return The previous rank, or null if this is the first rank
     */
    fun getRankBefore(currentRankId: String): RankData?

    // ==================== Player Data ====================

    /**
     * Get comprehensive player data.
     *
     * @param player The player to check
     * @return Player data, or null if not found
     */
    fun getPlayerData(player: OfflinePlayer): PlayerData?

    /**
     * Get comprehensive player data by UUID.
     *
     * @param uuid The player's UUID
     * @return Player data, or null if not found
     */
    fun getPlayerDataByUUID(uuid: UUID): PlayerData?

    /**
     * Reset a player's progression data.
     *
     * @param player The player to reset
     * @return true if reset was successful
     */
    fun resetPlayerData(player: OfflinePlayer): Boolean

    /**
     * Reset a player's progression data by UUID.
     *
     * @param uuid The player's UUID
     * @return true if reset was successful
     */
    fun resetPlayerDataByUUID(uuid: UUID): Boolean

    // ==================== Leaderboard ====================

    /**
     * Get top players sorted by rank and total spent.
     *
     * @param limit Maximum number of entries to return
     * @return List of top players
     */
    fun getTopPlayers(limit: Int): List<LeaderboardEntry>

    /**
     * Get a player's position in the leaderboard.
     *
     * @param player The player to check
     * @return Position (1-indexed), or -1 if not ranked
     */
    fun getLeaderboardPosition(player: OfflinePlayer): Int

    /**
     * Get a player's position in the leaderboard by UUID.
     *
     * @param uuid The player's UUID
     * @return Position (1-indexed), or -1 if not ranked
     */
    fun getLeaderboardPositionByUUID(uuid: UUID): Int

    // ==================== Rankup Operations ====================

    /**
     * Perform a rankup for a player.
     * This is an async operation that handles economy transactions.
     *
     * @param player The player to rankup (must be online)
     * @return CompletableFuture with true if rankup was successful
     */
    fun performRankup(player: Player): CompletableFuture<Boolean>

    /**
     * Perform maximum possible rankups for a player.
     * This is an async operation that ranks up until the player
     * can't afford the next rank or reaches max rank.
     *
     * @param player The player to rankup (must be online)
     * @return CompletableFuture with the result containing details of the operation
     */
    fun performMaxRankup(player: Player): CompletableFuture<MaxRankupResult>

    /**
     * Check if a player can afford the next rank.
     *
     * @param player The player to check
     * @return true if player can afford the next rankup
     */
    fun canAffordNextRank(player: OfflinePlayer): Boolean

    /**
     * Check if a player meets all requirements for the next rank.
     * This includes money and any additional requirements.
     *
     * @param player The player to check
     * @return true if player meets all requirements
     */
    fun meetsRankupRequirements(player: Player): Boolean

    /**
     * Get the cost to reach a specific rank from the player's current rank.
     *
     * @param player The player to check
     * @param targetRankId The target rank ID
     * @return Total cost, or -1 if target rank is lower or equal
     */
    fun getCostToRank(player: OfflinePlayer, targetRankId: String): Double

    // ==================== Utility ====================

    /**
     * Get the progress percentage towards the next rank (0-100).
     *
     * @param player The player to check
     * @return Progress percentage
     */
    fun getProgressPercentage(player: OfflinePlayer): Double

    /**
     * Get the money needed for the next rank.
     *
     * @param player The player to check
     * @return Money needed, or 0 if at max rank
     */
    fun getMoneyNeeded(player: OfflinePlayer): Double

    /**
     * Check if a player is at the maximum rank.
     *
     * @param player The player to check
     * @return true if player is at max rank
     */
    fun isMaxRank(player: OfflinePlayer): Boolean

    /**
     * Get the plugin version.
     *
     * @return Version string
     */
    fun getVersion(): String
}

/**
 * Provider for accessing the ShinobuRankup API.
 *
 * Usage:
 * ```kotlin
 * val api = ShinobuRankupProvider.getAPI()
 * if (api != null) {
 *     // Use the API
 * }
 * ```
 */
object ShinobuRankupProvider {

    @Volatile
    private var api: ShinobuRankupAPI? = null

    /**
     * Get the ShinobuRankup API instance.
     *
     * @return The API instance, or null if the plugin is not loaded
     */
    @JvmStatic
    fun getAPI(): ShinobuRankupAPI? = api

    /**
     * Register the API instance.
     * This should only be called by the main plugin.
     *
     * @param instance The API implementation
     */
    internal fun register(instance: ShinobuRankupAPI) {
        api = instance
    }

    /**
     * Unregister the API instance.
     * This should only be called by the main plugin during disable.
     */
    internal fun unregister() {
        api = null
    }

    /**
     * Check if the API is available.
     *
     * @return true if the API is registered and available
     */
    @JvmStatic
    fun isAvailable(): Boolean = api != null
}

/**
 * Exception thrown when a rankup operation fails.
 */
class RankupException(
    message: String,
    val reason: RankupFailReason,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Reasons why a rankup might fail.
 */
enum class RankupFailReason {
    /** Player is already at the maximum rank */
    MAX_RANK_REACHED,

    /** Player doesn't have enough money */
    INSUFFICIENT_FUNDS,

    /** Player doesn't meet additional requirements */
    REQUIREMENTS_NOT_MET,

    /** Player is on rankup cooldown */
    ON_COOLDOWN,

    /** Economy system is not available */
    ECONOMY_UNAVAILABLE,

    /** Player data could not be found */
    PLAYER_NOT_FOUND,

    /** Rank configuration is invalid */
    INVALID_RANK,

    /** Economy transaction failed */
    TRANSACTION_FAILED,

    /** Operation was cancelled by an event */
    CANCELLED,

    /** Unknown error occurred */
    UNKNOWN
}

/**
 * Event data for rankup events (for integration with event systems).
 */
data class RankupEventData(
    val playerUUID: UUID,
    val playerName: String,
    val fromRank: RankData,
    val toRank: RankData,
    val cost: Double,
    val timestamp: Long = System.currentTimeMillis()
)
