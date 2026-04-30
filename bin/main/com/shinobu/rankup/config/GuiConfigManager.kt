package com.shinobu.rankup.config

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.logging.Level

/**
 * Manages GUI configuration files for customizable menus.
 * Loads configuration from gui/ folder for each GUI type.
 */
class GuiConfigManager(private val plugin: JavaPlugin) {

    private lateinit var ranksConfig: YamlConfiguration
    private lateinit var confirmConfig: YamlConfiguration
    private lateinit var topConfig: YamlConfiguration

    private val guiFolder = File(plugin.dataFolder, "gui")

    companion object {
        // Default values
        const val DEFAULT_ROWS = 6
        const val DEFAULT_ITEMS_PER_PAGE = 28
    }

    /**
     * Initialize the GUI config manager.
     */
    fun initialize(): Result<Unit> {
        return try {
            // Create gui folder if it doesn't exist
            if (!guiFolder.exists()) {
                guiFolder.mkdirs()
            }

            // Save default configs
            saveDefaultConfigs()

            // Load all configs
            reload()

            plugin.logger.info("GUI configurations loaded successfully!")
            Result.success(Unit)
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to initialize GuiConfigManager", e)
            Result.failure(e)
        }
    }

    /**
     * Reload all GUI configurations.
     */
    fun reload(): Result<Unit> {
        return try {
            ranksConfig = loadConfig("ranks.yml")
            confirmConfig = loadConfig("confirm.yml")
            topConfig = loadConfig("top.yml")
            Result.success(Unit)
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to reload GUI configs", e)
            Result.failure(e)
        }
    }

    /**
     * Save default GUI config files.
     */
    private fun saveDefaultConfigs() {
        val configs = listOf("ranks.yml", "confirm.yml", "top.yml")

        for (configName in configs) {
            val file = File(guiFolder, configName)
            if (!file.exists()) {
                try {
                    plugin.saveResource("gui/$configName", false)
                    plugin.logger.info("Created GUI config: gui/$configName")
                } catch (e: Exception) {
                    plugin.logger.warning("Could not save GUI config: gui/$configName")
                }
            }
        }
    }

    /**
     * Load a specific config file.
     */
    private fun loadConfig(fileName: String): YamlConfiguration {
        val file = File(guiFolder, fileName)
        return if (file.exists()) {
            YamlConfiguration.loadConfiguration(file)
        } else {
            YamlConfiguration()
        }
    }

    // ============================================
    //              RANKS GUI CONFIG
    // ============================================

    fun ranksRows(): Int = ranksConfig.getInt("settings.rows", DEFAULT_ROWS)
    fun ranksItemsPerPage(): Int = ranksConfig.getInt("settings.items-per-page", DEFAULT_ITEMS_PER_PAGE)
    fun ranksFillEmpty(): Boolean = ranksConfig.getBoolean("settings.fill-empty", true)

    fun ranksSoundsEnabled(): Boolean = ranksConfig.getBoolean("settings.sounds.enabled", true)
    fun ranksSoundOpen(): Sound = parseSound(ranksConfig.getString("settings.sounds.open", "BLOCK_CHEST_OPEN"))
    fun ranksSoundClose(): Sound = parseSound(ranksConfig.getString("settings.sounds.close", "BLOCK_CHEST_CLOSE"))
    fun ranksSoundClick(): Sound = parseSound(ranksConfig.getString("settings.sounds.click", "UI_BUTTON_CLICK"))
    fun ranksSoundSuccess(): Sound = parseSound(ranksConfig.getString("settings.sounds.success", "ENTITY_PLAYER_LEVELUP"))
    fun ranksSoundError(): Sound = parseSound(ranksConfig.getString("settings.sounds.error", "ENTITY_VILLAGER_NO"))

