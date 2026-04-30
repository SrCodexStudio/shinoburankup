package com.shinobu.rankup.service

import com.shinobu.rankup.data.MaxRankupResult
import com.shinobu.rankup.data.PlayerData
import com.shinobu.rankup.data.RankData
import com.shinobu.rankup.economy.EconomyProvider
import com.shinobu.rankup.economy.TransactionResult
import com.shinobu.rankup.event.PlayerMaxRankupCompleteEvent
import com.shinobu.rankup.event.PlayerMaxRankupEvent
import com.shinobu.rankup.event.PlayerRankupCompleteEvent
import com.shinobu.rankup.event.PlayerRankupEvent
import com.shinobu.rankup.util.runOnMain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Result of a single rankup operation.
 */
sealed class RankupResult {
    data class Success(
        val previousRank: RankData,
        val newRank: RankData,
        val nextRank: RankData? = null,
        val cost: Double,
        val isFree: Boolean = false
    ) : RankupResult()

    sealed class Failure : RankupResult() {
        data class InsufficientFunds(val required: Double, val current: Double) : Failure()
        object AlreadyMaxRank : Failure()
        object AlreadyProcessing : Failure()
        object RankNotFound : Failure()
        object PlayerDataNotFound : Failure()
        object EconomyUnavailable : Failure()
        data class CancelledByEvent(val reason: String) : Failure()
        data class CooldownActive(val remainingSeconds: Long) : Failure()
        data class EconomyError(val message: String) : Failure()
        data class UnknownError(val message: String) : Failure()
        data class RequirementsNotMet(val failures: List<RequirementFailure>) : Failure()
    }
}

/**
 * Core service for handling rankup logic.
 * Thread-safe with proper mutex locking for player operations.
 */
