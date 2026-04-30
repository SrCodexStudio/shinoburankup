package com.shinobu.rankup.config

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader
import java.util.logging.Level

/**
 * Manages multi-language support for commands and GUI messages.
 *
 * Supports language files in resources/lang/ directory (en.yml, es.yml, etc.)
 * Language is configured in config.yml under "language" key.
 *
 * Note: This does NOT affect messages.yml or ranks.yml
 */
class LanguageManager(private val plugin: JavaPlugin) {

    private var currentLanguage: String = "en"
    private var langConfig: YamlConfiguration = YamlConfiguration()
    private var fallbackConfig: YamlConfiguration = YamlConfiguration()

    companion object {
        private val SUPPORTED_LANGUAGES = setOf("en", "es")
        private const val DEFAULT_LANGUAGE = "en"
    }

    /**
     * Initialize the language manager.
     * Loads the language file based on config.yml settings.
     */
    fun initialize(): Result<Unit> {
        return try {
            // Save default language files
            saveDefaultLanguageFiles()

            // Load the configured language
            reload()

            Result.success(Unit)
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to initialize LanguageManager", e)
            Result.failure(e)
        }
    }

    /**
     * Reload language files.
     * Call this when config.yml is reloaded to pick up language changes.
     *
     * Note: In FREE version, language is always English.
     * Users must edit lang/en.yml manually to translate.
     */
    fun reload() {
        // Check if FREE version - force English
        if (com.shinobu.rankup.BuildConfig.isFreeVersion()) {
            currentLanguage = com.shinobu.rankup.BuildConfig.FREE_DEFAULT_LANGUAGE

            val configuredLang = plugin.config.getString("language", DEFAULT_LANGUAGE)?.lowercase()
            if (configuredLang != null && configuredLang != "en") {
                plugin.logger.warning("=".repeat(60))
                plugin.logger.warning("FREE VERSION: Language selection is disabled!")
                plugin.logger.warning("Configured language '$configuredLang' ignored, using English.")
                plugin.logger.warning("To translate, edit: plugins/ShinobuRankup/lang/en.yml")
                plugin.logger.warning("Upgrade to PREMIUM for full language support.")
                plugin.logger.warning("=".repeat(60))
            }
        } else {
            // Premium: Get language from config
            currentLanguage = plugin.config.getString("language", DEFAULT_LANGUAGE)
                ?.lowercase()
                ?.takeIf { it in SUPPORTED_LANGUAGES }
                ?: DEFAULT_LANGUAGE
        }

        // Load the language file
        loadLanguageFile(currentLanguage)

        // Always load English as fallback
        if (currentLanguage != "en") {
            loadFallbackFile()
        }

        plugin.logger.info("Language loaded: $currentLanguage")
    }

    /**
     * Save default language files if they don't exist.
     */
    private fun saveDefaultLanguageFiles() {
        val langFolder = File(plugin.dataFolder, "lang")
        if (!langFolder.exists()) {
            langFolder.mkdirs()
        }

        for (lang in SUPPORTED_LANGUAGES) {
            val langFile = File(langFolder, "$lang.yml")
            if (!langFile.exists()) {
                try {
                    plugin.saveResource("lang/$lang.yml", false)
                    plugin.logger.info("Created language file: lang/$lang.yml")
                } catch (e: Exception) {
                    plugin.logger.warning("Could not save language file: lang/$lang.yml")
                }
            }
        }
    }

    /**
     * Load a specific language file.
     */
    private fun loadLanguageFile(language: String) {
        val langFile = File(plugin.dataFolder, "lang/$language.yml")

        if (langFile.exists()) {
            langConfig = YamlConfiguration.loadConfiguration(langFile)

            // Also load defaults from jar
            plugin.getResource("lang/$language.yml")?.let { resource ->
                val defaultConfig = YamlConfiguration.loadConfiguration(InputStreamReader(resource))
                langConfig.setDefaults(defaultConfig)
            }
        } else {
            // Load from jar if external file doesn't exist
            plugin.getResource("lang/$language.yml")?.let { resource ->
                langConfig = YamlConfiguration.loadConfiguration(InputStreamReader(resource))
            } ?: run {
                plugin.logger.warning("Language file not found: $language.yml, falling back to English")
                loadLanguageFile("en")
            }
        }
    }

