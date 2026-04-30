package com.srcodex.shinobustore

import com.srcodex.shinobustore.api.ShinobuStoreAPI
import com.srcodex.shinobustore.api.ShinobuStoreAPIImpl
import com.srcodex.shinobustore.command.*
import com.srcodex.shinobustore.config.ConfigManager
import com.srcodex.shinobustore.listener.MenuProtectionListener
import com.srcodex.shinobustore.menu.BaseMenu
import com.srcodex.shinobustore.paypal.CaptureTask
import com.srcodex.shinobustore.paypal.PayPalClient
import com.srcodex.shinobustore.paypal.PayPalService
import com.srcodex.shinobustore.service.StoreActions
import com.srcodex.shinobustore.transaction.TransactionManager
import com.srcodex.shinobustore.util.RateLimiter
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin

/**
 * ShinobuStore - Premium in-game store plugin with PayPal integration.
 *
 * A complete rewrite in Kotlin with modern APIs, optimized async operations,
 * and comprehensive security features.
 *
 * @author SrCodexStudio / SrCodex
 * @version 1.0.0
 */
class ShinobuStore : JavaPlugin() {

    // ═══════════════════════════════════════════════════════════════
    //                      MANAGERS & SERVICES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Configuration manager for plugin settings and messages.
     */
    lateinit var configManager: ConfigManager
        private set

    /**
     * Transaction manager for pending and completed transactions.
     */
    lateinit var transactionManager: TransactionManager
        private set

    /**
     * PayPal HTTP client for API communication.
     */
    lateinit var paypalClient: PayPalClient
        private set

    /**
     * PayPal service for business logic.
     */
    lateinit var paypalService: PayPalService
        private set

    /**
     * Rate limiter for abuse prevention.
     */
    lateinit var rateLimiter: RateLimiter
        private set

    /**
     * Capture task for checking pending transactions.
     */
    lateinit var captureTask: CaptureTask
        private set

    /**
     * Shared store actions used by multiple commands.
     * Eliminates duplicated open/cancel/timer logic.
     */
    lateinit var storeActions: StoreActions
        private set

    // ═══════════════════════════════════════════════════════════════
    //                      PLUGIN LIFECYCLE
    // ═══════════════════════════════════════════════════════════════

    override fun onEnable() {
        val startTime = System.currentTimeMillis()

        // Display startup banner
        displayBanner()

        // Initialize managers
        initializeManagers()

        // Register commands
        registerCommands()

        // Register listeners
        registerListeners()

        // Start tasks
        startTasks()

        // Register PlaceholderAPI if available
        if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
            try {
                com.srcodex.shinobustore.integration.ShinobuStorePlaceholders(this).register()
                logger.info("PlaceholderAPI integration enabled!")
            } catch (e: Exception) {
                logger.warning("Failed to register PlaceholderAPI expansion: ${e.message}")
            }
        }

        // Register API for external plugins
        server.servicesManager.register(
            ShinobuStoreAPI::class.java,
            ShinobuStoreAPIImpl(this),
            this,
            ServicePriority.Normal
        )
        logger.info("ShinobuStore API registered")

        // Startup complete
        val loadTime = System.currentTimeMillis() - startTime
        logger.info("ShinobuStore enabled successfully in ${loadTime}ms!")
        logger.info("Version: ${description.version}")
        logger.info("Author: SrCodexStudio / SrCodex")