    // Filler materials
    fun ranksBackgroundMaterial(): Material = parseMaterial(ranksConfig.getString("filler.background.material", "GRAY_STAINED_GLASS_PANE"))
    fun ranksBorderMaterial(): Material = parseMaterial(ranksConfig.getString("filler.border.material", "BLACK_STAINED_GLASS_PANE"))
    fun ranksBorderEnabled(): Boolean = ranksConfig.getBoolean("filler.border.enabled", true)
    fun ranksDecorationMaterial(): Material = parseMaterial(ranksConfig.getString("filler.decoration.material", "LIGHT_BLUE_STAINED_GLASS_PANE"))

    // Rank item materials by status
    fun ranksCompletedMaterial(): Material = parseMaterial(ranksConfig.getString("rank-items.completed.default-material", "LIME_CONCRETE"))
    fun ranksCompletedGlow(): Boolean = ranksConfig.getBoolean("rank-items.completed.glow", false)
    fun ranksCurrentMaterial(): Material = parseMaterial(ranksConfig.getString("rank-items.current.default-material", "GOLD_BLOCK"))
    fun ranksCurrentGlow(): Boolean = ranksConfig.getBoolean("rank-items.current.glow", true)
    fun ranksAvailableMaterial(): Material = parseMaterial(ranksConfig.getString("rank-items.available.default-material", "EMERALD_BLOCK"))
    fun ranksAvailableGlow(): Boolean = ranksConfig.getBoolean("rank-items.available.glow", true)
    fun ranksLockedMaterial(): Material = parseMaterial(ranksConfig.getString("rank-items.locked.default-material", "COAL_BLOCK"))
    fun ranksLockedGlow(): Boolean = ranksConfig.getBoolean("rank-items.locked.glow", false)

    // Navigation
    fun ranksPreviousPageEnabled(): Boolean = ranksConfig.getBoolean("navigation.previous-page.enabled", true)
    fun ranksPreviousPageSlot(): Int = ranksConfig.getInt("navigation.previous-page.slot", 45)
    fun ranksPreviousPageMaterial(): Material = parseMaterial(ranksConfig.getString("navigation.previous-page.material", "ARROW"))

    fun ranksNextPageEnabled(): Boolean = ranksConfig.getBoolean("navigation.next-page.enabled", true)
    fun ranksNextPageSlot(): Int = ranksConfig.getInt("navigation.next-page.slot", 53)
    fun ranksNextPageMaterial(): Material = parseMaterial(ranksConfig.getString("navigation.next-page.material", "ARROW"))

    fun ranksCloseEnabled(): Boolean = ranksConfig.getBoolean("navigation.close.enabled", true)
    fun ranksCloseSlot(): Int = ranksConfig.getInt("navigation.close.slot", 49)
    fun ranksCloseMaterial(): Material = parseMaterial(ranksConfig.getString("navigation.close.material", "BARRIER"))

    fun ranksPageInfoEnabled(): Boolean = ranksConfig.getBoolean("navigation.page-info.enabled", true)
    fun ranksPageInfoSlot(): Int = ranksConfig.getInt("navigation.page-info.slot", 4)
    fun ranksPageInfoMaterial(): Material = parseMaterial(ranksConfig.getString("navigation.page-info.material", "BOOK"))

    fun ranksRefreshEnabled(): Boolean = ranksConfig.getBoolean("navigation.refresh.enabled", true)
    fun ranksRefreshSlot(): Int = ranksConfig.getInt("navigation.refresh.slot", 47)
    fun ranksRefreshMaterial(): Material = parseMaterial(ranksConfig.getString("navigation.refresh.material", "SUNFLOWER"))

    // Player info
    fun ranksPlayerInfoEnabled(): Boolean = ranksConfig.getBoolean("player-info.enabled", true)
    fun ranksPlayerInfoSlot(): Int = ranksConfig.getInt("player-info.slot", 51)
    fun ranksPlayerInfoUseHead(): Boolean = ranksConfig.getBoolean("player-info.use-player-head", true)
    fun ranksPlayerInfoMaterial(): Material = parseMaterial(ranksConfig.getString("player-info.material", "PLAYER_HEAD"))

