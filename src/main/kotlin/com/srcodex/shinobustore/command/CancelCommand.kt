package com.srcodex.shinobustore.command

import com.srcodex.shinobustore.ShinobuStore
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Cancel transaction command handler.
 * Commands: /cancelitem, /cancel, /canceltransaction
 *
 * Delegates cancellation logic to [com.srcodex.shinobustore.service.StoreActions]
 * to avoid duplication with [ShinobuStoreCommand].
 */
class CancelCommand(private val plugin: ShinobuStore) : CommandExecutor, TabCompleter {

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

        plugin.storeActions.cancelPending(sender)
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> = emptyList()
}
