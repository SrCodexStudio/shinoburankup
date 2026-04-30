package com.shinobu.rankup.cache

import com.shinobu.rankup.BuildConfig
import com.shinobu.rankup.data.RankData
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe cache for rank configurations.
 *
 * This cache is designed for quick access to rank data with the following features:
 * - O(1) lookup by ID
 * - Cached sorted list for ordered iteration
 * - Thread-safe operations
 * - Support for next/previous rank navigation
 */
class RankCache {

    private val ranksById = ConcurrentHashMap<String, RankData>()
    private val lock = ReentrantReadWriteLock()

    @Volatile
    private var sortedRanks: List<RankData> = emptyList()

    @Volatile
    private var defaultRank: RankData? = null

    @Volatile
    private var maxRank: RankData? = null

    // Pre-computed navigation maps for O(1) next/previous lookup
    private val nextRankMap = ConcurrentHashMap<String, RankData>()
    private val previousRankMap = ConcurrentHashMap<String, RankData>()

    /**
     * Load all ranks into the cache.
     * This should be called during plugin initialization and reload.
     *
     * @param ranks List of all rank configurations
     */
    fun loadAll(ranks: List<RankData>) {
        lock.write {
            // Clear existing data
            ranksById.clear()
            nextRankMap.clear()
            previousRankMap.clear()

            // Defense-in-depth: enforce FREE version rank limit at the cache layer
            // This prevents bypasses even if the caller forgets to truncate
            val effectiveRanks = if (BuildConfig.isFreeVersion() && ranks.size > BuildConfig.FREE_MAX_RANKS) {
                ranks.sortedBy { it.order }.take(BuildConfig.FREE_MAX_RANKS)
            } else {
                ranks
            }

            // Populate the cache
            effectiveRanks.forEach { rank ->
                ranksById[rank.id] = rank
            }

            // Sort by order
            sortedRanks = effectiveRanks.sortedBy { it.order }

            // Set default (lowest order) and max (highest order) ranks
            defaultRank = sortedRanks.firstOrNull()
            maxRank = sortedRanks.lastOrNull()

            // Build navigation maps
            for (i in sortedRanks.indices) {
                val current = sortedRanks[i]
                if (i < sortedRanks.size - 1) {
                    nextRankMap[current.id] = sortedRanks[i + 1]
                }
                if (i > 0) {
                    previousRankMap[current.id] = sortedRanks[i - 1]
                }
            }
        }
    }

    /**
     * Get a rank by its ID.
     *
     * @param id The rank ID
     * @return The rank, or null if not found
     */
    fun getById(id: String): RankData? = ranksById[id]

    /**
     * Get all ranks sorted by order.
     *
     * @return Sorted list of all ranks
     */
    fun getAllSorted(): List<RankData> = lock.read { sortedRanks.toList() }

    /**
     * Get the default (starting) rank.
     *
     * @return The default rank, or null if no ranks are loaded
     */
    fun getDefault(): RankData? = defaultRank

    /**
     * Get the maximum (highest) rank.
     *
     * @return The max rank, or null if no ranks are loaded
     */
    fun getMax(): RankData? = maxRank

    /**
     * Get the next rank after the specified rank.
     *
     * @param currentId The current rank ID
     * @return The next rank, or null if at max rank
     */
    fun getNextRank(currentId: String): RankData? = nextRankMap[currentId]

    /**
     * Get the previous rank before the specified rank.
     *
     * @param currentId The current rank ID
     * @return The previous rank, or null if at first rank
     */
    fun getPreviousRank(currentId: String): RankData? = previousRankMap[currentId]

    /**
     * Check if a rank ID exists.
     *
     * @param id The rank ID to check
     * @return true if the rank exists
     */
    fun exists(id: String): Boolean = ranksById.containsKey(id)

    /**
     * Check if a rank is the maximum rank.
     *
     * @param id The rank ID to check
     * @return true if this is the max rank
     */
    fun isMaxRank(id: String): Boolean = maxRank?.id == id

    /**
     * Check if a rank is the default rank.
     *
     * @param id The rank ID to check
     * @return true if this is the default rank
     */
    fun isDefaultRank(id: String): Boolean = defaultRank?.id == id

    /**
     * Get all ranks from a starting rank to a target rank (inclusive).
     *
     * @param fromId The starting rank ID
     * @param toId The target rank ID
     * @return List of ranks in the path, or empty if invalid
     */
    fun getRankPath(fromId: String, toId: String): List<RankData> {
        return lock.read {
            val fromRank = ranksById[fromId] ?: return@read emptyList()
            val toRank = ranksById[toId] ?: return@read emptyList()

            if (fromRank.order >= toRank.order) {
                return@read emptyList()
            }

            sortedRanks.filter { it.order > fromRank.order && it.order <= toRank.order }
        }
    }

    /**
     * Calculate the total cost to reach a rank from another rank.
     *
     * @param fromId The starting rank ID
     * @param toId The target rank ID
     * @return Total cost, or -1 if invalid path
     */
    fun calculateCost(fromId: String, toId: String): Double {
        val path = getRankPath(fromId, toId)
        return if (path.isEmpty()) -1.0 else path.sumOf { it.cost }
    }

    /**
     * Get the rank at a specific position in the order.
     *
     * @param order The order position (0-indexed from sorted list)
     * @return The rank at that position, or null if out of bounds
     */
    fun getByOrder(order: Int): RankData? {
        return lock.read {
            sortedRanks.find { it.order == order }
        }
    }

    /**
     * Get the position of a rank in the sorted order.
     *
     * @param id The rank ID
     * @return Position (0-indexed), or -1 if not found
     */
    fun getPosition(id: String): Int {
        return lock.read {
            sortedRanks.indexOfFirst { it.id == id }
        }
    }

    /**
     * Get the total number of ranks.
     */
    fun size(): Int = ranksById.size

    /**
     * Check if the cache is empty.
     */
    fun isEmpty(): Boolean = ranksById.isEmpty()

    /**
     * Clear all cached ranks.
     */
    fun clear() {
        lock.write {
            ranksById.clear()
            nextRankMap.clear()
            previousRankMap.clear()
            sortedRanks = emptyList()
            defaultRank = null
            maxRank = null
        }
    }

    /**
     * Find ranks matching a predicate.
     *
     * @param predicate The filter predicate
     * @return List of matching ranks
     */
    fun filter(predicate: (RankData) -> Boolean): List<RankData> {
        return lock.read {
            sortedRanks.filter(predicate)
        }
    }

    /**
     * Get ranks within a cost range.
     *
     * @param minCost Minimum cost (inclusive)
     * @param maxCost Maximum cost (inclusive)
     * @return List of ranks within the cost range
     */
    fun getByPriceRange(minCost: Double, maxCost: Double): List<RankData> {
        return filter { it.cost in minCost..maxCost }
    }
}
