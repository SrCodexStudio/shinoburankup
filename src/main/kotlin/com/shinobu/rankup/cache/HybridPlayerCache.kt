package com.shinobu.rankup.cache

import com.shinobu.rankup.data.PlayerData
import kotlinx.coroutines.*
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Hybrid cache that combines local in-memory cache with optional Redis distributed cache.
 *
 * Cache hierarchy:
 * 1. L1 Cache: Local in-memory (PlayerCache) - fastest, single server
 * 2. L2 Cache: Redis distributed (RedisCache) - slower, multi-server sync
 *
 * Read path: L1 -> L2 -> Database
 * Write path: L1 + L2 (parallel)
 *
 * If Redis is disabled or unavailable, falls back to L1-only mode seamlessly.
 *
 * @property localCache The local in-memory cache
 * @property redisCache Optional Redis cache (null if disabled)
 */
class HybridPlayerCache(
    private val localCache: PlayerCache,
    private val redisCache: RedisCache? = null
) {

    private val logger = Logger.getLogger(HybridPlayerCache::class.java.name)

    /**
     * Dedicated coroutine scope for fire-and-forget Redis writes.
     * Uses Dispatchers.IO since Redis operations are I/O-bound.
     * SupervisorJob ensures one failed write doesn't cancel others.
     */
    private val redisScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Check if Redis is available.
     */
    fun isRedisEnabled(): Boolean = redisCache?.isAvailable() == true

    /**
     * Get player data from cache (synchronous, L1 only).
     * Only checks the local in-memory cache to avoid blocking the main thread.
     * Use [getAsync] in suspend contexts for full L1 + L2 lookup.
     *
     * @param uuid Player's UUID
     * @return Cached player data from L1, or null if not found locally
     */
    fun get(uuid: UUID): PlayerData? {
        return localCache.get(uuid)
    }

    /**
     * Get player data with async Redis lookup.
     * Use this in suspend functions for better performance.
     *
     * @param uuid Player's UUID
     * @return Cached player data, or null if not found
     */
    suspend fun getAsync(uuid: UUID): PlayerData? {
        // Try L1 (local) first
        localCache.get(uuid)?.let { return it }

        // Try L2 (Redis) if available
        if (isRedisEnabled()) {
            val redisData = redisCache?.get(uuid)

            if (redisData != null) {
                // Populate L1 from L2
                localCache.put(uuid, redisData)
                return redisData
            }
        }

        return null
    }

    /**
     * Put player data into cache.
     * Writes to both L1 and L2 (if available).
     *
     * @param uuid Player's UUID
     * @param data Player data to cache
     */
    fun put(uuid: UUID, data: PlayerData) {
        // Always write to L1
        localCache.put(uuid, data)

        // Write to L2 if available (fire-and-forget, never blocks main thread)
        if (isRedisEnabled()) {
            redisScope.launch {
                try {
                    redisCache?.put(uuid, data)
                } catch (e: Exception) {
                    logger.log(Level.WARNING, "Failed to write player $uuid to Redis (L2)", e)
                }
            }
        }
    }

    /**
     * Put player data with async Redis write.
     * Use this in suspend functions for better performance.
     *
     * @param uuid Player's UUID
     * @param data Player data to cache
     */
    suspend fun putAsync(uuid: UUID, data: PlayerData) {
        // Always write to L1
        localCache.put(uuid, data)

        // Write to L2 if available
        if (isRedisEnabled()) {
            redisCache?.put(uuid, data)
        }
    }

    /**
     * Update player data in cache if it exists.
     *
     * @param uuid Player's UUID
     * @param updater Function to update the player data
     * @return Updated player data, or null if not in cache
     */
    fun update(uuid: UUID, updater: (PlayerData) -> PlayerData): PlayerData? {
        val updated = localCache.update(uuid, updater)

        // Fire-and-forget write to L2, local cache is authoritative
        if (updated != null && isRedisEnabled()) {
            redisScope.launch {
                try {
                    redisCache?.put(uuid, updated)
                } catch (e: Exception) {
                    logger.log(Level.WARNING, "Failed to update player $uuid in Redis (L2)", e)
                }
            }
        }

        return updated
    }

    /**
     * Remove player data from cache.
     *
     * @param uuid Player's UUID
     * @return The removed data from L1, or null if not found
     */
    fun remove(uuid: UUID): PlayerData? {
        val removed = localCache.remove(uuid)

        // Fire-and-forget removal from L2
        if (isRedisEnabled()) {
            redisScope.launch {
                try {
                    redisCache?.remove(uuid)
                } catch (e: Exception) {
                    logger.log(Level.WARNING, "Failed to remove player $uuid from Redis (L2)", e)
                }
            }
        }

        return removed
    }

    /**
     * Remove player data with async Redis operation.
     *
     * @param uuid Player's UUID
     * @return The removed data from L1, or null if not found
     */
    suspend fun removeAsync(uuid: UUID): PlayerData? {
        val removed = localCache.remove(uuid)

        if (isRedisEnabled()) {
            redisCache?.remove(uuid)
        }

        return removed
    }

    /**
     * Check if a player is in the local L1 cache (synchronous, non-blocking).
     * Only checks the local in-memory cache to avoid blocking the main thread.
     * For a full L1 + L2 check, use the suspend variant [getAsync] != null.
     *
     * @param uuid Player's UUID
     * @return true if player data is in local cache
     */
    fun contains(uuid: UUID): Boolean {
        return localCache.contains(uuid)
    }

    /**
     * Get or load player data.
     *
     * @param uuid Player's UUID
     * @param loader Function to load data if not in cache
     * @return Player data from cache or loaded
     */
    inline fun getOrLoad(uuid: UUID, loader: () -> PlayerData?): PlayerData? {
        get(uuid)?.let { return it }

        return loader()?.also { put(uuid, it) }
    }

    /**
     * Get all cached player data from L1.
     *
     * @return Map of UUID to PlayerData
     */
    fun getAll(): Map<UUID, PlayerData> {
        return localCache.getAll()
    }

    /**
     * Get all cached UUIDs from L1.
     *
     * @return Set of cached player UUIDs
     */
    fun getCachedUUIDs(): Set<UUID> {
        return localCache.getCachedUUIDs()
    }

    /**
     * Clear all entries from both caches.
     * L1 is cleared immediately; L2 invalidation is fire-and-forget.
     */
    fun clear() {
        localCache.clear()

        // Fire-and-forget invalidation of L2
        if (isRedisEnabled()) {
            redisScope.launch {
                try {
                    redisCache?.invalidateAll()
                } catch (e: Exception) {
                    logger.log(Level.WARNING, "Failed to invalidate Redis cache (L2)", e)
                }
            }
        }
    }

    /**
     * Clear all entries with async Redis operation.
     */
    suspend fun clearAsync() {
        localCache.clear()

        if (isRedisEnabled()) {
            redisCache?.invalidateAll()
        }
    }

    /**
     * Remove all expired entries from L1 cache.
     *
     * @return Number of entries removed
     */
    fun cleanupExpired(): Int {
        return localCache.cleanupExpired()
    }

    /**
     * Get the current size of L1 cache.
     */
    fun size(): Int = localCache.size()

    /**
     * Get combined cache statistics.
     */
    fun getStats(): HybridCacheStats {
        val localStats = localCache.getStats()
        val redisStats = redisCache?.getStats()

        return HybridCacheStats(
            localSize = localStats.size,
            localMaxSize = localStats.maxSize,
            localHits = localStats.hits,
            localMisses = localStats.misses,
            localHitRate = localStats.hitRate,
            redisEnabled = isRedisEnabled(),
            redisHits = redisStats?.hits ?: 0,
            redisMisses = redisStats?.misses ?: 0,
            redisErrors = redisStats?.errors ?: 0,
            redisHitRate = redisStats?.hitRate ?: 0.0
        )
    }

    /**
     * Reset statistics for both caches.
     */
    fun resetStats() {
        localCache.resetStats()
        redisCache?.resetStats()
    }

    /**
     * Shutdown the hybrid cache.
     * Cancels all pending Redis writes and closes the Redis connection.
     */
    fun shutdown() {
        redisScope.cancel()
        redisCache?.close()
    }

    data class HybridCacheStats(
        val localSize: Int,
        val localMaxSize: Int,
        val localHits: Long,
        val localMisses: Long,
        val localHitRate: Double,
        val redisEnabled: Boolean,
        val redisHits: Long,
        val redisMisses: Long,
        val redisErrors: Long,
        val redisHitRate: Double
    )
}
