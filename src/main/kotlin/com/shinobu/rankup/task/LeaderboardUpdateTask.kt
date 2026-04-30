package com.shinobu.rankup.task

import com.shinobu.rankup.cache.LeaderboardCache
import com.shinobu.rankup.cache.RankCache
import com.shinobu.rankup.service.PlayerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Level

/**
 * Background task for periodically updating the leaderboard cache.
 *
 * Features:
 * - Configurable update interval
 * - Smart refresh (only updates if cache is stale)
 * - Thread-safe statistics tracking
 * - Graceful handling of database errors
 */
class LeaderboardUpdateTask(
    private val plugin: JavaPlugin,
    private val playerService: PlayerService,
    private val leaderboardCache: LeaderboardCache,
    private val rankCache: RankCache,
    private val intervalTicks: Long = 20L * 60 * 5, // Default: 5 minutes
    private val maxEntries: Int = 100
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var bukkitTask: BukkitTask? = null

    // Statistics
    private val totalUpdates = AtomicLong(0)
    private val totalErrors = AtomicLong(0)
    private val lastUpdateDuration = AtomicLong(0)
    private val lastUpdateTime = AtomicLong(0)
    private val isRunning = AtomicBoolean(false)
    private val isUpdating = AtomicBoolean(false)

    /**
     * Start the leaderboard update task.
     */
    fun start() {
        if (bukkitTask != null) {
            plugin.logger.warning("LeaderboardUpdateTask is already running!")
            return
        }

        // Run immediately once, then periodically
        bukkitTask = plugin.server.scheduler.runTaskTimerAsynchronously(
            plugin,
            Runnable { runUpdate() },
            20L, // Initial delay: 1 second
            intervalTicks
        )

        isRunning.set(true)
        plugin.logger.info("LeaderboardUpdateTask started with interval of ${intervalTicks / 20} seconds")
    }

    /**
     * Stop the leaderboard update task.
     */
    fun stop() {
        isRunning.set(false)

        bukkitTask?.cancel()
        bukkitTask = null

        scope.cancel()
        plugin.logger.info("LeaderboardUpdateTask stopped")
    }

    /**
     * Run the update operation asynchronously.
     */
    private fun runUpdate() {
        if (!isRunning.get()) return

        // Prevent concurrent updates
        if (!isUpdating.compareAndSet(false, true)) {
            plugin.logger.fine("Leaderboard update already in progress, skipping")
            return
        }

        scope.launch {
            try {
                val startTime = System.currentTimeMillis()

                // Only update if cache is stale
                if (leaderboardCache.needsRefresh()) {
                    performUpdate()
                    totalUpdates.incrementAndGet()

                    val duration = System.currentTimeMillis() - startTime
                    lastUpdateDuration.set(duration)
                    lastUpdateTime.set(System.currentTimeMillis())

                    plugin.logger.fine("Leaderboard updated in ${duration}ms")
                }
            } catch (e: Exception) {
                totalErrors.incrementAndGet()
                plugin.logger.log(Level.WARNING, "Leaderboard update failed", e)
            } finally {
                isUpdating.set(false)
            }
        }
    }

    /**
     * Perform the actual update operation.
     */
    private suspend fun performUpdate() {
        // Get top players from database
        val topPlayers = playerService.getTopPlayers(maxEntries)

        // Update the cache
        leaderboardCache.update(topPlayers) { rankId ->
            rankCache.getById(rankId)
        }
    }

    /**
     * Force an immediate update regardless of cache state.
     */
    suspend fun forceUpdate() {
        if (isUpdating.get()) {
            plugin.logger.warning("Leaderboard update already in progress")
            return
        }

        isUpdating.set(true)
        try {
            val startTime = System.currentTimeMillis()

            leaderboardCache.invalidate()
            performUpdate()

            totalUpdates.incrementAndGet()
            lastUpdateDuration.set(System.currentTimeMillis() - startTime)
            lastUpdateTime.set(System.currentTimeMillis())

            plugin.logger.info("Leaderboard force updated")
        } catch (e: Exception) {
            totalErrors.incrementAndGet()
            plugin.logger.log(Level.WARNING, "Leaderboard force update failed", e)
        } finally {
            isUpdating.set(false)
        }
    }

    /**
     * Get update task statistics.
     */
    fun getStats(): LeaderboardUpdateStats {
        return LeaderboardUpdateStats(
            isRunning = isRunning.get(),
            isUpdating = isUpdating.get(),
            totalUpdates = totalUpdates.get(),
            totalErrors = totalErrors.get(),
            lastUpdateDuration = Duration.ofMillis(lastUpdateDuration.get()),
            lastUpdateTime = if (lastUpdateTime.get() > 0) Instant.ofEpochMilli(lastUpdateTime.get()) else null,
            intervalTicks = intervalTicks,
            maxEntries = maxEntries,
            cacheSize = leaderboardCache.size(),
            timeUntilNextUpdate = leaderboardCache.getTimeUntilRefresh()
        )
    }

    /**
     * Reset statistics.
     */
    fun resetStats() {
        totalUpdates.set(0)
        totalErrors.set(0)
        lastUpdateDuration.set(0)
        lastUpdateTime.set(0)
    }

    data class LeaderboardUpdateStats(
        val isRunning: Boolean,
        val isUpdating: Boolean,
        val totalUpdates: Long,
        val totalErrors: Long,
        val lastUpdateDuration: Duration,
        val lastUpdateTime: Instant?,
        val intervalTicks: Long,
        val maxEntries: Int,
        val cacheSize: Int,
        val timeUntilNextUpdate: Duration
    ) {
        val intervalSeconds: Long get() = intervalTicks / 20
    }
}