class RankupService(
    private val plugin: Plugin,
    private val economyProvider: EconomyProvider,
    private val rewardService: RewardService,
    private val requirementChecker: RequirementChecker,
    private val rankProvider: () -> List<RankData>,
    private val playerDataProvider: suspend (UUID) -> PlayerData?,
    private val playerDataSaver: suspend (PlayerData) -> Unit,
    private val configProvider: () -> RankupConfig,
    private val onRankupComplete: (() -> Unit)? = null
) {
    private val logger: Logger = plugin.logger

    companion object {
        /** Timeout for acquiring player locks in milliseconds */
        private const val LOCK_TIMEOUT_MS = 10_000L // 10 seconds
    }

    // Mutex per player to prevent race conditions
    private val playerLocks = ConcurrentHashMap<UUID, Mutex>()

    // Cooldown tracking
    private val cooldowns = ConcurrentHashMap<UUID, Long>()

    // Processing guard to prevent double-execution race conditions
    private val processingPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    /**
     * Configuration for rankup operations.
     */
    data class RankupConfig(
        val maxRankupEnabled: Boolean = true,
        val maxRankupLimit: Int = 100,
        val cooldownSeconds: Int = 0,
        val requireConfirmation: Boolean = false,
        val refundPercentage: Int = 100
    )

    /**
     * Get the mutex for a player.
     */
    private fun getPlayerMutex(uuid: UUID): Mutex {
        return playerLocks.getOrPut(uuid) { Mutex() }
    }

    /**
     * Get all ranks sorted by order.
     */
    fun getAllRanks(): List<RankData> = rankProvider().sortedBy { it.order }

    /**
     * Get a rank by ID.
     */
    fun getRankById(id: String): RankData? = rankProvider().find { it.id.equals(id, ignoreCase = true) }

    /**
     * Get the default rank (lowest order or marked as default).
     */
    fun getDefaultRank(): RankData? {
        val ranks = getAllRanks()
        return ranks.minByOrNull { it.order }
    }

    /**
     * Get the next rank for a player.
     */
    suspend fun getNextRank(player: Player): RankData? {
        val playerData = playerDataProvider(player.uniqueId) ?: return null
        val currentRank = getRankById(playerData.currentRankId) ?: return null
        return getNextRank(currentRank)
    }

    /**
     * Get the next rank after a given rank.
     */
    fun getNextRank(currentRank: RankData): RankData? {
        return getAllRanks()
            .filter { it.order > currentRank.order }
            .minByOrNull { it.order }
    }

    /**
     * Check if a player can afford to rank up.
     */
    suspend fun canAffordRankup(player: Player): Boolean {
        val nextRank = getNextRank(player) ?: return false
        return economyProvider.has(player, nextRank.cost)
    }

    /**
     * Check if a player can rank up (has next rank available).
     */
    suspend fun canRankup(player: Player): Boolean {
        val nextRank = getNextRank(player) ?: return false
        return canAffordRankup(player)
    }

    /**
     * Check if player is on cooldown.
     */
    fun isOnCooldown(uuid: UUID): Boolean {
        val config = configProvider()
        if (config.cooldownSeconds <= 0) return false

        val lastRankup = cooldowns[uuid] ?: return false
        val elapsed = (System.currentTimeMillis() - lastRankup) / 1000
        return elapsed < config.cooldownSeconds
    }

    /**
     * Get remaining cooldown in seconds.
     */
    fun getRemainingCooldown(uuid: UUID): Long {
        val config = configProvider()
        if (config.cooldownSeconds <= 0) return 0

        val lastRankup = cooldowns[uuid] ?: return 0
        val elapsed = (System.currentTimeMillis() - lastRankup) / 1000
        return (config.cooldownSeconds - elapsed).coerceAtLeast(0)
    }

    /**
     * Calculate the player's progress to the next rank (0.0 to 1.0).
     */
    suspend fun getRankProgress(player: Player): Double {
        val nextRank = getNextRank(player) ?: return 1.0 // Already max rank
        val balance = economyProvider.getBalance(player)
        return (balance / nextRank.cost).coerceIn(0.0, 1.0)
    }

    /**
     * Get the current rank for a player.
     */
    suspend fun getCurrentRank(player: Player): RankData? {
        val playerData = playerDataProvider(player.uniqueId) ?: return null
        return getRankById(playerData.currentRankId)
    }

    /**
     * Calculate total cost from one rank to another.
     */
    fun calculateTotalCostToRank(fromRank: RankData, toRank: RankData): Double {
        if (toRank.order <= fromRank.order) return 0.0

        return getAllRanks()
            .filter { it.order > fromRank.order && it.order <= toRank.order }
            .sumOf { it.cost }
    }

    /**
     * Calculate how many ranks a player can afford from their current position.
     */
    suspend fun calculateAffordableRanks(player: Player): Pair<Int, Double> {
        val playerData = playerDataProvider(player.uniqueId) ?: return Pair(0, 0.0)
        val currentRank = getRankById(playerData.currentRankId) ?: return Pair(0, 0.0)

        var balance = economyProvider.getBalance(player)
        var ranksAffordable = 0
        var totalCost = 0.0
        var checkRank = currentRank

        val config = configProvider()
        val maxLimit = config.maxRankupLimit

        while (ranksAffordable < maxLimit) {
            val nextRank = getNextRank(checkRank) ?: break

            if (balance >= nextRank.cost) {
                balance -= nextRank.cost
                totalCost += nextRank.cost
                ranksAffordable++
                checkRank = nextRank
            } else {
                break
            }
        }

        return Pair(ranksAffordable, totalCost)
    }

    /**
     * Perform a single rankup for a player.
     * Uses timeout-protected mutex to prevent deadlocks.
     */
    suspend fun performRankup(player: Player): RankupResult {
        // Fast-path guard: reject if this player already has a rankup in flight
        if (!processingPlayers.add(player.uniqueId)) {
            return RankupResult.Failure.AlreadyProcessing
        }

        val mutex = getPlayerMutex(player.uniqueId)

        return try {
            withTimeout(LOCK_TIMEOUT_MS) {
                mutex.withLock {
                    performRankupInternal(player)
                }
            }
        } catch (e: CancellationException) {
            // Propagate cancellation - CRITICAL for coroutine health
            throw e
        } catch (e: Exception) {
            logger.warning("Rankup failed for ${player.name}: ${e.message}")
            RankupResult.Failure.UnknownError("Operation timed out or failed: ${e.message}")
        } finally {
            processingPlayers.remove(player.uniqueId)
        }
    }

    /**
     * Internal rankup logic (called within mutex lock).
     */
    private suspend fun performRankupInternal(player: Player): RankupResult {
        // Check economy availability
        if (!economyProvider.isAvailable()) {
            return RankupResult.Failure.EconomyUnavailable
        }

        // Check cooldown (unless bypassed)
        val bypassCooldown = player.hasPermission("shinoburankup.bypass.cooldown")
        if (!bypassCooldown && isOnCooldown(player.uniqueId)) {
            return RankupResult.Failure.CooldownActive(getRemainingCooldown(player.uniqueId))
        }

        // Get player data
        val playerData = playerDataProvider(player.uniqueId)
            ?: return RankupResult.Failure.PlayerDataNotFound

        // Get current rank
        val currentRank = getRankById(playerData.currentRankId)
            ?: return RankupResult.Failure.RankNotFound

        // Get next rank
        val nextRank = getNextRank(currentRank)
            ?: return RankupResult.Failure.AlreadyMaxRank

        // Check bypass permissions
        val bypassCost = player.hasPermission("shinoburankup.bypass.cost")
        val bypassRequirements = player.hasPermission("shinoburankup.bypass.requirements")

        // Check requirements (unless bypassed)
        if (!bypassRequirements && requirementChecker.hasRequirements(nextRank.requirements)) {
            val failures = requirementChecker.check(player, nextRank.requirements)
            if (failures.isNotEmpty()) {
                return RankupResult.Failure.RequirementsNotMet(failures)
            }
        }

        // Check funds (unless bypassed)
        if (!bypassCost) {
            val balance = economyProvider.getBalance(player)
            if (balance < nextRank.cost) {
                return RankupResult.Failure.InsufficientFunds(nextRank.cost, balance)
            }
        }

        // Fire pre-rankup event
        val preEvent = PlayerRankupEvent(player, currentRank, nextRank, nextRank.cost)
        val eventCancelled = plugin.runOnMain {
            Bukkit.getPluginManager().callEvent(preEvent)
            preEvent.isCancelled
        }

        if (eventCancelled) {
            return RankupResult.Failure.CancelledByEvent(preEvent.getCancelReason())
        }

        // Withdraw money (unless bypassed)
        // Re-check balance atomically on the main thread right before withdrawing
        // to close the TOCTOU race window between the balance check above and the actual withdraw
        if (!bypassCost) {
            val withdrawResult = plugin.runOnMain {
                val currentBalance = economyProvider.getBalance(player)
                if (currentBalance < nextRank.cost) {
                    TransactionResult.Failure("Insufficient funds")
                } else {
                    economyProvider.withdraw(player, nextRank.cost)
                }
            }

            when (withdrawResult) {
                is TransactionResult.Failure -> {
                    val balance = try { plugin.runOnMain { economyProvider.getBalance(player) } } catch (_: Exception) { 0.0 }
                    return if (balance < nextRank.cost) {
                        RankupResult.Failure.InsufficientFunds(nextRank.cost, balance)
                    } else {
                        RankupResult.Failure.EconomyError(withdrawResult.reason)
                    }
                }
                is TransactionResult.Success -> {
                    // Continue with rankup
                }
            }
        }

        // Update player data
        val updatedPlayerData = playerData.withRankup(nextRank.id, nextRank.cost)

        // Save player data
        try {
            playerDataSaver(updatedPlayerData)
        } catch (e: Exception) {
            // Refund on save failure (only if cost was actually withdrawn)
            if (!bypassCost) {
                plugin.runOnMain {
                    economyProvider.deposit(player, nextRank.cost)
                }
            }
            logger.severe("Failed to save player data for ${player.name}: ${e.message}")
            return RankupResult.Failure.UnknownError("Failed to save data: ${e.message}")
        }

        // Update cooldown
        cooldowns[player.uniqueId] = System.currentTimeMillis()

        // Check if milestone (every 10th rank)
        val isMilestone = nextRank.order % 10 == 0

        // Compute the rank after the new rank (for {next_rank} placeholder)
        val nextAfterNew = getNextRank(nextRank)

        // Execute rewards
        rewardService.executeRankupRewards(player, currentRank, nextRank, nextAfterNew, isMilestone)

        // Fire post-rankup event
        plugin.runOnMain {
            val postEvent = PlayerRankupCompleteEvent(
                player = player,
                previousRank = currentRank,
                newRank = nextRank,
                cost = if (bypassCost) 0.0 else nextRank.cost,
                isFreeRankup = bypassCost,
                totalRankups = updatedPlayerData.rankupCount,
                totalSpent = updatedPlayerData.totalSpent
            )
            Bukkit.getPluginManager().callEvent(postEvent)
        }

        // Notify leaderboard cache to refresh
        onRankupComplete?.invoke()

        return RankupResult.Success(
            previousRank = currentRank,
            newRank = nextRank,
            nextRank = nextAfterNew,
            cost = if (bypassCost) 0.0 else nextRank.cost,
            isFree = bypassCost
        )
    }

    /**
     * Perform max rankup - rank up as many times as possible.
     * Uses timeout-protected mutex to prevent deadlocks.
     */
    suspend fun performMaxRankup(player: Player): MaxRankupResult {
        // Fast-path guard: reject if this player already has a rankup in flight
        if (!processingPlayers.add(player.uniqueId)) {
            return MaxRankupResult.failure("Already processing a rankup", "already_processing")
        }

        val mutex = getPlayerMutex(player.uniqueId)

        return try {
            withTimeout(LOCK_TIMEOUT_MS) {
                mutex.withLock {
                    performMaxRankupInternal(player)
                }
            }
        } catch (e: CancellationException) {
            // Propagate cancellation - CRITICAL for coroutine health
            throw e
        } catch (e: Exception) {
            logger.warning("Max rankup failed for ${player.name}: ${e.message}")
            MaxRankupResult.failure("Operation timed out or failed: ${e.message}", "unknown")
        } finally {
            processingPlayers.remove(player.uniqueId)
        }
    }

    /**
     * Internal max rankup logic (called within mutex lock).
     */
    private suspend fun performMaxRankupInternal(player: Player): MaxRankupResult {
        val config = configProvider()

        if (!config.maxRankupEnabled) {
            return MaxRankupResult.failure("Max rankup is disabled", "unknown")
        }

        // Check bypass permissions
        val bypassCost = player.hasPermission("shinoburankup.bypass.cost")
        val bypassRequirements = player.hasPermission("shinoburankup.bypass.requirements")

        // Check economy availability (only needed if cost is not bypassed)
        if (!bypassCost && !economyProvider.isAvailable()) {
            return MaxRankupResult.failure("Economy not available", "unknown")
        }

        // Get player data
        val playerData = playerDataProvider(player.uniqueId)
            ?: return MaxRankupResult.failure("Player data not found", "unknown")

        // Get current rank
        val startRank = getRankById(playerData.currentRankId)
            ?: return MaxRankupResult.failure("Current rank not found", playerData.currentRankId)

        // Calculate affordable ranks or count remaining ranks if cost is bypassed
        val affordableRanks: Int
        val estimatedCost: Double

        if (bypassCost) {
            // Count all remaining ranks up to limit
            var count = 0
            var checkRank = startRank
            while (count < config.maxRankupLimit) {
                checkRank = getNextRank(checkRank) ?: break
                count++
            }
            affordableRanks = count
            estimatedCost = 0.0
        } else {
            val (calculated, cost) = calculateAffordableRanks(player)
            affordableRanks = calculated
            estimatedCost = cost
        }

        if (affordableRanks == 0) {
            val nextRank = getNextRank(startRank)
            if (nextRank == null) {
                return MaxRankupResult.failure("Already at max rank", startRank.id)
            }

            if (bypassCost) {
                return MaxRankupResult.failure("Already at max rank", startRank.id)
            }

            val balance = economyProvider.getBalance(player)
            return MaxRankupResult.failure(
                "Insufficient funds. Need ${economyProvider.format(nextRank.cost)}, have ${economyProvider.format(balance)}",
                startRank.id
            )
        }

        // Find target rank
        var targetRank = startRank
        var tempRank = startRank
        repeat(affordableRanks) {
            tempRank = getNextRank(tempRank) ?: return@repeat
            targetRank = tempRank
        }

        // Fire pre-event
        val preEvent = PlayerMaxRankupEvent(player, startRank, targetRank, estimatedCost, affordableRanks)
        val eventCancelled = plugin.runOnMain {
            Bukkit.getPluginManager().callEvent(preEvent)
            preEvent.isCancelled
        }

        if (eventCancelled) {
            return MaxRankupResult.failure(preEvent.getCancelReason(), startRank.id)
        }

        // Perform all rankups
        // OPTIMIZATION: Collect all ranks first, then queue commands at the end
        // This prevents blocking the main thread during economic transactions
        var currentPlayerData = playerData
        var currentRank = startRank
        var totalCost = 0.0
        val rankupDetails = mutableListOf<PlayerMaxRankupCompleteEvent.RankupDetails>()
        val ranksToReward = mutableListOf<RankData>() // Collect ranks for batch command queueing
        var ranksGained = 0

        val limit = config.maxRankupLimit.coerceAtMost(affordableRanks)

        for (i in 0 until limit) {
            val nextRank = getNextRank(currentRank) ?: break

            // Check requirements (unless bypassed) - stop ranking up if not met
            if (!bypassRequirements && requirementChecker.hasRequirements(nextRank.requirements)) {
                val failures = requirementChecker.check(player, nextRank.requirements)
                if (failures.isNotEmpty()) break
            }

            // Check if can afford (unless bypassed)
            if (!bypassCost) {
                val balance = economyProvider.getBalance(player)
                if (balance < nextRank.cost) break

                // Withdraw
                val withdrawResult = plugin.runOnMain {
                    economyProvider.withdraw(player, nextRank.cost)
                }

                if (withdrawResult is TransactionResult.Failure) {
                    break
                }
            }

            // Track the rankup
            rankupDetails.add(
                PlayerMaxRankupCompleteEvent.RankupDetails(
                    fromRank = currentRank,
                    toRank = nextRank,
                    cost = nextRank.cost
                )
            )

            totalCost += nextRank.cost
            ranksGained++

            // Collect rank for later command execution (OPTIMIZED)
            ranksToReward.add(nextRank)

            // Update tracking
            currentPlayerData = currentPlayerData.withRankup(nextRank.id, nextRank.cost)
            currentRank = nextRank
        }

        // OPTIMIZATION: Queue all rank commands at once AFTER all transactions complete
        // Commands will be executed gradually by CommandQueueService to prevent lag
        if (ranksToReward.isNotEmpty()) {
            rewardService.queueMultipleRankCommands(player, ranksToReward)
        }

        // Save player data
        try {
            playerDataSaver(currentPlayerData)
        } catch (e: Exception) {
            logger.severe("Failed to save player data for ${player.name} after max rankup: ${e.message}")
            // Note: We don't refund here as some rankups may have succeeded
        }

        val remainingBalance = economyProvider.getBalance(player)

        // Execute special max rankup rewards
        if (ranksGained > 0) {
            rewardService.executeMaxRankupRewards(
                player, startRank, currentRank, ranksGained, totalCost, remainingBalance
            )

            // Send detailed summary
            rewardService.sendMaxRankupSummary(
                player, startRank, currentRank, ranksGained, totalCost, remainingBalance
            )

            // Fire post-event
            plugin.runOnMain {
                val postEvent = PlayerMaxRankupCompleteEvent(
                    player = player,
                    startRank = startRank,
                    endRank = currentRank,
                    ranksGained = ranksGained,
                    totalCost = totalCost,
                    remainingBalance = remainingBalance,
                    individualRankups = rankupDetails
                )
                Bukkit.getPluginManager().callEvent(postEvent)
            }

            // Notify leaderboard cache to refresh
            onRankupComplete?.invoke()
        }

        return MaxRankupResult.success(
            ranksGained = ranksGained,
            totalCost = totalCost,
            fromRankId = startRank.id,
            toRankId = currentRank.id,
            finalRankDisplay = "${currentRank.prefix}${currentRank.displayName}"
        )
    }

    /**
     * Set a player's rank directly (admin function).
     * Uses timeout-protected mutex to prevent deadlocks.
     */
    suspend fun setPlayerRank(player: Player, rankId: String): Boolean {
        val mutex = getPlayerMutex(player.uniqueId)

        return try {
            withTimeout(LOCK_TIMEOUT_MS) {
                mutex.withLock {
                    val rank = getRankById(rankId) ?: return@withLock false
                    val playerData = playerDataProvider(player.uniqueId) ?: return@withLock false

                    val updatedData = playerData.copy(
                        currentRankId = rank.id,
                        lastRankup = Instant.now()
                    )

                    try {
                        playerDataSaver(updatedData)
                        true
                    } catch (e: Exception) {
                        logger.severe("Failed to set rank for ${player.name}: ${e.message}")
                        false
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warning("Set rank operation timed out for ${player.name}")
            false
        }
    }

    /**
     * Reset a player's data to default.
     * Uses timeout-protected mutex to prevent deadlocks.
     */
    suspend fun resetPlayerData(player: Player): Boolean {
        val mutex = getPlayerMutex(player.uniqueId)

        return try {
            withTimeout(LOCK_TIMEOUT_MS) {
                mutex.withLock {
                    val defaultRank = getDefaultRank() ?: return@withLock false

                    val newData = PlayerData(
                        uuid = player.uniqueId,
                        name = player.name,
                        currentRankId = defaultRank.id,
                        totalSpent = 0.0,
                        rankupCount = 0,
                        firstJoin = Instant.now(),
                        lastRankup = null,
                        lastSeen = Instant.now()
                    )

                    try {
                        playerDataSaver(newData)
                        cooldowns.remove(player.uniqueId)
                        true
                    } catch (e: Exception) {
                        logger.severe("Failed to reset data for ${player.name}: ${e.message}")
                        false
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warning("Reset data operation timed out for ${player.name}")
            false
        }
    }

    /**
     * Clean up resources for a specific player (call on player quit).
     * This prevents memory leaks from accumulated locks.
     *
     * @param uuid The player's UUID
     */
    fun cleanupPlayer(uuid: UUID) {
        playerLocks.remove(uuid)
        cooldowns.remove(uuid)
        processingPlayers.remove(uuid)
    }

    /**
     * Clean up all resources.
     * Call this on plugin disable.
     */
    fun cleanup() {
        playerLocks.clear()
        cooldowns.clear()
        processingPlayers.clear()
    }
}
