package com.shinobu.rankup

import com.shinobu.rankup.api.ShinobuRankupAPI
import com.shinobu.rankup.api.ShinobuRankupAPIImpl
import com.shinobu.rankup.api.ShinobuRankupProvider
import com.shinobu.rankup.security.IntegrityChecker
import com.shinobu.rankup.cache.HybridPlayerCache
import com.shinobu.rankup.cache.LeaderboardCache
import com.shinobu.rankup.cache.PlayerCache
import com.shinobu.rankup.cache.RankCache
import com.shinobu.rankup.cache.RedisCache
import com.shinobu.rankup.command.CommandManager
import com.shinobu.rankup.config.GuiConfigManager
import com.shinobu.rankup.config.FormatManager
import com.shinobu.rankup.config.LanguageManager
import com.shinobu.rankup.data.RankData
import com.shinobu.rankup.economy.EconomyFactory
import com.shinobu.rankup.economy.EconomyProvider
import com.shinobu.rankup.gui.GuiManager
import com.shinobu.rankup.gui.listener.GuiListener
import com.shinobu.rankup.hook.HookManager
import com.shinobu.rankup.listener.ChatListener
import com.shinobu.rankup.listener.PlayerListener
import com.shinobu.rankup.placeholder.PlaceholderProcessor
import com.shinobu.rankup.data.Database
import com.shinobu.rankup.database.DatabasePlayerService
import com.shinobu.rankup.service.CommandQueueService
import com.shinobu.rankup.service.PermissionService
import com.shinobu.rankup.service.PlayerService
import com.shinobu.rankup.service.RankupService
import com.shinobu.rankup.service.RequirementChecker
import com.shinobu.rankup.service.RewardService
import com.shinobu.rankup.task.AutoSaveTask
import com.shinobu.rankup.task.LeaderboardUpdateTask
import com.shinobu.rankup.util.PluginCoroutineScope
import com.shinobu.rankup.util.RateLimiter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader
import java.time.Duration
import java.util.logging.Level

/**
 * ShinobuRankup - A modern, high-performance rankup plugin for Paper/Spigot.
 *
 * Features:
 * - Vault economy integration
 * - PlaceholderAPI support
 * - Async database operations
 * - Comprehensive caching
 * - Clean API for developers
 */
class ShinobuRankup : JavaPlugin() {

    // Coroutine scope for plugin-wide async operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Core components
    lateinit var hookManager: HookManager
        private set

    lateinit var playerCache: PlayerCache
        private set

    lateinit var hybridCache: HybridPlayerCache
        private set

    private var redisCache: RedisCache? = null

    lateinit var rankCache: RankCache
        private set

    lateinit var leaderboardCache: LeaderboardCache
        private set

    lateinit var playerService: PlayerService
        private set

    lateinit var autoSaveTask: AutoSaveTask
        private set

    lateinit var leaderboardTask: LeaderboardUpdateTask
        private set

    lateinit var playerListener: PlayerListener
        private set

    lateinit var chatListener: ChatListener
        private set

    lateinit var guiManager: GuiManager
        private set

    lateinit var economyProvider: EconomyProvider
        private set

    lateinit var rankupService: RankupService
        private set

    lateinit var rewardService: RewardService
        private set

    lateinit var commandQueueService: CommandQueueService
        private set

    private lateinit var requirementChecker: RequirementChecker

    private lateinit var permissionService: PermissionService

    lateinit var formatManager: FormatManager
        private set

    lateinit var languageManager: LanguageManager
        private set

    lateinit var guiConfigManager: GuiConfigManager
        private set

    lateinit var placeholderProcessor: PlaceholderProcessor
        private set

    lateinit var coroutineScope: PluginCoroutineScope
        private set

    lateinit var database: Database
        private set

    private lateinit var commandManager: CommandManager

    private lateinit var apiImpl: ShinobuRankupAPIImpl

    // Default rank ID loaded from ranks.yml
    private var defaultRankId: String = "wanderer"

    // Ranks configuration file
    private lateinit var ranksConfig: YamlConfiguration

