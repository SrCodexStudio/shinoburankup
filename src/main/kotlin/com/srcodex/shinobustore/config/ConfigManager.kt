package com.srcodex.shinobustore.config

import com.srcodex.shinobustore.ShinobuStore
import com.srcodex.shinobustore.transaction.StoreCategory
import com.srcodex.shinobustore.transaction.StoreItem
import com.srcodex.shinobustore.util.ColorUtil
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.logging.Level

/**
 * Manages all plugin configuration files.
 */
class ConfigManager(private val plugin: ShinobuStore) {

    private lateinit var configFile: File
    private lateinit var messagesFile: File

    lateinit var config: YamlConfiguration
        private set
    lateinit var messages: YamlConfiguration
        private set

    // Cached values
    private var _categories: Map<String, StoreCategory> = emptyMap()
    private var _items: Map<String, StoreItem> = emptyMap()
    private var _prefix: String = ""

    val categories: Map<String, StoreCategory> get() = _categories
    val items: Map<String, StoreItem> get() = _items
    val prefix: String get() = _prefix

    /**
     * Loads all configuration files.
     */
    fun load() {
        plugin.dataFolder.mkdirs()

        // Save default configs if they don't exist
        saveDefaultConfig("config.yml")
        saveDefaultConfig("messages.yml")

        // Load configuration files
        configFile = File(plugin.dataFolder, "config.yml")
        messagesFile = File(plugin.dataFolder, "messages.yml")

        config = YamlConfiguration.loadConfiguration(configFile)
        messages = YamlConfiguration.loadConfiguration(messagesFile)

        // Parse and cache values
        parseConfig()

        plugin.logger.info("Configuration loaded successfully!")
    }

    /**
     * Reloads all configuration files.
     */
    fun reload() {
        config = YamlConfiguration.loadConfiguration(configFile)
        messages = YamlConfiguration.loadConfiguration(messagesFile)
        parseConfig()
        plugin.logger.info("Configuration reloaded!")
    }

    /**
     * Saves default config file if it doesn't exist.
     */
    private fun saveDefaultConfig(filename: String) {
        val file = File(plugin.dataFolder, filename)
        if (!file.exists()) {
            plugin.saveResource(filename, false)
        }
    }

    /**
     * Parses configuration values.
     */
    private fun parseConfig() {
        _prefix = messages.getString("prefix", "&b&lShinobu&f&lStore &8» &r") ?: ""
        debugEnabled = config.getBoolean("debug.enabled", false)
        parseCategories()
        parseItems()
    }

    /**
     * Parses category configurations.
     */
    private fun parseCategories() {
        val categoriesSection = config.getConfigurationSection("categories") ?: return
        val categoryMap = mutableMapOf<String, StoreCategory>()

        for (key in categoriesSection.getKeys(false)) {
            try {
                val section = categoriesSection.getConfigurationSection(key) ?: continue
                val category = StoreCategory(
                    id = key,
                    position = section.getInt("position", 0),
                    material = section.getString("material", "CHEST") ?: "CHEST",
                    display = section.getString("display", key) ?: key,
                    rows = section.getInt("rows", 3).coerceIn(1, 6),
                    lore = section.getStringList("lore")
                )
                categoryMap[key] = category
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "Failed to load category: $key", e)
            }
        }

