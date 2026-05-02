package com.shinobu.rankup.service

import com.shinobu.rankup.data.RankData
import com.shinobu.rankup.util.ColorUtil
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.logging.Logger

/**
 * High-performance command queue service for executing rank rewards.
 *
 * FEATURES:
 * - Distributes command execution across ticks to prevent lag
 * - Processes commands in batches to maintain server TPS
 * - Automatically cancels pending commands if player disconnects
 * - Thread-safe with concurrent data structures
 * - Configurable batch size and tick interval
 * - ROUND-ROBIN mode for fair distribution across players
 *
 * PERFORMANCE:
 * - Default: 10 commands per tick (200 commands = 1 second at 20 TPS)
 * - Prevents main thread blocking
 * - Memory efficient with automatic cleanup
 * - O(1) player command cancellation with per-player queues
 *
 * MODES:
 * - FIFO (default): First In, First Out - simple and fast
 * - ROUND-ROBIN: Fair distribution - each player gets equal turns
 */
class CommandQueueService(
    private val plugin: Plugin,
    private val configProvider: () -> QueueConfig = { QueueConfig() }
) {
    private val logger: Logger = plugin.logger

    /**
     * Queue processing mode.
     */
    enum class QueueMode {
        /** First In, First Out - process commands in order received */
        FIFO,
        /** Round-Robin - alternate between players for fair distribution */
        ROUND_ROBIN
    }

    /**
     * Configuration for the command queue.
     */
    data class QueueConfig(
        /** Maximum commands to execute per tick */
        val commandsPerTick: Int = 10,
        /** Ticks between each batch execution (1 tick = 50ms) */
        val tickInterval: Long = 1L,
        /** Maximum queue size per player to prevent memory issues */
        val maxQueueSizePerPlayer: Int = 1000,
        /** Whether to log command execution for debugging */
        val debugLogging: Boolean = false,
        /** Queue processing mode: FIFO or ROUND_ROBIN */
        val queueMode: QueueMode = QueueMode.ROUND_ROBIN
    )

    /**
     * Represents a command waiting to be executed.
     */
    private data class QueuedCommand(
        val playerUUID: UUID,
        val playerName: String,
        val command: String,
        val rankId: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Statistics for monitoring queue performance.
     */
    data class QueueStats(
        val totalPending: Int,
        val playersWithPending: Int,
        val commandsExecutedTotal: Long,
        val isProcessing: Boolean,
        val queueMode: String,
        val averageCommandsPerPlayer: Double
    )

    // Per-player command queues for O(1) cancellation and round-robin support
    private val playerQueues = ConcurrentHashMap<UUID, ConcurrentLinkedQueue<QueuedCommand>>()

    // Track player names for logging
    private val playerNames = ConcurrentHashMap<UUID, String>()

    // Round-robin iterator - tracks which players to process next
    private val activePlayerOrder = ConcurrentLinkedQueue<UUID>()

    // Processing task reference
    private var processingTask: BukkitTask? = null

    // Statistics
    @Volatile
    private var commandsExecutedTotal: Long = 0

    @Volatile
    private var isProcessing: Boolean = false

    /**
     * Get or create a queue for a player.
     */
    private fun getPlayerQueue(uuid: UUID): ConcurrentLinkedQueue<QueuedCommand> {
        return playerQueues.computeIfAbsent(uuid) {
            // Add to round-robin order when first command is queued
            if (!activePlayerOrder.contains(uuid)) {
                activePlayerOrder.offer(uuid)
            }
            ConcurrentLinkedQueue()
        }
    }

    /**
     * Queue commands for a single rank.
     * Commands will be executed gradually to prevent lag.
     *
     * @param player The player to execute commands for
     * @param rank The rank whose commands should be queued
     */
    fun queueRankCommands(player: Player, rank: RankData) {
        if (rank.commands.isEmpty()) return

        val config = configProvider()
        val playerQueue = getPlayerQueue(player.uniqueId)
        val currentCount = playerQueue.size

        // Check queue limit
        if (currentCount >= config.maxQueueSizePerPlayer) {
            logger.warning("Command queue limit reached for ${player.name}. Skipping ${rank.commands.size} commands from rank ${rank.id}")
            return
        }

        // Store player name for logging
        playerNames[player.uniqueId] = player.name

        // Sanitize player name once
        val sanitizedName = sanitizePlayerName(player.name)

        // Prepare placeholders
        val placeholders = mapOf(
            "player" to sanitizedName,
            "player_uuid" to player.uniqueId.toString(),
            "rank" to rank.id,
            "rank_display" to rank.displayName
        )

        // Queue each command
        var addedCount = 0
        for (command in rank.commands) {
            if (currentCount + addedCount >= config.maxQueueSizePerPlayer) {
                logger.warning("Queue limit reached while adding commands for ${player.name}")
                break
            }

            // Process placeholders
            var processedCommand = command
            placeholders.forEach { (key, value) ->
                processedCommand = processedCommand
                    .replace("{$key}", value)
                    .replace("%$key%", value)
            }

            playerQueue.offer(
                QueuedCommand(
                    playerUUID = player.uniqueId,
                    playerName = sanitizedName,
                    command = processedCommand,
                    rankId = rank.id
                )
            )
            addedCount++
        }

        if (config.debugLogging && addedCount > 0) {
            logger.info("Queued $addedCount commands for ${player.name} from rank ${rank.id}")
        }

        // Ensure processor is running
        if (addedCount > 0) {
            ensureProcessorRunning()
        }
    }

    /**
     * Queue commands for multiple ranks (used by max rankup).
     * More efficient than calling queueRankCommands multiple times.
     *
     * @param player The player to execute commands for
     * @param ranks List of ranks whose commands should be queued
     */
    fun queueMultipleRankCommands(player: Player, ranks: List<RankData>) {
        if (ranks.isEmpty()) return

        val config = configProvider()
        val playerQueue = getPlayerQueue(player.uniqueId)
        val currentCount = playerQueue.size

        if (currentCount >= config.maxQueueSizePerPlayer) {
            logger.warning("Command queue limit reached for ${player.name}. Skipping commands from ${ranks.size} ranks")
            return
        }

        // Store player name for logging
        playerNames[player.uniqueId] = player.name

        // Sanitize player name once
        val sanitizedName = sanitizePlayerName(player.name)

        var addedCount = 0
        val maxToAdd = config.maxQueueSizePerPlayer - currentCount

        for (rank in ranks) {
            if (rank.commands.isEmpty()) continue

            // Prepare placeholders for this rank
            val placeholders = mapOf(
                "player" to sanitizedName,
                "player_uuid" to player.uniqueId.toString(),
                "rank" to rank.id,
                "rank_display" to rank.displayName
            )

            for (command in rank.commands) {
                if (addedCount >= maxToAdd) {
                    logger.warning("Queue limit reached for ${player.name}. Some commands were skipped.")
                    break
                }

                // Process placeholders
                var processedCommand = command
                placeholders.forEach { (key, value) ->
                    processedCommand = processedCommand
                        .replace("{$key}", value)
                        .replace("%$key%", value)
                }

                playerQueue.offer(
                    QueuedCommand(
                        playerUUID = player.uniqueId,
                        playerName = sanitizedName,
                        command = processedCommand,
                        rankId = rank.id
                    )
                )
                addedCount++
            }

            if (addedCount >= maxToAdd) break
        }

        if (addedCount > 0) {
            if (config.debugLogging) {
                logger.info("Queued $addedCount commands for ${player.name} from ${ranks.size} ranks")
            }

            // Ensure processor is running
            ensureProcessorRunning()
        }
    }

    /**
     * Cancel all pending commands for a player.
     * O(1) operation - just clears the player's queue.
     * Called when player disconnects or on admin command.
     *
     * @param uuid The player's UUID
     * @return Number of commands cancelled
     */
    fun cancelPlayerCommands(uuid: UUID): Int {
        // Read player name BEFORE removing from the map
        val playerName = playerNames[uuid] ?: uuid.toString()

        val playerQueue = playerQueues.remove(uuid)
        val count = playerQueue?.size ?: 0

        // Remove from round-robin order
        activePlayerOrder.remove(uuid)

        // Clean up player name
        playerNames.remove(uuid)

        if (count > 0) {
            logger.info("Cancelled $count pending commands for player $playerName")
        }

        return count
    }

    /**
     * Get current queue statistics.
     */
    fun getStats(): QueueStats {
        val totalPending = playerQueues.values.sumOf { it.size }
        val playersWithPending = playerQueues.count { it.value.isNotEmpty() }
        val avgPerPlayer = if (playersWithPending > 0) {
            totalPending.toDouble() / playersWithPending
        } else {
            0.0
        }

        return QueueStats(
            totalPending = totalPending,
            playersWithPending = playersWithPending,
            commandsExecutedTotal = commandsExecutedTotal,
            isProcessing = isProcessing,
            queueMode = configProvider().queueMode.name,
            averageCommandsPerPlayer = avgPerPlayer
        )
    }

    /**
     * Get pending command count for a specific player.
     */
    fun getPendingCount(uuid: UUID): Int {
        return playerQueues[uuid]?.size ?: 0
    }

    /**
     * Ensure the command processor task is running.
     */
    private fun ensureProcessorRunning() {
        if (processingTask == null || processingTask?.isCancelled == true) {
            startProcessor()
        }
    }

    /**
     * Start the command processing task.
     */
    private fun startProcessor() {
        val config = configProvider()

        processingTask = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            when (config.queueMode) {
                QueueMode.FIFO -> processCommandBatchFIFO(config.commandsPerTick)
                QueueMode.ROUND_ROBIN -> processCommandBatchRoundRobin(config.commandsPerTick)
            }
        }, 1L, config.tickInterval)

        isProcessing = true

        if (config.debugLogging) {
            logger.info("Command queue processor started (mode: ${config.queueMode})")
        }
    }

    /**
     * Process a batch of commands using FIFO mode.
     * Processes commands in the order they were received globally.
     */
    private fun processCommandBatchFIFO(batchSize: Int) {
        if (playerQueues.isEmpty() || playerQueues.all { it.value.isEmpty() }) {
            stopProcessor()
            return
        }

        val config = configProvider()
        var processed = 0

        // Process from each player's queue in order
        val iterator = playerQueues.entries.iterator()
        while (processed < batchSize && iterator.hasNext()) {
            val (uuid, queue) = iterator.next()

            while (processed < batchSize && queue.isNotEmpty()) {
                val queuedCommand = queue.poll() ?: break

                // Check if player is still online
                val player = Bukkit.getPlayer(uuid)
                if (player == null || !player.isOnline) {
                    // Player disconnected, clear their queue
                    queue.clear()
                    break
                }

                // Execute the command
                executeCommand(player, queuedCommand.command)
                processed++
                commandsExecutedTotal++

                if (config.debugLogging) {
                    logger.info("Executed command for ${queuedCommand.playerName}: ${queuedCommand.command.take(50)}...")
                }
            }

            // Clean up empty queues
            if (queue.isEmpty()) {
                playerQueues.remove(uuid)
                activePlayerOrder.remove(uuid)
                playerNames.remove(uuid)
            }
        }
    }

    /**
     * Process a batch of commands using Round-Robin mode.
     * Alternates between players for fair distribution.
     */
    private fun processCommandBatchRoundRobin(batchSize: Int) {
        if (playerQueues.isEmpty() || playerQueues.all { it.value.isEmpty() }) {
            stopProcessor()
            return
        }

        val config = configProvider()
        var processed = 0
        var emptyPasses = 0
        val maxEmptyPasses = activePlayerOrder.size + 1

        while (processed < batchSize && emptyPasses < maxEmptyPasses) {
            // Get next player in round-robin order
            val uuid = activePlayerOrder.poll()
            if (uuid == null) {
                emptyPasses++
                continue
            }

            val queue = playerQueues[uuid]
            if (queue == null || queue.isEmpty()) {
                // Clean up and don't re-add to rotation
                playerQueues.remove(uuid)
                playerNames.remove(uuid)
                emptyPasses++
                continue
            }

            // Check if player is still online
            val player = Bukkit.getPlayer(uuid)
            if (player == null || !player.isOnline) {
                // Player disconnected, clear their queue
                queue.clear()
                playerQueues.remove(uuid)
                playerNames.remove(uuid)
                emptyPasses++
                continue
            }

            // Process ONE command for this player (fair distribution)
            val queuedCommand = queue.poll()
            if (queuedCommand != null) {
                executeCommand(player, queuedCommand.command)
                processed++
                commandsExecutedTotal++
                emptyPasses = 0 // Reset empty counter

                if (config.debugLogging) {
                    logger.info("[RR] Executed for ${queuedCommand.playerName}: ${queuedCommand.command.take(50)}...")
                }
            }

            // Re-add player to rotation if they have more commands
            if (queue.isNotEmpty()) {
                activePlayerOrder.offer(uuid)
            } else {
                // Clean up empty queue
                playerQueues.remove(uuid)
                playerNames.remove(uuid)
            }
        }
    }

    /**
     * Execute a single command.
     */
    private fun executeCommand(player: Player, command: String) {
        try {
            when {
                command.startsWith("[console]") -> {
                    val cmd = command.removePrefix("[console]").trim()
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
                }
                command.startsWith("[player]") -> {
                    val cmd = command.removePrefix("[player]").trim()
                    player.performCommand(cmd)
                }
                command.startsWith("[op]") -> {
                    // SECURITY: [op] prefix removed - execute as console instead
                    logger.warning("Command uses deprecated [op] prefix. Use [console] instead: $command")
                    val cmd = command.removePrefix("[op]").trim()
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
                }
                command.startsWith("[message]") -> {
                    val msg = command.removePrefix("[message]").trim()
                    player.sendMessage(ColorUtil.parse(msg))
                }
                else -> {
                    // Default: run as console
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
                }
            }
        } catch (e: Exception) {
            logger.warning("Failed to execute command '$command' for ${player.name}: ${e.message}")
        }
    }

    /**
     * Stop the command processor task.
     */
    private fun stopProcessor() {
        processingTask?.cancel()
        processingTask = null
        isProcessing = false

        val config = configProvider()
        if (config.debugLogging) {
            logger.info("Command queue processor stopped (queue empty)")
        }
    }

    /**
     * Sanitize player name to prevent command injection.
     */
    private fun sanitizePlayerName(name: String): String {
        // Allow: letters, digits, underscore, dot, dash, asterisk, space
        // Covers Java Edition (a-z, 0-9, _) and Bedrock Edition via Geyser/Floodgate (. - * prefix)
        return name.filter { it.isLetterOrDigit() || it in "._-* " }.take(19)
    }

    /**
     * Shutdown the service and cancel all pending commands.
     * Call this on plugin disable.
     */
    fun shutdown() {
        processingTask?.cancel()
        processingTask = null
        isProcessing = false

        val pendingCount = playerQueues.values.sumOf { it.size }
        playerQueues.clear()
        activePlayerOrder.clear()
        playerNames.clear()

        if (pendingCount > 0) {
            logger.info("Command queue shutdown. Cancelled $pendingCount pending commands.")
        }
    }
}