    /**
     * Load the fallback (English) file for missing translations.
     */
    private fun loadFallbackFile() {
        val fallbackFile = File(plugin.dataFolder, "lang/en.yml")

        if (fallbackFile.exists()) {
            fallbackConfig = YamlConfiguration.loadConfiguration(fallbackFile)
        } else {
            plugin.getResource("lang/en.yml")?.let { resource ->
                fallbackConfig = YamlConfiguration.loadConfiguration(InputStreamReader(resource))
            }
        }
    }

    /**
     * Get a translated string.
     *
     * @param key The dot-notation key (e.g., "admin.help.header")
     * @param placeholders Map of placeholders to replace
     * @return The translated string with placeholders replaced
     */
    fun get(key: String, placeholders: Map<String, String> = emptyMap()): String {
        var message = langConfig.getString(key)
            ?: fallbackConfig.getString(key)
            ?: "<red>Missing: $key</red>"

        // Replace placeholders
        placeholders.forEach { (placeholder, value) ->
            message = message
                .replace("{$placeholder}", value)
                .replace("%$placeholder%", value)
        }

        return message
    }

    /**
     * Get a translated string list.
     *
     * @param key The dot-notation key
     * @param placeholders Map of placeholders to replace
     * @return The translated string list with placeholders replaced
     */
    fun getList(key: String, placeholders: Map<String, String> = emptyMap()): List<String> {
        val list = langConfig.getStringList(key).ifEmpty {
            fallbackConfig.getStringList(key)
        }

        if (list.isEmpty()) {
            return listOf("<red>Missing: $key</red>")
        }

        return list.map { line ->
            var result = line
            placeholders.forEach { (placeholder, value) ->
                result = result
                    .replace("{$placeholder}", value)
                    .replace("%$placeholder%", value)
            }
            result
        }
    }

    /**
     * Get the prefix for messages.
     */
    fun getPrefix(): String {
        return get("general.prefix")
    }

    /**
     * Get the current language code.
     */
    fun getCurrentLanguage(): String = currentLanguage

    /**
     * Check if a key exists in the language file.
     */
    fun hasKey(key: String): Boolean {
        return langConfig.contains(key) || fallbackConfig.contains(key)
    }

    // ============================================
    //           CONVENIENCE METHODS
    // ============================================

    // General messages
    fun noPermission() = get("general.no-permission")
    fun playerOnly() = get("general.player-only")
    fun playerNotFound(player: String) = get("general.player-not-found", mapOf("player" to player))
    fun invalidArguments() = get("general.invalid-arguments")
    fun unknownCommand() = get("general.unknown-command")
    fun errorOccurred() = get("general.error-occurred")
    fun mustBeNumber() = get("general.must-be-number")
    fun cooldownActive(time: String) = get("general.cooldown-active", mapOf("time" to time))

    // Admin commands
    fun adminInfoHeader(player: String) = get("admin.info.header", mapOf("player" to player))
    fun adminInfoLine(placeholders: Map<String, String>) = get("admin.info.line", placeholders)
    fun adminInfoNoNextRank() = get("admin.info.no-next-rank")
    fun adminInfoError() = get("admin.info.error")

    fun adminHelpHeader() = get("admin.help.header")
    fun adminHelpCommands() = getList("admin.help.commands")
    fun adminHelpFooter() = get("admin.help.footer")

    fun adminReloadSuccess(rankCount: Int) = get("admin.reload.success", mapOf("rank_count" to rankCount.toString()))
    fun adminReloadFail() = get("admin.reload.fail")

    fun adminSetrankSuccess(player: String, rank: String) = get("admin.setrank.success", mapOf("player" to player, "rank" to rank))
    fun adminSetrankSuccessTarget(rank: String) = get("admin.setrank.success-target", mapOf("rank" to rank))
    fun adminSetrankFail() = get("admin.setrank.fail")
    fun adminSetrankNotFound(rank: String) = get("admin.setrank.rank-not-found", mapOf("rank" to rank))

