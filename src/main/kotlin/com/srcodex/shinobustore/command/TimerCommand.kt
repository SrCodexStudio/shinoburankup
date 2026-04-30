package com.srcodex.shinobustore.command

import com.srcodex.shinobustore.ShinobuStore
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Timer command handler.
 * Shows remaining time for pending transactions.
 * Commands: /timer
 *
 * Delegates timer display logic to [com.srcodex.shinobustore.service.StoreActions]
 * to avoid duplication with [ShinobuStoreCommand].
 */
class TimerCommand(private val plugin: ShinobuStore) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage(plugin.configManager.getMessage("general.player-only"))
            return true
        }

        if (!sender.hasPermission("shinobustore.use")) {
            sender.sendMessage(plugin.configManager.getMessage("general.no-permission"))
            return true
        }

        plugin.storeActions.showTimer(sender)
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> = emptyList()
}
