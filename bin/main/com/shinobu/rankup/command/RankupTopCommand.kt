package com.shinobu.rankup.command

import com.shinobu.rankup.util.ColorUtil
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/**
 * Command: /rankuptop
 * Opens the top players leaderboard GUI.
 */
class RankupTopCommand(
    private val plugin: Plugin,
    private val guiOpener: CommandManager.GuiOpener,
    private val messageProvider: CommandManager.MessageProvider
) : CommandExecutor, TabCompleter {

    private val permission = "shinoburankup.top"

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

        // Open the top players GUI
        try {
            guiOpener.openTopPlayersGui(sender)
        } catch (e: Exception) {
            sender.sendMessage(ColorUtil.parse(messageProvider.getMessage("ranktop.error")))
            plugin.logger.severe("Error opening top players GUI for ${sender.name}: ${e.message}")
            e.printStackTrace()
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        // No tab completions for /rankuptop
        return emptyList()
    }
}