    fun adminResetSuccess(player: String) = get("admin.reset.success", mapOf("player" to player))
    fun adminResetSuccessTarget() = get("admin.reset.success-target")
    fun adminResetFail() = get("admin.reset.fail")

    // Rankup command
    fun rankupProcessing() = get("rankup.processing")
    fun rankupSuccess(rank: String, previousRank: String? = null): String {
        val placeholders = mutableMapOf("rank" to rank)
        if (previousRank != null) placeholders["previous_rank"] = previousRank
        return get("rankup.success.message", placeholders)
    }
    fun rankupFailNoMoney(cost: String, balance: String, needed: String) = get("rankup.fail-no-money", mapOf(
        "cost" to cost,
        "balance" to balance,
        "needed" to needed
    ))
    fun rankupFailMaxRank() = get("rankup.fail-max-rank")
    fun rankupError() = get("rankup.error")
    fun rankupCancelled() = get("rankup.cancelled")

    // Rankupmax command
    fun rankupmaxProcessing() = get("rankupmax.processing")
    fun rankupmaxSuccess(count: Int, rank: String, previousRank: String, totalSpent: String = "", balance: String = "") = get("rankupmax.success.message", mapOf(
        "count" to count.toString(),
        "rank" to rank,
        "previous_rank" to previousRank,
        "total_spent" to totalSpent,
        "balance" to balance
    ))
    fun rankupmaxPartial(count: Int, rank: String) = get("rankupmax.partial", mapOf(
        "count" to count.toString(),
        "rank" to rank
    ))
    fun rankupmaxFail() = get("rankupmax.fail")
    fun rankupmaxDisabled() = get("rankupmax.disabled")

    // Ranks command
    fun ranksOpening() = get("ranks.opening")
    fun ranksError() = get("ranks.error")

    // Ranktop command
    fun ranktopOpening() = get("ranktop.opening")
    fun ranktopError() = get("ranktop.error")
    fun ranktopNotRanked() = get("ranktop.not-ranked")

    // Broadcast messages
    fun broadcastRankup(player: String, rank: String, previousRank: String) = get("broadcast.rankup", mapOf(
        "player" to player,
        "rank" to rank,
        "previous_rank" to previousRank
    ))
    fun broadcastMilestone(player: String, rank: String, rankNumber: Int) = get("broadcast.milestone", mapOf(
        "player" to player,
        "rank" to rank,
        "rank_number" to rankNumber.toString()
    ))
    fun broadcastFirstRank(player: String, rank: String) = get("broadcast.first-rank", mapOf(
        "player" to player,
        "rank" to rank
    ))
    fun broadcastMaxRank(player: String, rank: String) = get("broadcast.max-rank", mapOf(
        "player" to player,
        "rank" to rank
    ))

    // GUI - Ranks
    fun guiRanksTitle(page: Int, maxPage: Int) = get("gui.ranks.title", mapOf(
        "page" to page.toString(),
        "max_page" to maxPage.toString()
    ))
    fun guiRanksPreviousPage() = get("gui.ranks.previous-page")
    fun guiRanksPreviousPageLore(page: Int) = getList("gui.ranks.previous-page-lore", mapOf("page" to page.toString()))
    fun guiRanksNextPage() = get("gui.ranks.next-page")
    fun guiRanksNextPageLore(page: Int) = getList("gui.ranks.next-page-lore", mapOf("page" to page.toString()))
    fun guiRanksClose() = get("gui.ranks.close")
    fun guiRanksCloseLore() = getList("gui.ranks.close-lore")
    fun guiRanksRefresh() = get("gui.ranks.refresh")
    fun guiRanksRefreshLore() = getList("gui.ranks.refresh-lore")
    fun guiRanksPlayerInfo() = get("gui.ranks.player-info")
    fun guiRanksPlayerInfoLore(placeholders: Map<String, String>) = getList("gui.ranks.player-info-lore", placeholders)
    fun guiRanksCompleted(placeholders: Map<String, String>) = getList("gui.ranks.item-lore.completed", placeholders)
    fun guiRanksCurrent(placeholders: Map<String, String>) = getList("gui.ranks.item-lore.current", placeholders)
    fun guiRanksAvailable(placeholders: Map<String, String>) = getList("gui.ranks.item-lore.available", placeholders)
    fun guiRanksLocked(placeholders: Map<String, String>) = getList("gui.ranks.item-lore.locked", placeholders)

