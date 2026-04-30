package com.shinobu.rankup.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Repository for player data persistence.
 * Handles all CRUD operations for player ranking information.
 *
 * All operations are asynchronous and use prepared statements
 * to prevent SQL injection attacks.
 *
 * @property database The database manager instance
 * @property logger Logger for operation tracking
 */
class PlayerRepository(
    private val database: Database,
    private val logger: Logger
) {

    /**
     * Find a player by their UUID.
     *
     * @param uuid The player's UUID
     * @return The player data or null if not found
     */
    suspend fun findByUUID(uuid: UUID): PlayerData? = withContext(Dispatchers.IO) {
        try {
            database.queryOne(
                """
                SELECT uuid, name, current_rank_id, total_spent, rankup_count,
                       first_join, last_rankup, last_seen, metadata
                FROM player_data
                WHERE uuid = ?
                """.trimIndent(),
                uuid.toString()
            ) { rs -> mapResultSetToPlayerData(rs) }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to find player by UUID: $uuid", e)
            null
        }
    }

    /**
     * Find a player by their name (case-insensitive).
     *
     * @param name The player's name
     * @return The player data or null if not found
     */
    suspend fun findByName(name: String): PlayerData? = withContext(Dispatchers.IO) {
        try {
            database.queryOne(
                """
                SELECT uuid, name, current_rank_id, total_spent, rankup_count,
                       first_join, last_rankup, last_seen, metadata
                FROM player_data
                WHERE LOWER(name) = LOWER(?)
                """.trimIndent(),
                name
            ) { rs -> mapResultSetToPlayerData(rs) }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to find player by name: $name", e)
            null
        }
    }

    /**
     * Find all players with a specific rank.
     *
     * @param rankId The rank ID to search for
     * @return List of player data with the specified rank
     */
    suspend fun findByRank(rankId: String): List<PlayerData> = withContext(Dispatchers.IO) {
        try {
            database.query(
                """
                SELECT uuid, name, current_rank_id, total_spent, rankup_count,
                       first_join, last_rankup, last_seen, metadata
                FROM player_data
                WHERE current_rank_id = ?
                ORDER BY rankup_count DESC
                """.trimIndent(),
                rankId
            ) { rs -> mapResultSetToPlayerData(rs) }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to find players by rank: $rankId", e)
            emptyList()
        }
    }

    /**
     * Get top players by rankup count.
     *
     * @param limit Maximum number of players to return
     * @return List of top players ordered by rankup count
     */
    suspend fun getTopPlayers(limit: Int = 10): List<PlayerData> = withContext(Dispatchers.IO) {
        try {
            database.query(
                """
                SELECT uuid, name, current_rank_id, total_spent, rankup_count,
                       first_join, last_rankup, last_seen, metadata
                FROM player_data
                ORDER BY rankup_count DESC, total_spent DESC
                LIMIT ?
                """.trimIndent(),
                limit
            ) { rs -> mapResultSetToPlayerData(rs) }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to get top players", e)
            emptyList()
        }
    }

    /**
     * Get top players by total spent.
     *
     * @param limit Maximum number of players to return
     * @return List of top players ordered by total spent
     */
    suspend fun getTopBySpent(limit: Int = 10): List<PlayerData> = withContext(Dispatchers.IO) {
        try {
            database.query(
                """
                SELECT uuid, name, current_rank_id, total_spent, rankup_count,
                       first_join, last_rankup, last_seen, metadata
                FROM player_data
                ORDER BY total_spent DESC, rankup_count DESC
                LIMIT ?
                """.trimIndent(),
                limit
            ) { rs -> mapResultSetToPlayerData(rs) }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to get top players by spent", e)
            emptyList()
        }
    }

    /**
     * Get player's position on the leaderboard.
     *
     * @param uuid The player's UUID
     * @return The player's position (1-indexed) or null if not found
     */
    suspend fun getPlayerPosition(uuid: UUID): Int? = withContext(Dispatchers.IO) {
        try {
            // This query counts how many players have more rankups
            database.queryOne(
                """
                SELECT COUNT(*) + 1 as position
                FROM player_data
                WHERE rankup_count > (
                    SELECT rankup_count FROM player_data WHERE uuid = ?
                )
                """.trimIndent(),
                uuid.toString()
            ) { rs -> rs.getInt("position") }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to get player position: $uuid", e)
            null
        }
    }

    /**
     * Save or update player data.
     * Uses UPSERT pattern for atomic insert/update.
     *
     * @param data The player data to save
     * @return true if save was successful
     */
    suspend fun save(data: PlayerData): Boolean = withContext(Dispatchers.IO) {
        try {
            val sql = when (database.getType()) {
                Database.DatabaseType.SQLITE -> """
                    INSERT INTO player_data (uuid, name, current_rank_id, total_spent,
                                            rankup_count, first_join, last_rankup, last_seen, metadata)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        name = excluded.name,
                        current_rank_id = excluded.current_rank_id,
                        total_spent = excluded.total_spent,
                        rankup_count = excluded.rankup_count,
                        last_rankup = excluded.last_rankup,
                        last_seen = excluded.last_seen,
                        metadata = excluded.metadata
                """.trimIndent()

                Database.DatabaseType.MYSQL -> """
                    INSERT INTO player_data (uuid, name, current_rank_id, total_spent,
                                            rankup_count, first_join, last_rankup, last_seen, metadata)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        name = VALUES(name),
                        current_rank_id = VALUES(current_rank_id),
                        total_spent = VALUES(total_spent),
                        rankup_count = VALUES(rankup_count),
                        last_rankup = VALUES(last_rankup),
                        last_seen = VALUES(last_seen),
                        metadata = VALUES(metadata)
                """.trimIndent()
            }

            database.execute(
                sql,
                data.uuid.toString(),
                data.name,
                data.currentRankId,
                data.totalSpent,
                data.rankupCount,
                formatTimestamp(data.firstJoin),
                data.lastRankup?.let { formatTimestamp(it) },
                formatTimestamp(data.lastSeen),
                serializeMetadata(data.metadata)
            )

            true
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to save player data: ${data.uuid}", e)
            false
        }
    }

    /**
     * Batch save multiple player data entries.
     * More efficient for saving multiple players at once.
     *
     * @param dataList List of player data to save
     * @return Number of successfully saved entries
     */
    suspend fun saveAll(dataList: List<PlayerData>): Int = withContext(Dispatchers.IO) {
        var successCount = 0

        database.transaction { conn ->
            val sql = when (database.getType()) {
                Database.DatabaseType.SQLITE -> """
                    INSERT INTO player_data (uuid, name, current_rank_id, total_spent,
                                            rankup_count, first_join, last_rankup, last_seen, metadata)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        name = excluded.name,
                        current_rank_id = excluded.current_rank_id,
                        total_spent = excluded.total_spent,
                        rankup_count = excluded.rankup_count,
                        last_rankup = excluded.last_rankup,
                        last_seen = excluded.last_seen,
                        metadata = excluded.metadata
                """.trimIndent()

                Database.DatabaseType.MYSQL -> """
                    INSERT INTO player_data (uuid, name, current_rank_id, total_spent,
                                            rankup_count, first_join, last_rankup, last_seen, metadata)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        name = VALUES(name),
                        current_rank_id = VALUES(current_rank_id),
                        total_spent = VALUES(total_spent),
                        rankup_count = VALUES(rankup_count),
                        last_rankup = VALUES(last_rankup),
                        last_seen = VALUES(last_seen),
                        metadata = VALUES(metadata)
                """.trimIndent()
            }

            conn.prepareStatement(sql).use { stmt ->
                dataList.forEach { data ->
                    stmt.setString(1, data.uuid.toString())
                    stmt.setString(2, data.name)
                    stmt.setString(3, data.currentRankId)
                    stmt.setDouble(4, data.totalSpent)
                    stmt.setInt(5, data.rankupCount)
                    stmt.setString(6, formatTimestamp(data.firstJoin))
                    stmt.setString(7, data.lastRankup?.let { formatTimestamp(it) })
                    stmt.setString(8, formatTimestamp(data.lastSeen))
                    stmt.setString(9, serializeMetadata(data.metadata))
                    stmt.addBatch()
                }

                val results = stmt.executeBatch()
                successCount = results.count { it >= 0 }
            }

            successCount
        }

        successCount
    }

    /**
     * Delete player data by UUID.
     *
     * @param uuid The player's UUID
     * @return true if deletion was successful
     */
    suspend fun delete(uuid: UUID): Boolean = withContext(Dispatchers.IO) {
        try {
            database.execute(
                "DELETE FROM player_data WHERE uuid = ?",
                uuid.toString()
            )
            true
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to delete player data: $uuid", e)
            false
        }
    }

    /**
     * Check if a player exists in the database.
     *
     * @param uuid The player's UUID
     * @return true if player exists
     */
    suspend fun exists(uuid: UUID): Boolean = withContext(Dispatchers.IO) {
        try {
            database.queryOne(
                "SELECT 1 FROM player_data WHERE uuid = ?",
                uuid.toString()
            ) { true } ?: false
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to check player existence: $uuid", e)
            false
        }
    }

    /**
     * Get total player count.
     *
     * @return Total number of players in the database
     */
    suspend fun count(): Int = withContext(Dispatchers.IO) {
        try {
            database.queryOne(
                "SELECT COUNT(*) as count FROM player_data"
            ) { rs -> rs.getInt("count") } ?: 0
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to count players", e)
            0
        }
    }

    /**
     * Get count of players at each rank.
     *
     * @return Map of rank ID to player count
     */
    suspend fun getPlayerCountByRank(): Map<String, Int> = withContext(Dispatchers.IO) {
        try {
            database.query(
                """
                SELECT current_rank_id, COUNT(*) as count
                FROM player_data
                GROUP BY current_rank_id
                """.trimIndent()
            ) { rs -> Pair(rs.getString("current_rank_id"), rs.getInt("count")) }
                .toMap()
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to get player count by rank", e)
            emptyMap()
        }
    }

    /**
     * Reset player's rank to default.
     *
     * @param uuid The player's UUID
     * @param defaultRankId The default rank ID
     * @return true if reset was successful
     */
    suspend fun resetRank(uuid: UUID, defaultRankId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            database.execute(
                """
                UPDATE player_data
                SET current_rank_id = ?, total_spent = 0, rankup_count = 0, last_rankup = NULL
                WHERE uuid = ?
                """.trimIndent(),
                defaultRankId,
                uuid.toString()
            )
            true
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to reset player rank: $uuid", e)
            false
        }
    }

    /**
     * Set player's rank directly (admin command).
     *
     * @param uuid The player's UUID
     * @param rankId The rank ID to set
     * @return true if update was successful
     */
    suspend fun setRank(uuid: UUID, rankId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            database.execute(
                """
                UPDATE player_data
                SET current_rank_id = ?, last_seen = ?
                WHERE uuid = ?
                """.trimIndent(),
                rankId,
                formatTimestamp(Instant.now()),
                uuid.toString()
            )
            true
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to set player rank: $uuid -> $rankId", e)
            false
        }
    }

    /**
     * Map a ResultSet row to PlayerData.
     */
    private fun mapResultSetToPlayerData(rs: ResultSet): PlayerData {
        return PlayerData(
            uuid = UUID.fromString(rs.getString("uuid")),
            name = rs.getString("name"),
            currentRankId = rs.getString("current_rank_id"),
            totalSpent = rs.getDouble("total_spent"),
            rankupCount = rs.getInt("rankup_count"),
            firstJoin = parseTimestamp(rs.getString("first_join")),
            lastRankup = rs.getString("last_rankup")?.let { parseTimestamp(it) },
            lastSeen = parseTimestamp(rs.getString("last_seen")),
            metadata = deserializeMetadata(rs.getString("metadata"))
        )
    }

    /**
     * Format timestamp for database storage.
     */
    private fun formatTimestamp(instant: Instant): String {
        return instant.toString()
    }

    /**
     * Parse timestamp from database.
     */
    private fun parseTimestamp(value: String): Instant {
        return try {
            Instant.parse(value)
        } catch (e: Exception) {
            Instant.now()
        }
    }

    /**
     * Serialize metadata map to JSON string.
     */
    private fun serializeMetadata(metadata: Map<String, String>): String? {
        if (metadata.isEmpty()) return null
        return metadata.entries.joinToString(",") { "${it.key}:${it.value}" }
    }

    /**
     * Deserialize metadata from storage format.
     */
    private fun deserializeMetadata(value: String?): Map<String, String> {
        if (value.isNullOrBlank()) return emptyMap()
        return try {
            value.split(",")
                .mapNotNull { entry ->
                    val parts = entry.split(":", limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else null
                }
                .toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