    override fun onEnable() {
        val startTime = System.currentTimeMillis()

        try {
            // ======================================
            // SECURITY: Anti-Tamper Integrity Check
            // ======================================
            val integrityResult = IntegrityChecker.verify(this)
            IntegrityChecker.logResult(this, integrityResult)

            if (!integrityResult.valid && !integrityResult.isDevelopment) {
                logger.severe("=" .repeat(60))
                logger.severe("SECURITY ALERT: Plugin integrity check failed!")
                logger.severe("The JAR file may have been modified or tampered with.")
                logger.severe("Please download the original plugin from the official source.")
                logger.severe("=" .repeat(60))
                server.pluginManager.disablePlugin(this)
                return
            }

            // Save default config files
            saveDefaultConfig()
            saveDefaultRanksConfig()

            // Initialize caches
            initializeCaches()

            // Initialize database (must be before services)
            initializeDatabase()

            // Load ranks from ranks.yml (must be before services for default rank)
            loadRanks()

            // Security: verify rank limit enforcement in FREE version
            // Defense-in-depth check after all loading is complete
            if (BuildConfig.isFreeVersion() && rankCache.size() > BuildConfig.FREE_MAX_RANKS) {
                logger.severe("FREE version: Rank limit exceeded (${rankCache.size()} > ${BuildConfig.FREE_MAX_RANKS})! Enforcing limit.")
                val limited = rankCache.getAllSorted().take(BuildConfig.FREE_MAX_RANKS)
                rankCache.clear()
                rankCache.loadAll(limited)
            }

            // Initialize services (uses database and default rank)
            initializeServices()

            // Initialize hooks (Vault, PlaceholderAPI)
            initializeHooks()

            // Initialize GUI manager (anti-dupe protected)
            initializeGuiManager()

            // Initialize listeners
            initializeListeners()

            // Initialize tasks
            initializeTasks()

            // Periodic cleanup task (every 5 minutes) - prevents memory leaks
            // from expired cache entries and stale rate limiter data
            server.scheduler.runTaskTimer(this, Runnable {
                try {
                    playerCache.cleanupExpired()
                    RateLimiter.COMMAND_LIMITER.cleanup()
                    RateLimiter.ECONOMY_LIMITER.cleanup()
                } catch (_: Exception) {}
            }, 6000L, 6000L) // 5 min delay, 5 min interval

            // Initialize API
            initializeAPI()

            // Now initialize hooks delayed (after API is ready)
            initializeHooksDelayed()

            // Register commands
            registerCommands()

            val loadTime = System.currentTimeMillis() - startTime
            logger.info("ShinobuRankup v${description.version} enabled in ${loadTime}ms")

        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Failed to enable ShinobuRankup", e)
            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        try {
            // Stop tasks
            if (::autoSaveTask.isInitialized) {
                autoSaveTask.stop(finalSave = true)
            }
            if (::leaderboardTask.isInitialized) {
                leaderboardTask.stop()
            }

            // Clear caches to release memory
            try { playerCache.clear() } catch (_: Exception) {}
            try { leaderboardCache.invalidate() } catch (_: Exception) {}

            // Cleanup listeners
            if (::playerListener.isInitialized) {
                playerListener.cleanup()
            }

            // Unregister commands
            if (::commandManager.isInitialized) {
                commandManager.unregisterCommands()
            }

            // Shutdown GUI manager
            GuiManager.shutdown()

            // Cleanup rankup service
            if (::rankupService.isInitialized) {
                rankupService.cleanup()
            }

            // Clear rate limiters to release memory
            try {
                RateLimiter.COMMAND_LIMITER.clear()
                RateLimiter.ECONOMY_LIMITER.clear()
            } catch (_: Exception) {}

            // Shutdown command queue service (cancel pending commands)
            if (::commandQueueService.isInitialized) {
                commandQueueService.shutdown()
            }

            // Shutdown coroutine scope
            if (::coroutineScope.isInitialized) {
                coroutineScope.shutdown()
            }

            // Shutdown API
            if (::apiImpl.isInitialized) {
                apiImpl.shutdown()
            }
            ShinobuRankupProvider.unregister()

            // Shutdown hooks
            if (::hookManager.isInitialized) {
                hookManager.shutdown()
            }

            // Shutdown hybrid cache (closes Redis connection if enabled)
            if (::hybridCache.isInitialized) {
                hybridCache.shutdown()
            }

            // Shutdown database connection pool
            if (::database.isInitialized) {
                runBlocking {
                    database.shutdown()
                }
            }

            // Cancel legacy coroutine scope
            scope.cancel()

            logger.info("ShinobuRankup disabled")

        } catch (e: Exception) {
            logger.log(Level.WARNING, "Error during plugin disable", e)
        }
    }

    private fun initializeCaches() {
        val cacheSize = config.getInt("cache.player-size", 500)
        val cacheTtl = config.getLong("cache.player-ttl-minutes", 10)
        val leaderboardSize = config.getInt("cache.leaderboard-size", 100)
        val leaderboardTtl = config.getLong("cache.leaderboard-ttl-minutes", 5)

        // Initialize local player cache
        playerCache = PlayerCache(
            maxSize = cacheSize,
            ttl = Duration.ofMinutes(cacheTtl)
        )

        // Initialize Redis cache if enabled
        val redisSection = config.getConfigurationSection("database.redis")
        val redisEnabled = redisSection?.getBoolean("enabled", false) ?: false

        if (redisEnabled) {
            logger.info("Redis cache is enabled, initializing...")

            val redisConfig = RedisCache.RedisConfig(
                host = redisSection?.getString("host", "localhost") ?: "localhost",
                port = redisSection?.getInt("port", 6379) ?: 6379,
                password = redisSection?.getString("password")?.takeIf { it.isNotBlank() },
                database = redisSection?.getInt("database", 0) ?: 0,
                timeout = redisSection?.getInt("timeout", 3000) ?: 3000,
                ssl = redisSection?.getBoolean("ssl", false) ?: false,
                keyPrefix = redisSection?.getString("key-prefix", "shinobu:rankup:") ?: "shinobu:rankup:",
                ttlSeconds = redisSection?.getInt("ttl", 600) ?: 600,
                poolMaxTotal = redisSection?.getInt("pool.max-total", 16) ?: 16,
                poolMaxIdle = redisSection?.getInt("pool.max-idle", 8) ?: 8,
                poolMinIdle = redisSection?.getInt("pool.min-idle", 2) ?: 2,
                poolMaxWait = redisSection?.getLong("pool.max-wait", 3000) ?: 3000
            )

            redisCache = RedisCache(this, redisConfig)

            if (redisCache?.initialize() == true) {
                logger.info("Redis cache connected successfully!")
            } else {
                logger.warning("Redis cache failed to connect. Using local cache only.")
                redisCache = null
            }
        } else {
            logger.info("Redis cache is disabled. Using local in-memory cache only.")
        }

        // Create hybrid cache (combines local + optional Redis)
        hybridCache = HybridPlayerCache(playerCache, redisCache)

        rankCache = RankCache()

        leaderboardCache = LeaderboardCache(
            maxEntries = leaderboardSize,
            ttl = Duration.ofMinutes(leaderboardTtl)
        )

        val redisStatus = if (hybridCache.isRedisEnabled()) "Redis: enabled" else "Redis: disabled"
        logger.info("Caches initialized (player: $cacheSize, leaderboard: $leaderboardSize, $redisStatus)")
    }

    /**
     * Save default ranks.yml if it doesn't exist.
     */
    private fun saveDefaultRanksConfig() {
        val ranksFile = File(dataFolder, "ranks.yml")
        if (!ranksFile.exists()) {
            saveResource("ranks.yml", false)
            logger.info("Created default ranks.yml")
        }
    }

    /**
     * Initialize the database connection pool and tables.
     * Supports both SQLite and MySQL/MariaDB based on config.yml settings.
     *
     * Note: This uses runBlocking which is acceptable during plugin startup (onEnable)
     * as the server expects plugins to block while loading. However, we wrap it
     * to handle timeout and prevent infinite hangs.
     */
    private fun initializeDatabase() {
        database = Database(this)

        val dbConfig = config.getConfigurationSection("database")

        if (dbConfig == null) {
            throw IllegalStateException("Database configuration section not found in config.yml")
        }

        val dbType = dbConfig.getString("type", "sqlite")?.lowercase() ?: "sqlite"
        logger.info("Initializing database with type: $dbType")

        // runBlocking is acceptable here during onEnable as the server expects
        // plugins to block while loading. We add a timeout for safety.
        val success = runBlocking {
            try {
                kotlinx.coroutines.withTimeout(30_000L) {
                    database.initialize(dbConfig)
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                logger.severe("Database initialization timed out after 30 seconds")
                false
            }
        }

        if (!success) {
            throw IllegalStateException("Failed to initialize database. Check config and logs.")
        }

        logger.info("Database initialized successfully (${database.getType().displayName})")
    }

    private fun initializeServices() {
        // Initialize coroutine scope for async operations
        coroutineScope = PluginCoroutineScope(this)

        // Initialize player service with database (supports both SQLite and MySQL/MariaDB)
        playerService = DatabasePlayerService(this, database, playerCache)

        // Initialize format manager (numbers, currency, progress bars)
        formatManager = FormatManager(this)
        formatManager.initialize().onFailure {
            logger.warning("Failed to initialize format manager: ${it.message}")
        }

        // Initialize language manager for command/GUI translations
        languageManager = LanguageManager(this)
        languageManager.initialize().onFailure {
            logger.warning("Failed to initialize language: ${it.message}")
        }

        // Initialize GUI config manager for customizable GUIs
        guiConfigManager = GuiConfigManager(this)
        guiConfigManager.initialize().onFailure {
            logger.warning("Failed to initialize GUI configs: ${it.message}")
        }

        // Initialize economy provider
        economyProvider = EconomyFactory.create(this, config.getString("economy.provider", "vault") ?: "vault")

        // Initialize command queue service (OPTIMIZED - prevents lag during mass rankups)
        commandQueueService = CommandQueueService(this) {
            val modeString = config.getString("performance.queue-mode", "ROUND_ROBIN") ?: "ROUND_ROBIN"
            val queueMode = try {
                CommandQueueService.QueueMode.valueOf(modeString.uppercase())
            } catch (e: IllegalArgumentException) {
                logger.warning("Invalid queue-mode '$modeString', using ROUND_ROBIN")
                CommandQueueService.QueueMode.ROUND_ROBIN
            }

            CommandQueueService.QueueConfig(
                commandsPerTick = config.getInt("performance.commands-per-tick", 10),
                tickInterval = config.getLong("performance.tick-interval", 1L),
                maxQueueSizePerPlayer = config.getInt("performance.max-queue-per-player", 1000),
                debugLogging = config.getBoolean("performance.debug-logging", false),
                queueMode = queueMode
            )
        }

        // Initialize requirement checker with optional PlaceholderAPI resolver
        val papiResolver: ((Player, String) -> String)? = try {
            if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
                { player, placeholder -> me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, placeholder) }
            } else null
        } catch (_: Exception) { null }

        requirementChecker = RequirementChecker(papiResolver)

        // Initialize permission service (LuckPerms with Bukkit fallback, used by RewardService)
        permissionService = PermissionService(this)

        // Initialize reward service (with command queue for optimized execution)
        rewardService = RewardService(
            plugin = this,
            formatter = formatManager,
            lang = languageManager,
            configProvider = {
                RewardService.RewardConfig(
                titleEnabled = config.getBoolean("effects.title.enabled", true),
                titleFadeIn = config.getInt("effects.title.fade-in", 10),
                titleStay = config.getInt("effects.title.stay", 70),
                titleFadeOut = config.getInt("effects.title.fade-out", 20),
                subtitleEnabled = config.getBoolean("effects.title.subtitle-enabled", true),
                actionBarEnabled = config.getBoolean("effects.action-bar.enabled", true),
                soundEnabled = config.getBoolean("effects.sound.enabled", true),
                soundName = config.getString("effects.sound.name", "ENTITY_PLAYER_LEVELUP") ?: "ENTITY_PLAYER_LEVELUP",
                soundVolume = config.getDouble("effects.sound.volume", 1.0).toFloat(),
                soundPitch = config.getDouble("effects.sound.pitch", 1.2).toFloat(),
                fireworkEnabled = config.getBoolean("effects.firework.enabled", false),
                fireworkAmount = config.getInt("effects.firework.amount", 1),
                particlesEnabled = config.getBoolean("effects.particles.enabled", true),
                particleType = config.getString("effects.particles.type", "TOTEM_OF_UNDYING") ?: "TOTEM_OF_UNDYING",
                particleCount = config.getInt("effects.particles.count", 50),
                lightningEnabled = config.getBoolean("effects.lightning.enabled", false),
                broadcastEnabled = config.getBoolean("broadcast.enabled", true),
                broadcastFormat = config.getString("broadcast.format", "<dark_gray>[<gold>★</gold>]</dark_gray> <yellow>{player}</yellow> <gray>has achieved the rank of</gray> {rank_display}<gray>!</gray>") ?: "",
                milestoneEnabled = config.getBoolean("broadcast.milestone-enabled", true),
                milestoneFormat = config.getString("broadcast.milestone-format", "<dark_gray>[<gradient:#FFD700:#FF6B6B>★★★</gradient>]</dark_gray> <gold><bold>{player}</bold></gold> <yellow>has reached a milestone!</yellow> <gray>Now:</gray> {rank_display}") ?: ""
            )
            },
            commandQueue = commandQueueService,
            permissionService = permissionService
        )

        // Initialize rankup service
        rankupService = RankupService(
            plugin = this,
            economyProvider = economyProvider,
            rewardService = rewardService,
            requirementChecker = requirementChecker,
            rankProvider = { rankCache.getAllSorted() },
            playerDataProvider = { uuid -> playerCache.get(uuid) },
            // CRITICAL: Save to both cache AND database immediately for rankups
            // This prevents data loss if server crashes after rankup
            playerDataSaver = { data ->
                playerCache.put(data.uuid, data)
                playerService.savePlayerData(data)
            },
            configProvider = {
                RankupService.RankupConfig(
                    maxRankupEnabled = config.getBoolean("rankup.max-enabled", true),
                    maxRankupLimit = config.getInt("rankup.max-limit", 100),
                    cooldownSeconds = config.getInt("rankup.cooldown-seconds", 0),
                    requireConfirmation = config.getBoolean("rankup.require-confirmation", false),
                    refundPercentage = config.getInt("rankup.refund-percentage", 100)
                )
            },
            onRankupComplete = {
                // Invalidate leaderboard cache so it refreshes on next view
                leaderboardCache.invalidate()
            }
        )

        logger.info("Services initialized (economy: ${economyProvider.getName()})")
    }

