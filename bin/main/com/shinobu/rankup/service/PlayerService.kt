package com.shinobu.rankup.service

import com.shinobu.rankup.data.PlayerData
import java.util.UUID

/**
 * Service interface for player data operations.
 * Implementations handle the actual storage mechanism.
 */
interface PlayerService {

    /**
     * Load player data asynchronously.
     *
     * @param uuid Player's UUID
     * @return Player data, or null if not found
     */
    suspend fun loadPlayerData(uuid: UUID): PlayerData?

    /**
     * Load player data synchronously.
     * Should only be used in async contexts like AsyncPlayerPreLoginEvent.
     *
     * @param uuid Player's UUID
     * @return Player data, or null if not found
     */
    fun loadPlayerDataSync(uuid: UUID): PlayerData?

    /**
     * Save player data asynchronously.
     *
     * @param data Player data to save
     * @return true if save was successful
     */
    suspend fun savePlayerData(data: PlayerData): Boolean

    /**
     * Save player data synchronously.
     *
     * @param data Player data to save
     * @return true if save was successful
     */
    fun savePlayerDataSync(data: PlayerData): Boolean

    /**
     * Delete player data.
     *
     * @param uuid Player's UUID
     * @return true if deletion was successful
     */
    suspend fun deletePlayerData(uuid: UUID): Boolean

    /**
     * Check if player data exists.
     *
     * @param uuid Player's UUID
     * @return true if data exists
     */
    suspend fun playerDataExists(uuid: UUID): Boolean

    /**
     * Get all player data.
     * Use with caution on large databases.
     *
     * @return List of all player data
     */
    suspend fun getAllPlayerData(): List<PlayerData>

    /**
     * Get player data for multiple UUIDs.
     *
     * @param uuids List of player UUIDs
     * @return Map of UUID to PlayerData
     */
    suspend fun getPlayerDataBatch(uuids: List<UUID>): Map<UUID, PlayerData>

    /**
     * Save multiple player data entries.
     *
     * @param dataList List of player data to save
     * @return Number of successfully saved entries
     */
    suspend fun savePlayerDataBatch(dataList: List<PlayerData>): Int

    /**
     * Get top players by rank order and total spent.
     *
     * @param limit Maximum number of results
     * @return List of top player data
     */
    suspend fun getTopPlayers(limit: Int): List<PlayerData>

    /**
     * Get total number of registered players.
     *
     * @return Total player count
     */
    suspend fun getPlayerCount(): Long
}
