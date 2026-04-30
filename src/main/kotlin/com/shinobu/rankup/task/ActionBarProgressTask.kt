package com.shinobu.rankup.task

import com.shinobu.rankup.config.FormatManager
import com.shinobu.rankup.data.RankData
import com.shinobu.rankup.hook.VaultHook
import com.shinobu.rankup.util.ColorUtil
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Repeating task that shows rankup progress in the action bar for online players.
 *
 * Runs on the main thread at a configurable tick interval so that Bukkit API
 * calls (sendActionBar) are thread-safe. Economy lookups via Vault are also
 * safe on the main thread since most economy providers synchronize internally.
 *
 * Design decisions:
 * - Uses synchronous runTaskTimer (not async) because sendActionBar requires
 *   the main thread via Paper's Adventure API.
 * - Vault getBalance is called on the main thread; this is acceptable because
 *   Vault economy providers are designed for synchronous access and the call
 *   is lightweight (in-memory lookup for most providers).
 * - The sorted rank list is cached per tick cycle to avoid re-sorting for
 *   every player.
 * - Per-player errors are silently caught to prevent one player's issue from
 *   breaking the action bar for everyone else.
 */
class ActionBarProgressTask(
    private val plugin: JavaPlugin,
    private val vaultHook: VaultHook,
    private val formatManager: FormatManager,
    private val rankProvider: () -> List<RankData>,
    private val playerRankProvider: (Player) -> RankData?,
    private val format: String = "&7{rank_display} &8[{progress_bar}&8] &e{progress}% &7-> &6{next_rank}",
    private val maxRankFormat: String = "&6&l* &e{rank_display} &6&l* &7MAX RANK",
    private val showAtMaxRank: Boolean = false,
    private val intervalTicks: Long = 20L
) {

    private var bukkitTask: BukkitTask? = null
    private val isRunning = AtomicBoolean(false)

    /**
     * Start the action bar progress task.
     *
     * Uses synchronous scheduling because sendActionBar must execute
     * on the main thread. Initial delay of 40 ticks (2 seconds) allows
     * the server to finish startup and player data to load.
     */
    fun start() {
        if (bukkitTask != null) {
            plugin.logger.warning("ActionBarProgressTask is already running!")
            return
        }

        bukkitTask = plugin.server.scheduler.runTaskTimer(
            plugin,
            Runnable { tick() },
            40L, // 2 second delay before first run
            intervalTicks
        )

        isRunning.set(true)
        plugin.logger.info("ActionBarProgressTask started with interval of ${intervalTicks} ticks")
    }

    /**
     * Stop the action bar progress task.
     */
    fun stop() {
        isRunning.set(false)

        bukkitTask?.cancel()
        bukkitTask = null

        plugin.logger.info("ActionBarProgressTask stopped")
    }

    /**
     * Check if the task is currently running.
     */
    fun isRunning(): Boolean = isRunning.get()

    /**
     * Main tick executed every interval.
     * Retrieves sorted ranks once per cycle, then iterates online players.
     */
    private fun tick() {
        if (!isRunning.get()) return
        if (!vaultHook.isEnabled()) return

        val onlinePlayers = Bukkit.getOnlinePlayers()
        if (onlinePlayers.isEmpty()) return

        // Sort ranks once per tick, not per player
        val allRanks = rankProvider().sortedBy { it.order }
        if (allRanks.isEmpty()) return

        for (player in onlinePlayers) {
            try {
                sendProgressBar(player, allRanks)
            } catch (_: Exception) {
                // Silently skip players with errors to avoid disrupting
                // the action bar for other players in the same tick cycle.
            }
        }
    }

    /**
     * Build and send the progress action bar for a single player.
     *
     * @param player The player to send the action bar to
     * @param allRanks Pre-sorted list of all ranks (ascending by order)
     */
    private fun sendProgressBar(player: Player, allRanks: List<RankData>) {
        val currentRank = playerRankProvider(player) ?: return
        val nextRank = allRanks.firstOrNull { it.order > currentRank.order }

        // Player is at max rank
        if (nextRank == null) {
            if (showAtMaxRank) {
                val message = maxRankFormat
                    .replace("{rank_display}", currentRank.displayName)
                    .replace("{rank_id}", currentRank.id)
                player.sendActionBar(ColorUtil.parse(message))
            }
            return
        }

        val balance = vaultHook.getBalance(player)
        val cost = nextRank.cost
        val progress = formatManager.calculateProgress(balance, cost)
        val progressInt = progress.toInt()
        val progressBar = formatManager.createProgressBar(progress)

        // Build the message with all available placeholders
        val message = format
            .replace("{rank_display}", currentRank.displayName)
            .replace("{rank_id}", currentRank.id)
            .replace("{next_rank}", nextRank.displayName)
            .replace("{next_rank_id}", nextRank.id)
            .replace("{progress_bar}", progressBar)
            .replace("{progress}", progressInt.toString())
            .replace("{cost}", formatManager.formatCurrency(cost))
            .replace("{balance}", formatManager.formatCurrency(balance))
            .replace("{remaining}", formatManager.formatCurrency((cost - balance).coerceAtLeast(0.0)))

        player.sendActionBar(ColorUtil.parse(message))
    }

    companion object {

        /**
         * Factory method to create and start the action bar progress task.
         *
         * @param plugin The owning plugin instance
         * @param vaultHook Vault economy hook (must be enabled)
         * @param formatManager Format manager for currency and progress bars
         * @param rankProvider Lambda that returns all available ranks
         * @param playerRankProvider Lambda that returns a player's current rank
         * @param format Action bar format string with placeholders
         * @param maxRankFormat Format shown when player is at max rank
         * @param showAtMaxRank Whether to show action bar at max rank
         * @param intervalTicks How often to update (default 20 = 1 second)
         * @return The started task instance
         */
        fun start(
            plugin: JavaPlugin,
            vaultHook: VaultHook,
            formatManager: FormatManager,
            rankProvider: () -> List<RankData>,
            playerRankProvider: (Player) -> RankData?,
            format: String = "&7{rank_display} &8[{progress_bar}&8] &e{progress}% &7-> &6{next_rank}",
            maxRankFormat: String = "&6&l* &e{rank_display} &6&l* &7MAX RANK",
            showAtMaxRank: Boolean = false,
            intervalTicks: Long = 20L
        ): ActionBarProgressTask {
            val task = ActionBarProgressTask(
                plugin = plugin,
                vaultHook = vaultHook,
                formatManager = formatManager,
                rankProvider = rankProvider,
                playerRankProvider = playerRankProvider,
                format = format,
                maxRankFormat = maxRankFormat,
                showAtMaxRank = showAtMaxRank,
                intervalTicks = intervalTicks
            )
            task.start()
            return task
        }
    }
}
