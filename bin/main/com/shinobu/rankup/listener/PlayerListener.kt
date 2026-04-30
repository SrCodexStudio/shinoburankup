package com.shinobu.rankup.listener

import com.shinobu.rankup.cache.PlayerCache
import com.shinobu.rankup.data.PlayerData
import com.shinobu.rankup.service.CommandQueueService
import com.shinobu.rankup.service.PlayerService
import com.shinobu.rankup.util.RateLimiter
import com.shinobu.rankup.util.runOnMain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * Handles player-related events for ShinobuRankup.
 *
 * This listener manages:
 * - Pre-loading player data on async login
 * - Initializing or updating player data on join
 * - Saving and cleaning up data on quit
 * - Proper async handling with coroutines
 */
class PlayerListener(
    private val plugin: JavaPlugin,
    private val playerService: PlayerService,
    private val playerCache: PlayerCache,
    private val defaultRankProvider: () -> String,
    private val commandQueueService: CommandQueueService? = null,
    private val onPlayerQuit: ((UUID) -> Unit)? = null
) : Listener {

    // Coroutine scope for async operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Pre-loaded data from AsyncPlayerPreLoginEvent
    private val preloadedData = ConcurrentHashMap<UUID, PlayerData>()

    // Track players being processed to prevent duplicate operations
    private val processingPlayers = ConcurrentHashMap.newKeySet<UUID>()

    // Open GUIs that need cleanup
    private val openGUIs = ConcurrentHashMap<UUID, Any>()

    /**
     * Pre-load player data during async login.
     * This is called on an async thread, so database operations are safe here.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onAsyncPreLogin(event: AsyncPlayerPreLoginEvent) {
        // Only proceed if login is allowed
        if (event.loginResult != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return
        }

        val uuid = event.uniqueId
        val name = event.name

        try {
            // Mark as processing
            if (!processingPlayers.add(uuid)) {
                return // Already being processed
            }

            // Load or create player data
            val existingData = playerService.loadPlayerDataSync(uuid)

            val playerData = if (existingData != null) {
                // Update name if changed
                if (existingData.name != name) {
                    existingData.withName(name).withLastSeen()
                } else {
                    existingData.withLastSeen()
                }
            } else {
                // Create new player data
                createNewPlayerData(uuid, name)
            }

            // Store for pickup on PlayerJoinEvent
            preloadedData[uuid] = playerData

            plugin.logger.fine("Pre-loaded data for $name ($uuid)")

        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to pre-load data for $name", e)
        } finally {
            processingPlayers.remove(uuid)
        }
    }

    /**
     * Handle player join - finalize data loading and apply rank.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val uuid = player.uniqueId

        scope.launch {
            try {
                // Get pre-loaded data or load now
                val playerData = preloadedData.remove(uuid)
                    ?: loadOrCreatePlayerData(player)

                // Cache the data
                playerCache.put(uuid, playerData)

                // Save to database (in case of new player or updates)
                playerService.savePlayerData(playerData)

                // Apply rank permissions/effects on main thread
                plugin.runOnMain {
                    if (player.isOnline) {
                        applyRankEffects(player, playerData)
                    }
                }

                plugin.logger.fine("Loaded data for ${player.name}: rank=${playerData.currentRankId}")

            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "Failed to load data for ${player.name}", e)
            }
        }
    }

    /**
     * Handle player quit - save data and cleanup.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val uuid = player.uniqueId

        // Cleanup any preloaded data that wasn't used
        preloadedData.remove(uuid)

        // Close any open GUIs
        openGUIs.remove(uuid)

        // Cleanup rate limiters to prevent memory leak
        RateLimiter.COMMAND_LIMITER.reset(uuid)
        RateLimiter.ECONOMY_LIMITER.reset(uuid)

        // Cancel pending commands in queue to prevent wasted execution
        commandQueueService?.cancelPlayerCommands(uuid)

        // Notify cleanup callback (for RankupService lock cleanup)
        onPlayerQuit?.invoke(uuid)

        scope.launch {
            try {
                // Get and update cached data
                val playerData = playerCache.get(uuid)
                    ?: playerService.loadPlayerData(uuid)

                if (playerData != null) {
                    // Update last seen
                    val updatedData = playerData.withLastSeen()

                    // Save to database
                    playerService.savePlayerData(updatedData)

                    // Update cache
                    playerCache.put(uuid, updatedData)

                    plugin.logger.fine("Saved data for ${player.name} on quit")
                }

            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "Failed to save data for ${player.name} on quit", e)
            }
        }
    }

    /**
     * Load or create player data for a player.
     */
    private suspend fun loadOrCreatePlayerData(player: Player): PlayerData {
        val uuid = player.uniqueId
        val name = player.name

        // Try to load from database
        val existingData = playerService.loadPlayerData(uuid)

        return if (existingData != null) {
            // Update name if changed
            if (existingData.name != name) {
                existingData.withName(name).withLastSeen()
            } else {
                existingData.withLastSeen()
            }
        } else {
            // Create new player data
            createNewPlayerData(uuid, name)
        }
    }

    /**
     * Create new player data with default rank.
     */
    private fun createNewPlayerData(uuid: UUID, name: String): PlayerData {
        return PlayerData(
            uuid = uuid,
            name = name,
            currentRankId = defaultRankProvider(),
            totalSpent = 0.0,
            rankupCount = 0,
            firstJoin = Instant.now(),
            lastSeen = Instant.now()
        )
    }

    /**
     * Apply rank effects to a player.
     * This should be called on the main thread.
     */
    private fun applyRankEffects(player: Player, data: PlayerData) {
        // Rank effects are now handled by ChatListener directly
        // This method can be extended for other effects like:
        // - Scoreboard updates
        // - Tab list modifications
        // - Welcome messages
        plugin.logger.fine("Player ${player.name} loaded with rank: ${data.currentRankId}")
    }

    /**
     * Register a GUI as open for a player (for cleanup on quit).
     */
    fun registerOpenGUI(uuid: UUID, gui: Any) {
        openGUIs[uuid] = gui
    }

    /**
     * Unregister a GUI for a player.
     */
    fun unregisterOpenGUI(uuid: UUID) {
        openGUIs.remove(uuid)
    }

    /**
     * Check if a player has an open GUI.
     */
    fun hasOpenGUI(uuid: UUID): Boolean = openGUIs.containsKey(uuid)

    /**
     * Get all players currently being processed.
     */
    fun getProcessingPlayers(): Set<UUID> = processingPlayers.toSet()

    /**
     * Cleanup resources.
     * Should be called on plugin disable.
     */
    fun cleanup() {
        scope.cancel() // Cancel all pending coroutines
        preloadedData.clear()
        processingPlayers.clear()
        openGUIs.clear()
    }

    /**
     * Force save all online players' data.
     *
     * @return Number of players saved
     */
    suspend fun saveAllOnlinePlayers(): Int {
        var count = 0

        for (player in plugin.server.onlinePlayers) {
            try {
                val data = playerCache.get(player.uniqueId)
                if (data != null) {
                    playerService.savePlayerData(data.withLastSeen())
                    count++
                }
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "Failed to save data for ${player.name}", e)
            }
        }

        return count
    }
}
