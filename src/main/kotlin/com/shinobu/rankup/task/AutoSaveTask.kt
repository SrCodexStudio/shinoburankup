package com.shinobu.rankup.task

import com.shinobu.rankup.cache.PlayerCache
import com.shinobu.rankup.service.PlayerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Level

/**
 * Background task for periodically saving cached player data.
 *
 * Features:
 * - Configurable save interval
 * - Only saves dirty (modified) entries
 * - Thread-safe statistics tracking
 * - Graceful shutdown with final save
 */
class AutoSaveTask(
    private val plugin: JavaPlugin,
    private val playerService: PlayerService,
    private val playerCache: PlayerCache,
    private val intervalTicks: Long = 20L * 60 * 5 // Default: 5 minutes
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var bukkitTask: BukkitTask? = null

    // Statistics
    private val totalSaves = AtomicLong(0)
    private val totalErrors = AtomicLong(0)
    private val lastSaveCount = AtomicInteger(0)
    private val lastSaveTime = AtomicLong(0)
    private val isRunning = AtomicBoolean(false)

    // Dirty tracking
    private val dirtyPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet<java.util.UUID>()

    /**
     * Start the auto-save task.
     */
    fun start() {
        if (bukkitTask != null) {
            plugin.logger.warning("AutoSaveTask is already running!")
            return
        }

        bukkitTask = plugin.server.scheduler.runTaskTimerAsynchronously(
            plugin,
            Runnable { runSave() },
            intervalTicks,
            intervalTicks
        )

        isRunning.set(true)
        plugin.logger.info("AutoSaveTask started with interval of ${intervalTicks / 20} seconds")
    }

    /**
     * Stop the auto-save task.
     *
     * @param finalSave Whether to perform a final save before stopping
     */
    fun stop(finalSave: Boolean = true) {
        isRunning.set(false)

        bukkitTask?.cancel()
        bukkitTask = null

        if (finalSave) {
            plugin.logger.info("Performing final save before shutdown...")
            runSaveBlocking()
        }

        scope.cancel()
        plugin.logger.info("AutoSaveTask stopped")
    }

    /**
     * Mark a player as dirty (needing save).
     */
    fun markDirty(uuid: java.util.UUID) {
        dirtyPlayers.add(uuid)
    }

    /**
     * Mark multiple players as dirty.
     */
    fun markDirty(uuids: Collection<java.util.UUID>) {
        dirtyPlayers.addAll(uuids)
    }

    /**
     * Check if a player is marked as dirty.
     */
    fun isDirty(uuid: java.util.UUID): Boolean = dirtyPlayers.contains(uuid)

    /**
     * Run the save operation asynchronously.
     */
    private fun runSave() {
        if (!isRunning.get()) return

        scope.launch {
            try {
                val saved = performSave()
                lastSaveCount.set(saved)
                lastSaveTime.set(System.currentTimeMillis())

                if (saved > 0) {
                    plugin.logger.fine("AutoSave completed: $saved players saved")
                }
            } catch (e: Exception) {
                totalErrors.incrementAndGet()
                plugin.logger.log(Level.WARNING, "AutoSave failed", e)
            }
        }
    }

    /**
     * Run the save operation blocking (for shutdown).
     */
    private fun runSaveBlocking() {
        try {
            kotlinx.coroutines.runBlocking {
                val saved = performSave()
                plugin.logger.info("Final save completed: $saved players saved")
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Final save failed", e)
        }
    }

    /**
     * Perform the actual save operation.
     *
     * @return Number of players saved
     */
    private suspend fun performSave(): Int {
        // Get dirty players or all online players if no dirty tracking
        val playersToSave = if (dirtyPlayers.isNotEmpty()) {
            dirtyPlayers.toList().also { dirtyPlayers.clear() }
        } else {
            plugin.server.onlinePlayers.map { it.uniqueId }
        }

        if (playersToSave.isEmpty()) {
            return 0
        }

        var savedCount = 0
        var errorCount = 0

        for (uuid in playersToSave) {
            if (!scope.isActive) break

            try {
                val data = playerCache.get(uuid)
                if (data != null) {
                    if (playerService.savePlayerData(data)) {
                        savedCount++
                        totalSaves.incrementAndGet()
                    } else {
                        errorCount++
                        // Re-mark as dirty for retry
                        dirtyPlayers.add(uuid)
                    }
                }
            } catch (e: Exception) {
                errorCount++
                totalErrors.incrementAndGet()
                dirtyPlayers.add(uuid) // Re-mark for retry
                plugin.logger.log(Level.FINE, "Failed to save player $uuid", e)
            }
        }

        if (errorCount > 0) {
            plugin.logger.warning("AutoSave: $errorCount errors occurred during save")
        }

        return savedCount
    }

    /**
     * Force an immediate save of all cached data.
     *
     * @return Number of players saved
     */
    suspend fun forceSave(): Int {
        // Mark all cached players as dirty
        dirtyPlayers.addAll(playerCache.getCachedUUIDs())
        return performSave()
    }

    /**
     * Get auto-save statistics.
     */
    fun getStats(): AutoSaveStats {
        return AutoSaveStats(
            isRunning = isRunning.get(),
            totalSaves = totalSaves.get(),
            totalErrors = totalErrors.get(),
            lastSaveCount = lastSaveCount.get(),
            lastSaveTime = if (lastSaveTime.get() > 0) Instant.ofEpochMilli(lastSaveTime.get()) else null,
            pendingDirtyCount = dirtyPlayers.size,
            intervalTicks = intervalTicks
        )
    }

    /**
     * Reset statistics.
     */
    fun resetStats() {
        totalSaves.set(0)
        totalErrors.set(0)
        lastSaveCount.set(0)
        lastSaveTime.set(0)
    }

    data class AutoSaveStats(
        val isRunning: Boolean,
        val totalSaves: Long,
        val totalErrors: Long,
        val lastSaveCount: Int,
        val lastSaveTime: Instant?,
        val pendingDirtyCount: Int,
        val intervalTicks: Long
    ) {
        val intervalSeconds: Long get() = intervalTicks / 20
    }
}
