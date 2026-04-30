package com.shinobu.rankup.command

import com.shinobu.rankup.service.RankupService
import com.shinobu.rankup.util.PluginCoroutineScope
import org.bukkit.command.CommandExecutor
import org.bukkit.command.PluginCommand
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Logger

/**
 * Manages all plugin commands registration and handling.
 */
class CommandManager(
    private val plugin: JavaPlugin,
    private val rankupService: RankupService,
    private val coroutineScope: PluginCoroutineScope,
    private val guiOpener: GuiOpener,
    private val messageProvider: MessageProvider
) {
    private val logger: Logger = plugin.logger
    private val registeredCommands = mutableListOf<String>()

    /**
     * Interface for opening GUIs from commands.
     */
    interface GuiOpener {
        fun openRanksGui(player: org.bukkit.entity.Player)
        fun openTopPlayersGui(player: org.bukkit.entity.Player)
    }

    /**
     * Interface for getting messages from config.
     */
    interface MessageProvider {
        fun getMessage(key: String, placeholders: Map<String, String> = emptyMap()): String
        fun getPrefix(): String
    }

    /**
     * Register all plugin commands.
     */
    fun registerCommands() {
        logger.info("Registering commands...")

        // Player commands
        registerCommand(
            "rankup",
            RankupCommand(plugin, rankupService, coroutineScope, messageProvider)
        )

        registerCommand(
            "rankupmax",
            RankupMaxCommand(plugin, rankupService, coroutineScope, messageProvider)
        )

        registerCommand(
            "ranks",
            RankCommand(plugin, guiOpener, messageProvider)
        )

        registerCommand(
            "rankuptop",
            RankupTopCommand(plugin, guiOpener, messageProvider)
        )

        // Admin commands (registered as 'shinoburankup' in plugin.yml)
        registerCommand(
            "shinoburankup",
            RankupAdminCommand(plugin, rankupService, coroutineScope, messageProvider)
        )

        logger.info("Successfully registered ${registeredCommands.size} commands")
    }

    /**
     * Register a single command.
     */
    private fun registerCommand(name: String, executor: CommandExecutor) {
        val command: PluginCommand? = plugin.getCommand(name)

        if (command == null) {
            logger.warning("Command '$name' not found in plugin.yml! Skipping registration.")
            return
        }

        command.setExecutor(executor)

        // Register tab completer if the executor implements it
        if (executor is TabCompleter) {
            command.tabCompleter = executor
        }

        registeredCommands.add(name)
        logger.info("Registered command: /$name")
    }

    /**
     * Unregister all commands (called on plugin disable).
     */
    fun unregisterCommands() {
        registeredCommands.forEach { name ->
            plugin.getCommand(name)?.setExecutor(null)
            plugin.getCommand(name)?.tabCompleter = null
        }
        registeredCommands.clear()
        logger.info("Unregistered all commands")
    }

    /**
     * Get a list of registered command names.
     */
    fun getRegisteredCommands(): List<String> = registeredCommands.toList()
}
