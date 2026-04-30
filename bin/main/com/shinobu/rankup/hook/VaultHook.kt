package com.shinobu.rankup.hook

import com.shinobu.rankup.util.ColorUtil
import net.milkbowl.vault.chat.Chat
import net.milkbowl.vault.economy.Economy
import net.milkbowl.vault.economy.EconomyResponse
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.text.DecimalFormat
import java.util.logging.Level

/**
 * Vault economy and chat integration for ShinobuRankup.
 * Provides safe access to economy operations and prefix/suffix management.
 *
 * Thread-safety: All methods are safe to call from any thread,
 * but actual operations may need to be synchronized by Vault implementation.
 */
class VaultHook(private val plugin: JavaPlugin) {

    @Volatile
    private var economy: Economy? = null

    @Volatile
    private var chat: Chat? = null

    @Volatile
    private var isSetup: Boolean = false

    @Volatile
    private var isChatSetup: Boolean = false

    private val currencyFormat = DecimalFormat("#,##0.00")

    /**
     * Setup Vault economy integration.
     * Must be called during plugin enable, after Vault is loaded.
     *
     * @return true if Vault economy is available and setup successfully
     */
    fun setup(): Boolean {
        if (isSetup) {
            return economy != null
        }

        if (!isVaultPresent()) {
            plugin.logger.warning("Vault not found! Economy features will be disabled.")
            isSetup = true
            return false
        }

        val rsp = plugin.server.servicesManager.getRegistration(Economy::class.java)
        if (rsp == null) {
            plugin.logger.warning("No economy provider found! Make sure you have an economy plugin installed.")
            isSetup = true
            return false
        }

        economy = rsp.provider
        isSetup = true

        plugin.logger.info("Successfully hooked into Vault economy: ${economy?.name ?: "Unknown"}")
        return true
    }

    /**
     * Setup Vault chat integration for prefix/suffix support.
     * Call this after economy setup.
     *
     * @return true if Vault chat is available and setup successfully
     */
    fun setupChat(): Boolean {
        if (isChatSetup) {
            return chat != null
        }

        if (!isVaultPresent()) {
            isChatSetup = true
            return false
        }

        val rsp = plugin.server.servicesManager.getRegistration(Chat::class.java)
        if (rsp == null) {
            plugin.logger.info("No Vault chat provider found. Prefix features will use displayname instead.")
            isChatSetup = true
            return false
        }

        chat = rsp.provider
        isChatSetup = true

        plugin.logger.info("Successfully hooked into Vault chat: ${chat?.name ?: "Unknown"}")
        return true
    }

