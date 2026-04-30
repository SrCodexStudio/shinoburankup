package com.srcodex.shinobustore.command

import com.srcodex.shinobustore.ShinobuStore
import com.srcodex.shinobustore.transaction.CompletedTransaction
import com.srcodex.shinobustore.util.ColorUtil
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Unified ShinobuStore command handler.
 * Provides access to all plugin features through /shinobustore.
 * Commands: /shinobustore, /ss
 *
 * Delegates shared operations (open, cancel, timer) to
 * [com.srcodex.shinobustore.service.StoreActions] to avoid code duplication.
 */
class ShinobuStoreCommand(private val plugin: ShinobuStore) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "help", "?" -> showHelp(sender)
            "open", "store", "shop" -> handleOpen(sender)
            "cancel" -> handleCancel(sender)
            "timer", "time" -> handleTimer(sender)
            "lookup", "search" -> handleLookup(sender, args)
            "reload" -> handleReload(sender)
            "version", "ver", "about" -> showVersion(sender)
            "debug" -> handleDebug(sender, args)
            "forcecapture" -> handleForceCapture(sender, args)
            "clearpending" -> handleClearPending(sender, args)
            "simulate", "test" -> handleSimulate(sender, args)
            else -> {
                sender.sendMessage(plugin.configManager.getMessage("general.unknown-command"))
                showHelp(sender)
            }
        }

        return true
    }

    /**
     * Shows help information.
     */
    private fun showHelp(sender: CommandSender) {
        sender.sendMessage(ColorUtil.colorize("&6&l======= ShinobuStore Help ======="))
        sender.sendMessage("")
        sender.sendMessage(ColorUtil.colorize("&e/shinobustore open &7- Open the store menu"))
        sender.sendMessage(ColorUtil.colorize("&e/shinobustore cancel &7- Cancel pending transactions"))
        sender.sendMessage(ColorUtil.colorize("&e/shinobustore timer &7- Check transaction timers"))

        if (sender.hasPermission("shinobustore.lookup")) {
            sender.sendMessage(ColorUtil.colorize("&e/shinobustore lookup <...> &7- Search transactions"))
        }

        if (sender.hasPermission("shinobustore.admin")) {
            sender.sendMessage("")
            sender.sendMessage(ColorUtil.colorize("&c&lAdmin Commands:"))
            sender.sendMessage(ColorUtil.colorize("&e/shinobustore reload &7- Reload configuration"))
            sender.sendMessage(ColorUtil.colorize("&e/shinobustore debug [on|off] &7- Toggle debug mode"))
            sender.sendMessage(ColorUtil.colorize("&e/shinobustore forcecapture <orderId> &7- Force capture order"))
            sender.sendMessage(ColorUtil.colorize("&e/shinobustore clearpending [--confirm|player] &7- Clear pending transactions"))
        }

        sender.sendMessage("")
        sender.sendMessage(ColorUtil.colorize("&e/shinobustore version &7- Show plugin version"))
        sender.sendMessage(ColorUtil.colorize("&6&l==============================="))
    }

    /**
     * Shows plugin version.
     */
    private fun showVersion(sender: CommandSender) {
        sender.sendMessage(ColorUtil.colorize("&6&lShinobuStore &7v${plugin.description.version}"))
        sender.sendMessage(ColorUtil.colorize("&7Author: &eSrCodexStudio / SrCodex"))
        sender.sendMessage(ColorUtil.colorize("&7Website: &bhttps://srcodex.com"))
        sender.sendMessage(ColorUtil.colorize("&7Kotlin Edition with PayPal v2 API"))
    }

    // ---------------------------------------------------------------
    //  Delegated to StoreActions (eliminates duplicated logic)
    // ---------------------------------------------------------------

    /**
     * Handles store open command -- delegates to StoreActions.
     */
    private fun handleOpen(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage(plugin.configManager.getMessage("general.player-only"))
            return
        }

        if (!sender.hasPermission("shinobustore.use")) {
            sender.sendMessage(plugin.configManager.getMessage("general.no-permission"))
            return
        }

        plugin.storeActions.openStore(sender)
    }

    /**
     * Handles cancel command -- delegates to StoreActions.
     */
    private fun handleCancel(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage(plugin.configManager.getMessage("general.player-only"))
            return
        }

        plugin.storeActions.cancelPending(sender)
    }

    /**
     * Handles timer command -- delegates to StoreActions.
     */
    private fun handleTimer(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage(plugin.configManager.getMessage("general.player-only"))
            return
        }

        plugin.storeActions.showTimer(sender)
    }

    // ---------------------------------------------------------------
    //  Admin commands
    // ---------------------------------------------------------------

    /**
     * Handles lookup command -- delegates to LookupCommand logic.
     * Uses safe call on getCommand to avoid NPE if "lookup" is unregistered.
     */
    private fun handleLookup(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("shinobustore.lookup")) {
            sender.sendMessage(plugin.configManager.getMessage("general.no-permission"))
            return
        }

        val lookupArgs = if (args.size > 1) args.drop(1).toTypedArray() else emptyArray()
        val lookupCmd = plugin.getCommand("lookup") ?: run {
            // Fallback: execute directly without a Command reference
            // This handles the edge case where the command is not registered
            LookupCommand(plugin).onCommand(sender, object : Command("lookup") {
                override fun execute(s: CommandSender, l: String, a: Array<out String>) = false
            }, "lookup", lookupArgs)
            return
        }
        LookupCommand(plugin).onCommand(sender, lookupCmd, "lookup", lookupArgs)
    }

    /**
     * Handles reload command -- calls plugin.reload() for full reload
     * (config + rate limiter recreation), not just configManager.reload().
     */
    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission("shinobustore.reload")) {
            sender.sendMessage(plugin.configManager.getMessage("general.no-permission"))
            return
        }

        plugin.reload()
        sender.sendMessage(plugin.configManager.getMessage("general.reload-success"))
    }

    /**
     * Handles debug toggle.
     */
    private fun handleDebug(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("shinobustore.admin")) {
            sender.sendMessage(plugin.configManager.getMessage("general.no-permission"))
            return
        }

        if (args.size < 2) {
            val status = if (plugin.configManager.debugEnabled) "&aenabled" else "&cdisabled"
            sender.sendMessage(ColorUtil.colorize("&7Debug mode is currently $status"))
            sender.sendMessage(ColorUtil.colorize("&7Usage: /shinobustore debug <on|off>"))
            return
        }

        when (args[1].lowercase()) {
            "on", "true", "enable" -> {
                plugin.configManager.setDebug(true)
                sender.sendMessage(ColorUtil.colorize("&aDebug mode enabled"))
            }
            "off", "false", "disable" -> {
                plugin.configManager.setDebug(false)
                sender.sendMessage(ColorUtil.colorize("&cDebug mode disabled"))
            }
            else -> sender.sendMessage(ColorUtil.colorize("&7Usage: /shinobustore debug <on|off>"))
        }
    }

    /**
     * Handles force capture command.
     */
    private fun handleForceCapture(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("shinobustore.admin")) {
            sender.sendMessage(plugin.configManager.getMessage("general.no-permission"))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(ColorUtil.colorize("&cUsage: /shinobustore forcecapture <orderId>"))
            return
        }

        val orderId = args[1]
        val pending = plugin.transactionManager.getPending(orderId)

        if (pending == null) {
            sender.sendMessage(ColorUtil.colorize("&cTransaction not found: $orderId"))
            return
        }

        sender.sendMessage(ColorUtil.colorize("&eAttempting to force capture order: $orderId"))

        // Trigger capture check
        plugin.captureTask.checkTransaction(pending)

        sender.sendMessage(ColorUtil.colorize("&aCapture check initiated. Check console for results."))
    }

    /**
     * Handles clear pending command.
     * Requires --confirm flag for clearing ALL pending transactions (safety measure).
     * Supports per-player clearing: /shinobustore clearpending <playerName>
     */
    private fun handleClearPending(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("shinobustore.admin")) {
            sender.sendMessage(plugin.configManager.getMessage("general.no-permission"))
            return
        }

        // No arguments: show warning and require --confirm
        if (args.size < 2) {
            val count = plugin.transactionManager.getAllPending().size
            sender.sendMessage(ColorUtil.colorize(
                "&cThis will clear &f$count &cpending transaction(s) for ALL players!"))
            sender.sendMessage(ColorUtil.colorize(
                "&cUse &f/shinobustore clearpending --confirm &cto confirm."))
            return
        }

        // --confirm flag: clear all pending transactions
        if (args[1] == "--confirm") {
            val count = plugin.transactionManager.clearAllPending()
            sender.sendMessage(ColorUtil.colorize("&aCleared $count pending transaction(s)."))
            plugin.logger.info("${sender.name} cleared all pending transactions ($count)")
            return
        }

        // Per-player clear: /shinobustore clearpending <playerName>
        val playerName = args[1]
        val target = Bukkit.getOfflinePlayer(playerName)
        val removed = plugin.transactionManager.removePendingForPlayer(target.uniqueId)
        sender.sendMessage(ColorUtil.colorize(
            "&aCleared ${removed.size} pending transaction(s) for $playerName."))
    }

    /**
     * Handles the simulate/test command.
     * Simulates a full purchase without hitting PayPal API.
     */
    private fun handleSimulate(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("shinobustore.admin")) {
            sender.sendMessage(plugin.configManager.getMessage("general.no-permission"))
            return
        }

        if (sender !is org.bukkit.entity.Player) {
            sender.sendMessage(plugin.configManager.getMessage("simulate.not-player"))
            return
        }

        val player = sender

        // No args: list available items
        if (args.size < 2) {
            sender.sendMessage(plugin.configManager.getMessage("simulate.list-header"))
            val items = plugin.configManager.items
            for ((id, item) in items) {
                sender.sendMessage(ColorUtil.colorize(
                    "&8  - &f$id &7(${item.display} &7- ${plugin.configManager.currencySymbol}${String.format(java.util.Locale.US, "%.2f", item.cost)})"
                ))
            }
            return
        }

        val itemId = args[1].lowercase()
        val item = plugin.configManager.items[itemId]
        if (item == null) {
            sender.sendMessage(plugin.configManager.getMessage("simulate.item-not-found", mapOf("item" to itemId)))
            return
        }

        // Simulate the purchase
        sender.sendMessage(plugin.configManager.getMessage("simulate.started", mapOf("item" to item.display)))

        val now = System.currentTimeMillis()
        val orderId = "TEST-${java.util.UUID.randomUUID().toString().substring(0, 8)}"
        val cost = item.cost
        val total = item.calculateTotal(
            plugin.configManager.feePercentage,
            plugin.configManager.feeFixed
        )
        val fee = total - cost

        // Execute reward commands on main thread (we're already on main thread)
        val executedCommands = mutableListOf<String>()
        for (cmd in item.commands) {
            val processedCmd = cmd
                .replace("{player}", player.name)
                .replace("{uuid}", player.uniqueId.toString())
                .replace("{item}", item.id)
                .replace("{cost}", String.format(java.util.Locale.US, "%.2f", cost))
                .replace("{total}", String.format(java.util.Locale.US, "%.2f", total))
            try {
                plugin.server.dispatchCommand(plugin.server.consoleSender, processedCmd)
                executedCommands.add(processedCmd)
            } catch (e: Exception) {
                plugin.logger.warning("[TEST] Failed to execute command '$processedCmd': ${e.message}")
            }
        }

        // Record in transaction history
        val completed = CompletedTransaction(
            orderId = orderId,
            captureId = "TEST-SIMULATED",
            playerUuid = player.uniqueId,
            playerName = player.name,
            itemId = item.id,
            itemDisplay = ColorUtil.stripColors(item.display),
            cost = cost,
            fee = fee,
            total = total,
            createdAt = now,
            completedAt = now,
            commandsExecuted = executedCommands
        )
        plugin.transactionManager.addHistory(completed)

        // Notify
        sender.sendMessage(plugin.configManager.getMessage("simulate.completed",
            mapOf("count" to executedCommands.size.toString())))

        plugin.logger.info("[TEST] Simulated purchase: ${player.name} bought ${item.id} ($orderId) - ${executedCommands.size} commands executed")
    }

    // ---------------------------------------------------------------
    //  Tab completion
    // ---------------------------------------------------------------

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        val completions = mutableListOf<String>()

        when (args.size) {
            1 -> {
                completions.addAll(listOf("help", "open", "cancel", "timer", "version"))

                if (sender.hasPermission("shinobustore.lookup")) {
                    completions.add("lookup")
                }

                if (sender.hasPermission("shinobustore.admin")) {
                    completions.addAll(listOf("reload", "debug", "forcecapture", "clearpending", "simulate"))
                }
            }
            2 -> {
                when (args[0].lowercase()) {
                    "debug" -> completions.addAll(listOf("on", "off"))
                    "lookup" -> completions.addAll(listOf("player", "transaction", "pending", "recent", "stats"))
                    "clearpending" -> {
                        if (sender.hasPermission("shinobustore.admin")) {
                            completions.add("--confirm")
                            completions.addAll(Bukkit.getOnlinePlayers().map { it.name })
                        }
                    }
                    "simulate", "test" -> {
                        if (sender.hasPermission("shinobustore.admin")) {
                            completions.addAll(plugin.configManager.items.keys)
                        }
                    }
                }
            }
            3 -> {
                if (args[0].lowercase() == "lookup") {
                    when (args[1].lowercase()) {
                        "player", "pending" -> {
                            completions.addAll(Bukkit.getOnlinePlayers().map { it.name })
                        }
                        "recent" -> {
                            completions.addAll(listOf("5", "10", "20", "50"))
                        }
                    }
                }
            }
        }

        return completions.filter { it.startsWith(args.last(), ignoreCase = true) }
    }
}
