package com.shinobu.rankup.economy

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.logging.Logger

/**
 * Result of an economy transaction.
 */
sealed class TransactionResult {
    data class Success(val amount: Double, val newBalance: Double) : TransactionResult()
    data class Failure(val reason: String) : TransactionResult()
}

/**
 * Interface for economy operations.
 * Allows for different economy providers (Vault, custom, etc.)
 */
interface EconomyProvider {
    /**
     * Check if the economy provider is available.
     */
    fun isAvailable(): Boolean

    /**
     * Get the name of the economy provider.
     */
    fun getName(): String

    /**
     * Get a player's balance.
     */
    fun getBalance(player: Player): Double

    /**
     * Check if a player has enough money.
     */
    fun has(player: Player, amount: Double): Boolean

    /**
     * Withdraw money from a player.
     */
    fun withdraw(player: Player, amount: Double): TransactionResult

    /**
     * Deposit money to a player.
     */
    fun deposit(player: Player, amount: Double): TransactionResult

    /**
     * Format a money amount according to the economy plugin's format.
     */
    fun format(amount: Double): String
}

/**
 * Vault economy provider implementation.
 */
class VaultEconomyProvider(private val plugin: Plugin) : EconomyProvider {

    private var economy: Economy? = null
    private val logger: Logger = plugin.logger

    init {
        setupEconomy()
    }

    private fun setupEconomy(): Boolean {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            logger.warning("Vault not found! Economy features will be disabled.")
            return false
        }

        val rsp = Bukkit.getServicesManager().getRegistration(Economy::class.java)
        if (rsp == null) {
            logger.warning("No economy plugin found! Make sure you have an economy plugin like EssentialsX or CMI installed.")
            return false
        }

        economy = rsp.provider
        logger.info("Successfully hooked into economy: ${economy?.name}")
        return true
    }

    override fun isAvailable(): Boolean = economy != null

    override fun getName(): String = economy?.name ?: "None"

    override fun getBalance(player: Player): Double {
        return economy?.getBalance(player) ?: 0.0
    }

    override fun has(player: Player, amount: Double): Boolean {
        return economy?.has(player, amount) ?: false
    }

    override fun withdraw(player: Player, amount: Double): TransactionResult {
        val eco = economy ?: return TransactionResult.Failure("Economy not available")

        if (amount <= 0) {
            return TransactionResult.Failure("Amount must be positive")
        }

        if (!eco.has(player, amount)) {
            return TransactionResult.Failure("Insufficient funds")
        }

        val response = eco.withdrawPlayer(player, amount)
        return if (response.transactionSuccess()) {
            TransactionResult.Success(amount, eco.getBalance(player))
        } else {
            TransactionResult.Failure(response.errorMessage ?: "Unknown error")
        }
    }

    override fun deposit(player: Player, amount: Double): TransactionResult {
        val eco = economy ?: return TransactionResult.Failure("Economy not available")

        if (amount <= 0) {
            return TransactionResult.Failure("Amount must be positive")
        }

        val response = eco.depositPlayer(player, amount)
        return if (response.transactionSuccess()) {
            TransactionResult.Success(amount, eco.getBalance(player))
        } else {
            TransactionResult.Failure(response.errorMessage ?: "Unknown error")
        }
    }

    override fun format(amount: Double): String {
        return economy?.format(amount) ?: "$%.2f".format(amount)
    }
}

/**
 * Factory for creating economy providers.
 */
object EconomyFactory {

    fun create(plugin: Plugin, type: String = "vault"): EconomyProvider {
        return when (type.lowercase()) {
            "vault" -> VaultEconomyProvider(plugin)
            else -> VaultEconomyProvider(plugin) // Default to Vault
        }
    }
}