        _categories = categoryMap
    }

    /**
     * Parses item configurations.
     */
    private fun parseItems() {
        val itemsSection = config.getConfigurationSection("items") ?: return
        val itemMap = mutableMapOf<String, StoreItem>()

        for (key in itemsSection.getKeys(false)) {
            try {
                val section = itemsSection.getConfigurationSection(key) ?: continue
                val cost = section.getDouble("cost", 0.0)
                if (cost <= 0.0) {
                    plugin.logger.warning("Item '$key' has invalid cost: $cost (must be > 0). Skipping.")
                    continue
                }

                val material = section.getString("material", "STONE") ?: "STONE"
                if (!material.startsWith("head:", ignoreCase = true)) {
                    try {
                        org.bukkit.Material.valueOf(material.uppercase())
                    } catch (_: Exception) {
                        plugin.logger.warning("Item '$key' has invalid material: $material. Using STONE.")
                    }
                }

                val item = StoreItem(
                    id = key,
                    category = section.getString("category", "") ?: "",
                    position = section.getInt("position", 0),
                    material = material,
                    display = section.getString("display", key) ?: key,
                    cost = cost,
                    passFee = section.getBoolean("pass-fee", false),
                    lore = section.getStringList("lore"),
                    commands = section.getStringList("commands")
                )
                itemMap[key] = item
            } catch (e: Exception) {
                plugin.logger.log(Level.WARNING, "Failed to load item: $key", e)
            }
        }

        _items = itemMap
    }

    /**
     * Gets items for a specific category.
     */
    fun getItemsForCategory(categoryId: String): List<StoreItem> {
        return _items.values.filter { it.category.equals(categoryId, ignoreCase = true) }
    }

    /**
     * Gets a message from the messages config.
     */
    fun getMessage(path: String, placeholders: Map<String, String> = emptyMap()): String {
        val message = messages.getString(path, "&cMissing message: $path") ?: "&cMissing message: $path"
        val withPrefix = message.replace("{prefix}", _prefix)
        return ColorUtil.format(withPrefix, placeholders)
    }

    /**
     * Gets a message list from the messages config.
     */
    fun getMessageList(path: String, placeholders: Map<String, String> = emptyMap()): List<String> {
        return messages.getStringList(path).map { line ->
            val withPrefix = line.replace("{prefix}", _prefix)
            ColorUtil.format(withPrefix, placeholders)
        }
    }

    // PayPal Configuration Getters
    val paypalClientId: String get() = config.getString("paypal.client-id", "") ?: ""
    val paypalSecret: String get() = config.getString("paypal.secret", "") ?: ""
    val paypalEnvironment: String get() = config.getString("paypal.environment", "LIVE") ?: "LIVE"
    val paypalBrandName: String get() = config.getString("paypal.brand-name", "Server Store") ?: "Server Store"
    val paypalCurrency: String get() = config.getString("paypal.currency", "USD") ?: "USD"
    val currencySymbol: String get() = config.getString("paypal.currency-symbol", "$") ?: "$"
    val feePercentage: Double get() = config.getDouble("paypal.fee.percentage", 2.9)
    val feeFixed: Double get() = config.getDouble("paypal.fee.fixed", 0.30)

    // Transaction Configuration Getters
    val checkInterval: Long get() = config.getLong("transactions.check-interval", 300)
    val expireAfter: Long get() {
        val value = config.getLong("transactions.expire-after", 3600)
        // Migration: if value > 86400, it's likely in old millisecond format
        return if (value > 86400) {
            plugin.logger.warning("expire-after value ($value) appears to be in milliseconds. Please update config to seconds format.")
            value // Use as-is since it's already in ms
        } else {
            value * 1000 // Convert seconds to ms for internal use
        }
    }
    val maxPendingPerPlayer: Int get() = config.getInt("transactions.max-pending-per-player", 1)
    val storeHistory: Boolean get() = config.getBoolean("transactions.store-history", true)
    val historyRetentionDays: Int get() = config.getInt("transactions.history-retention-days", 90)

    // Rate Limit Configuration Getters
    val rateLimitEnabled: Boolean get() = config.getBoolean("rate-limit.enabled", true)
    val maxRequestsPerMinute: Int get() = config.getInt("rate-limit.max-requests-per-minute", 10)
    val storeOpenCooldown: Long get() = config.getLong("rate-limit.store-open-cooldown", 3) * 1000
    val purchaseCooldown: Long get() = config.getLong("rate-limit.purchase-cooldown", 5) * 1000
    val abuseBanDuration: Long get() = config.getLong("rate-limit.abuse-ban-duration", 15)
    val abuseThreshold: Int get() = config.getInt("rate-limit.abuse-threshold", 50)

    // Menu Configuration Getters
    val categoryMenuTitle: String get() = ColorUtil.colorize(config.getString("menu.category.title", "&b&lShinobu Store") ?: "&b&lShinobu Store")
    val categoryMenuRows: Int get() = config.getInt("menu.category.rows", 3).coerceIn(1, 6)
    val menuProtectionEnabled: Boolean get() = config.getBoolean("menu.protection.enabled", true)
    val fillerEnabled: Boolean get() = config.getBoolean("menu.filler.enabled", true)
    val fillerMaterial: String get() = config.getString("menu.filler.material", "GRAY_STAINED_GLASS_PANE") ?: "GRAY_STAINED_GLASS_PANE"
    val fillerName: String get() = config.getString("menu.filler.name", " ") ?: " "
    val confirmMenuEnabled: Boolean get() = config.getBoolean("menu.confirm-menu.enabled", true)
    val confirmMenuTitle: String get() = config.getString("menu.confirm-menu.title", "&8Confirm Purchase") ?: "&8Confirm Purchase"

    // Sound Configuration Getters
    val soundMenuOpen: String get() = config.getString("menu.sounds.menu-open", "BLOCK_CHEST_OPEN") ?: "BLOCK_CHEST_OPEN"
    val soundMenuClick: String get() = config.getString("menu.sounds.menu-click", "UI_BUTTON_CLICK") ?: "UI_BUTTON_CLICK"
    val soundPurchaseStart: String get() = config.getString("menu.sounds.purchase-start", "ENTITY_EXPERIENCE_ORB_PICKUP") ?: "ENTITY_EXPERIENCE_ORB_PICKUP"
    val soundPurchaseComplete: String get() = config.getString("menu.sounds.purchase-complete", "ENTITY_PLAYER_LEVELUP") ?: "ENTITY_PLAYER_LEVELUP"
    val soundError: String get() = config.getString("menu.sounds.error", "ENTITY_VILLAGER_NO") ?: "ENTITY_VILLAGER_NO"

    // Debug Configuration
    var debugEnabled: Boolean = false
        private set

    val logTransactions: Boolean get() = config.getBoolean("debug.log-transactions", true)
    val logPaypalRequests: Boolean get() = config.getBoolean("debug.log-paypal-requests", false)

    // Aliases for rate limiter
    val rateLimitMaxRequests: Int get() = maxRequestsPerMinute
    val rateLimitAbuseThreshold: Int get() = abuseThreshold
    val rateLimitBanDuration: Long get() = abuseBanDuration

    /**
     * Validates PayPal configuration.
     */
    fun isPayPalConfigured(): Boolean {
        return paypalClientId.isNotBlank() && paypalSecret.isNotBlank()
    }

    /**
     * Sets debug mode enabled/disabled.
     */
    fun setDebug(enabled: Boolean) {
        debugEnabled = enabled
        config.set("debug.enabled", enabled)
        try {
            config.save(configFile)
        } catch (_: Exception) {
            // Ignore save errors for runtime toggle
        }
    }
}