    private fun initializeHooks() {
        // Create hook manager with null-safe API provider
        hookManager = HookManager(this) {
            if (::apiImpl.isInitialized) apiImpl else null
        }

        // Note: Actual initialization will happen in initializeHooksDelayed()
        // after API is initialized to ensure PlaceholderAPI has access to the API
    }

    /**
     * Initialize hooks after API is ready.
     * This ensures PlaceholderAPI expansion can access the API.
     */
    private fun initializeHooksDelayed() {
        server.scheduler.runTask(this) { _ ->
            hookManager.initialize()

            // Initialize placeholder processor (supports both Essentials {shinobu_*} and PAPI %shinobu_*% formats)
            placeholderProcessor = PlaceholderProcessor(
                apiProvider = { if (::apiImpl.isInitialized) apiImpl else null },
                vaultHook = hookManager.vault
            )
            logger.info("Placeholder processor initialized (Essentials & PAPI formats supported)")

            // Warn if Vault is not available
            if (!hookManager.isVaultEnabled()) {
                logger.warning("Vault is not available! Economy features will not work.")
            }

            // Log PlaceholderAPI status for debugging
            if (hookManager.isPlaceholderAPIEnabled()) {
                logger.info("PlaceholderAPI expansion 'shinobu' registered successfully!")
            } else {
                val papiPlugin = server.pluginManager.getPlugin("PlaceholderAPI")
                if (papiPlugin != null) {
                    logger.warning("PlaceholderAPI found but expansion failed to register!")
                }
            }
        }
    }

