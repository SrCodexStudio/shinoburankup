package com.srcodex.shinobustore.transaction

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.srcodex.shinobustore.ShinobuStore
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * Manages pending and completed transactions with persistence.
 * Uses concurrent data structures for thread safety and Mutex-guarded
 * atomic writes to prevent file corruption under concurrent saves.
 */
class TransactionManager(private val plugin: ShinobuStore) {

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    private val pendingFile: File = File(plugin.dataFolder, "data/pending.json")
    private val historyFile: File = File(plugin.dataFolder, "data/history.json")

    // Thread-safe transaction storage
    private val pendingTransactions = ConcurrentHashMap<String, PendingTransaction>()
    private val transactionHistory = ConcurrentHashMap<String, CompletedTransaction>()

    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Mutexes to serialize file writes and prevent partial/corrupt saves
    private val pendingSaveMutex = Mutex()
    private val historySaveMutex = Mutex()

    /**
     * Loads transactions from storage.
     */
    fun load() {
        plugin.dataFolder.resolve("data").mkdirs()

        loadPending()
        loadHistory()

        // Clean up expired pending transactions
        cleanupExpired()

        plugin.logger.info("Loaded ${pendingTransactions.size} pending transactions")
        plugin.logger.info("Loaded ${transactionHistory.size} historical transactions")
    }

    /**
     * Saves all transactions to storage synchronously.
     * Used during plugin disable / shutdown for guaranteed persistence.
     */
    fun save() {
        savePendingSync()
        saveHistorySync()
    }

    /**
     * Shuts down the manager and saves data.
     */
    fun shutdown() {
        scope.cancel()
        save()
    }

    // =====================================================================
    //                      PENDING TRANSACTIONS
    // =====================================================================

    /**
     * Adds a new pending transaction.
     */
    fun addPending(transaction: PendingTransaction) {
        pendingTransactions[transaction.orderId] = transaction

        if (plugin.configManager.logTransactions) {
            plugin.logger.info("New pending transaction: ${transaction.orderId} for ${transaction.playerName}")
        }

        // Save asynchronously with atomic write
        scope.launch { savePendingSafe() }
    }

    /**
     * Gets a pending transaction by order ID.
     */
    fun getPending(orderId: String): PendingTransaction? {
        return pendingTransactions[orderId]
    }

    /**
     * Gets all pending transactions for a player.
     */
    fun getPendingForPlayer(playerUUID: UUID): List<PendingTransaction> {
        return pendingTransactions.values.filter { it.playerUUID == playerUUID }
    }

    /**
     * Checks if a player has any pending transactions.
     */
    fun hasPending(playerUUID: UUID): Boolean {
        return pendingTransactions.values.any { it.playerUUID == playerUUID && !it.isExpired() }
    }

    /**
     * Gets the count of pending transactions for a player.
     */
    fun getPendingCount(playerUUID: UUID): Int {
        return pendingTransactions.values.count { it.playerUUID == playerUUID && !it.isExpired() }
    }

    /**
     * Removes a pending transaction.
     */
    fun removePending(orderId: String): PendingTransaction? {
        val removed = pendingTransactions.remove(orderId)
        if (removed != null) {
            scope.launch { savePendingSafe() }
        }
        return removed
    }

    /**
     * Removes all pending transactions for a player.
     */
    fun removePendingForPlayer(playerUUID: UUID): List<PendingTransaction> {
        val removed = pendingTransactions.values
            .filter { it.playerUUID == playerUUID }
            .mapNotNull { pendingTransactions.remove(it.orderId) }

        if (removed.isNotEmpty()) {
            scope.launch { savePendingSafe() }
        }

        return removed
    }

    /**
     * Gets all pending transactions (non-expired only).
     */
    fun getAllPending(): Collection<PendingTransaction> {
        return pendingTransactions.values.filter { !it.isExpired() }
    }

    /**
     * Cleans up expired pending transactions.
     */
    fun cleanupExpired(): Int {
        val expired = pendingTransactions.values.filter { it.isExpired() }
        expired.forEach { pendingTransactions.remove(it.orderId) }

        if (expired.isNotEmpty()) {
            plugin.logger.info("Cleaned up ${expired.size} expired transactions")
            scope.launch { savePendingSafe() }
        }

        return expired.size
    }

