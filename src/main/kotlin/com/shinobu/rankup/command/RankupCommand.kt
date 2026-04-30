package com.shinobu.rankup.command

import com.shinobu.rankup.service.RankupResult
import com.shinobu.rankup.service.RankupService
import com.shinobu.rankup.util.ColorUtil
import com.shinobu.rankup.util.PluginCoroutineScope
import com.shinobu.rankup.util.RateLimiter
import com.shinobu.rankup.util.formatMoney
import kotlinx.coroutines.launch
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/**
 * Command: /rankup
 * Allows players to upgrade to the next rank by spending money.
 */
class RankupCommand(
    private val plugin: Plugin,
    private val rankupService: RankupService,
    private val coroutineScope: PluginCoroutineScope,
    private val messageProvider: CommandManager.MessageProvider
) : CommandExecutor, TabCompleter {

    private val permission = "shinoburankup.rankup"

    // Rate limiter to prevent command spam (economy-safe: 2 second cooldown)
    private val rateLimiter = RateLimiter.ECONOMY_LIMITER

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("general.player-only")))
            return true
        }

        if (!sender.hasPermission(permission)) {
            sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("general.no-permission")))
            return true
        }

        // Check rate limit to prevent command spam
        if (!rateLimiter.isAllowed(sender.uniqueId)) {
            if (rateLimiter.isHardBlocked(sender.uniqueId)) {
                sender.sendMessage(ColorUtil.parse("&cYou are sending commands too fast! Please wait."))
            }
            return true
        }

        // Execute rankup asynchronously
        coroutineScope.launch {
            executeRankup(sender)
        }

        return true
    }

    /**
     * Execute the rankup logic.
     */
    private suspend fun executeRankup(player: Player) {
        // Show "processing" message
        player.sendMessage(ColorUtil.parse(messageProvider.getMessage("rankup.processing")))

        val result = rankupService.performRankup(player)

        when (result) {
            is RankupResult.Success -> {
                handleSuccess(player, result)
            }
            is RankupResult.Failure -> {
                handleFailure(player, result)
            }
        }
    }

    /**
     * Handle successful rankup.
     */
    private fun handleSuccess(player: Player, result: RankupResult.Success) {
        // Send success message from language file
        player.sendMessage(ColorUtil.parse(
            messageProvider.getMessage("rankup.success.message", mapOf(
                "rank" to result.newRank.displayName,
                "previous_rank" to result.previousRank.displayName,
                "cost" to result.cost.formatMoney(),
                "next_rank" to (result.nextRank?.displayName ?: "MAX")
            ))
        ))

        // Check for next rank and show progress
        coroutineScope.launch {
            showNextRankInfo(player)
        }
    }

    /**
     * Handle failed rankup.
     */
    private fun handleFailure(player: Player, failure: RankupResult.Failure) {
        // Play fail sound
        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f)

        when (failure) {
            is RankupResult.Failure.RequirementsNotMet -> {
                val headerMsg = messageProvider.getMessage("rankup.requirements-not-met")
                player.sendMessage(ColorUtil.parse(headerMsg))
                for (reqFailure in failure.failures) {
                    val msgKey = "rankup.requirement-${reqFailure.type}"
                    var msg = messageProvider.getMessage(msgKey)
                    msg = msg.replace("{required}", reqFailure.required)
                        .replace("{current}", reqFailure.current)
                        .replace("{permission}", reqFailure.required)
                        .replace("{item}", reqFailure.required)
                    player.sendMessage(ColorUtil.parse(msg))
                }
                return
            }
            is RankupResult.Failure.CooldownActive -> {
                val msg = messageProvider.getMessage("rankup.cooldown")
                    .replace("{remaining}", failure.remainingSeconds.toString())
                player.sendMessage(ColorUtil.parse(msg))
                return
            }
            is RankupResult.Failure.AlreadyProcessing -> {
                player.sendMessage(ColorUtil.parse(messageProvider.getMessage("rankup.already-processing")))
                return
            }
            else -> {
                // All other failure types use a single message
            }
        }

        val message = when (failure) {
            is RankupResult.Failure.AlreadyMaxRank -> {
                messageProvider.getMessage("rankup.fail-max-rank")
            }
            is RankupResult.Failure.InsufficientFunds -> {
                messageProvider.getMessage("rankup.fail-no-money", mapOf(
                    "cost" to failure.required.formatMoney(),
                    "balance" to failure.current.formatMoney()
                ))
            }
            is RankupResult.Failure.RankNotFound -> {
                messageProvider.getMessage("rankup.error")
            }
            is RankupResult.Failure.PlayerDataNotFound -> {
                messageProvider.getMessage("rankup.error")
            }
            is RankupResult.Failure.EconomyUnavailable -> {
                messageProvider.getMessage("rankup.error")
            }
            is RankupResult.Failure.CancelledByEvent -> {
                messageProvider.getMessage("rankup.error")
            }
            is RankupResult.Failure.EconomyError -> {
                messageProvider.getMessage("rankup.error")
            }
            is RankupResult.Failure.UnknownError -> {
                messageProvider.getMessage("rankup.error")
            }
            // RequirementsNotMet, CooldownActive, and AlreadyProcessing are handled above with early return
            is RankupResult.Failure.RequirementsNotMet,
            is RankupResult.Failure.CooldownActive,
            is RankupResult.Failure.AlreadyProcessing -> {
                // Unreachable due to early returns above, but required for exhaustive when
                messageProvider.getMessage("rankup.error")
            }
        }

        player.sendMessage(ColorUtil.parse(message))
    }

    /**
     * Show information about the next rank.
     */
    private suspend fun showNextRankInfo(player: Player) {
        // Next rank info is now handled by the reward service
        // This method can be extended if needed
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        // No tab completions for /rankup
        return emptyList()
    }
}