    /**
     * Load ranks from ranks.yml configuration file.
     * Respects the default-rank setting for new players.
     */
    private fun loadRanks() {
        // Load ranks.yml configuration
        val ranksFile = File(dataFolder, "ranks.yml")
        if (!ranksFile.exists()) {
            logger.warning("ranks.yml not found! Saving default...")
            saveResource("ranks.yml", false)
        }

        ranksConfig = YamlConfiguration.loadConfiguration(ranksFile)

        // Also load defaults from jar for missing values
        getResource("ranks.yml")?.let { resource ->
            val defaultConfig = YamlConfiguration.loadConfiguration(InputStreamReader(resource))
            ranksConfig.setDefaults(defaultConfig)
        }

        // Get default rank from ranks.yml
        defaultRankId = ranksConfig.getString("default-rank", "wanderer") ?: "wanderer"
        logger.info("Default rank set to: $defaultRankId")

        // Load ranks section
        val ranksSection = ranksConfig.getConfigurationSection("ranks")
        if (ranksSection == null) {
            logger.warning("No ranks section found in ranks.yml! Using default ranks.")
            loadDefaultRanks()
            return
        }

        val ranks = mutableListOf<RankData>()

        for (rankId in ranksSection.getKeys(false)) {
            try {
                val section = ranksSection.getConfigurationSection(rankId) ?: continue

                // Get display name - handle both MiniMessage format and legacy color codes
                val displayName = section.getString("display-name", rankId) ?: rankId

                // Get icon material - handle invalid materials gracefully
                val iconName = section.getString("icon", "PAPER") ?: "PAPER"
                val icon = Material.matchMaterial(iconName) ?: run {
                    logger.warning("Invalid icon material '$iconName' for rank $rankId, using PAPER")
                    Material.PAPER
                }

                // Get commands - convert placeholders if needed
                val commands = section.getStringList("commands").map { cmd ->
                    // Normalize placeholder format from %player% to {player}
                    cmd.replace("%player%", "{player}")
                }

                // Get permissions list
                val permissions = section.getStringList("permissions")

                // Parse customizable text fields with disable detection
                val rawTitle = section.getString("title")
                val rawSubtitle = section.getString("subtitle")
                val rawSound = section.getString("sound")
                val rawBroadcast = section.getString("broadcast-message")

                // Check if field should be disabled (empty string or # prefix)
                fun isDisabled(value: String?): Boolean =
                    value == "" || value?.startsWith("#") == true

                val titleDisabled = isDisabled(rawTitle)
                val subtitleDisabled = isDisabled(rawSubtitle)
                val soundDisabled = isDisabled(rawSound)
                val broadcastDisabled = isDisabled(rawBroadcast)

                // Parse requirements section
                val reqSection = section.getConfigurationSection("requirements")
                val requirements = if (reqSection != null) {
                    com.shinobu.rankup.data.RankRequirements(
                        minPlaytime = reqSection.getLong("min-playtime", 0L),
                        minKills = reqSection.getInt("min-kills", 0),
                        minLevel = reqSection.getInt("min-level", 0),
                        requiredPermissions = reqSection.getStringList("permissions"),
                        requiredItems = reqSection.getConfigurationSection("items")?.let { itemsSection ->
                            itemsSection.getKeys(false).mapNotNull { key ->
                                try {
                                    val material = org.bukkit.Material.valueOf(key.uppercase())
                                    val amount = itemsSection.getInt(key, 1)
                                    material to amount
                                } catch (_: Exception) { null }
                            }.toMap()
                        } ?: emptyMap(),
                        minPlayerKills = reqSection.getInt("min-player-kills", 0),
                        minMobKills = reqSection.getInt("min-mob-kills", 0),
                        placeholders = reqSection.getConfigurationSection("placeholders")?.let { pSec ->
                            pSec.getKeys(false).associate { key ->
                                val placeholder = if (key.startsWith("%")) key else "%${key}%"
                                placeholder to (pSec.getString(key) ?: "")
                            }.filter { it.value.isNotBlank() }
                        } ?: emptyMap()
                    )
                } else {
                    com.shinobu.rankup.data.RankRequirements()
                }

                // Build rank data with ALL customization fields
                val rank = RankData(
                    id = rankId,
                    displayName = displayName,
                    prefix = section.getString("prefix", "") ?: "",
                    cost = section.getDouble("cost", 0.0),
                    order = section.getInt("order", ranks.size),
                    permission = permissions.firstOrNull(), // Primary permission for backward compatibility
                    icon = icon,
                    commands = commands,
                    broadcastMessage = if (broadcastDisabled) null else rawBroadcast,
                    requirements = requirements,

                    // Per-rank customization fields
                    description = section.getStringList("description"),
                    lore = section.getStringList("lore"),
                    glow = section.getBoolean("glow", false),
                    headTexture = section.getString("head-texture"),
                    title = if (titleDisabled) null else rawTitle,
                    subtitle = if (subtitleDisabled) null else rawSubtitle,
                    sound = if (soundDisabled) null else rawSound,
                    titleDisabled = titleDisabled,
                    subtitleDisabled = subtitleDisabled,
                    soundDisabled = soundDisabled,
                    broadcastDisabled = broadcastDisabled
                )

                ranks.add(rank)
                logger.fine("Loaded rank: ${rank.id} (order: ${rank.order}, cost: ${rank.cost})")

            } catch (e: Exception) {
                logger.log(Level.WARNING, "Failed to load rank: $rankId", e)
            }
        }

        if (ranks.isEmpty()) {
            logger.warning("No valid ranks loaded from ranks.yml! Using default ranks.")
            loadDefaultRanks()
        } else {
            // Sort ranks by order before applying FREE limitation
            val sortedRanks = ranks.sortedBy { it.order }

            // Apply FREE version limitation
            val ranksToLoad = if (BuildConfig.isFreeVersion()) {
                val maxRanks = BuildConfig.FREE_MAX_RANKS
                if (sortedRanks.size > maxRanks) {
                    logger.warning("=".repeat(60))
                    logger.warning("FREE VERSION: Rank limit reached!")
                    logger.warning("Found ${sortedRanks.size} ranks, but FREE version only supports $maxRanks.")
                    logger.warning("Only the first $maxRanks ranks (by order) will be loaded.")
                    logger.warning("Upgrade to PREMIUM for unlimited ranks.")
                    logger.warning("=".repeat(60))
                    sortedRanks.take(maxRanks)
                } else {
                    sortedRanks
                }
            } else {
                sortedRanks
            }

            rankCache.loadAll(ranksToLoad)
            logger.info("Loaded ${ranksToLoad.size} ranks from ranks.yml (default: $defaultRankId)")
        }

        // Verify default rank exists
        if (rankCache.getById(defaultRankId) == null) {
            val actualDefault = rankCache.getDefault()
            if (actualDefault != null) {
                logger.warning("Default rank '$defaultRankId' not found! Using '${actualDefault.id}' instead.")
                defaultRankId = actualDefault.id
            }
        }
    }