    /**
     * Check if Vault plugin is present on the server.
     */
    private fun isVaultPresent(): Boolean {
        return try {
            plugin.server.pluginManager.getPlugin("Vault") != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if economy is available and working.
     */
    fun isEnabled(): Boolean = economy != null && isSetup

    /**
     * Get the name of the economy provider.
     */
    fun getEconomyName(): String = economy?.name ?: "None"

    /**
     * Get player's current balance.
     *
     * @param player The player to check
     * @return The player's balance, or 0.0 if economy is unavailable
     */
    fun getBalance(player: OfflinePlayer): Double {
        return try {
            economy?.getBalance(player) ?: 0.0
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to get balance for ${player.name}", e)
            0.0
        }
    }

    /**
     * Check if player has at least the specified amount.
     *
     * @param player The player to check
     * @param amount The amount to check for
     * @return true if player has sufficient funds
     */
    fun has(player: OfflinePlayer, amount: Double): Boolean {
        if (amount <= 0) return true

        return try {
            economy?.has(player, amount) ?: false
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to check balance for ${player.name}", e)
            false
        }
    }

    /**
     * Withdraw money from player's account.
     *
     * @param player The player to withdraw from
     * @param amount The amount to withdraw (must be positive)
     * @return true if withdrawal was successful
     */
    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        if (amount <= 0) {
            plugin.logger.warning("Attempted to withdraw non-positive amount: $amount")
            return false
        }

        val eco = economy ?: run {
            plugin.logger.warning("Cannot withdraw: Economy not available")
            return false
        }

        return try {
            val response = eco.withdrawPlayer(player, amount)
            when (response.type) {
                EconomyResponse.ResponseType.SUCCESS -> {
                    plugin.logger.fine("Withdrew ${format(amount)} from ${player.name}")
                    true
                }
                EconomyResponse.ResponseType.FAILURE -> {
                    plugin.logger.warning("Failed to withdraw from ${player.name}: ${response.errorMessage}")
                    false
                }
                EconomyResponse.ResponseType.NOT_IMPLEMENTED -> {
                    plugin.logger.severe("Economy provider does not support withdrawals!")
                    false
                }
                else -> false
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Exception during withdrawal for ${player.name}", e)
            false
        }
    }

    /**
     * Deposit money to player's account.
     *
     * @param player The player to deposit to
     * @param amount The amount to deposit (must be positive)
     * @return true if deposit was successful
     */
    fun deposit(player: OfflinePlayer, amount: Double): Boolean {
        if (amount <= 0) {
            plugin.logger.warning("Attempted to deposit non-positive amount: $amount")
            return false
        }

        val eco = economy ?: run {
            plugin.logger.warning("Cannot deposit: Economy not available")
            return false
        }

        return try {
            val response = eco.depositPlayer(player, amount)
            when (response.type) {
                EconomyResponse.ResponseType.SUCCESS -> {
                    plugin.logger.fine("Deposited ${format(amount)} to ${player.name}")
                    true
                }
                EconomyResponse.ResponseType.FAILURE -> {
                    plugin.logger.warning("Failed to deposit to ${player.name}: ${response.errorMessage}")
                    false
                }
                EconomyResponse.ResponseType.NOT_IMPLEMENTED -> {
                    plugin.logger.severe("Economy provider does not support deposits!")
                    false
                }
                else -> false
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Exception during deposit for ${player.name}", e)
            false
        }
    }

    /**
     * Format an amount as currency string.
     *
     * @param amount The amount to format
     * @return Formatted currency string (e.g., "$1,234.56")
     */
    fun format(amount: Double): String {
        return try {
            economy?.format(amount) ?: fallbackFormat(amount)
        } catch (e: Exception) {
            fallbackFormat(amount)
        }
    }

    /**
     * Fallback currency formatting when economy format fails.
     */
    private fun fallbackFormat(amount: Double): String {
        return "$${currencyFormat.format(amount)}"
    }

    /**
     * Get the currency name (singular).
     */
    fun getCurrencyName(): String {
        return try {
            economy?.currencyNameSingular() ?: "dollar"
        } catch (e: Exception) {
            "dollar"
        }
    }

    /**
     * Get the currency name (plural).
     */
    fun getCurrencyNamePlural(): String {
        return try {
            economy?.currencyNamePlural() ?: "dollars"
        } catch (e: Exception) {
            "dollars"
        }
    }

    /**
     * Transfer money from one player to another.
     *
     * @param from The player to withdraw from
     * @param to The player to deposit to
     * @param amount The amount to transfer
     * @return true if transfer was successful
     */
    fun transfer(from: OfflinePlayer, to: OfflinePlayer, amount: Double): Boolean {
        if (!has(from, amount)) {
            return false
        }

        if (!withdraw(from, amount)) {
            return false
        }

        if (!deposit(to, amount)) {
            // Rollback: refund the sender
            deposit(from, amount)
            plugin.logger.warning("Transfer failed, refunded ${from.name}")
            return false
        }

        return true
    }

    /**
     * Perform an atomic economy operation with automatic rollback on failure.
     *
     * @param player The player involved
     * @param amount The amount for the operation
     * @param operation The operation to perform (returns true on success)
     * @return true if the operation completed successfully
     */
    fun withTransaction(
        player: OfflinePlayer,
        amount: Double,
        operation: () -> Boolean
    ): Boolean {
        if (!withdraw(player, amount)) {
            return false
        }

        return try {
            if (operation()) {
                true
            } else {
                // Rollback on operation failure
                deposit(player, amount)
                false
            }
        } catch (e: Exception) {
            // Rollback on exception
            deposit(player, amount)
            plugin.logger.log(Level.WARNING, "Transaction failed, refunded ${player.name}", e)
            false
        }
    }

    // ==================== Chat/Prefix Methods ====================

    /**
     * Check if chat (prefix/suffix) support is available.
     */
    fun isChatEnabled(): Boolean = chat != null && isChatSetup

    /**
     * Get player's prefix from Vault.
     *
     * @param player The player
     * @return The player's prefix or empty string
     */
    fun getPlayerPrefix(player: Player): String {
        return try {
            chat?.getPlayerPrefix(player) ?: ""
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to get prefix for ${player.name}", e)
            ""
        }
    }

    /**
     * Get player's suffix from Vault.
     *
     * @param player The player
     * @return The player's suffix or empty string
     */
    fun getPlayerSuffix(player: Player): String {
        return try {
            chat?.getPlayerSuffix(player) ?: ""
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to get suffix for ${player.name}", e)
            ""
        }
    }

    /**
     * Set player's prefix in Vault.
     * Note: This requires a permission plugin that supports Vault Chat.
     *
     * @param player The player
     * @param prefix The prefix to set
     */
    fun setPlayerPrefix(player: Player, prefix: String) {
        val chatProvider = chat
        if (chatProvider == null) {
            plugin.logger.fine("Cannot set prefix: Vault Chat not available")
            return
        }

        try {
            chatProvider.setPlayerPrefix(player, prefix)
            plugin.logger.fine("Set prefix for ${player.name}: $prefix")
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to set prefix for ${player.name}", e)
        }
    }

    /**
     * Set player's suffix in Vault.
     *
     * @param player The player
     * @param suffix The suffix to set
     */
    fun setPlayerSuffix(player: Player, suffix: String) {
        val chatProvider = chat
        if (chatProvider == null) {
            plugin.logger.fine("Cannot set suffix: Vault Chat not available")
            return
        }

        try {
            chatProvider.setPlayerSuffix(player, suffix)
            plugin.logger.fine("Set suffix for ${player.name}: $suffix")
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to set suffix for ${player.name}", e)
        }
    }

    /**
     * Update player's displayname to include their rank prefix.
     * This is a fallback when Vault Chat is not available.
     *
     * @param player The player
     * @param prefix The rank prefix
     * @param useDisplayName Whether to modify the player's displayname
     */
    fun updatePlayerDisplay(player: Player, prefix: String, useDisplayName: Boolean = true) {
        if (useDisplayName) {
            // Update displayname to include prefix
            // Supports all color formats: &c, &#RRGGBB, &x&R&R&G&G&B&B
            val coloredPrefix = ColorUtil.colorize(prefix)
            val newDisplayName = if (prefix.isNotEmpty()) {
                "$coloredPrefix ${player.name}"
            } else {
                player.name
            }
            player.setDisplayName(newDisplayName)
            player.setPlayerListName(newDisplayName)
            plugin.logger.fine("Updated displayname for ${player.name}: $newDisplayName")
        }
    }
}
