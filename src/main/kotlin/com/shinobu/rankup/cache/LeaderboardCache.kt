package com.shinobu.rankup.cache

import com.shinobu.rankup.data.LeaderboardEntry
import com.shinobu.rankup.data.PlayerData
import com.shinobu.rankup.data.RankData
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Thread-safe cache for leaderboard data.
 *
 * Features:
 * - Cached top players list with TTL
 * - Position lookup by UUID
 * - Configurable update interval
 * - Thread-safe for concurrent access
 *
 * @param maxEntries Maximum number of leaderboard entries to cache
 * @param ttl Time-to-live for the cached leaderboard
 */
class LeaderboardCache(
    private val maxEntries: Int = 100,
    private val ttl: Duration = Duration.ofMinutes(5)
) {

    private val lock = ReentrantReadWriteLock()

    // The cached leaderboard entries
    private val leaderboard = CopyOnWriteArrayList<LeaderboardEntry>()

    // Position lookup map for O(1) position queries
    private val positionMap = mutableMapOf<UUID, Int>()

    // Last update timestamp
    private val lastUpdate = AtomicReference<Instant>(Instant.EPOCH)

    /**
     * Check if the leaderboard needs to be refreshed.
     *
     * @return true if the cache is stale or empty
     */
    fun needsRefresh(): Boolean {
        val last = lastUpdate.get()
        return leaderboard.isEmpty() || Instant.now().isAfter(last.plus(ttl))
    }

    /**
     * Update the leaderboard with new data.
     *
     * @param players List of player data to process
     * @param rankProvider Function to get rank data by ID
     */
    fun update(
        players: List<PlayerData>,
        rankProvider: (String) -> RankData?
    ) {
        lock.write {
            // Sort players by rank order (descending) then by total spent (descending)
            val sorted = players
                .mapNotNull { player ->
                    val rank = rankProvider(player.currentRankId) ?: return@mapNotNull null
                    Triple(player, rank, rank.order)
                }
                .sortedWith(
                    compareByDescending<Triple<PlayerData, RankData, Int>> { it.third }
                        .thenByDescending { it.first.totalSpent }
                        .thenByDescending { it.first.rankupCount }
                )
                .take(maxEntries)

            // Clear and rebuild
            leaderboard.clear()
            positionMap.clear()

            sorted.forEachIndexed { index, (player, rank, _) ->
                val position = index + 1
                leaderboard.add(
                    LeaderboardEntry(
                        position = position,
                        uuid = player.uuid,
                        name = player.name,
                        rankId = player.currentRankId,
                        rankDisplayName = rank.displayName,
                        totalSpent = player.totalSpent,
                        rankupCount = player.rankupCount
                    )
                )
                positionMap[player.uuid] = position
            }

            lastUpdate.set(Instant.now())
        }
    }

    /**
     * Get the top players from the leaderboard.
     *
     * @param limit Maximum number of entries to return
     * @return List of top leaderboard entries
     */
    fun getTop(limit: Int): List<LeaderboardEntry> {
        return lock.read {
            leaderboard.take(limit.coerceAtMost(maxEntries))
        }
    }

    /**
     * Get a specific page of the leaderboard.
     *
     * @param page Page number (1-indexed)
     * @param pageSize Number of entries per page
     * @return List of entries for the page
     */
    fun getPage(page: Int, pageSize: Int): List<LeaderboardEntry> {
        if (page < 1 || pageSize < 1) return emptyList()

        return lock.read {
            val startIndex = (page - 1) * pageSize
            if (startIndex >= leaderboard.size) {
                emptyList()
            } else {
                leaderboard.subList(
                    startIndex,
                    (startIndex + pageSize).coerceAtMost(leaderboard.size)
                )
            }
        }
    }

    /**
     * Get a player's position in the leaderboard.
     *
     * @param uuid Player's UUID
     * @return Position (1-indexed), or -1 if not in leaderboard
     */
    fun getPosition(uuid: UUID): Int {
        return lock.read {
            positionMap[uuid] ?: -1
        }
    }

    /**
     * Get a player's leaderboard entry.
     *
     * @param uuid Player's UUID
     * @return The leaderboard entry, or null if not in leaderboard
     */
    fun getEntry(uuid: UUID): LeaderboardEntry? {
        val position = getPosition(uuid)
        if (position < 1) return null

        return lock.read {
            leaderboard.getOrNull(position - 1)
        }
    }

    /**
     * Check if a player is in the leaderboard.
     *
     * @param uuid Player's UUID
     * @return true if player is in the leaderboard
     */
    fun contains(uuid: UUID): Boolean = getPosition(uuid) > 0

    /**
     * Get the total number of entries in the leaderboard.
     */
    fun size(): Int = leaderboard.size

    /**
     * Get the total number of pages.
     *
     * @param pageSize Number of entries per page
     * @return Total page count
     */
    fun getTotalPages(pageSize: Int): Int {
        if (pageSize < 1) return 0
        return (leaderboard.size + pageSize - 1) / pageSize
    }

    /**
     * Get the last update timestamp.
     */
    fun getLastUpdate(): Instant = lastUpdate.get()

    /**
     * Get time until next refresh is needed.
     */
    fun getTimeUntilRefresh(): Duration {
        val nextRefresh = lastUpdate.get().plus(ttl)
        val now = Instant.now()
        return if (now.isBefore(nextRefresh)) {
            Duration.between(now, nextRefresh)
        } else {
            Duration.ZERO
        }
    }

    /**
     * Force refresh on next access.
     */
    fun invalidate() {
        lastUpdate.set(Instant.EPOCH)
    }

    /**
     * Clear the leaderboard cache.
     */
    fun clear() {
        lock.write {
            leaderboard.clear()
            positionMap.clear()
            lastUpdate.set(Instant.EPOCH)
        }
    }

    /**
     * Get surrounding entries around a player's position.
     *
     * @param uuid Player's UUID
     * @param range Number of entries above and below to include
     * @return List of nearby entries, including the player's entry
     */
    fun getSurrounding(uuid: UUID, range: Int): List<LeaderboardEntry> {
        val position = getPosition(uuid)
        if (position < 1) return emptyList()

        return lock.read {
            val startIndex = (position - 1 - range).coerceAtLeast(0)
            val endIndex = (position - 1 + range + 1).coerceAtMost(leaderboard.size)
            leaderboard.subList(startIndex, endIndex)
        }
    }

    /**
     * Get statistics about the leaderboard.
     */
    fun getStats(): LeaderboardStats {
        return lock.read {
            val totalSpent = leaderboard.sumOf { it.totalSpent }
            val totalRankups = leaderboard.sumOf { it.rankupCount }
            val avgSpent = if (leaderboard.isNotEmpty()) totalSpent / leaderboard.size else 0.0
            val avgRankups = if (leaderboard.isNotEmpty()) totalRankups.toDouble() / leaderboard.size else 0.0

            LeaderboardStats(
                totalEntries = leaderboard.size,
                totalMoneySpent = totalSpent,
                totalRankups = totalRankups,
                averageMoneySpent = avgSpent,
                averageRankups = avgRankups,
                lastUpdate = lastUpdate.get()
            )
        }
    }

    data class LeaderboardStats(
        val totalEntries: Int,
        val totalMoneySpent: Double,
        val totalRankups: Int,
        val averageMoneySpent: Double,
        val averageRankups: Double,
        val lastUpdate: Instant
    )
}
