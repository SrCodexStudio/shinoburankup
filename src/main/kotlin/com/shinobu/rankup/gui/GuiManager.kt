package com.shinobu.rankup.gui

import com.shinobu.rankup.cache.LeaderboardCache
import com.shinobu.rankup.cache.PlayerCache
import com.shinobu.rankup.cache.RankCache
import com.shinobu.rankup.hook.VaultHook
import com.shinobu.rankup.service.PlayerService
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Central manager for all GUI operations in ShinobuRankup.
 *
 * Responsibilities:
 * - Track all open GUIs per player
 * - Provide thread-safe access to GUI state
 * - Handle cleanup on inventory close
 * - Manage GUI lifecycle
 * - Provide services to GUI instances
 *
 * This is a singleton that should be initialized once during plugin startup.
 * All GUI operations should go through this manager for proper anti-dupe protection.
 */
class GuiManager private constructor(
    private val plugin: JavaPlugin,
    private val rankCache: RankCache,
    private val playerCache: PlayerCache,
    private val leaderboardCache: LeaderboardCache,
    private val vaultHook: VaultHook,
    private val playerService: PlayerService
) {

    /**
     * Map of player UUIDs to their currently open GUI.
     * Thread-safe for async access patterns.
     */
    private val openGuis: ConcurrentHashMap<UUID, BaseGui> = ConcurrentHashMap()

    /**
     * Map tracking the last click time per player for cooldown enforcement.
     * Prevents click spam and potential exploits.
     */
    private val lastClickTime: ConcurrentHashMap<UUID, Long> = ConcurrentHashMap()

    /**
     * Global click cooldown in milliseconds.
     * Prevents rapid clicking exploits.
     */
    var clickCooldownMs: Long = 100L

    companion object {
        @Volatile
        private var instance: GuiManager? = null

        /**
         * Initializes the GuiManager singleton.
         * Must be called once during plugin onEnable().
         *
         * @param plugin The main plugin instance
         * @param rankCache Cache for rank configurations
         * @param playerCache Cache for player data
         * @param leaderboardCache Cache for leaderboard data
         * @param vaultHook Vault economy integration
         * @param playerService Service for player data operations
         * @return The initialized GuiManager instance
         */
        fun initialize(
            plugin: JavaPlugin,
            rankCache: RankCache,
            playerCache: PlayerCache,
            leaderboardCache: LeaderboardCache,
            vaultHook: VaultHook,
            playerService: PlayerService
        ): GuiManager {
            return instance ?: synchronized(this) {
                instance ?: GuiManager(
                    plugin,
                    rankCache,
                    playerCache,
                    leaderboardCache,
                    vaultHook,
                    playerService
                ).also { instance = it }
            }
        }

        /**
         * Gets the GuiManager instance.
         * Throws IllegalStateException if not initialized.
         */
        fun getInstance(): GuiManager {
            return instance ?: throw IllegalStateException(
                "GuiManager has not been initialized. Call initialize() first."
            )
        }

        /**
         * Checks if the GuiManager has been initialized.
         */
        fun isInitialized(): Boolean = instance != null

        /**
         * Shuts down the GuiManager and cleans up resources.
         * Should be called during plugin onDisable().
         */
        fun shutdown() {
            instance?.cleanup()
            instance = null
        }
    }

    /**
     * Gets the plugin instance.
     */
    fun getPlugin(): JavaPlugin = plugin

    /**
     * Registers a GUI as open for a player.
     * Automatically closes any previously open GUI.
     *
     * @param player The player opening the GUI
     * @param gui The GUI being opened
     */
    fun registerGui(player: Player, gui: BaseGui) {
        val previousGui = openGuis.put(player.uniqueId, gui)

        // Close previous GUI if exists (shouldn't happen normally)
        previousGui?.let {
            plugin.logger.fine("Player ${player.name} had a GUI open, replacing it")
        }
    }

    /**
     * Unregisters a GUI for a player.
     * Called automatically when inventory is closed.
     *
     * @param player The player whose GUI is being closed
     * @return The GUI that was closed, or null if none was registered
     */
    fun unregisterGui(player: Player): BaseGui? {
        return openGuis.remove(player.uniqueId)
    }

    /**
     * Gets the currently open GUI for a player.
     *
     * @param player The player to check
     * @return The open GUI, or null if none
     */
    fun getOpenGui(player: Player): BaseGui? {
        return openGuis[player.uniqueId]
    }

    /**
     * Checks if a player has a GUI open.
     *
     * @param player The player to check
     * @return True if the player has a GUI open
     */
    fun hasOpenGui(player: Player): Boolean {
        return openGuis.containsKey(player.uniqueId)
    }

    /**
     * Checks if a player is on click cooldown.
     * Updates the last click time if not on cooldown.
     *
     * @param player The player to check
     * @return True if the player can click (not on cooldown)
     */
    fun canClick(player: Player): Boolean {
        val uuid = player.uniqueId
        val now = System.currentTimeMillis()
        var allowed = false
        lastClickTime.compute(uuid) { _, lastTime ->
            if (lastTime == null || (now - lastTime) >= clickCooldownMs) {
                allowed = true
                now
            } else {
                allowed = false
                lastTime
            }
        }
        return allowed
    }

    /**
     * Forces a GUI to close for a player.
     * Use this for programmatic GUI closing.
     *
     * @param player The player whose GUI should be closed
     */
    fun closeGui(player: Player) {
        if (hasOpenGui(player)) {
            // Schedule on main thread to ensure thread safety with Bukkit API
            if (plugin.server.isPrimaryThread) {
                player.closeInventory()
            } else {
                plugin.server.scheduler.runTask(plugin) { _ ->
                    player.closeInventory()
                }
            }
        }
    }

    /**
     * Closes all open GUIs for all players.
     * Used during plugin shutdown or reload.
     * Note: Does not use scheduler since this may be called during plugin disable.
     */
    fun closeAllGuis() {
        openGuis.keys.toList().forEach { uuid ->
            plugin.server.getPlayer(uuid)?.closeInventory()
        }
    }

    /**
     * Gets the number of currently open GUIs.
     */
    fun getOpenGuiCount(): Int = openGuis.size

    /**
     * Gets all players with open GUIs.
     */
    fun getPlayersWithOpenGuis(): Set<UUID> = openGuis.keys.toSet()

    /**
     * Cleans up all resources.
     * Called during plugin shutdown.
     */
    private fun cleanup() {
        closeAllGuis()
        openGuis.clear()
        lastClickTime.clear()
    }

    /**
     * Removes stale entries for offline players.
     * Can be called periodically for cleanup.
     */
    fun cleanupOfflinePlayers() {
        val onlineUuids = plugin.server.onlinePlayers.map { it.uniqueId }.toSet()

        openGuis.keys.removeIf { uuid ->
            !onlineUuids.contains(uuid).also { isOnline ->
                if (!isOnline) {
                    plugin.logger.fine("Cleaned up stale GUI entry for offline player: $uuid")
                }
            }
        }

        lastClickTime.keys.removeIf { uuid ->
            !onlineUuids.contains(uuid)
        }
    }

    /**
     * Opens a ranks GUI for a player.
     */
    fun openRanksGui(player: Player, page: Int = 0) {
        RanksGui(player, page, rankCache, playerCache, vaultHook).open()
    }

    /**
     * Opens a rankup confirmation GUI for a player.
     */
    fun openRankupConfirmGui(player: Player, targetRankId: String) {
        RankupConfirmGui(player, targetRankId).open()
    }

    /**
     * Opens the top players leaderboard GUI.
     */
    fun openTopPlayersGui(player: Player, page: Int = 0) {
        TopPlayersGui(player, page, rankCache, playerCache, leaderboardCache, vaultHook).open()
    }

    /**
     * Get the RankCache instance.
     */
    fun getRankCache(): RankCache = rankCache

    /**
     * Get the PlayerCache instance.
     */
    fun getPlayerCache(): PlayerCache = playerCache

    /**
     * Get the LeaderboardCache instance.
     */
    fun getLeaderboardCache(): LeaderboardCache = leaderboardCache

    /**
     * Get the VaultHook instance.
     */
    fun getVaultHook(): VaultHook = vaultHook

    /**
     * Get the PlayerService instance.
     */
    fun getPlayerService(): PlayerService = playerService
}

/**
 * Extension function to get the GuiManager from a plugin instance.
 */
fun JavaPlugin.guiManager(): GuiManager = GuiManager.getInstance()
