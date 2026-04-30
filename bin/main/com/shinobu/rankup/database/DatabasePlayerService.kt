package com.shinobu.rankup.database

import com.shinobu.rankup.cache.PlayerCache
import com.shinobu.rankup.data.Database
import com.shinobu.rankup.data.PlayerData
import com.shinobu.rankup.service.PlayerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.bukkit.plugin.java.JavaPlugin
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID
import java.util.logging.Level

/**
 * Universal PlayerService implementation supporting both SQLite and MySQL/MariaDB.
 * Uses the Database class which automatically handles database-specific SQL syntax.
 *
 * All database operations use prepared statements to prevent SQL injection.
 * Operations are performed asynchronously using Kotlin coroutines with Dispatchers.IO.
 *
 * Thread-safety: All public methods are thread-safe.
 * Performance: Uses indexing for efficient queries and batch operations for bulk saves.
 *
 * @property plugin The plugin instance for logging
 * @property database The Database instance with HikariCP connection pooling
 * @property cache The PlayerCache for in-memory caching
 */
class DatabasePlayerService(
    private val plugin: JavaPlugin,
    private val database: Database,
    private val cache: PlayerCache
) : PlayerService {

    /**
     * Get UPSERT SQL based on database type.
     * SQLite uses ON CONFLICT, MySQL uses ON DUPLICATE KEY UPDATE.
     */
    private fun getUpsertSql(): String {
        return when (database.getType()) {
            Database.DatabaseType.SQLITE -> """
                INSERT INTO player_data (uuid, name, current_rank_id, total_spent, rankup_count,
                                        first_join, last_rankup, last_seen, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    name = excluded.name,
                    current_rank_id = excluded.current_rank_id,
                    total_spent = excluded.total_spent,
                    rankup_count = excluded.rankup_count,
                    first_join = excluded.first_join,
                    last_rankup = excluded.last_rankup,
                    last_seen = excluded.last_seen,
                    metadata = excluded.metadata
            """.trimIndent()

            Database.DatabaseType.MYSQL -> """
                INSERT INTO player_data (uuid, name, current_rank_id, total_spent, rankup_count,
                                        first_join, last_rankup, last_seen, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    name = VALUES(name),
                    current_rank_id = VALUES(current_rank_id),
                    total_spent = VALUES(total_spent),
                    rankup_count = VALUES(rankup_count),
                    first_join = VALUES(first_join),
                    last_rankup = VALUES(last_rankup),
                    last_seen = VALUES(last_seen),
                    metadata = VALUES(metadata)
            """.trimIndent()
        }
    }

    companion object {
        // SQL queries as constants (compatible with both SQLite and MySQL)
        private const val SQL_SELECT_BY_UUID = """
            SELECT uuid, name, current_rank_id, total_spent, rankup_count,
                   first_join, last_rankup, last_seen, metadata
            FROM player_data WHERE uuid = ?
        """

        private const val SQL_SELECT_ALL = """
            SELECT uuid, name, current_rank_id, total_spent, rankup_count,
                   first_join, last_rankup, last_seen, metadata
            FROM player_data
        """

        private const val SQL_SELECT_TOP = """
            SELECT uuid, name, current_rank_id, total_spent, rankup_count,
                   first_join, last_rankup, last_seen, metadata
            FROM player_data
            ORDER BY rankup_count DESC, total_spent DESC
            LIMIT ?
        """

        private const val SQL_DELETE = "DELETE FROM player_data WHERE uuid = ?"

        private const val SQL_EXISTS = "SELECT 1 FROM player_data WHERE uuid = ? LIMIT 1"

        private const val SQL_COUNT = "SELECT COUNT(*) FROM player_data"

        private const val SQL_SELECT_BATCH = """
            SELECT uuid, name, current_rank_id, total_spent, rankup_count,
                   first_join, last_rankup, last_seen, metadata
            FROM player_data WHERE uuid IN (%s)
        """
    }

    /**
     * Load player data asynchronously from the database.
     *
     * @param uuid Player's UUID
     * @return Player data, or null if not found
     */
    override suspend fun loadPlayerData(uuid: UUID): PlayerData? = withContext(Dispatchers.IO) {
        try {
            database.queryOne(SQL_SELECT_BY_UUID, uuid.toString()) { rs ->
                mapResultSetToPlayerData(rs)
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to load player data for $uuid", e)
            null
        }
    }

    /**
     * Load player data synchronously.
     * Use only in async contexts like AsyncPlayerPreLoginEvent.
     *
     * @param uuid Player's UUID
     * @return Player data, or null if not found
     */
    override fun loadPlayerDataSync(uuid: UUID): PlayerData? {
        return runBlocking(Dispatchers.IO) {
            loadPlayerData(uuid)
        }
    }

    /**
     * Save player data asynchronously to the database.
     * Uses UPSERT (INSERT ... ON CONFLICT/DUPLICATE KEY) for atomic save operations.
     * Automatically uses the correct SQL syntax for SQLite or MySQL.
     *
     * @param data Player data to save
     * @return true if save was successful
     */
    override suspend fun savePlayerData(data: PlayerData): Boolean = withContext(Dispatchers.IO) {
        try {
            val metadataJson = if (data.metadata.isNotEmpty()) {
                data.metadata.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }
                    .let { "{$it}" }
            } else null

            database.execute(
                getUpsertSql(),
                data.uuid.toString(),
                data.name,
                data.currentRankId,
                data.totalSpent,
                data.rankupCount,
                data.firstJoin.toEpochMilli(),
                data.lastRankup?.toEpochMilli(),
                data.lastSeen.toEpochMilli(),
                metadataJson
            )

            // Update cache
            cache.put(data.uuid, data)

            plugin.logger.fine("Saved player data for ${data.name} (${data.uuid})")
            true
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to save player data for ${data.uuid}", e)
            false
        }
    }

    /**
     * Save player data synchronously.
     *
     * @param data Player data to save
     * @return true if save was successful
     */
    override fun savePlayerDataSync(data: PlayerData): Boolean {
        return runBlocking(Dispatchers.IO) {
            savePlayerData(data)
        }
    }

    /**
     * Delete player data from the database.
     *
     * @param uuid Player's UUID
     * @return true if deletion was successful
     */
    override suspend fun deletePlayerData(uuid: UUID): Boolean = withContext(Dispatchers.IO) {
        try {
            database.execute(SQL_DELETE, uuid.toString())

            // Remove from cache
            cache.remove(uuid)

            plugin.logger.fine("Deleted player data for $uuid")
            true
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to delete player data for $uuid", e)
            false
        }
    }

    /**
     * Check if player data exists in the database.
     *
     * @param uuid Player's UUID
     * @return true if data exists
     */
    override suspend fun playerDataExists(uuid: UUID): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = database.queryOne(SQL_EXISTS, uuid.toString()) { true }
            result ?: false
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to check if player data exists for $uuid", e)
            false
        }
    }

    /**
     * Get all player data from the database.
     * Warning: Use with caution on large databases.
     *
     * @return List of all player data
     */
    override suspend fun getAllPlayerData(): List<PlayerData> = withContext(Dispatchers.IO) {
        try {
            database.query(SQL_SELECT_ALL) { rs ->
                mapResultSetToPlayerData(rs)
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to get all player data", e)
            emptyList()
        }
    }

    /**
     * Get player data for multiple UUIDs efficiently.
     * Uses a single query with IN clause for better performance.
     *
     * @param uuids List of player UUIDs
     * @return Map of UUID to PlayerData
     */
    override suspend fun getPlayerDataBatch(uuids: List<UUID>): Map<UUID, PlayerData> =
        withContext(Dispatchers.IO) {
            if (uuids.isEmpty()) return@withContext emptyMap()

            try {
                // Build parameterized IN clause
                val placeholders = uuids.joinToString(",") { "?" }
                val sql = SQL_SELECT_BATCH.format(placeholders)
                val params = uuids.map { it.toString() }.toTypedArray()

                database.query(sql, *params) { rs ->
                    mapResultSetToPlayerData(rs)
                }.associateBy { it.uuid }
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "Failed to get player data batch", e)
                emptyMap()
            }
        }

    /**
     * Save multiple player data entries efficiently.
     * Uses batch execution for better performance.
     *
     * @param dataList List of player data to save
     * @return Number of successfully saved entries
     */
    override suspend fun savePlayerDataBatch(dataList: List<PlayerData>): Int =
        withContext(Dispatchers.IO) {
            if (dataList.isEmpty()) return@withContext 0

            try {
                val statements = dataList.map { data ->
                    val metadataJson = if (data.metadata.isNotEmpty()) {
                        data.metadata.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }
                            .let { "{$it}" }
                    } else null

                    getUpsertSql() to arrayOf<Any?>(
                        data.uuid.toString(),
                        data.name,
                        data.currentRankId,
                        data.totalSpent,
                        data.rankupCount,
                        data.firstJoin.toEpochMilli(),
                        data.lastRankup?.toEpochMilli(),
                        data.lastSeen.toEpochMilli(),
                        metadataJson
                    )
                }

                val success = database.executeBatch(statements)

                if (success) {
                    // Update cache for all saved entries
                    dataList.forEach { data ->
                        cache.put(data.uuid, data)
                    }
                    plugin.logger.fine("Batch saved ${dataList.size} player data entries")
                    dataList.size
                } else {
                    0
                }
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "Failed to batch save player data", e)
                0
            }
        }

    /**
     * Get top players by rank count and total spent.
     *
     * @param limit Maximum number of results
     * @return List of top player data
     */
    override suspend fun getTopPlayers(limit: Int): List<PlayerData> = withContext(Dispatchers.IO) {
        try {
            database.query(SQL_SELECT_TOP, limit) { rs ->
                mapResultSetToPlayerData(rs)
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to get top players", e)
            emptyList()
        }
    }

    /**
     * Get total number of registered players.
     *
     * @return Total player count
     */
    override suspend fun getPlayerCount(): Long = withContext(Dispatchers.IO) {
        try {
            database.queryOne(SQL_COUNT) { rs ->
                rs.getLong(1)
            } ?: 0L
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to get player count", e)
            0L
        }
    }

    /**
     * Map a ResultSet row to PlayerData.
     * Handles both SQLite (epoch millis as BIGINT) and MySQL (TIMESTAMP) formats.
     */
    private fun mapResultSetToPlayerData(rs: ResultSet): PlayerData {
        val metadataJson = rs.getString("metadata")
        val metadata = if (metadataJson != null && metadataJson.isNotEmpty()) {
            parseMetadataJson(metadataJson)
        } else {
            emptyMap()
        }

        // Handle timestamp parsing for both SQLite (epoch millis) and MySQL (TIMESTAMP)
        val firstJoin = parseTimestamp(rs, "first_join")
        val lastRankup = parseTimestampNullable(rs, "last_rankup")
        val lastSeen = parseTimestamp(rs, "last_seen")

        return PlayerData(
            uuid = UUID.fromString(rs.getString("uuid")),
            name = rs.getString("name"),
            currentRankId = rs.getString("current_rank_id"),
            totalSpent = rs.getDouble("total_spent"),
            rankupCount = rs.getInt("rankup_count"),
            firstJoin = firstJoin,
            lastRankup = lastRankup,
            lastSeen = lastSeen,
            metadata = metadata
        )
    }

    /**
     * Parse timestamp from ResultSet, handling both epoch millis and TIMESTAMP formats.
     */
    private fun parseTimestamp(rs: ResultSet, column: String): Instant {
        return try {
            when (database.getType()) {
                Database.DatabaseType.SQLITE -> {
                    // SQLite stores as epoch millis (BIGINT) or TEXT
                    val value = rs.getLong(column)
                    if (rs.wasNull() || value == 0L) Instant.now() else Instant.ofEpochMilli(value)
                }
                Database.DatabaseType.MYSQL -> {
                    // MySQL stores as TIMESTAMP
                    val timestamp = rs.getTimestamp(column)
                    timestamp?.toInstant() ?: Instant.now()
                }
            }
        } catch (e: Exception) {
            // Fallback: try to read as long (epoch millis)
            try {
                val value = rs.getLong(column)
                if (value > 0) Instant.ofEpochMilli(value) else Instant.now()
            } catch (e2: Exception) {
                Instant.now()
            }
        }
    }

    /**
     * Parse nullable timestamp from ResultSet.
     */
    private fun parseTimestampNullable(rs: ResultSet, column: String): Instant? {
        return try {
            when (database.getType()) {
                Database.DatabaseType.SQLITE -> {
                    val value = rs.getLong(column)
                    if (rs.wasNull() || value == 0L) null else Instant.ofEpochMilli(value)
                }
                Database.DatabaseType.MYSQL -> {
                    val timestamp = rs.getTimestamp(column)
                    timestamp?.toInstant()
                }
            }
        } catch (e: Exception) {
            try {
                val value = rs.getLong(column)
                if (value > 0) Instant.ofEpochMilli(value) else null
            } catch (e2: Exception) {
                null
            }
        }
    }

    /**
     * Parse simple JSON metadata string to map.
     * Format: {"key1":"value1","key2":"value2"}
     */
    private fun parseMetadataJson(json: String): Map<String, String> {
        if (json.isBlank() || json == "{}") return emptyMap()

        return try {
            val content = json.trim().removeSurrounding("{", "}")
            if (content.isBlank()) return emptyMap()

            content.split(",")
                .mapNotNull { pair ->
                    val parts = pair.split(":", limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim().removeSurrounding("\"")
                        val value = parts[1].trim().removeSurrounding("\"")
                        key to value
                    } else null
                }
                .toMap()
        } catch (e: Exception) {
            plugin.logger.log(Level.FINE, "Failed to parse metadata JSON: $json", e)
            emptyMap()
        }
    }
}
