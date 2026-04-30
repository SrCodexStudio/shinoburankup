package com.shinobu.rankup.command

import com.shinobu.rankup.BuildConfig
import com.shinobu.rankup.data.MaxRankupResult
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
 * Command: /rankupmax
 * Allows players to rank up as many times as they can afford in one command.
 *
 * This command is FULLY ASYNC to prevent server lag when processing many rankups.
 */
class RankupMaxCommand(
    private val plugin: Plugin,
    private val rankupService: RankupService,
    private val coroutineScope: PluginCoroutineScope,
    private val messageProvider: CommandManager.MessageProvider
) : CommandExecutor, TabCompleter {

    private val permission = "shinoburankup.rankupmax"

    // Rate limiter to prevent command spam (economy-safe: 2 second cooldown)
    // RankupMax is more expensive, so we use the stricter economy limiter
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

        // Block command in FREE version
        if (BuildConfig.isFreeVersion()) {
            sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("rankupmax.disabled")))
            return true
        }

        if (!sender.hasPermission(permission)) {
            sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("general.no-permission")))
            return true
        }

        // Check rate limit to prevent command spam (especially important for economy operations)
        if (!rateLimiter.isAllowed(sender.uniqueId)) {
            if (rateLimiter.isHardBlocked(sender.uniqueId)) {
                sender.sendMessage(ColorUtil.parse("&cYou are sending commands too fast! Please wait."))
            }
            return true
        }

        // Execute max rankup asynchronously to prevent lag
        coroutineScope.launch {
            executeMaxRankup(sender)
        }

        return true
    }

    /**
     * Execute the max rankup logic.
     */
    private suspend fun executeMaxRankup(player: Player) {
        // Show "processing" message
        player.sendMessage(ColorUtil.parse(messageProvider.getMessage("rankupmax.processing")))

        // Calculate how many ranks they can afford first
        val (affordableRanks, _) = rankupService.calculateAffordableRanks(player)

        if (affordableRanks == 0) {
            // Show why they can't rank up
            player.sendMessage(ColorUtil.parse(messageProvider.getMessage("rankupmax.fail")))
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f)
            return
        }

        // Perform the max rankup
        val result = rankupService.performMaxRankup(player)

        if (result.success) {
            handleSuccess(player, result)
        } else {
            handleFailure(player, result)
        }
    }

    /**
     * Handle successful max rankup.
     * Note: The detailed summary is sent by RewardService.sendMaxRankupSummary()
     * This method is now simplified since RewardService handles the message
     */
    private fun handleSuccess(player: Player, result: MaxRankupResult) {
        // Message is already sent by RewardService.sendMaxRankupSummary()
        // No additional message needed here to avoid duplicates
    }

    /**
     * Handle failed max rankup.
     */
    private fun handleFailure(player: Player, result: MaxRankupResult) {
        player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f)
        player.sendMessage(ColorUtil.parse(messageProvider.getMessage("rankupmax.fail")))
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        // No tab completions for /rankupmax
        return emptyList()
    }
}
