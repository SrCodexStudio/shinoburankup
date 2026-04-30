package com.srcodex.shinobustore.api

import com.srcodex.shinobustore.ShinobuStore
import com.srcodex.shinobustore.menu.CategoryMenu
import com.srcodex.shinobustore.menu.StoreMenu
import com.srcodex.shinobustore.transaction.CompletedTransaction
import com.srcodex.shinobustore.transaction.PendingTransaction
import com.srcodex.shinobustore.transaction.StoreCategory
import com.srcodex.shinobustore.transaction.StoreItem
import com.srcodex.shinobustore.transaction.TransactionStatistics
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Public API for ShinobuStore.
 *
 * External plugins can obtain an instance via Bukkit's ServicesManager:
 * ```kotlin
 * val registration = Bukkit.getServicesManager()
 *     .getRegistration(ShinobuStoreAPI::class.java)
 * val api: ShinobuStoreAPI? = registration?.provider
 *
 * // Example: check if a player has pending transactions
 * api?.hasPendingTransaction(player.uniqueId)
 *
 * // Example: open the store for a player
 * api?.openStore(player)
 * ```
 *
 * All methods are safe to call from the main server thread. Methods that return
 * collections always return immutable snapshots.
 */
interface ShinobuStoreAPI {

    /**
     * Gets all configured store categories.
     * @return immutable map of category ID to [StoreCategory]
     */
    fun getCategories(): Map<String, StoreCategory>

    /**
     * Gets all configured store items across all categories.
     * @return immutable map of item ID to [StoreItem]
     */
    fun getItems(): Map<String, StoreItem>

    /**
     * Gets all store items belonging to a specific category.
     * @param categoryId the category identifier
     * @return list of items in the category, empty if category does not exist
     */
    fun getItemsForCategory(categoryId: String): List<StoreItem>

    /**
     * Checks whether a player has any active (non-expired) pending transactions.
     * @param playerUUID the player's UUID
     * @return true if the player has at least one active pending transaction
     */
    fun hasPendingTransaction(playerUUID: UUID): Boolean

    /**
     * Gets the count of active (non-expired) pending transactions for a player.
     * @param playerUUID the player's UUID
     * @return number of active pending transactions
     */
    fun getPendingCount(playerUUID: UUID): Int

    /**
     * Gets all pending transactions for a player, including expired ones.
     * @param playerUUID the player's UUID
     * @return list of pending transactions, sorted by creation time descending
     */
    fun getPendingTransactions(playerUUID: UUID): List<PendingTransaction>

    /**
     * Gets the completed transaction history for a player.
     * @param playerUUID the player's UUID
     * @return list of completed transactions, sorted by completion time descending
     */
    fun getTransactionHistory(playerUUID: UUID): List<CompletedTransaction>

    /**
     * Gets aggregate transaction statistics for the store.
     * @return current [TransactionStatistics] snapshot
     */
    fun getStatistics(): TransactionStatistics

    /**
     * Opens the main store category menu for a player.
     * Must be called from the main server thread.
     * @param player the player to open the store for
     */
    fun openStore(player: Player)

    /**
     * Opens a specific category's item menu for a player.
     * Must be called from the main server thread.
     * Does nothing if the category ID does not exist.
     * @param player the player to open the menu for
     * @param categoryId the category identifier to display
     */
    fun openCategory(player: Player, categoryId: String)

    /**
     * Checks whether PayPal credentials are configured.
     * @return true if both client ID and secret are set and non-blank
     */
    fun isStoreConfigured(): Boolean
}

/**
 * Default implementation of [ShinobuStoreAPI].
 *
 * Registered as a Bukkit service during plugin enable. Delegates all operations
 * to the plugin's internal managers, ensuring a stable public contract even if
 * internal implementations change.
 */
class ShinobuStoreAPIImpl(private val plugin: ShinobuStore) : ShinobuStoreAPI {

    override fun getCategories(): Map<String, StoreCategory> =
        plugin.configManager.categories

    override fun getItems(): Map<String, StoreItem> =
        plugin.configManager.items

    override fun getItemsForCategory(categoryId: String): List<StoreItem> =
        plugin.configManager.getItemsForCategory(categoryId)

    override fun hasPendingTransaction(playerUUID: UUID): Boolean =
        plugin.transactionManager.hasPending(playerUUID)

    override fun getPendingCount(playerUUID: UUID): Int =
        plugin.transactionManager.getPendingCount(playerUUID)

    override fun getPendingTransactions(playerUUID: UUID): List<PendingTransaction> =
        plugin.transactionManager.getPendingForPlayer(playerUUID)

    override fun getTransactionHistory(playerUUID: UUID): List<CompletedTransaction> =
        plugin.transactionManager.getHistoryForPlayer(playerUUID)

    override fun getStatistics(): TransactionStatistics =
        plugin.transactionManager.getStatistics()

    override fun openStore(player: Player) {
        CategoryMenu(plugin, player).open()
    }

    override fun openCategory(player: Player, categoryId: String) {
        val category = plugin.configManager.categories[categoryId] ?: return
        StoreMenu(plugin, player, categoryId, category.display, category.rows).open()
    }

    override fun isStoreConfigured(): Boolean =
        plugin.configManager.isPayPalConfigured()
}
