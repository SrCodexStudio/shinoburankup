package com.srcodex.shinobustore.command

import com.srcodex.shinobustore.ShinobuStore
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Main store command handler.
 * Commands: /store, /shop, /buy, /tienda
 *
 * Delegates store-opening logic to [com.srcodex.shinobustore.service.StoreActions]
 * to avoid duplication with [ShinobuStoreCommand].
 */
class StoreCommand(private val plugin: ShinobuStore) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        // Handle reload subcommand (admin only, works from console too)
        if (args.isNotEmpty() && args[0].equals("reload", ignoreCase = true)) {
            if (!sender.hasPermission("shinobustore.reload")) {
                sender.sendMessage(plugin.configManager.getMessage("general.no-permission"))
                return true
            }
            plugin.reload()
            sender.sendMessage(plugin.configManager.getMessage("general.reload-success"))
            return true
        }

        // Main store command - must be a player
        if (sender !is Player) {
            sender.sendMessage(plugin.configManager.getMessage("general.player-only"))
            return true
        }

        // Permission check
        if (!sender.hasPermission("shinobustore.use")) {
            sender.sendMessage(plugin.configManager.getMessage("general.no-permission"))
            return true
        }

        // Delegate to shared StoreActions
        plugin.storeActions.openStore(sender)
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1 && sender.hasPermission("shinobustore.reload")) {
            return listOf("reload").filter { it.startsWith(args[0], ignoreCase = true) }
        }
        return emptyList()
    }
}