    private fun loadDefaultRanks() {
        val defaultRanks = listOf(
            RankData("member", "Member", "&7[Member]", 0.0, 0, icon = Material.WOODEN_SWORD),
            RankData("iron", "Iron", "&f[Iron]", 1000.0, 1, icon = Material.IRON_SWORD),
            RankData("gold", "Gold", "&6[Gold]", 5000.0, 2, icon = Material.GOLDEN_SWORD),
            RankData("diamond", "Diamond", "&b[Diamond]", 15000.0, 3, icon = Material.DIAMOND_SWORD),
            RankData("emerald", "Emerald", "&a[Emerald]", 50000.0, 4, icon = Material.EMERALD),
            RankData("netherite", "Netherite", "&8[Netherite]", 100000.0, 5, icon = Material.NETHERITE_SWORD)
        )

        rankCache.loadAll(defaultRanks)
        logger.info("Loaded ${defaultRanks.size} default ranks")
    }

    private fun initializeGuiManager() {
        guiManager = GuiManager.initialize(
            plugin = this,
            rankCache = rankCache,
            playerCache = playerCache,
            leaderboardCache = leaderboardCache,
            vaultHook = hookManager.vault,
            playerService = playerService
        )
        logger.info("GUI manager initialized (anti-dupe protection enabled)")
    }

