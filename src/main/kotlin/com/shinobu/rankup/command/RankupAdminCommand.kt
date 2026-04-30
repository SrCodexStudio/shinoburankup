package com.shinobu.rankup.command

import com.shinobu.rankup.service.RankupService
import com.shinobu.rankup.util.ColorUtil
import com.shinobu.rankup.util.PluginCoroutineScope
import com.shinobu.rankup.util.RateLimiter
import com.shinobu.rankup.util.formatMoney
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/**
 * Command: /rankupadmin
 * Admin commands for managing the rankup system.
 *
 * Subcommands:
 * - reload: Reload configurations
 * - setrank <player> <rank>: Set a player's rank
 * - reset <player>: Reset a player's rankup data
 * - give <player> <amount>: Give free rankups (not implemented yet)
 * - info <player>: View player's rankup information
 */
class RankupAdminCommand(
    private val plugin: Plugin,
    private val rankupService: RankupService,
    private val coroutineScope: PluginCoroutineScope,
    private val messageProvider: CommandManager.MessageProvider
) : CommandExecutor, TabCompleter {

    private val basePermission = "shinoburankup.admin"

    private val subcommands = listOf("reload", "setrank", "reset", "give", "info", "help")

    // Rate limiter for give command to prevent abuse (3 second cooldown)
    private val giveRateLimiter = RateLimiter(cooldownMs = 3000L, maxAttempts = 3)

    // Maximum rankups allowed per give command to prevent server overload
    private companion object {
        const val MAX_GIVE_AMOUNT = 50
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (!sender.hasPermission(basePermission)) {
            sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("general.no-permission")))
            return true
        }

        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> handleReload(sender)
            "setrank" -> handleSetRank(sender, args)
            "reset" -> handleReset(sender, args)
            "give" -> handleGive(sender, args)
            "info" -> handleInfo(sender, args)
            "help" -> showHelp(sender)
            else -> {
                sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("general.invalid-arguments")))
            }
        }

        return true
    }

    /**
     * Handle /rankupadmin reload
     */
    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission("$basePermission.reload")) {
            sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("general.no-permission")))
            return
        }

        try {
            // Call the full plugin reload method
            if (plugin is com.shinobu.rankup.ShinobuRankup) {
                plugin.reload()
                val rankCount = rankupService.getAllRanks().size
                sender.sendMessage(ColorUtil.parse(
                    messageProvider.getMessage("admin.reload.success", mapOf("rank_count" to rankCount.toString()))
                ))
            } else {
                plugin.reloadConfig()
                sender.sendMessage(ColorUtil.parse(
                    messageProvider.getMessage("admin.reload.success", mapOf("rank_count" to "0"))
                ))
            }
        } catch (e: Exception) {
            sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("admin.reload.fail")))
            plugin.logger.severe("Error reloading config: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Handle /rankupadmin setrank <player> <rank>
     */
    private fun handleSetRank(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("$basePermission.setrank")) {
            sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("general.no-permission")))
            return
        }

        if (args.size < 3) {
            sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("general.invalid-arguments")))
            return
        }

        val playerName = args[1]
        val rankId = args[2]

        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            sender.sendMessage(ColorUtil.parse(
                messageProvider.getMessage("general.player-not-found", mapOf("player" to playerName))
            ))
            return
        }

        // Check if rank exists
        val rank = rankupService.getRankById(rankId)
        if (rank == null) {
            sender.sendMessage(ColorUtil.parse(
                messageProvider.getMessage("admin.setrank.rank-not-found", mapOf("rank" to rankId))
            ))
            return
        }

        // Execute async
        coroutineScope.launch {
            val success = rankupService.setPlayerRank(targetPlayer, rankId)

            if (success) {
                sender.sendMessage(ColorUtil.parse(
                    messageProvider.getMessage("admin.setrank.success", mapOf(
                        "player" to targetPlayer.name,
                        "rank_display" to rank.displayName
                    ))
                ))
                targetPlayer.sendMessage(ColorUtil.parse(
                    messageProvider.getMessage("admin.setrank.success-target", mapOf(
                        "rank_display" to rank.displayName
                    ))
                ))
            } else {
                sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("admin.setrank.fail")))
            }
        }
    }

    /**
     * Handle /rankupadmin reset <player>
     */
    private fun handleReset(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("$basePermission.reset")) {
            sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("general.no-permission")))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("general.invalid-arguments")))
            return
        }

        val playerName = args[1]
        val targetPlayer = Bukkit.getPlayer(playerName)

        if (targetPlayer == null) {
            sender.sendMessage(ColorUtil.parse(
                messageProvider.getMessage("general.player-not-found", mapOf("player" to playerName))
            ))
            return
        }

        coroutineScope.launch {
            val success = rankupService.resetPlayerData(targetPlayer)

            if (success) {
                sender.sendMessage(ColorUtil.parse(
                    messageProvider.getMessage("admin.reset.success", mapOf("player" to targetPlayer.name))
                ))
                targetPlayer.sendMessage(ColorUtil.parse(
                    messageProvider.getMessage("admin.reset.success-target")
                ))
            } else {
                sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("admin.reload.fail")))
            }
        }
    }

    /**
     * Handle /rankupadmin give <player> <amount>
     * Gives free rankups that don't cost money.
     *
     * SECURITY: Rate limited and max amount capped to prevent abuse.
     */
    private fun handleGive(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("$basePermission.give")) {
            sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("general.no-permission")))
            return
        }

        // Rate limit check for admin give command
        if (sender is Player) {
            if (!giveRateLimiter.isAllowed(sender.uniqueId)) {
                val remaining = giveRateLimiter.getRemainingCooldownSeconds(sender.uniqueId)
                sender.sendMessage(ColorUtil.parse("&cPlease wait ${remaining}s before using this command again."))
                return
            }
        }

        if (args.size < 3) {
            sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("general.invalid-arguments")))
            return
        }

        val playerName = args[1]
        val requestedAmount = args[2].toIntOrNull()

        if (requestedAmount == null || requestedAmount <= 0) {
            sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("general.invalid-arguments")))
            return
        }

        // SECURITY: Cap maximum amount to prevent server overload
        val amount = requestedAmount.coerceAtMost(MAX_GIVE_AMOUNT)
        if (requestedAmount > MAX_GIVE_AMOUNT) {
            sender.sendMessage(ColorUtil.parse("&eAmount capped to $MAX_GIVE_AMOUNT (requested: $requestedAmount)"))
        }

        val targetPlayer = Bukkit.getPlayer(playerName)
        if (targetPlayer == null) {
            sender.sendMessage(ColorUtil.parse(
                messageProvider.getMessage("general.player-not-found", mapOf("player" to playerName))
            ))
            return
        }

        coroutineScope.launch {
            var successCount = 0
            var currentRankDisplay = ""

            repeat(amount) {
                val currentRank = rankupService.getCurrentRank(targetPlayer)
                val nextRank = currentRank?.let { rankupService.getNextRank(it) }

                if (nextRank == null) {
                    return@repeat
                }

                val success = rankupService.setPlayerRank(targetPlayer, nextRank.id)
                if (success) {
                    successCount++
                    currentRankDisplay = nextRank.displayName
                }
            }

            if (successCount > 0) {
                sender.sendMessage(ColorUtil.parse(
                    messageProvider.getMessage("admin.setrank.success", mapOf(
                        "player" to targetPlayer.name,
                        "rank_display" to currentRankDisplay
                    ))
                ))
                targetPlayer.sendMessage(ColorUtil.parse(
                    messageProvider.getMessage("rankupmax.success", mapOf(
                        "count" to successCount.toString(),
                        "rank_display" to currentRankDisplay
                    ))
                ))
            } else {
                sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("rankupmax.fail")))
            }
        }
    }

    /**
     * Handle /rankupadmin info <player>
     */
    private fun handleInfo(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("$basePermission.info")) {
            sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("general.no-permission")))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("general.invalid-arguments")))
            return
        }

        val playerName = args[1]
        val targetPlayer = Bukkit.getPlayer(playerName)

        if (targetPlayer == null) {
            sender.sendMessage(ColorUtil.parse(
                messageProvider.getMessage("general.player-not-found", mapOf("player" to playerName))
            ))
            return
        }

        coroutineScope.launch {
            val currentRank = rankupService.getCurrentRank(targetPlayer)
            val nextRank = currentRank?.let { rankupService.getNextRank(it) }
            val progress = rankupService.getRankProgress(targetPlayer)
            val allRanks = rankupService.getAllRanks()

            // Build placeholders for info message
            val placeholders = mapOf(
                "player" to targetPlayer.name,
                "rank_display" to (currentRank?.displayName ?: "-"),
                "rank_order" to (currentRank?.order?.toString() ?: "N/A"),
                "total_ranks" to allRanks.size.toString(),
                "next_rank" to (nextRank?.displayName ?: messageProvider.getMessage("admin.info.no-next-rank")),
                "next_rank_cost" to (nextRank?.cost?.formatMoney() ?: "-"),
                "balance" to "-",
                "progress" to String.format("%.1f", progress * 100),
                "progress_bar" to buildProgressBar(progress)
            )

            // Send header
            sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("admin.info.header", placeholders)))

            // Send info line
            sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("admin.info.line", placeholders)))
        }
    }

    /**
     * Build a simple progress bar.
     */
    private fun buildProgressBar(progress: Double): String {
        val filled = (progress * 20).toInt()
        val empty = 20 - filled
        return "<green>${"|".repeat(filled)}</green><gray>${"|".repeat(empty)}</gray>"
    }

    /**
     * Show help message.
     */
    private fun showHelp(sender: CommandSender) {
        // Header
        sender.sendMessage(ColorUtil.parse(
            messageProvider.getMessage("admin.help.header")
        ))

        // Player commands
        sender.sendMessage(ColorUtil.parse(
            messageProvider.getMessage("admin.help.player-commands")
        ))

        // Admin commands (only show if has admin permission)
        if (sender.hasPermission(basePermission)) {
            sender.sendMessage(ColorUtil.parse(
                messageProvider.getMessage("admin.help.admin-commands")
            ))
        }

        // Footer
        sender.sendMessage(ColorUtil.parse(
            messageProvider.getMessage("admin.help.footer")
        ))
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (!sender.hasPermission(basePermission)) {
            return emptyList()
        }

        return when (args.size) {
            1 -> {
                // Complete subcommand
                subcommands.filter { it.startsWith(args[0].lowercase()) }
            }
            2 -> {
                // Complete player name for relevant subcommands
                when (args[0].lowercase()) {
                    "setrank", "reset", "give", "info" -> {
                        Bukkit.getOnlinePlayers()
                            .map { it.name }
                            .filter { it.lowercase().startsWith(args[1].lowercase()) }
                    }
                    else -> emptyList()
                }
            }
            3 -> {
                // Complete rank name for setrank
                when (args[0].lowercase()) {
                    "setrank" -> {
                        rankupService.getAllRanks()
                            .map { it.id }
                            .filter { it.lowercase().startsWith(args[2].lowercase()) }
                    }
                    "give" -> {
                        // Suggest some common amounts
                        listOf("1", "5", "10", "25", "50", "100")
                            .filter { it.startsWith(args[2]) }
                    }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }
}