    // GUI - Confirm
    fun guiConfirmTitle(rank: String) = get("gui.confirm.title", mapOf("rank" to rank))
    fun guiConfirmCurrentRank(rank: String) = get("gui.confirm.current-rank", mapOf("rank" to rank))
    fun guiConfirmCurrentRankLore(placeholders: Map<String, String>) = getList("gui.confirm.current-rank-lore", placeholders)
    fun guiConfirmArrowTitle() = get("gui.confirm.arrow-title")
    fun guiConfirmArrowLore(placeholders: Map<String, String>) = getList("gui.confirm.arrow-lore", placeholders)
    fun guiConfirmNextRank(rank: String) = get("gui.confirm.next-rank", mapOf("rank" to rank))
    fun guiConfirmNextRankLore(placeholders: Map<String, String>) = getList("gui.confirm.next-rank-lore", placeholders)
    fun guiConfirmCostTitle() = get("gui.confirm.cost-title")
    fun guiConfirmCostCanAfford(placeholders: Map<String, String>) = getList("gui.confirm.cost-can-afford", placeholders)
    fun guiConfirmCostCannotAfford(placeholders: Map<String, String>) = getList("gui.confirm.cost-cannot-afford", placeholders)
    fun guiConfirmButton() = get("gui.confirm.confirm-button")
    fun guiConfirmButtonLore(placeholders: Map<String, String>) = getList("gui.confirm.confirm-lore", placeholders)
    fun guiConfirmCannotAffordButton() = get("gui.confirm.cannot-afford-button")
    fun guiConfirmCannotAffordLore(placeholders: Map<String, String>) = getList("gui.confirm.cannot-afford-lore", placeholders)
    fun guiConfirmCancelButton() = get("gui.confirm.cancel-button")
    fun guiConfirmCancelLore() = getList("gui.confirm.cancel-lore")
    fun guiConfirmRewardsTitle() = get("gui.confirm.rewards-title")
    fun guiConfirmRewardsLore(rewards: List<String>) = getList("gui.confirm.rewards-lore", mapOf("rewards" to rewards.joinToString("\n")))
    fun guiConfirmErrorTitle() = get("gui.confirm.error-title")
    fun guiConfirmErrorLore() = getList("gui.confirm.error-lore")

    // GUI - Top
    fun guiTopTitle() = get("gui.top.title")
    fun guiTopPlayerName(position: Int, player: String) = get("gui.top.player-name", mapOf(
        "position" to position.toString(),
        "player" to player
    ))
    fun guiTopPlayerLore(placeholders: Map<String, String>) = getList("gui.top.player-lore", placeholders)
    fun guiTopEmptySlot(position: Int) = get("gui.top.empty-slot", mapOf("position" to position.toString()))
    fun guiTopEmptySlotLore() = getList("gui.top.empty-slot-lore")
    fun guiTopYourPosition() = get("gui.top.your-position")
    fun guiTopYourPositionLore(placeholders: Map<String, String>) = getList("gui.top.your-position-lore", placeholders)
    fun guiTopNotRanked() = get("gui.top.not-ranked")
    fun guiTopNotRankedLore() = getList("gui.top.not-ranked-lore")
    fun guiTopServerStats() = get("gui.top.server-stats")
    fun guiTopServerStatsLore(placeholders: Map<String, String>) = getList("gui.top.server-stats-lore", placeholders)
    fun guiTopRefresh() = get("gui.top.refresh")
    fun guiTopRefreshLore() = getList("gui.top.refresh-lore")

    // Format messages
    fun formatMoney(amount: String) = get("format.money", mapOf("amount" to amount))
    fun formatProgressBar(progress: String, percentage: String) = get("format.progress-bar", mapOf(
        "progress" to progress,
        "percentage" to percentage
    ))
    fun formatPosition(position: Int) = get("format.position.${when(position) {
        1 -> "first"
        2 -> "second"
        3 -> "third"
        else -> "default"
    }}", mapOf("position" to position.toString()))
    fun formatRankStatus(status: String) = get("format.rank-status.$status")
}