    // Rank slots
    fun ranksSlots(): List<Int> {
        val slots = mutableListOf<Int>()
        for (i in 1..4) {
            val row = ranksConfig.getIntegerList("rank-slots.row-$i")
            if (row.isNotEmpty()) {
                slots.addAll(row)
            }
        }
        return slots.ifEmpty { listOf(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43) }
    }

    // Decoration slots
    fun ranksTopRowSlots(): List<Int> = ranksConfig.getIntegerList("decorations.top-row.slots").ifEmpty { listOf(0, 1, 2, 3, 5, 6, 7, 8) }
    fun ranksTopRowEnabled(): Boolean = ranksConfig.getBoolean("decorations.top-row.enabled", true)
    fun ranksTopRowMaterial(): Material = parseMaterial(ranksConfig.getString("decorations.top-row.material", "BLACK_STAINED_GLASS_PANE"))

    fun ranksBottomRowSlots(): List<Int> = ranksConfig.getIntegerList("decorations.bottom-row.slots").ifEmpty { listOf(45, 46, 48, 50, 52, 53) }
    fun ranksBottomRowEnabled(): Boolean = ranksConfig.getBoolean("decorations.bottom-row.enabled", true)

    fun ranksSideColumnsEnabled(): Boolean = ranksConfig.getBoolean("decorations.side-columns.enabled", true)
    fun ranksSideLeftSlots(): List<Int> = ranksConfig.getIntegerList("decorations.side-columns.left-slots").ifEmpty { listOf(9, 18, 27, 36) }
    fun ranksSideRightSlots(): List<Int> = ranksConfig.getIntegerList("decorations.side-columns.right-slots").ifEmpty { listOf(17, 26, 35, 44) }
    fun ranksSideColumnsMaterial(): Material = parseMaterial(ranksConfig.getString("decorations.side-columns.material", "GRAY_STAINED_GLASS_PANE"))

    // ============================================
    //              CONFIRM GUI CONFIG
    // ============================================

    fun confirmRows(): Int = confirmConfig.getInt("settings.rows", 5)

    fun confirmSoundsEnabled(): Boolean = confirmConfig.getBoolean("settings.sounds.enabled", true)
    fun confirmSoundOpen(): Sound = parseSound(confirmConfig.getString("settings.sounds.open", "BLOCK_CHEST_OPEN"))
    fun confirmSoundConfirm(): Sound = parseSound(confirmConfig.getString("settings.sounds.confirm", "ENTITY_PLAYER_LEVELUP"))
    fun confirmSoundCancel(): Sound = parseSound(confirmConfig.getString("settings.sounds.cancel", "BLOCK_NOTE_BLOCK_BASS"))
    fun confirmSoundCannotAfford(): Sound = parseSound(confirmConfig.getString("settings.sounds.cannot-afford", "ENTITY_VILLAGER_NO"))

    fun confirmBackgroundMaterial(): Material = parseMaterial(confirmConfig.getString("filler.background.material", "GRAY_STAINED_GLASS_PANE"))
    fun confirmSeparatorMaterial(): Material = parseMaterial(confirmConfig.getString("filler.separator.material", "BLACK_STAINED_GLASS_PANE"))

    // Current rank
    fun confirmCurrentRankEnabled(): Boolean = confirmConfig.getBoolean("current-rank.enabled", true)
    fun confirmCurrentRankSlot(): Int = confirmConfig.getInt("current-rank.slot", 11)
    fun confirmCurrentRankMaterial(): Material = parseMaterial(confirmConfig.getString("current-rank.material", "BOOK"))
    fun confirmCurrentRankUseIcon(): Boolean = confirmConfig.getBoolean("current-rank.use-rank-icon", true)

