package com.shinobu.rankup.service

import com.shinobu.rankup.config.FormatManager
import com.shinobu.rankup.config.LanguageManager
import com.shinobu.rankup.data.RankData
import com.shinobu.rankup.service.PermissionService
import com.shinobu.rankup.util.ColorUtil
import com.shinobu.rankup.util.runOnMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kyori.adventure.title.Title
import org.bukkit.*
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.entity.EntityType
import org.bukkit.entity.Firework
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.FireworkMeta
import org.bukkit.plugin.Plugin
import java.time.Duration
import java.util.logging.Logger

/**
 * Service responsible for executing rank rewards and effects.
 *
 * OPTIMIZATION:
 * - Commands are queued and executed gradually via CommandQueueService
 * - Prevents main thread blocking during mass rankups
 * - Visual effects are executed immediately (low cost)
 */
class RewardService(
    private val plugin: Plugin,
    private val formatter: FormatManager,
    private val lang: LanguageManager,
    private val configProvider: () -> RewardConfig,
    private val commandQueue: CommandQueueService,
    private val permissionService: PermissionService
) {
    private val logger: Logger = plugin.logger

    /**
     * Version-safe particle reference for HAPPY_VILLAGER (called VILLAGER_HAPPY in older versions).
     * Falls back to HEART if neither name is found.
     */
    private val happyVillagerParticle: Particle by lazy {
        try {
            Particle.valueOf("HAPPY_VILLAGER")
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                Particle.valueOf("VILLAGER_HAPPY")
            } catch (_: Exception) {
                Particle.HEART
            }
        }
    }

    /**
     * Version-safe particle reference for TOTEM_OF_UNDYING (called TOTEM in older versions).
     * Falls back to HAPPY_VILLAGER particle if neither name is found.
     */
    private val totemParticle: Particle by lazy {
        try {
            Particle.valueOf("TOTEM_OF_UNDYING")
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                Particle.valueOf("TOTEM")
            } catch (_: Exception) {
                happyVillagerParticle
            }
        }
    }

    /**
     * Configuration for rewards and effects.
     */
    data class RewardConfig(
        val titleEnabled: Boolean = true,
        val titleFadeIn: Int = 10,
        val titleStay: Int = 70,
        val titleFadeOut: Int = 20,
        val subtitleEnabled: Boolean = true,
        val actionBarEnabled: Boolean = true,
        val soundEnabled: Boolean = true,
        val soundName: String = "ENTITY_PLAYER_LEVELUP",
        val soundVolume: Float = 1.0f,
        val soundPitch: Float = 1.2f,
        val fireworkEnabled: Boolean = false,
        val fireworkAmount: Int = 1,
        val particlesEnabled: Boolean = true,
        val particleType: String = "TOTEM_OF_UNDYING",
        val particleCount: Int = 50,
        val lightningEnabled: Boolean = false,
        val broadcastEnabled: Boolean = true,
        val broadcastFormat: String = "<dark_gray>[<gold>★</gold>]</dark_gray> <yellow>{player}</yellow> <gray>has achieved the rank of</gray> {rank_display}<gray>!</gray>",
        val milestoneEnabled: Boolean = true,
        val milestoneFormat: String = "<dark_gray>[<gradient:#FFD700:#FF6B6B>★★★</gradient>]</dark_gray> <gold><bold>{player}</bold></gold> <yellow>has reached a milestone!</yellow> <gray>Now:</gray> {rank_display}"
    )

    /**
     * Execute all rewards for a rankup.
     * Commands are queued for gradual execution to prevent lag.
     */
    suspend fun executeRankupRewards(
        player: Player,
        previousRank: RankData,
        newRank: RankData,
        nextRank: RankData? = null,
        isMilestone: Boolean = false
    ) {
        val config = configProvider()

        // Queue rank commands for gradual execution (OPTIMIZED)
        // Commands will be executed over time to prevent main thread blocking
        commandQueue.queueRankCommands(player, newRank)

        // Grant rank permission if defined
        newRank.permission?.let { permission ->
            if (permission.isNotBlank()) {
                try {
                    permissionService.grantPermission(player, permission)
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to grant permission '$permission' to ${player.name}: ${e.message}")
                }
            }
        }

        // Send title
        if (config.titleEnabled) {
            plugin.runOnMain {
                sendRankupTitle(player, newRank, nextRank, isMilestone)
            }
        }

        // Send action bar
        if (config.actionBarEnabled) {
            plugin.runOnMain {
                sendRankupActionBar(player, newRank, nextRank)
            }
        }

        // Play sound (pass rank for per-rank sound customization)
        if (config.soundEnabled) {
            plugin.runOnMain {
                playRankupSound(player, config, isMilestone, newRank)
            }
        }

        // Spawn particles
        if (config.particlesEnabled) {
            plugin.runOnMain {
                spawnParticles(player, config)
            }
        }

        // Spawn firework
        if (config.fireworkEnabled) {
            plugin.runOnMain {
                spawnFirework(player, config)
            }
        }

        // Lightning effect
        if (config.lightningEnabled) {
            plugin.runOnMain {
                strikeLightning(player)
            }
        }

        // Broadcast
        if (config.broadcastEnabled) {
            plugin.runOnMain {
                broadcastRankup(player, newRank, nextRank, isMilestone, config)
            }
        }

        // Boss bar effect
        plugin.runOnMain {
            showRankupBossBar(player, newRank)
        }
    }

    /**
     * Queue commands for a single rank (used by individual rankups within max rankup).
     * Commands are queued for gradual execution to prevent lag.
     *
     * @param player The player to execute commands for
     * @param rank The rank whose commands should be queued
     */
    fun queueRankCommands(player: Player, rank: RankData) {
        commandQueue.queueRankCommands(player, rank)
    }

    /**
     * Queue commands for multiple ranks at once (OPTIMIZED for max rankup).
     * More efficient than queuing one rank at a time.
     *
     * @param player The player to execute commands for
     * @param ranks List of ranks whose commands should be queued
     */
    fun queueMultipleRankCommands(player: Player, ranks: List<RankData>) {
        commandQueue.queueMultipleRankCommands(player, ranks)
    }

    /**
     * Get the number of pending commands for a player.
     */
    fun getPendingCommandCount(player: Player): Int {
        return commandQueue.getPendingCount(player.uniqueId)
    }

    /**
     * Execute commands for a rank.
     * Supports placeholders: {player}, {player_uuid}, {rank}, {rank_display}
     *
     * Security: Player names are sanitized to prevent command injection.
     * The [op] prefix has been removed for security reasons - use [console] instead.
     */
    private fun executeCommands(player: Player, rank: RankData) {
        if (rank.commands.isEmpty()) return

        // SECURITY: Sanitize player name to prevent command injection
        val sanitizedName = sanitizePlayerName(player.name)

        val placeholders = mapOf(
            "player" to sanitizedName,
            "player_uuid" to player.uniqueId.toString(),
            "rank" to rank.id,
            "rank_display" to rank.displayName
        )

        rank.commands.forEach { command ->
            var processedCommand = command
            placeholders.forEach { (key, value) ->
                processedCommand = processedCommand
                    .replace("{$key}", value)
                    .replace("%$key%", value)
            }

            try {
                // Determine if it's a console or player command
                when {
                    processedCommand.startsWith("[console]") -> {
                        val cmd = processedCommand.removePrefix("[console]").trim()
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
                    }
                    processedCommand.startsWith("[player]") -> {
                        val cmd = processedCommand.removePrefix("[player]").trim()
                        player.performCommand(cmd)
                    }
                    processedCommand.startsWith("[op]") -> {
                        // SECURITY: [op] prefix removed - log warning and execute as console instead
                        logger.warning("Command uses deprecated [op] prefix (security risk). Use [console] instead: $processedCommand")
                        val cmd = processedCommand.removePrefix("[op]").trim()
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
                    }
                    processedCommand.startsWith("[message]") -> {
                        val msg = processedCommand.removePrefix("[message]").trim()
                        player.sendMessage(ColorUtil.parse(msg))
                    }
                    else -> {
                        // Default: run as console
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand)
                    }
                }
            } catch (e: Exception) {
                logger.warning("Failed to execute command '$processedCommand' for ${player.name}: ${e.message}")
            }
        }
    }

    /**
     * Sanitize player name to prevent command injection.
     * Only allows alphanumeric characters and underscore (valid Minecraft names).
     */
    private fun sanitizePlayerName(name: String): String {
        // Allow: letters, digits, underscore, dot, dash, asterisk, space
        // Covers Java Edition (a-z, 0-9, _) and Bedrock Edition via Geyser/Floodgate (. - * prefix)
        return name.filter { it.isLetterOrDigit() || it in "._-* " }.take(19)
    }

    /**
     * Send rankup title to player.
     * Uses per-rank customization if available, otherwise falls back to LanguageManager.
     *
     * Customization priority:
     * 1. If titleDisabled = true -> skip entirely
     * 2. If rank.title is set -> use per-rank title
     * 3. Otherwise -> use default from language file
     */
    private fun sendRankupTitle(player: Player, rank: RankData, nextRank: RankData? = null, isMilestone: Boolean) {
        // Skip if title is disabled for this rank
        if (rank.titleDisabled) return

        val config = configProvider()

        val placeholders = mapOf(
            "player" to player.name,
            "rank" to rank.displayName,
            "rank_display" to rank.displayName,
            "rank_id" to rank.id,
            "next_rank" to (nextRank?.displayName ?: "")
        )

        val titlePath = if (isMilestone) "rankup.milestone.title" else "rankup.success.title"
        val subtitlePath = if (isMilestone) "rankup.milestone.subtitle" else "rankup.success.subtitle"

        // Use per-rank title if set, otherwise fall back to language file
        val titleText = rank.title?.let { processPlaceholders(it, placeholders) }
            ?: lang.get(titlePath, placeholders)

        // Check subtitle: disabled, per-rank, or default
        val subtitleText = when {
            rank.subtitleDisabled -> ""
            !config.subtitleEnabled -> ""
            rank.subtitle != null -> processPlaceholders(rank.subtitle, placeholders)
            else -> lang.get(subtitlePath, placeholders)
        }

        val title = ColorUtil.parse(titleText)
        val subtitle = if (subtitleText.isNotEmpty()) {
            ColorUtil.parse(subtitleText)
        } else {
            net.kyori.adventure.text.Component.empty()
        }

        val times = Title.Times.of(
            Duration.ofMillis(config.titleFadeIn * 50L),
            Duration.ofMillis(config.titleStay * 50L),
            Duration.ofMillis(config.titleFadeOut * 50L)
        )

        player.showTitle(Title.title(title, subtitle, times))
    }

    /**
     * Process placeholders in a string.
     */
    private fun processPlaceholders(text: String, placeholders: Map<String, String>): String {
        var result = text
        placeholders.forEach { (key, value) ->
            result = result
                .replace("{$key}", value)
                .replace("%$key%", value)
        }
        return result
    }

    /**
     * Send action bar message to player.
     * Uses LanguageManager for multi-language support.
     */
    private fun sendRankupActionBar(player: Player, rank: RankData, nextRank: RankData? = null) {
        val placeholders = mapOf(
            "player" to player.name,
            "rank" to rank.displayName,
            "next_rank" to (nextRank?.displayName ?: "")
        )
        val messageText = lang.get("rankup.success.actionbar", placeholders)
        val message = ColorUtil.parse(messageText)
        player.sendActionBar(message)
    }

    /**
     * Play rankup sound.
     * Uses per-rank sound if configured, otherwise uses default from config.
     *
     * @param rank The rank data (used for per-rank sound customization)
     */
    private fun playRankupSound(player: Player, config: RewardConfig, isMilestone: Boolean, rank: RankData? = null) {
        // Skip if sound is disabled for this rank
        if (rank?.soundDisabled == true) return

        try {
            // Priority: milestone sound > per-rank sound > config default
            val soundName = when {
                isMilestone -> "UI_TOAST_CHALLENGE_COMPLETE"
                rank?.sound != null -> rank.sound
                else -> config.soundName
            }
            val sound = Sound.valueOf(soundName.uppercase())
            player.playSound(player.location, sound, config.soundVolume, config.soundPitch)
        } catch (e: IllegalArgumentException) {
            // Fallback sound
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, config.soundVolume, config.soundPitch)
        }
    }

    /**
     * Spawn particles around player.
     */
    private fun spawnParticles(player: Player, config: RewardConfig) {
        try {
            val particle = Particle.valueOf(config.particleType.uppercase())
            player.world.spawnParticle(
                particle,
                player.location.add(0.0, 1.0, 0.0),
                config.particleCount,
                0.5, 0.5, 0.5,
                0.1
            )
        } catch (e: IllegalArgumentException) {
            // Fallback particle (version-safe)
            player.world.spawnParticle(
                happyVillagerParticle,
                player.location.add(0.0, 1.0, 0.0),
                config.particleCount,
                0.5, 0.5, 0.5,
                0.1
            )
        }
    }

    /**
     * Spawn firework at player location.
     */
    private fun spawnFirework(player: Player, config: RewardConfig) {
        repeat(config.fireworkAmount) {
            val firework = player.world.spawnEntity(player.location, EntityType.FIREWORK) as Firework
            val meta = firework.fireworkMeta

            val effect = FireworkEffect.builder()
                .withColor(Color.YELLOW, Color.ORANGE)
                .withFade(Color.RED)
                .with(FireworkEffect.Type.BALL_LARGE)
                .flicker(true)
                .trail(true)
                .build()

            meta.addEffect(effect)
            meta.power = 1

            firework.fireworkMeta = meta

            // Detonate immediately after a short delay
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                firework.detonate()
            }, 2L)
        }
    }

    /**
     * Strike cosmetic lightning at player location.
     */
    private fun strikeLightning(player: Player) {
        // Use effect lightning (no damage)
        player.world.strikeLightningEffect(player.location)
    }

    /**
     * Broadcast rankup to server.
     * Uses LanguageManager for multi-language support.
     * Respects per-rank broadcastDisabled flag.
     */
    private fun broadcastRankup(player: Player, rank: RankData, nextRank: RankData? = null, isMilestone: Boolean, config: RewardConfig) {
        // Skip if broadcast is disabled for this rank
        if (rank.broadcastDisabled) return

        // Use custom broadcast message from rank if available
        val customMessage = rank.broadcastMessage

        val placeholders = mapOf(
            "player" to player.name,
            "rank" to rank.displayName,
            "rank_display" to rank.displayName,
            "rank_id" to rank.id,
            "tier" to ((rank.order / 10) + 1).toString(),
            "next_rank" to (nextRank?.displayName ?: "")
        )

        val broadcastMessage = if (customMessage != null) {
            // Process custom message with placeholders
            val processed = processPlaceholders(customMessage, placeholders)
            ColorUtil.parse(processed)
        } else {
            // Use message from LanguageManager (respects configured language)
            val path = if (isMilestone && config.milestoneEnabled) {
                "broadcast.milestone"
            } else {
                "broadcast.rankup"
            }
            val messageText = lang.get(path, placeholders)
            ColorUtil.parse(messageText)
        }

        // Send to all online players except the one ranking up (they already get a personal message)
        for (onlinePlayer in Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.uniqueId != player.uniqueId) {
                onlinePlayer.sendMessage(broadcastMessage)
            }
        }
        // Also log to console
        Bukkit.getConsoleSender().sendMessage(broadcastMessage)
    }

    /**
     * Execute rewards for max rankup.
     * Uses LanguageManager for multi-language support.
     */
    suspend fun executeMaxRankupRewards(
        player: Player,
        startRank: RankData,
        endRank: RankData,
        ranksGained: Int,
        totalCost: Double,
        remainingBalance: Double
    ) {
        val config = configProvider()

        val placeholders = mapOf(
            "player" to player.name,
            "count" to ranksGained.toString(),
            "old_rank" to startRank.displayName,
            "old_rank_id" to startRank.id,
            "rank" to endRank.id,
            "rank_display" to endRank.displayName,
            "total_cost" to totalCost.toLong().toString(),
            "total_cost_formatted" to formatter.formatCurrency(totalCost.toLong()),
            "balance" to remainingBalance.toLong().toString(),
            "balance_formatted" to formatter.formatCurrency(remainingBalance.toLong())
        )

        // Special max rankup title - using LanguageManager
        plugin.runOnMain {
            val titleText = lang.get("rankupmax.success.title", placeholders)
            val subtitleText = lang.get("rankupmax.success.subtitle", placeholders)

            val title = ColorUtil.parse(titleText)
            val subtitle = ColorUtil.parse(subtitleText)

            val times = Title.Times.of(
                Duration.ofMillis(config.titleFadeIn * 50L),
                Duration.ofMillis(100 * 50L), // Longer stay for max rankup
                Duration.ofMillis(config.titleFadeOut * 50L)
            )

            player.showTitle(Title.title(title, subtitle, times))
        }

        // Special sound
        plugin.runOnMain {
            player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
        }

        // Extra particles for max rankup
        if (config.particlesEnabled) {
            plugin.runOnMain {
                player.world.spawnParticle(
                    totemParticle,
                    player.location.add(0.0, 1.0, 0.0),
                    100,
                    1.0, 1.0, 1.0,
                    0.5
                )
            }
        }

        // Special broadcast - using LanguageManager
        if (config.broadcastEnabled) {
            plugin.runOnMain {
                val broadcastText = lang.get("broadcast.max-rankup", placeholders)
                val broadcastMessage = ColorUtil.parse(broadcastText)
                // Send to all online players except the one ranking up (they already get a personal message)
                for (onlinePlayer in Bukkit.getOnlinePlayers()) {
                    if (onlinePlayer.uniqueId != player.uniqueId) {
                        onlinePlayer.sendMessage(broadcastMessage)
                    }
                }
                // Also log to console
                Bukkit.getConsoleSender().sendMessage(broadcastMessage)
            }
        }

        // Boss bar effect for final rank reached
        plugin.runOnMain {
            showRankupBossBar(player, endRank)
        }
    }

    /**
     * Send the detailed max rankup summary to the player.
     * Uses LanguageManager for multi-language support.
     */
    suspend fun sendMaxRankupSummary(
        player: Player,
        startRank: RankData,
        endRank: RankData,
        ranksGained: Int,
        totalCost: Double,
        remainingBalance: Double
    ) {
        plugin.runOnMain {
            val placeholders = mapOf(
                "player" to player.name,
                "count" to ranksGained.toString(),
                "old_rank" to startRank.displayName,
                "old_rank_id" to startRank.id,
                "rank" to endRank.id,
                "rank_display" to endRank.displayName,
                "total_cost" to totalCost.toLong().toString(),
                "total_cost_formatted" to formatter.formatCurrency(totalCost.toLong()),
                "balance" to remainingBalance.toLong().toString(),
                "balance_formatted" to formatter.formatCurrency(remainingBalance.toLong())
            )

            // Send message from LanguageManager (respects configured language)
            val messageText = lang.get("rankupmax.success.message", placeholders)
            val messageContent = ColorUtil.parse(messageText)
            player.sendMessage(messageContent)
        }
    }

    /**
     * Show a temporary boss bar for a rankup announcement.
     */
    private fun showRankupBossBar(player: Player, newRank: RankData) {
        val bossBarEnabled = plugin.config.getBoolean("effects.bossbar.enabled", false)
        if (!bossBarEnabled) return

        try {
            val colorName = plugin.config.getString("effects.bossbar.color", "YELLOW") ?: "YELLOW"
            val styleName = plugin.config.getString("effects.bossbar.style", "SOLID") ?: "SOLID"
            val durationTicks = plugin.config.getLong("effects.bossbar.duration-ticks", 100L)

            val barColor = try { BarColor.valueOf(colorName.uppercase()) } catch (_: Exception) { BarColor.YELLOW }
            val barStyle = try { BarStyle.valueOf(styleName.uppercase()) } catch (_: Exception) { BarStyle.SOLID }

            val title = ColorUtil.colorize("&6&l✦ &eRanked up to ${newRank.displayName} &6&l✦")
            val bossBar = Bukkit.createBossBar(title, barColor, barStyle)
            bossBar.progress = 1.0
            bossBar.addPlayer(player)

            // Remove after duration
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                bossBar.removePlayer(player)
                bossBar.removeAll()
            }, durationTicks)
        } catch (e: Exception) {
            plugin.logger.fine("Failed to show boss bar: ${e.message}")
        }
    }
}
