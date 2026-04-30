package com.shinobu.rankup.hook

import com.shinobu.rankup.api.ShinobuRankupAPI
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * Manages all external plugin hooks for ShinobuRankup.
 *
 * Provides centralized initialization, status checking, and access to all hooks.
 * Thread-safe for concurrent access.
 */
class HookManager(
    private val plugin: JavaPlugin,
    private val apiProvider: () -> ShinobuRankupAPI?
) {

    // Individual hooks
    val vault: VaultHook = VaultHook(plugin)
    private lateinit var placeholderAPI: PlaceholderAPIHook

    // Hook status tracking
    private val hookStatus = ConcurrentHashMap<String, HookStatus>()

    /**
     * Represents the status of an external hook.
     */
    data class HookStatus(
        val name: String,
        val available: Boolean,
        val enabled: Boolean,
        val version: String? = null,
        val message: String? = null
    )

    /**
     * Initialize all hooks.
     * Should be called during plugin enable, preferably after a short delay
     * to ensure all plugins are loaded.
     */
    fun initialize() {
        plugin.logger.info("Initializing external plugin hooks...")

        // Initialize Vault (required)
        initializeVault()

        // Initialize PlaceholderAPI (optional)
        initializePlaceholderAPI()

        // Log summary
        logHookSummary()
    }

    /**
     * Initialize Vault hook (Economy + Chat).
     */
    private fun initializeVault() {
        try {
            val vaultPlugin = plugin.server.pluginManager.getPlugin("Vault")
            val isAvailable = vaultPlugin != null
            val isEconomyEnabled = if (isAvailable) vault.setup() else false

            hookStatus["Vault"] = HookStatus(
                name = "Vault",
                available = isAvailable,
                enabled = isEconomyEnabled,
                version = vaultPlugin?.description?.version,
                message = when {
                    !isAvailable -> "Plugin not found"
                    !isEconomyEnabled -> "No economy provider found"
                    else -> "Using ${vault.getEconomyName()}"
                }
            )

            // Initialize Vault Chat for prefix/suffix support
            initializeVaultChat(isAvailable, vaultPlugin?.description?.version)

        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to initialize Vault hook", e)
            hookStatus["Vault"] = HookStatus(
                name = "Vault",
                available = false,
                enabled = false,
                message = "Error: ${e.message}"
            )
        }
    }

    /**
     * Initialize Vault Chat hook for prefix/suffix support.
     */
    private fun initializeVaultChat(vaultAvailable: Boolean, vaultVersion: String?) {
        try {
            val isChatEnabled = if (vaultAvailable) vault.setupChat() else false

            hookStatus["VaultChat"] = HookStatus(
                name = "VaultChat",
                available = vaultAvailable,
                enabled = isChatEnabled,
                version = vaultVersion,
                message = when {
                    !vaultAvailable -> "Vault not found"
                    !isChatEnabled -> "No chat provider (prefix via displayname)"
                    else -> "Prefix support enabled"
                }
            )
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to initialize Vault Chat hook", e)
            hookStatus["VaultChat"] = HookStatus(
                name = "VaultChat",
                available = false,
                enabled = false,
                message = "Error: ${e.message}"
            )
        }
    }

    /**
     * Initialize PlaceholderAPI hook.
     */
    private fun initializePlaceholderAPI() {
        try {
            val papiPlugin = plugin.server.pluginManager.getPlugin("PlaceholderAPI")
            val isAvailable = papiPlugin != null

            placeholderAPI = PlaceholderAPIHook(plugin, apiProvider, vault)
            val isEnabled = if (isAvailable) placeholderAPI.setup() else false

            hookStatus["PlaceholderAPI"] = HookStatus(
                name = "PlaceholderAPI",
                available = isAvailable,
                enabled = isEnabled,
                version = papiPlugin?.description?.version,
                message = when {
                    !isAvailable -> "Plugin not found (optional)"
                    !isEnabled -> "Failed to register expansion"
                    else -> "Expansion registered"
                }
            )
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to initialize PlaceholderAPI hook", e)
            hookStatus["PlaceholderAPI"] = HookStatus(
                name = "PlaceholderAPI",
                available = false,
                enabled = false,
                message = "Error: ${e.message}"
            )
        }
    }

    /**
     * Log a summary of all hook statuses.
     */
    private fun logHookSummary() {
        plugin.logger.info("=== Hook Status Summary ===")

        hookStatus.values.forEach { status ->
            val statusIcon = when {
                status.enabled -> "[OK]"
                status.available -> "[WARN]"
                else -> "[--]"
            }

            val versionStr = status.version?.let { " v$it" } ?: ""
            val messageStr = status.message?.let { " - $it" } ?: ""

            plugin.logger.info("$statusIcon ${status.name}$versionStr$messageStr")
        }

        plugin.logger.info("===========================")
    }

    /**
     * Shutdown all hooks.
     * Should be called during plugin disable.
     */
    fun shutdown() {
        plugin.logger.info("Shutting down external plugin hooks...")

        try {
            if (::placeholderAPI.isInitialized) {
                placeholderAPI.unregister()
            }
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Error during PlaceholderAPI shutdown", e)
        }

        hookStatus.clear()
    }

    // ==================== Status Checking ====================

    /**
     * Check if Vault economy is available.
     */
    fun isVaultEnabled(): Boolean = vault.isEnabled()

    /**
     * Check if Vault Chat (prefix/suffix) is available.
     */
    fun isVaultChatEnabled(): Boolean = vault.isChatEnabled()

    /**
     * Check if PlaceholderAPI is available.
     */
    fun isPlaceholderAPIEnabled(): Boolean {
        return if (::placeholderAPI.isInitialized) {
            placeholderAPI.isEnabled()
        } else {
            false
        }
    }

    /**
     * Get the status of a specific hook.
     */
    fun getHookStatus(hookName: String): HookStatus? = hookStatus[hookName]

    /**
     * Get all hook statuses.
     */
    fun getAllHookStatuses(): Map<String, HookStatus> = hookStatus.toMap()

    /**
     * Get a list of all enabled hooks.
     */
    fun getEnabledHooks(): List<String> {
        return hookStatus.values
            .filter { it.enabled }
            .map { it.name }
    }

    /**
     * Get a list of all available (but not necessarily enabled) hooks.
     */
    fun getAvailableHooks(): List<String> {
        return hookStatus.values
            .filter { it.available }
            .map { it.name }
    }

    // ==================== Hook Access ====================

    /**
     * Get the PlaceholderAPI hook.
     */
    fun getPlaceholderAPI(): PlaceholderAPIHook? {
        return if (::placeholderAPI.isInitialized && placeholderAPI.isEnabled()) {
            placeholderAPI
        } else {
            null
        }
    }

    /**
     * Execute an action only if Vault is enabled.
     *
     * @param action The action to execute with VaultHook
     * @return The result of the action, or null if Vault is not enabled
     */
    inline fun <T> withVault(action: (VaultHook) -> T): T? {
        return if (isVaultEnabled()) {
            action(vault)
        } else {
            null
        }
    }

    /**
     * Execute an action only if PlaceholderAPI is enabled.
     *
     * @param action The action to execute with PlaceholderAPIHook
     * @return The result of the action, or null if PlaceholderAPI is not enabled
     */
    fun <T> withPlaceholderAPI(action: (PlaceholderAPIHook) -> T): T? {
        return if (isPlaceholderAPIEnabled()) {
            action(placeholderAPI)
        } else {
            null
        }
    }

    // ==================== Utility Functions ====================

    /**
     * Format a money amount using Vault if available, otherwise use fallback.
     */
    fun formatMoney(amount: Double): String {
        return if (isVaultEnabled()) {
            vault.format(amount)
        } else {
            String.format("$%.2f", amount)
        }
    }

    /**
     * Check if economy features are available.
     */
    fun hasEconomy(): Boolean = isVaultEnabled()

    /**
     * Check if chat/prefix features are available.
     */
    fun hasChat(): Boolean = isVaultChatEnabled()

    /**
     * Reload all hooks (useful after config changes).
     */
    fun reload() {
        shutdown()
        initialize()
    }
}