    // Arrow
    fun confirmArrowEnabled(): Boolean = confirmConfig.getBoolean("arrow.enabled", true)
    fun confirmArrowSlot(): Int = confirmConfig.getInt("arrow.slot", 13)
    fun confirmArrowMaterial(): Material = parseMaterial(confirmConfig.getString("arrow.material", "ARROW"))
    fun confirmArrowGlow(): Boolean = confirmConfig.getBoolean("arrow.glow", true)

    // Next rank
    fun confirmNextRankEnabled(): Boolean = confirmConfig.getBoolean("next-rank.enabled", true)
    fun confirmNextRankSlot(): Int = confirmConfig.getInt("next-rank.slot", 15)
    fun confirmNextRankMaterial(): Material = parseMaterial(confirmConfig.getString("next-rank.material", "ENCHANTED_BOOK"))
    fun confirmNextRankUseIcon(): Boolean = confirmConfig.getBoolean("next-rank.use-rank-icon", true)
    fun confirmNextRankGlow(): Boolean = confirmConfig.getBoolean("next-rank.glow", true)

    // Cost info
    fun confirmCostInfoEnabled(): Boolean = confirmConfig.getBoolean("cost-info.enabled", true)
    fun confirmCostInfoSlot(): Int = confirmConfig.getInt("cost-info.slot", 22)
    fun confirmCostCanAffordMaterial(): Material = parseMaterial(confirmConfig.getString("cost-info.can-afford.material", "GOLD_INGOT"))
    fun confirmCostCannotAffordMaterial(): Material = parseMaterial(confirmConfig.getString("cost-info.cannot-afford.material", "IRON_INGOT"))

    // Rewards
    fun confirmRewardsEnabled(): Boolean = confirmConfig.getBoolean("rewards-preview.enabled", true)
    fun confirmRewardsSlot(): Int = confirmConfig.getInt("rewards-preview.slot", 31)
    fun confirmRewardsMaterial(): Material = parseMaterial(confirmConfig.getString("rewards-preview.material", "CHEST"))

    // Confirm button
    fun confirmButtonEnabled(): Boolean = confirmConfig.getBoolean("confirm-button.enabled", true)
    fun confirmButtonSlot(): Int = confirmConfig.getInt("confirm-button.slot", 29)
    fun confirmButtonCanAffordMaterial(): Material = parseMaterial(confirmConfig.getString("confirm-button.can-afford.material", "LIME_CONCRETE"))
    fun confirmButtonCannotAffordMaterial(): Material = parseMaterial(confirmConfig.getString("confirm-button.cannot-afford.material", "GRAY_CONCRETE"))

    // Cancel button
    fun confirmCancelEnabled(): Boolean = confirmConfig.getBoolean("cancel-button.enabled", true)
    fun confirmCancelSlot(): Int = confirmConfig.getInt("cancel-button.slot", 33)
    fun confirmCancelMaterial(): Material = parseMaterial(confirmConfig.getString("cancel-button.material", "RED_CONCRETE"))

    // Decorations
    fun confirmTopRowSlots(): List<Int> = confirmConfig.getIntegerList("decorations.top-row.slots").ifEmpty { (0..8).toList() }
    fun confirmBottomRowSlots(): List<Int> = confirmConfig.getIntegerList("decorations.bottom-row.slots").ifEmpty { (36..44).toList() }
    fun confirmLeftColumnSlots(): List<Int> = confirmConfig.getIntegerList("decorations.left-column.slots").ifEmpty { listOf(9, 18, 27) }
    fun confirmRightColumnSlots(): List<Int> = confirmConfig.getIntegerList("decorations.right-column.slots").ifEmpty { listOf(17, 26, 35) }
    fun confirmSeparatorSlots(): List<Int> = confirmConfig.getIntegerList("decorations.separators.slots").ifEmpty { listOf(10, 12, 14, 16, 19, 20, 21, 23, 24, 25, 28, 30, 32, 34) }
    fun confirmLeftColumnMaterial(): Material = parseMaterial(confirmConfig.getString("decorations.left-column.material", "BLUE_STAINED_GLASS_PANE"))
    fun confirmRightColumnMaterial(): Material = parseMaterial(confirmConfig.getString("decorations.right-column.material", "BLUE_STAINED_GLASS_PANE"))