        // Check PayPal configuration
        if (!configManager.isPayPalConfigured()) {
            logger.warning("═══════════════════════════════════════════════════════════")
            logger.warning(" PayPal is not configured!")
            logger.warning(" Players will not be able to make purchases.")
            logger.warning(" Please configure PayPal in config.yml")
            logger.warning("═══════════════════════════════════════════════════════════")
        }
    }

    override fun onDisable() {
        // Close all open menus first (prevents CoroutineScope leaks)
        BaseMenu.closeAll()

        // Stop rate limiter cleanup task
        if (::rateLimiter.isInitialized) {
            rateLimiter.stopCleanupTask()
        }

        // Stop capture task
        if (::captureTask.isInitialized) {
            captureTask.stop()
        }

        // Cancel all remaining tasks
        server.scheduler.cancelTasks(this)

        // Shutdown PayPal service (cancels coroutine scope)
        if (::paypalService.isInitialized) {
            paypalService.shutdown()
        }

        // Shutdown PayPal client (closes connection pool)
        if (::paypalClient.isInitialized) {
            paypalClient.shutdown()
        }

        // Save transactions
        if (::transactionManager.isInitialized) {
            transactionManager.shutdown()
        }

        logger.info("ShinobuStore disabled successfully!")
    }

    // ═══════════════════════════════════════════════════════════════
    //                      INITIALIZATION
    // ═══════════════════════════════════════════════════════════════

    private fun initializeManagers() {
        logger.info("Initializing managers...")

        // Configuration
        saveDefaultConfig()
        configManager = ConfigManager(this)
        configManager.load()
        logger.info("Configuration loaded")

        // Rate limiter
        rateLimiter = RateLimiter(
            plugin = this,
            maxRequestsPerMinute = configManager.rateLimitMaxRequests,
            cooldownMillis = configManager.storeOpenCooldown,
            abuseThreshold = configManager.rateLimitAbuseThreshold,
            abuseBanDurationMinutes = configManager.rateLimitBanDuration
        )
        rateLimiter.startCleanupTask()
        logger.info("Rate limiter initialized")

        // Transaction manager
        transactionManager = TransactionManager(this)
        transactionManager.load()
        logger.info("Transaction manager initialized")

        // PayPal client
        paypalClient = PayPalClient(this)
        logger.info("PayPal client initialized")

        // PayPal service (receives the shared client)
        paypalService = PayPalService(this, paypalClient)
        logger.info("PayPal service initialized")

        // Capture task
        captureTask = CaptureTask(this)
        logger.info("Capture task initialized")

        // Store actions (shared service for commands)
        storeActions = StoreActions(this)
        logger.info("Store actions initialized")
    }

    private fun registerCommands() {
        logger.info("Registering commands...")

        // Store command (/store, /shop, /buy, /tienda)
        val storeCommand = StoreCommand(this)
        getCommand("store")?.apply {
            setExecutor(storeCommand)
            tabCompleter = storeCommand
        }

        // Cancel command (/cancelitem, /cancel, /canceltransaction)
        val cancelCommand = CancelCommand(this)
        getCommand("cancelitem")?.apply {
            setExecutor(cancelCommand)
            tabCompleter = cancelCommand
        }

        // Timer command (/timer)
        val timerCommand = TimerCommand(this)
        getCommand("timer")?.apply {
            setExecutor(timerCommand)
            tabCompleter = timerCommand
        }

        // Lookup command (/lookup)
        val lookupCommand = LookupCommand(this)
        getCommand("lookup")?.apply {
            setExecutor(lookupCommand)
            tabCompleter = lookupCommand
        }

        // ShinobuStore unified command (/shinobustore, /ss)
        val ssCommand = ShinobuStoreCommand(this)
        getCommand("shinobustore")?.apply {
            setExecutor(ssCommand)
            tabCompleter = ssCommand
        }

        logger.info("Commands registered")
    }

    private fun registerListeners() {
        logger.info("Registering listeners...")

        // Menu protection listener
        server.pluginManager.registerEvents(
            MenuProtectionListener(this),
            this
        )

        logger.info("Listeners registered")
    }

    private fun startTasks() {
        logger.info("Starting scheduled tasks...")

        // Start capture task
        captureTask.start()

        // Schedule periodic cleanup
        server.scheduler.runTaskTimerAsynchronously(this, Runnable {
            val expiredCount = transactionManager.cleanupExpired()
            if (expiredCount > 0 && configManager.debugEnabled) {
                logger.info("Cleanup: Removed $expiredCount expired transactions")
            }
        }, 20L * 60 * 5, 20L * 60 * 5) // Every 5 minutes

        // Schedule history cleanup
        server.scheduler.runTaskTimerAsynchronously(this, Runnable {
            val oldCount = transactionManager.cleanupHistory()
            if (oldCount > 0 && configManager.debugEnabled) {
                logger.info("Cleanup: Removed $oldCount old history entries")
            }
        }, 20L * 60 * 60, 20L * 60 * 60) // Every hour

        logger.info("Scheduled tasks started")
    }

    // ═══════════════════════════════════════════════════════════════
    //                      UTILITIES
    // ═══════════════════════════════════════════════════════════════

    /**
     * Displays the startup banner.
     */
    private fun displayBanner() {
        logger.info("")
        logger.info("═══════════════════════════════════════════════════════════")
        logger.info("    ____  __    _             __          ____  __                ")
        logger.info("   / __/ / /   (_)___  ___   / /_  __ __ / __/ / /_ ___   ____ ___")
        logger.info("  _\\ \\  / _ \\ / // _ \\/ _ \\ / __/ / // /_\\ \\  / __// _ \\ / __// -_)")
        logger.info(" /___/ /_//_//_//_//_/\\___//_/    \\_,_//___/  \\__/ \\___//_/   \\__/ ")
        logger.info("")
        logger.info("                   Premium Store Plugin")
        logger.info("              by SrCodexStudio / SrCodex")
        logger.info("═══════════════════════════════════════════════════════════")
        logger.info("")
    }

    /**
     * Reloads the plugin configuration and all dependent components.
     */
    fun reload() {
        configManager.reload()

        // Recreate rate limiter
        rateLimiter.stopCleanupTask()
        rateLimiter = RateLimiter(
            plugin = this,
            maxRequestsPerMinute = configManager.rateLimitMaxRequests,
            cooldownMillis = configManager.storeOpenCooldown,
            abuseThreshold = configManager.rateLimitAbuseThreshold,
            abuseBanDurationMinutes = configManager.rateLimitBanDuration
        )
        rateLimiter.startCleanupTask()

        // Recreate PayPal client (environment may have changed)
        paypalClient.shutdown()
        paypalClient = PayPalClient(this)

        // Recreate PayPal service with new client
        paypalService.shutdown()
        paypalService = PayPalService(this, paypalClient)

        // Restart capture task (interval may have changed)
        captureTask.stop()
        captureTask = CaptureTask(this)
        captureTask.start()

        logger.info("Plugin reloaded successfully!")
    }

    // ═══════════════════════════════════════════════════════════════
    //                      API
    // ═══════════════════════════════════════════════════════════════

    companion object {
        /**
         * Gets the plugin instance.
         * @throws IllegalStateException if called before plugin is enabled
         */
        @JvmStatic
        fun getInstance(): ShinobuStore {
            return getPlugin(ShinobuStore::class.java)
        }
    }
}
