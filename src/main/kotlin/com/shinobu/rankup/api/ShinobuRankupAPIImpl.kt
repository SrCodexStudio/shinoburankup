package com.shinobu.rankup.api

import com.shinobu.rankup.cache.LeaderboardCache
import com.shinobu.rankup.cache.PlayerCache
import com.shinobu.rankup.cache.RankCache
import com.shinobu.rankup.data.LeaderboardEntry
import com.shinobu.rankup.data.MaxRankupResult
import com.shinobu.rankup.data.PlayerData
import com.shinobu.rankup.data.RankData
import com.shinobu.rankup.hook.VaultHook
import com.shinobu.rankup.service.PlayerService
import com.shinobu.rankup.task.AutoSaveTask
import com.shinobu.rankup.util.runOnMain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.future
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.logging.Level

/**
 * Implementation of the ShinobuRankup API.
 *
 * This class provides thread-safe access to all plugin functionality.
 * All async operations use coroutines internally but expose CompletableFuture
 * for Java compatibility.
 */
class ShinobuRankupAPIImpl(
    private val plugin: JavaPlugin,
    private val playerCache: PlayerCache,
    private val rankCache: RankCache,
    private val leaderboardCache: LeaderboardCache,
    private val playerService: PlayerService,
    private val vaultHook: VaultHook,
    private val autoSaveTask: AutoSaveTask
) : ShinobuRankupAPI {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ==================== Rank Information ====================

    override fun getPlayerRank(player: OfflinePlayer): RankData? {
        return getPlayerRankByUUID(player.uniqueId)
    }

    override fun getPlayerRankByUUID(uuid: UUID): RankData? {
        // Try cache first
        var playerData = playerCache.get(uuid)

        // If not in cache, try to load from database
        if (playerData == null) {
            playerData = kotlinx.coroutines.runBlocking {
                playerService.loadPlayerData(uuid)
            }

            // Cache if found
            if (playerData != null) {
                playerCache.put(uuid, playerData)
            }
        }

        // If still null, return default rank for online players
        if (playerData == null) {
            val player = Bukkit.getPlayer(uuid)
            if (player != null && player.isOnline) {
                // Player is online but data not loaded yet, return default rank
                return rankCache.getDefault()
            }
            return null
        }

        return rankCache.getById(playerData.currentRankId)
    }

    override fun getNextRank(player: OfflinePlayer): RankData? {
        return getNextRankByUUID(player.uniqueId)
    }

    override fun getNextRankByUUID(uuid: UUID): RankData? {
        // Try cache first
        var playerData = playerCache.get(uuid)

        // If not in cache, try to load from database
        if (playerData == null) {
            playerData = kotlinx.coroutines.runBlocking {
                playerService.loadPlayerData(uuid)
            }

            // Cache if found
            if (playerData != null) {
                playerCache.put(uuid, playerData)
            }
        }

        // If still null, return next rank after default for online players
        if (playerData == null) {
            val player = Bukkit.getPlayer(uuid)
            if (player != null && player.isOnline) {
                val defaultRank = rankCache.getDefault()
                return if (defaultRank != null) rankCache.getNextRank(defaultRank.id) else null
            }
            return null
        }

        return rankCache.getNextRank(playerData.currentRankId)
    }

    override fun setPlayerRank(player: OfflinePlayer, rankId: String): Boolean {
        return setPlayerRankByUUID(player.uniqueId, rankId)
    }

    override fun setPlayerRankByUUID(uuid: UUID, rankId: String): Boolean {
        // Verify rank exists
        if (rankCache.getById(rankId) == null) return false

        // Get or create player data
        var playerData = playerCache.get(uuid)

        if (playerData == null) {
            // Try to load from database
            playerData = kotlinx.coroutines.runBlocking {
                playerService.loadPlayerData(uuid)
            }
        }

        if (playerData == null) {
            // Create new player data
            val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
            playerData = PlayerData(
                uuid = uuid,
                name = offlinePlayer.name ?: uuid.toString(),
                currentRankId = rankId
            )
        } else {
            // Update existing player data
            playerData = playerData.copy(currentRankId = rankId)
        }

        // Update cache
        playerCache.put(uuid, playerData)

        // Mark as dirty for auto-save
        autoSaveTask.markDirty(uuid)

        return true
    }

    // ==================== Rank Registry ====================

    override fun getAllRanks(): List<RankData> {
        return rankCache.getAllSorted()
    }

    override fun getRankById(id: String): RankData? {
        return rankCache.getById(id)
    }

    override fun getDefaultRank(): RankData? {
        return rankCache.getDefault()
    }

    override fun getMaxRank(): RankData? {
        return rankCache.getMax()
    }

    override fun getRankAfter(currentRankId: String): RankData? {
        return rankCache.getNextRank(currentRankId)
    }

    override fun getRankBefore(currentRankId: String): RankData? {
        return rankCache.getPreviousRank(currentRankId)
    }

    // ==================== Player Data ====================

    override fun getPlayerData(player: OfflinePlayer): PlayerData? {
        return getPlayerDataByUUID(player.uniqueId)
    }

    override fun getPlayerDataByUUID(uuid: UUID): PlayerData? {
        // Try cache first
        var playerData = playerCache.get(uuid)

        // If not in cache, try to load from database
        if (playerData == null) {
            playerData = kotlinx.coroutines.runBlocking {
                playerService.loadPlayerData(uuid)
            }

            // Cache if found
            if (playerData != null) {
                playerCache.put(uuid, playerData)
            }
        }

        return playerData
    }

    override fun resetPlayerData(player: OfflinePlayer): Boolean {
        return resetPlayerDataByUUID(player.uniqueId)
    }

    override fun resetPlayerDataByUUID(uuid: UUID): Boolean {
        val defaultRank = rankCache.getDefault() ?: return false

        var playerData = playerCache.get(uuid)

        if (playerData == null) {
            val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
            playerData = PlayerData(
                uuid = uuid,
                name = offlinePlayer.name ?: uuid.toString(),
                currentRankId = defaultRank.id
            )
        } else {
            playerData = playerData.copy(
                currentRankId = defaultRank.id,
                totalSpent = 0.0,
                rankupCount = 0
            )
        }

        playerCache.put(uuid, playerData)
        autoSaveTask.markDirty(uuid)

        return true
    }

    // ==================== Leaderboard ====================

    override fun getTopPlayers(limit: Int): List<LeaderboardEntry> {
        return leaderboardCache.getTop(limit)
    }

    override fun getLeaderboardPosition(player: OfflinePlayer): Int {
        return getLeaderboardPositionByUUID(player.uniqueId)
    }

    override fun getLeaderboardPositionByUUID(uuid: UUID): Int {
        return leaderboardCache.getPosition(uuid)
    }

    // ==================== Rankup Operations ====================

    override fun performRankup(player: Player): CompletableFuture<Boolean> {
        return scope.future {
            performRankupInternal(player)
        }
    }

    private suspend fun performRankupInternal(player: Player): Boolean {
        val uuid = player.uniqueId

        // Get player data
        val playerData = playerCache.get(uuid)
            ?: return false

        // Get next rank
        val nextRank = rankCache.getNextRank(playerData.currentRankId)
            ?: return false // Already at max rank

        // Check if player can afford
        if (!vaultHook.has(player, nextRank.cost)) {
            return false
        }

        // Withdraw money
        if (!vaultHook.withdraw(player, nextRank.cost)) {
            return false
        }

        // Update player data
        val updatedData = playerData.withRankup(nextRank.id, nextRank.cost)
        playerCache.put(uuid, updatedData)
        autoSaveTask.markDirty(uuid)

        // Execute rank commands on main thread
        plugin.runOnMain {
            executeRankCommands(player, nextRank)
        }

        // Fire event (could be implemented with Bukkit events)
        plugin.logger.info("${player.name} ranked up to ${nextRank.displayName}")

        return true
    }

    override fun performMaxRankup(player: Player): CompletableFuture<MaxRankupResult> {
        return scope.future {
            performMaxRankupInternal(player)
        }
    }

    private suspend fun performMaxRankupInternal(player: Player): MaxRankupResult {
        val uuid = player.uniqueId

        // Get player data
        val playerData = playerCache.get(uuid)
            ?: return MaxRankupResult.failure("Player data not found", "unknown")

        val startRankId = playerData.currentRankId
        var currentRankId = startRankId
        var currentRankDisplay = ""
        var ranksGained = 0
        var totalCost = 0.0

        // Loop until can't afford or at max
        while (true) {
            val nextRank = rankCache.getNextRank(currentRankId) ?: break

            val balance = vaultHook.getBalance(player)
            if (balance < nextRank.cost) break

            // Withdraw money
            if (!vaultHook.withdraw(player, nextRank.cost)) break

            currentRankId = nextRank.id
            currentRankDisplay = "${nextRank.prefix}${nextRank.displayName}"
            ranksGained++
            totalCost += nextRank.cost

            // Execute rank commands on main thread
            plugin.runOnMain {
                executeRankCommands(player, nextRank)
            }
        }

        if (ranksGained > 0) {
            // Update player data
            val updatedData = playerData.copy(
                currentRankId = currentRankId,
                totalSpent = playerData.totalSpent + totalCost,
                rankupCount = playerData.rankupCount + ranksGained
            )
            playerCache.put(uuid, updatedData)
            autoSaveTask.markDirty(uuid)

            plugin.logger.info("${player.name} ranked up $ranksGained times to $currentRankId (cost: $totalCost)")

            return MaxRankupResult.success(ranksGained, totalCost, startRankId, currentRankId, currentRankDisplay)
        }

        // Check why we couldn't rank up
        val nextRank = rankCache.getNextRank(startRankId)
        return if (nextRank == null) {
            MaxRankupResult.failure("Already at max rank", startRankId)
        } else {
            MaxRankupResult.failure("Insufficient funds", startRankId)
        }
    }

    override fun canAffordNextRank(player: OfflinePlayer): Boolean {
        val nextRank = getNextRank(player) ?: return false
        return vaultHook.has(player, nextRank.cost)
    }

    override fun meetsRankupRequirements(player: Player): Boolean {
        val nextRank = getNextRank(player) ?: return false

        // Check money
        if (!vaultHook.has(player, nextRank.cost)) {
            return false
        }

        // Additional requirement checks can be added here
        // (playtime, kills, level, items, etc.)

        return true
    }

    override fun getCostToRank(player: OfflinePlayer, targetRankId: String): Double {
        val playerData = getPlayerData(player) ?: return -1.0
        return rankCache.calculateCost(playerData.currentRankId, targetRankId)
    }

    // ==================== Utility ====================

    override fun getProgressPercentage(player: OfflinePlayer): Double {
        val nextRank = getNextRank(player) ?: return 100.0

        val balance = vaultHook.getBalance(player)
        val cost = nextRank.cost

        if (cost <= 0) return 100.0

        return ((balance / cost) * 100).coerceIn(0.0, 100.0)
    }

    override fun getMoneyNeeded(player: OfflinePlayer): Double {
        val nextRank = getNextRank(player) ?: return 0.0
        val balance = vaultHook.getBalance(player)
        return (nextRank.cost - balance).coerceAtLeast(0.0)
    }

    override fun isMaxRank(player: OfflinePlayer): Boolean {
        return getNextRank(player) == null
    }

    override fun getVersion(): String {
        return plugin.description.version
    }

    // ==================== Internal Helpers ====================

    /**
     * Execute rank commands for a player.
     * Must be called on the main thread.
     */
    private fun executeRankCommands(player: Player, rank: RankData) {
        for (command in rank.commands) {
            try {
                val parsedCommand = command
                    .replace("{player}", player.name)
                    .replace("{rank}", rank.id)
                    .replace("{rank_display}", rank.displayName)

                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCommand)
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "Failed to execute rank command: $command", e)
            }
        }

        // Broadcast message if configured
        rank.broadcastMessage?.let { message ->
            val parsedMessage = message
                .replace("{player}", player.name)
                .replace("{rank}", rank.displayName)

            Bukkit.broadcast(com.shinobu.rankup.util.ColorUtil.parse(parsedMessage))
        }
    }

    /**
     * Shutdown the API implementation.
     */
    fun shutdown() {
        scope.cancel() // Cancel all pending coroutines
    }
}
