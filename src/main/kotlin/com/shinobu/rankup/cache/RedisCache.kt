package com.shinobu.rankup.cache

import com.shinobu.rankup.data.PlayerData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.plugin.java.JavaPlugin
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.time.Instant
import java.util.UUID
import java.util.logging.Level

/**
 * Redis-based distributed cache for player data.
 *
 * Provides a distributed caching layer that can be shared across multiple
 * server instances (BungeeCord/Velocity networks).
 *
 * Features:
 * - Distributed cache for multi-server setups
 * - Automatic TTL-based expiration
 * - Connection pooling with Jedis
 * - Graceful fallback on connection errors
 *
 * @property plugin The plugin instance for logging
 * @property config Redis configuration
 */
class RedisCache(
    private val plugin: JavaPlugin,
    private val config: RedisConfig
) : AutoCloseable {

    /**
     * Redis configuration data class.
     */
    data class RedisConfig(
        val host: String = "localhost",
        val port: Int = 6379,
        val password: String? = null,
        val database: Int = 0,
        val timeout: Int = 3000,
        val ssl: Boolean = false,
        val keyPrefix: String = "shinobu:rankup:",
        val ttlSeconds: Int = 600,
        val poolMaxTotal: Int = 16,
        val poolMaxIdle: Int = 8,
        val poolMinIdle: Int = 2,
        val poolMaxWait: Long = 3000
    )

    private var jedisPool: JedisPool? = null
    private var isConnected = false

    // Statistics
    @Volatile
    private var hits: Long = 0

    @Volatile
    private var misses: Long = 0

    @Volatile
    private var errors: Long = 0

    /**
     * Initialize Redis connection pool.
     *
     * @return true if connection was successful
     */
    fun initialize(): Boolean {
        return try {
            val poolConfig = JedisPoolConfig().apply {
                maxTotal = config.poolMaxTotal
                maxIdle = config.poolMaxIdle
                minIdle = config.poolMinIdle
                setMaxWait(java.time.Duration.ofMillis(config.poolMaxWait))
                testOnBorrow = true
                testOnReturn = true
                testWhileIdle = true
            }

            jedisPool = if (config.password.isNullOrBlank()) {
                JedisPool(
                    poolConfig,
                    config.host,
                    config.port,
                    config.timeout,
                    config.ssl
                )
            } else {
                JedisPool(
                    poolConfig,
                    config.host,
                    config.port,
                    config.timeout,
                    config.password,
                    config.database,
                    config.ssl
                )
            }

            // Test connection
            jedisPool?.resource?.use { jedis ->
                jedis.ping()
            }

            isConnected = true
            plugin.logger.info("Redis cache connected successfully to ${config.host}:${config.port}")
            true
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to connect to Redis: ${e.message}")
            plugin.logger.warning("Redis cache will be disabled. Using local memory cache only.")
            isConnected = false
            false
        }
    }

    /**
     * Check if Redis is connected and available.
     */
    fun isAvailable(): Boolean = isConnected && jedisPool != null

    /**
     * Get player data from Redis cache.
     *
     * @param uuid Player's UUID
     * @return Cached player data, or null if not found
     */
    suspend fun get(uuid: UUID): PlayerData? = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext null

        try {
            jedisPool?.resource?.use { jedis ->
                val key = buildKey(uuid)
                val data = jedis.hgetAll(key)

                if (data.isNullOrEmpty()) {
                    misses++
                    return@withContext null
                }

                hits++
                deserializePlayerData(data)
            }
        } catch (e: Exception) {
            errors++
            plugin.logger.log(Level.FINE, "Redis get error for $uuid", e)
            null
        }
    }

    /**
     * Put player data into Redis cache.
     *
     * @param uuid Player's UUID
     * @param data Player data to cache
     */
    suspend fun put(uuid: UUID, data: PlayerData): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext false

        try {
            jedisPool?.resource?.use { jedis ->
                val key = buildKey(uuid)
                val serialized = serializePlayerData(data)

                jedis.hset(key, serialized)
                jedis.expire(key, config.ttlSeconds.toLong())
            }
            true
        } catch (e: Exception) {
            errors++
            plugin.logger.log(Level.FINE, "Redis put error for $uuid", e)
            false
        }
    }

    /**
     * Remove player data from Redis cache.
     *
     * @param uuid Player's UUID
     */
    suspend fun remove(uuid: UUID): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext false

        try {
            jedisPool?.resource?.use { jedis ->
                val key = buildKey(uuid)
                jedis.del(key)
            }
            true
        } catch (e: Exception) {
            errors++
            plugin.logger.log(Level.FINE, "Redis remove error for $uuid", e)
            false
        }
    }

    /**
     * Check if player data exists in Redis cache.
     *
     * @param uuid Player's UUID
     * @return true if data exists
     */
    suspend fun exists(uuid: UUID): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext false

        try {
            jedisPool?.resource?.use { jedis ->
                val key = buildKey(uuid)
                jedis.exists(key)
            } ?: false
        } catch (e: Exception) {
            errors++
            false
        }
    }

    /**
     * Invalidate all cache entries (clear Redis keys with our prefix).
     */
    suspend fun invalidateAll(): Boolean = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext false

        try {
            jedisPool?.resource?.use { jedis ->
                val pattern = "${config.keyPrefix}*"
                var cursor = "0"

                do {
                    val result = jedis.scan(cursor, redis.clients.jedis.params.ScanParams().match(pattern).count(100))
                    cursor = result.cursor

                    if (result.result.isNotEmpty()) {
                        jedis.del(*result.result.toTypedArray())
                    }
                } while (cursor != "0")
            }
            plugin.logger.info("Redis cache invalidated")
            true
        } catch (e: Exception) {
            errors++
            plugin.logger.log(Level.WARNING, "Failed to invalidate Redis cache", e)
            false
        }
    }

    /**
     * Build Redis key for a player UUID.
     */
    private fun buildKey(uuid: UUID): String {
        return "${config.keyPrefix}player:$uuid"
    }

    /**
     * Serialize PlayerData to a map for Redis HSET.
     */
    private fun serializePlayerData(data: PlayerData): Map<String, String> {
        return mapOf(
            "uuid" to data.uuid.toString(),
            "name" to data.name,
            "currentRankId" to data.currentRankId,
            "totalSpent" to data.totalSpent.toString(),
            "rankupCount" to data.rankupCount.toString(),
            "firstJoin" to data.firstJoin.toEpochMilli().toString(),
            "lastRankup" to (data.lastRankup?.toEpochMilli()?.toString() ?: ""),
            "lastSeen" to data.lastSeen.toEpochMilli().toString(),
            "metadata" to serializeMetadata(data.metadata)
        )
    }

    /**
     * Deserialize PlayerData from Redis hash map.
     */
    private fun deserializePlayerData(data: Map<String, String>): PlayerData? {
        return try {
            PlayerData(
                uuid = UUID.fromString(data["uuid"] ?: return null),
                name = data["name"] ?: return null,
                currentRankId = data["currentRankId"] ?: return null,
                totalSpent = data["totalSpent"]?.toDoubleOrNull() ?: 0.0,
                rankupCount = data["rankupCount"]?.toIntOrNull() ?: 0,
                firstJoin = data["firstJoin"]?.toLongOrNull()?.let { Instant.ofEpochMilli(it) } ?: Instant.now(),
                lastRankup = data["lastRankup"]?.toLongOrNull()?.let { Instant.ofEpochMilli(it) },
                lastSeen = data["lastSeen"]?.toLongOrNull()?.let { Instant.ofEpochMilli(it) } ?: Instant.now(),
                metadata = deserializeMetadata(data["metadata"] ?: "")
            )
        } catch (e: Exception) {
            plugin.logger.log(Level.FINE, "Failed to deserialize player data from Redis", e)
            null
        }
    }

    /**
     * Serialize metadata map to JSON string format.
     * Matches the JSON format used by DatabasePlayerService for consistency.
     */
    private fun serializeMetadata(metadata: Map<String, String>): String {
        if (metadata.isEmpty()) return "{}"
        val entries = metadata.entries.joinToString(",") { (k, v) ->
            "\"${k.replace("\"", "\\\"")}\":\"${v.replace("\"", "\\\"")}\""
        }
        return "{$entries}"
    }

    /**
     * Deserialize metadata from JSON string format.
     * Includes fallback for legacy key=value format for backwards compatibility
     * with data that may have been cached before the format migration.
     */
    private fun deserializeMetadata(data: String): Map<String, String> {
        if (data.isBlank() || data == "{}") return emptyMap()

        // Try JSON format first (new format, matches DatabasePlayerService)
        if (data.startsWith("{")) {
            return try {
                val content = data.removeSurrounding("{", "}")
                if (content.isBlank()) return emptyMap()
                content.split(",").associate { entry ->
                    val parts = entry.split(":", limit = 2)
                    val key = parts[0].trim().removeSurrounding("\"")
                    val value = parts.getOrElse(1) { "" }.trim().removeSurrounding("\"")
                    key to value
                }
            } catch (_: Exception) { emptyMap() }
        }

        // Fallback: legacy key=value format
        return try {
            data.split(",").associate { entry ->
                val parts = entry.split("=", limit = 2)
                parts[0].trim() to parts.getOrElse(1) { "" }.trim()
            }
        } catch (_: Exception) { emptyMap() }
    }

    /**
     * Get cache statistics.
     */
    fun getStats(): RedisCacheStats {
        return RedisCacheStats(
            isConnected = isConnected,
            hits = hits,
            misses = misses,
            errors = errors,
            hitRate = if (hits + misses > 0) hits.toDouble() / (hits + misses) else 0.0
        )
    }

    /**
     * Reset statistics.
     */
    fun resetStats() {
        hits = 0
        misses = 0
        errors = 0
    }

    /**
     * Close Redis connection pool.
     */
    override fun close() {
        try {
            jedisPool?.close()
            jedisPool = null
            isConnected = false
            plugin.logger.info("Redis cache connection closed")
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Error closing Redis connection", e)
        }
    }

    data class RedisCacheStats(
        val isConnected: Boolean,
        val hits: Long,
        val misses: Long,
        val errors: Long,
        val hitRate: Double
    )
}