    // ============================================
    //              TOP GUI CONFIG
    // ============================================

    fun topRows(): Int = topConfig.getInt("settings.rows", 6)
    fun topPlayersCount(): Int = topConfig.getInt("settings.top-players-count", 10)

    fun topSoundsEnabled(): Boolean = topConfig.getBoolean("settings.sounds.enabled", true)
    fun topSoundOpen(): Sound = parseSound(topConfig.getString("settings.sounds.open", "BLOCK_CHEST_OPEN"))
    fun topSoundClick(): Sound = parseSound(topConfig.getString("settings.sounds.click", "UI_BUTTON_CLICK"))

    fun topBackgroundMaterial(): Material = parseMaterial(topConfig.getString("filler.background.material", "GRAY_STAINED_GLASS_PANE"))
    fun topBorderMaterial(): Material = parseMaterial(topConfig.getString("filler.border.material", "BLACK_STAINED_GLASS_PANE"))

    // Header
    fun topHeaderEnabled(): Boolean = topConfig.getBoolean("header.enabled", true)
    fun topHeaderSlot(): Int = topConfig.getInt("header.slot", 4)
    fun topHeaderMaterial(): Material = parseMaterial(topConfig.getString("header.material", "GOLDEN_HELMET"))
    fun topHeaderGlow(): Boolean = topConfig.getBoolean("header.glow", true)

    // Podium (top 3)
    fun topFirstSlot(): Int = topConfig.getInt("podium.first.slot", 13)
    fun topFirstBorderSlots(): List<Int> = topConfig.getIntegerList("podium.first.border-slots").ifEmpty { listOf(3, 4, 5, 12, 14) }
    fun topFirstBorderMaterial(): Material = parseMaterial(topConfig.getString("podium.first.border-material", "GOLD_BLOCK"))

    fun topSecondSlot(): Int = topConfig.getInt("podium.second.slot", 11)
    fun topSecondBorderSlots(): List<Int> = topConfig.getIntegerList("podium.second.border-slots").ifEmpty { listOf(1, 2, 10) }
    fun topSecondBorderMaterial(): Material = parseMaterial(topConfig.getString("podium.second.border-material", "IRON_BLOCK"))

    fun topThirdSlot(): Int = topConfig.getInt("podium.third.slot", 15)
    fun topThirdBorderSlots(): List<Int> = topConfig.getIntegerList("podium.third.border-slots").ifEmpty { listOf(6, 7, 16) }
    fun topThirdBorderMaterial(): Material = parseMaterial(topConfig.getString("podium.third.border-material", "COPPER_BLOCK"))

    // Leaderboard slots (4-10)
    fun topLeaderboardSlots(): List<Int> = topConfig.getIntegerList("leaderboard.slots").ifEmpty { listOf(28, 29, 30, 31, 32, 33, 34) }
    fun topLeaderboardUseHeads(): Boolean = topConfig.getBoolean("leaderboard.use-player-heads", true)
    fun topLeaderboardFallbackMaterial(): Material = parseMaterial(topConfig.getString("leaderboard.fallback-material", "PLAYER_HEAD"))

    // Empty slot
    fun topEmptySlotMaterial(): Material = parseMaterial(topConfig.getString("empty-slot.material", "GRAY_STAINED_GLASS_PANE"))

    // Your position
    fun topYourPositionEnabled(): Boolean = topConfig.getBoolean("your-position.enabled", true)
    fun topYourPositionSlot(): Int = topConfig.getInt("your-position.slot", 49)
    fun topYourPositionUseHead(): Boolean = topConfig.getBoolean("your-position.use-player-head", true)

