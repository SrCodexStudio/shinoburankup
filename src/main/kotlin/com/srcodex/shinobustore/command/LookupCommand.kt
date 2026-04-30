package com.srcodex.shinobustore.command

import com.srcodex.shinobustore.ShinobuStore
import com.srcodex.shinobustore.util.TimeUtil
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/**
 * Lookup command handler for administrators.
 * Allows viewing transaction details.
 * Commands: /lookup
 */
class LookupCommand(private val plugin: ShinobuStore) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        // Check permission
        if (!sender.hasPermission("shinobustore.lookup")) {
            sender.sendMessage(plugin.configManager.getMessage("general.no-permission"))
            return true
        }

        if (args.isEmpty()) {
            showUsage(sender)
            return true
        }

        when (args[0].lowercase()) {
            "player" -> handlePlayerLookup(sender, args)
            "transaction", "tx" -> handleTransactionLookup(sender, args)
            "pending" -> handlePendingLookup(sender, args)
            "recent" -> handleRecentLookup(sender, args)
            "stats" -> handleStatsLookup(sender)
            else -> showUsage(sender)
        }

        return true
    }

    /**
     * Shows command usage.
     */
    private fun showUsage(sender: CommandSender) {
        sender.sendMessage(plugin.configManager.getMessage("lookup.usage-header"))
        sender.sendMessage("§e/lookup player <name> §7- View player transactions")
        sender.sendMessage("§e/lookup transaction <id> §7- View transaction details")
        sender.sendMessage("§e/lookup pending [player] §7- View pending transactions")
        sender.sendMessage("§e/lookup recent [count] §7- View recent transactions")
        sender.sendMessage("§e/lookup stats §7- View transaction statistics")
    }

    /**
     * Handles player lookup.
     */
    private fun handlePlayerLookup(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage(plugin.configManager.getMessage("lookup.player-required"))
            return
        }

        val playerName = args[1]
        val targetPlayer = Bukkit.getOfflinePlayer(playerName)

        if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline) {
            sender.sendMessage(plugin.configManager.getMessage("lookup.player-not-found"))
            return
        }

        val uuid = targetPlayer.uniqueId

        // Get pending transactions
        val pending = plugin.transactionManager.getPendingForPlayer(uuid)

        // Get completed transactions
        val completed = plugin.transactionManager.getHistoryForPlayer(uuid)

        sender.sendMessage(plugin.configManager.getMessage(
            "lookup.player-header",
            mapOf("player" to (targetPlayer.name ?: playerName))
        ))

        sender.sendMessage("§7Pending Transactions: §e${pending.size}")
        for (tx in pending) {
            val remaining = tx.expiresAt - System.currentTimeMillis()
            val timeStr = if (remaining > 0) TimeUtil.formatMillis(remaining) else "§cExpired"
            sender.sendMessage("  §7- ${tx.itemDisplay} §7| ${plugin.configManager.currencySymbol}${tx.total} §7| $timeStr")
        }

        sender.sendMessage("§7Completed Transactions: §a${completed.size}")
        val recentCompleted = completed.takeLast(5)
        for (tx in recentCompleted) {
            sender.sendMessage("  §7- ${tx.itemDisplay} §7| ${plugin.configManager.currencySymbol}${tx.total} §7| ${tx.orderId}")
        }

        if (completed.size > 5) {
            sender.sendMessage("  §7... and ${completed.size - 5} more")
        }
    }

    /**
     * Handles transaction lookup by ID.
     */
    private fun handleTransactionLookup(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) {
            sender.sendMessage(plugin.configManager.getMessage("lookup.transaction-id-required"))
            return
        }

        val orderId = args[1]

        // Search in pending
        val pending = plugin.transactionManager.getPending(orderId)
        if (pending != null) {
            sender.sendMessage("§6=== Pending Transaction ===")
            sender.sendMessage("§7Order ID: §e${pending.orderId}")
            sender.sendMessage("§7Player: §e${Bukkit.getOfflinePlayer(pending.playerUuid).name}")
            sender.sendMessage("§7Item: §e${pending.itemDisplay}")
            sender.sendMessage("§7Cost: §a${plugin.configManager.currencySymbol}${pending.cost}")
            sender.sendMessage("§7Fee: §a${plugin.configManager.currencySymbol}${pending.fee}")
            sender.sendMessage("§7Total: §a${plugin.configManager.currencySymbol}${pending.total}")
            sender.sendMessage("§7Status: §ePending")
            val remaining = pending.expiresAt - System.currentTimeMillis()
            if (remaining > 0) {
                sender.sendMessage("§7Expires in: §e${TimeUtil.formatMillis(remaining)}")
            } else {
                sender.sendMessage("§7Status: §cExpired")
            }
            sender.sendMessage("§7Checkout URL: §b${pending.checkoutUrl}")
            return
        }

        // Search in completed
        val completed = plugin.transactionManager.getHistory(orderId)
        if (completed != null) {
            sender.sendMessage("§a=== Completed Transaction ===")
            sender.sendMessage("§7Order ID: §e${completed.orderId}")
            sender.sendMessage("§7Capture ID: §e${completed.captureId}")
            sender.sendMessage("§7Player: §e${Bukkit.getOfflinePlayer(completed.playerUuid).name}")
            sender.sendMessage("§7Item: §e${completed.itemDisplay}")
            sender.sendMessage("§7Cost: §a${plugin.configManager.currencySymbol}${completed.cost}")
            sender.sendMessage("§7Fee: §a${plugin.configManager.currencySymbol}${completed.fee}")
            sender.sendMessage("§7Total: §a${plugin.configManager.currencySymbol}${completed.total}")
            sender.sendMessage("§7Completed: §a${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(completed.completedAt))}")
            sender.sendMessage("§7Commands Executed: §a${completed.commandsExecuted.size}")
            return
        }

        sender.sendMessage(plugin.configManager.getMessage("lookup.transaction-not-found"))
    }

    /**
     * Handles pending transactions lookup.
     */
    private fun handlePendingLookup(sender: CommandSender, args: Array<out String>) {
        val pending = if (args.size >= 2) {
            val playerName = args[1]
            val targetPlayer = Bukkit.getOfflinePlayer(playerName)
            if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline) {
                sender.sendMessage(plugin.configManager.getMessage("lookup.player-not-found"))
                return
            }
            plugin.transactionManager.getPendingForPlayer(targetPlayer.uniqueId)
        } else {
            plugin.transactionManager.getAllPending().toList()
        }

        if (pending.isEmpty()) {
            sender.sendMessage(plugin.configManager.getMessage("lookup.no-pending"))
            return
        }

        sender.sendMessage("§6=== Pending Transactions (${pending.size}) ===")
        for (tx in pending) {
            val playerName = Bukkit.getOfflinePlayer(tx.playerUuid).name ?: "Unknown"
            val remaining = tx.expiresAt - System.currentTimeMillis()
            val timeStr = if (remaining > 0) TimeUtil.formatMillis(remaining) else "§cExpired"
            sender.sendMessage("§7${playerName} §7| ${tx.itemDisplay} §7| ${plugin.configManager.currencySymbol}${tx.total} §7| $timeStr")
        }
    }

    /**
     * Handles recent transactions lookup.
     */
    private fun handleRecentLookup(sender: CommandSender, args: Array<out String>) {
        val count = if (args.size >= 2) {
            args[1].toIntOrNull()?.coerceIn(1, 50) ?: 10
        } else {
            10
        }

        val recent = plugin.transactionManager.getRecentCompleted(count)

        if (recent.isEmpty()) {
            sender.sendMessage(plugin.configManager.getMessage("lookup.no-recent"))
            return
        }

        sender.sendMessage("§a=== Recent Completed Transactions (${recent.size}) ===")
        for (tx in recent) {
            val playerName = Bukkit.getOfflinePlayer(tx.playerUuid).name ?: "Unknown"
            val date = java.text.SimpleDateFormat("MM/dd HH:mm").format(java.util.Date(tx.completedAt))
            sender.sendMessage("§7$date §7| $playerName §7| ${tx.itemDisplay} §7| ${plugin.configManager.currencySymbol}${tx.total}")
        }
    }

    /**
     * Handles statistics lookup.
     */
    private fun handleStatsLookup(sender: CommandSender) {
        val stats = plugin.transactionManager.getStatistics()

        sender.sendMessage("§6=== Transaction Statistics ===")
        sender.sendMessage("§7Total Completed: §a${stats.totalCompleted}")
        sender.sendMessage("§7Total Revenue: §a${plugin.configManager.currencySymbol}${String.format("%.2f", stats.totalRevenue)}")
        sender.sendMessage("§7Current Pending: §e${stats.currentPending}")
        sender.sendMessage("§7Today's Sales: §a${stats.todaySales}")
        sender.sendMessage("§7Today's Revenue: §a${plugin.configManager.currencySymbol}${String.format("%.2f", stats.todayRevenue)}")
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (!sender.hasPermission("shinobustore.lookup")) {
            return emptyList()
        }

        return when (args.size) {
            1 -> listOf("player", "transaction", "pending", "recent", "stats").filter {
                it.startsWith(args[0], ignoreCase = true)
            }
            2 -> when (args[0].lowercase()) {
                "player", "pending" -> Bukkit.getOnlinePlayers().map { it.name }.filter {
                    it.startsWith(args[1], ignoreCase = true)
                }
                "recent" -> listOf("5", "10", "20", "50").filter {
                    it.startsWith(args[1])
                }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
