package com.shinobu.rankup.cache

import com.shinobu.rankup.data.PlayerData
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe LRU cache for player data with TTL support.
 *
 * This cache is designed for high-concurrency access with the following features:
 * - LRU eviction when capacity is exceeded
 * - Time-based expiration (TTL)
 * - Thread-safe operations using read-write locks
 * - Automatic cleanup of expired entries
 *
 * @param maxSize Maximum number of entries to cache
 * @param ttl Time-to-live for cache entries
 */
class PlayerCache(
    private val maxSize: Int = 500,
    private val ttl: Duration = Duration.ofMinutes(10)
) {

    private data class CacheEntry(
        val data: PlayerData,
        val createdAt: Instant = Instant.now(),
        var lastAccess: Instant = Instant.now(),
        val entryTtl: Duration
    ) {
        fun isExpired(): Boolean = Instant.now().isAfter(createdAt.plus(entryTtl))

        fun touch() {
            lastAccess = Instant.now()
        }
    }

    private val cache = ConcurrentHashMap<UUID, CacheEntry>()
    private val lock = ReentrantReadWriteLock()

    // Statistics
    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)

    /**
     * Get player data from cache.
     *
     * @param uuid Player's UUID
     * @return Cached player data, or null if not found or expired
     */
    fun get(uuid: UUID): PlayerData? {
        val entry = cache[uuid]

        return when {
            entry == null -> {
                misses.incrementAndGet()
                null
            }
            entry.isExpired() -> {
                cache.remove(uuid)
                misses.incrementAndGet()
                null
            }
            else -> {
                entry.touch()
                hits.incrementAndGet()
                entry.data
            }
        }
    }

    /**
     * Put player data into cache.
     *
     * @param uuid Player's UUID
     * @param data Player data to cache
     */
    fun put(uuid: UUID, data: PlayerData) {
        lock.write {
            // Evict if at capacity
            if (cache.size >= maxSize && !cache.containsKey(uuid)) {
                evictLRU()
            }

            cache[uuid] = CacheEntry(data, entryTtl = ttl)
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
        return lock.write {
            val entry = cache[uuid]
            if (entry != null && !entry.isExpired()) {
                val updated = updater(entry.data)
                cache[uuid] = CacheEntry(updated, entryTtl = ttl)
                updated
            } else {
                null
            }
        }
    }

    /**
     * Remove player data from cache.
     *
     * @param uuid Player's UUID
     * @return The removed data, or null if not found
     */
    fun remove(uuid: UUID): PlayerData? {
        return cache.remove(uuid)?.data
    }

    /**
     * Check if a player is in cache and not expired.
     *
     * @param uuid Player's UUID
     * @return true if player data is cached and valid
     */
    fun contains(uuid: UUID): Boolean {
        val entry = cache[uuid] ?: return false
        if (entry.isExpired()) {
            cache.remove(uuid)
            return false
        }
        return true
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
     * Get all cached player data (excluding expired entries).
     *
     * @return Map of UUID to PlayerData
     */
    fun getAll(): Map<UUID, PlayerData> {
        return lock.read {
            cache.entries
                .filter { !it.value.isExpired() }
                .associate { it.key to it.value.data }
        }
    }

    /**
     * Get all cached UUIDs.
     *
     * @return Set of cached player UUIDs
     */
    fun getCachedUUIDs(): Set<UUID> {
        return cache.keys.toSet()
    }

    /**
     * Clear all entries from cache.
     */
    fun clear() {
        lock.write {
            cache.clear()
        }
    }

    /**
     * Remove all expired entries from cache.
     *
     * @return Number of entries removed
     */
    fun cleanupExpired(): Int {
        return lock.write {
            val expiredKeys = cache.entries
                .filter { it.value.isExpired() }
                .map { it.key }

            expiredKeys.forEach { cache.remove(it) }
            expiredKeys.size
        }
    }

    /**
     * Evict the least recently used entry.
     * Must be called within a write lock.
     */
    private fun evictLRU() {
        val lru = cache.entries
            .minByOrNull { it.value.lastAccess }
            ?.key

        lru?.let { cache.remove(it) }
    }

    /**
     * Get the current size of the cache.
     */
    fun size(): Int = cache.size

    /**
     * Get cache statistics.
     */
    fun getStats(): CacheStats {
        val h = hits.get()
        val m = misses.get()
        return CacheStats(
            size = cache.size,
            maxSize = maxSize,
            hits = h,
            misses = m,
            hitRate = if (h + m > 0) h.toDouble() / (h + m) else 0.0
        )
    }

    /**
     * Reset cache statistics.
     */
    fun resetStats() {
        hits.set(0)
        misses.set(0)
    }

    data class CacheStats(
        val size: Int,
        val maxSize: Int,
        val hits: Long,
        val misses: Long,
        val hitRate: Double
    )
}