    // =====================================================================
    //                      TRANSACTION HISTORY
    // =====================================================================

    /**
     * Completes a pending transaction and moves it to history.
     */
    fun completePending(
        orderId: String,
        captureId: String? = null,
        commandsExecuted: List<String> = emptyList()
    ): CompletedTransaction? {
        val pending = removePending(orderId) ?: return null

        val completed = CompletedTransaction(
            orderId = pending.orderId,
            captureId = captureId,
            playerUuid = pending.playerUuid,
            playerName = pending.playerName,
            itemId = pending.itemId,
            itemDisplay = pending.itemDisplay,
            cost = pending.cost,
            fee = pending.fee,
            total = pending.total,
            createdAt = pending.createdAt,
            completedAt = System.currentTimeMillis(),
            commandsExecuted = commandsExecuted
        )

        if (plugin.configManager.storeHistory) {
            addHistory(completed)
        }

        if (plugin.configManager.logTransactions) {
            plugin.logger.info("Transaction completed: $orderId for ${pending.playerName}")
        }

        return completed
    }

    /**
     * Adds a completed transaction to history.
     */
    fun addHistory(transaction: CompletedTransaction) {
        transactionHistory[transaction.orderId] = transaction
        scope.launch { saveHistorySafe() }
    }

    /**
     * Gets a historical transaction by order ID.
     */
    fun getHistory(orderId: String): CompletedTransaction? {
        return transactionHistory[orderId]
    }

    /**
     * Gets all historical transactions for a player.
     */
    fun getHistoryForPlayer(playerUUID: UUID): List<CompletedTransaction> {
        return transactionHistory.values
            .filter { it.playerUUID == playerUUID }
            .sortedByDescending { it.completedAt }
    }

    /**
     * Gets all historical transactions.
     */
    fun getAllHistory(): Collection<CompletedTransaction> {
        return transactionHistory.values.sortedByDescending { it.completedAt }
    }

    /**
     * Cleans up old history based on retention days.
     */
    fun cleanupHistory(): Int {
        val retentionDays = plugin.configManager.historyRetentionDays
        if (retentionDays <= 0) return 0

        val cutoff = System.currentTimeMillis() - (retentionDays * 24 * 60 * 60 * 1000L)
        val old = transactionHistory.values.filter { it.completedAt < cutoff }
        old.forEach { transactionHistory.remove(it.orderId) }

        if (old.isNotEmpty()) {
            plugin.logger.info("Cleaned up ${old.size} old history entries")
            scope.launch { saveHistorySafe() }
        }

        return old.size
    }

    // =====================================================================
    //                      PERSISTENCE
    // =====================================================================

    private fun loadPending() {
        try {
            if (!pendingFile.exists()) return

            val json = pendingFile.readText()
            if (json.isBlank()) return

            val type = object : TypeToken<Map<String, PendingTransaction>>() {}.type
            val loaded: Map<String, PendingTransaction> = gson.fromJson(json, type) ?: emptyMap()

            pendingTransactions.clear()
            pendingTransactions.putAll(loaded)
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to load pending transactions", e)
        }
    }