    // Server stats
    fun topServerStatsEnabled(): Boolean = topConfig.getBoolean("server-stats.enabled", true)
    fun topServerStatsSlot(): Int = topConfig.getInt("server-stats.slot", 45)
    fun topServerStatsMaterial(): Material = parseMaterial(topConfig.getString("server-stats.material", "BOOK"))

    // Info button
    fun topInfoButtonEnabled(): Boolean = topConfig.getBoolean("info-button.enabled", true)
    fun topInfoButtonSlot(): Int = topConfig.getInt("info-button.slot", 53)
    fun topInfoButtonMaterial(): Material = parseMaterial(topConfig.getString("info-button.material", "CLOCK"))

    // Decorations
    fun topPodiumBaseSlots(): List<Int> = topConfig.getIntegerList("decorations.podium-base.slots").ifEmpty { listOf(19, 20, 21, 22, 23, 24, 25) }
    fun topPodiumBaseMaterial(): Material = parseMaterial(topConfig.getString("decorations.podium-base.material", "QUARTZ_BLOCK"))

    // Position colors
    fun topFirstColor(): String = topConfig.getString("position-colors.first", "&6&l") ?: "&6&l"
    fun topSecondColor(): String = topConfig.getString("position-colors.second", "&7&l") ?: "&7&l"
    fun topThirdColor(): String = topConfig.getString("position-colors.third", "&c&l") ?: "&c&l"
    fun topDefaultColor(): String = topConfig.getString("position-colors.default", "&f") ?: "&f"

    // Click actions
    fun topShowChatInfo(): Boolean = topConfig.getBoolean("click-actions.show-chat-info", true)
    fun topPlayClickSound(): Boolean = topConfig.getBoolean("click-actions.play-sound", true)

    // ============================================
    //              UTILITY METHODS
    // ============================================

    /**
     * Parse a material name to Material enum.
     */
    private fun parseMaterial(name: String?): Material {
        if (name.isNullOrBlank()) return Material.STONE
        return try {
            Material.valueOf(name.uppercase())
        } catch (e: IllegalArgumentException) {
            plugin.logger.warning("Invalid material: $name, using STONE")
            Material.STONE
        }
    }

    /**
     * Parse a sound name to Sound enum.
     */
    private fun parseSound(name: String?): Sound {
        if (name.isNullOrBlank()) return Sound.UI_BUTTON_CLICK
        return try {
            Sound.valueOf(name.uppercase())
        } catch (e: IllegalArgumentException) {
            plugin.logger.warning("Invalid sound: $name, using UI_BUTTON_CLICK")
            Sound.UI_BUTTON_CLICK
        }
    }

    /**
     * Get raw config for advanced customization.
     */
    fun getRanksConfig(): YamlConfiguration = ranksConfig
    fun getConfirmConfig(): YamlConfiguration = confirmConfig
    fun getTopConfig(): YamlConfiguration = topConfig

    // ============================================
    //              LORE TEMPLATES
    // ============================================

    /**
     * Get the lore template for a given rank status.
     * @param status One of: "completed", "current", "available", "locked"
     * @return The template lines from gui/ranks.yml, or hardcoded fallback
     */
    fun getLoreTemplate(status: String): List<String> {
        val template = ranksConfig.getStringList("lore-templates.$status")
        return template.ifEmpty { getDefaultLoreTemplate(status) }
    }

    private fun getDefaultLoreTemplate(status: String): List<String> {
        val base = listOf(
            "&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬", "", "{description}",
            " {status}", " &7Cost: &6{cost}", " &7Order: &f#{order}", ""
        )
        val footer = listOf("&8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬", "{action}")
        return when (status) {
            "completed" -> base + footer
            "current", "available" -> base + listOf(
                " &7Progress:", " {progress_bar} {status_color}{progress}%", "",
                "{requirements}", "{rewards}"
            ) + footer
            "locked" -> base + listOf(" &c✗ Complete previous ranks first", "") + footer
            else -> base + footer
        }
    }
}