    private fun initializeListeners() {
        // Use the defaultRankId loaded from ranks.yml
        val rankIdForNewPlayers = this.defaultRankId

        playerListener = PlayerListener(
            plugin = this,
            playerService = playerService,
            playerCache = playerCache,
            defaultRankProvider = { rankIdForNewPlayers },
            // Command queue for canceling pending commands on quit
            commandQueueService = commandQueueService,
            // Clean up player locks to prevent memory leaks
            onPlayerQuit = { uuid ->
                if (::rankupService.isInitialized) {
                    rankupService.cleanupPlayer(uuid)
                }
            }
        )

        server.pluginManager.registerEvents(playerListener, this)

        // Register GUI listener for anti-dupe protection
        server.pluginManager.registerEvents(GuiListener(), this)

        // Register chat listener to force rank prefix in chat
        chatListener = ChatListener {
            if (::apiImpl.isInitialized) apiImpl else null
        }
        server.pluginManager.registerEvents(chatListener, this)

        logger.info("Listeners registered (chat prefix forcing enabled)")
    }

    private fun initializeTasks() {
        val autoSaveInterval = config.getLong("tasks.auto-save-interval-seconds", 300)
        val leaderboardInterval = config.getLong("tasks.leaderboard-update-interval-seconds", 300)

        autoSaveTask = AutoSaveTask(
            plugin = this,
            playerService = playerService,
            playerCache = playerCache,
            intervalTicks = autoSaveInterval * 20
        )
        autoSaveTask.start()

        leaderboardTask = LeaderboardUpdateTask(
            plugin = this,
            playerService = playerService,
            leaderboardCache = leaderboardCache,
            rankCache = rankCache,
            intervalTicks = leaderboardInterval * 20
        )
        leaderboardTask.start()

        logger.info("Background tasks started")
    }