    /**
     * Atomic async save for pending transactions.
     * Writes to a temporary file first, then renames to avoid partial writes.
     * Guarded by [pendingSaveMutex] to serialize concurrent save requests.
     */
    private suspend fun savePendingSafe() {
        pendingSaveMutex.withLock {
            try {
                pendingFile.parentFile?.mkdirs()
                val json = gson.toJson(pendingTransactions.toMap())
                val tmpFile = File(pendingFile.parentFile, "pending.json.tmp")
                tmpFile.writeText(json)
                if (!tmpFile.renameTo(pendingFile)) {
                    // Fallback: direct write if rename fails (Windows limitation)
                    pendingFile.writeText(json)
                    tmpFile.delete()
                }
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "Failed to save pending transactions", e)
            }
        }
    }

    /**
     * Synchronous save for pending transactions.
     * Used during shutdown when the coroutine scope is already cancelled.
     */
    private fun savePendingSync() {
        try {
            pendingFile.parentFile?.mkdirs()
            pendingFile.writeText(gson.toJson(pendingTransactions.toMap()))
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to save pending transactions", e)
        }
    }

    private fun loadHistory() {
        try {
            if (!historyFile.exists()) return

            val json = historyFile.readText()
            if (json.isBlank()) return

            val type = object : TypeToken<Map<String, CompletedTransaction>>() {}.type
            val loaded: Map<String, CompletedTransaction> = gson.fromJson(json, type) ?: emptyMap()

            transactionHistory.clear()
            transactionHistory.putAll(loaded)
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to load transaction history", e)
        }
    }

    /**
     * Atomic async save for transaction history.
     * Writes to a temporary file first, then renames to avoid partial writes.
     * Guarded by [historySaveMutex] to serialize concurrent save requests.
     */
    private suspend fun saveHistorySafe() {
        historySaveMutex.withLock {
            try {
                historyFile.parentFile?.mkdirs()
                val json = gson.toJson(transactionHistory.toMap())
                val tmpFile = File(historyFile.parentFile, "history.json.tmp")
                tmpFile.writeText(json)
                if (!tmpFile.renameTo(historyFile)) {
                    // Fallback: direct write if rename fails (Windows limitation)
                    historyFile.writeText(json)
                    tmpFile.delete()
                }
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "Failed to save transaction history", e)
            }
        }
    }

    /**
     * Synchronous save for transaction history.
     * Used during shutdown when the coroutine scope is already cancelled.
     */
    private fun saveHistorySync() {
        try {
            historyFile.parentFile?.mkdirs()
            historyFile.writeText(gson.toJson(transactionHistory.toMap()))
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to save transaction history", e)
        }
    }

    // =====================================================================
    //                      LOOKUP
    // =====================================================================

    /**
     * Looks up a transaction by order ID (checks both pending and history).
     * Returns a [TransactionLookupResult] sealed type instead of [Any?].
     */
    fun lookup(orderId: String): TransactionLookupResult {
        val pending = getPending(orderId)
        if (pending != null) return TransactionLookupResult.Pending(pending)

        val completed = getHistory(orderId)
        if (completed != null) return TransactionLookupResult.Completed(completed)

        return TransactionLookupResult.NotFound
    }

    /**
     * Looks up transactions by player UUID (checks both pending and history).
     */
    fun lookupByPlayer(playerUUID: UUID): Pair<List<PendingTransaction>, List<CompletedTransaction>> {
        return Pair(
            getPendingForPlayer(playerUUID),
            getHistoryForPlayer(playerUUID)
        )
    }

    /**
     * Gets recent completed transactions.
     */
    fun getRecentCompleted(count: Int): List<CompletedTransaction> {
        return transactionHistory.values
            .sortedByDescending { it.completedAt }
            .take(count)
    }

    /**
     * Gets transaction statistics.
     */
    fun getStatistics(): TransactionStatistics {
        val allCompleted = transactionHistory.values.toList()
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayTransactions = allCompleted.filter { it.completedAt >= todayStart }

        return TransactionStatistics(
            totalCompleted = allCompleted.size,
            totalRevenue = allCompleted.sumOf { it.total },
            currentPending = pendingTransactions.values.count { !it.isExpired() },
            todaySales = todayTransactions.size,
            todayRevenue = todayTransactions.sumOf { it.total }
        )
    }

    /**
     * Clears all pending transactions.
     * Returns the count of cleared transactions.
     */
    fun clearAllPending(): Int {
        val count = pendingTransactions.size
        pendingTransactions.clear()
        scope.launch { savePendingSafe() }
        return count
    }
}

/**
 * Type-safe result for transaction lookups by order ID.
 * Replaces the previous [Any?] return type.
 */
sealed class TransactionLookupResult {
    data class Pending(val transaction: PendingTransaction) : TransactionLookupResult()
    data class Completed(val transaction: CompletedTransaction) : TransactionLookupResult()
    data object NotFound : TransactionLookupResult()
}