    private fun initializeAPI() {
        apiImpl = ShinobuRankupAPIImpl(
            plugin = this,
            playerCache = playerCache,
            rankCache = rankCache,
            leaderboardCache = leaderboardCache,
            playerService = playerService,
            vaultHook = hookManager.vault,
            autoSaveTask = autoSaveTask
        )

        ShinobuRankupProvider.register(apiImpl)
        logger.info("API registered")
    }

    private fun registerCommands() {
        // Create GUI opener implementation
        val guiOpener = object : CommandManager.GuiOpener {
            override fun openRanksGui(player: Player) {
                guiManager.openRanksGui(player)
            }

            override fun openTopPlayersGui(player: Player) {
                guiManager.openTopPlayersGui(player)
            }
        }

        // Create message provider implementation that uses languageManager
        val messageProvider = object : CommandManager.MessageProvider {
            override fun getMessage(key: String, placeholders: Map<String, String>): String {
                return languageManager.get(key, placeholders)
            }

            override fun getPrefix(): String {
                return languageManager.getPrefix()
            }
        }

        // Initialize command manager
        commandManager = CommandManager(
            plugin = this,
            rankupService = rankupService,
            coroutineScope = coroutineScope,
            guiOpener = guiOpener,
            messageProvider = messageProvider
        )

        // Register all commands
        commandManager.registerCommands()
    }

    /**
     * Get the ShinobuRankup API.
     * Use this from an instance, or use the static ShinobuRankup.getAPI() from companion.
     */
    @get:JvmName("api")
    val api: ShinobuRankupAPI
        get() = apiImpl

    /**
     * Reload the plugin configuration.
     */
    fun reload() {
        // Reload config.yml
        reloadConfig()

        // Reload ranks.yml
        loadRanks()

        // Reload language files (picks up language setting from config)
        if (::languageManager.isInitialized) {
            languageManager.reload()
        }

        // Reload format.yml
        if (::formatManager.isInitialized) {
            formatManager.reload()
        }

        // Reload GUI configurations
        if (::guiConfigManager.isInitialized) {
            guiConfigManager.reload()
        }

        // Reload hooks
        hookManager.reload()

        // Force leaderboard update asynchronously (no need to block main thread)
        if (::coroutineScope.isInitialized && ::leaderboardTask.isInitialized) {
            coroutineScope.launch {
                try {
                    leaderboardTask.forceUpdate()
                } catch (e: Exception) {
                    logger.warning("Failed to update leaderboard during reload: ${e.message}")
                }
            }
        }

        logger.info("ShinobuRankup reloaded (default rank: $defaultRankId, language: ${languageManager.getCurrentLanguage()})")
    }

    /**
     * Get the default rank ID for new players.
     */
    fun getDefaultRankId(): String = defaultRankId

    /**
     * Process placeholders in a string for a player.
     * Supports both Essentials style {shinobu_*} and PlaceholderAPI style %shinobu_*%.
     *
     * @param text The text to process
     * @param player The player for placeholder values
     * @return The text with placeholders replaced
     */
    fun processPlaceholders(text: String, player: org.bukkit.OfflinePlayer): String {
        return if (::placeholderProcessor.isInitialized) {
            placeholderProcessor.process(text, player)
        } else {
            text
        }
    }

    /**
     * Process placeholders in a list of strings for a player.
     *
     * @param texts The texts to process
     * @param player The player for placeholder values
     * @return The texts with placeholders replaced
     */
    fun processPlaceholders(texts: List<String>, player: org.bukkit.OfflinePlayer): List<String> {
        return if (::placeholderProcessor.isInitialized) {
            placeholderProcessor.process(texts, player)
        } else {
            texts
        }
    }

    companion object {
        /**
         * Get the plugin instance.
         */
        @JvmStatic
        fun getInstance(): ShinobuRankup {
            return getPlugin(ShinobuRankup::class.java)
        }

        /**
         * Get the ShinobuRankup API.
         */
        @JvmStatic
        fun getAPI(): ShinobuRankupAPI? = ShinobuRankupProvider.getAPI()
    }
}
