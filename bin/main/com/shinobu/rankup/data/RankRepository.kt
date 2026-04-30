package com.shinobu.rankup.data

import com.shinobu.rankup.BuildConfig
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

/**
 * Repository for rank configuration data.
 * Loads and manages rank definitions from the ranks.yml configuration file.
 *
 * Thread-safe: Uses ConcurrentHashMap for storage and immutable data classes.
 *
 * @property plugin The plugin instance for file access
 */
class RankRepository(private val plugin: JavaPlugin) {

    private val ranksById = ConcurrentHashMap<String, RankData>()
    private val ranksByOrder = ConcurrentHashMap<Int, RankData>()

    @Volatile
    private var defaultRankId: String = "default"

    @Volatile
    private var maxRankId: String = ""

    @Volatile
    private var isLoaded: Boolean = false

    /**
     * Load ranks from configuration file.
     * Creates default ranks.yml if it doesn't exist.
     *
     * @return true if ranks loaded successfully
     */
    fun load(): Boolean {
        return try {
            val ranksFile = File(plugin.dataFolder, "ranks.yml")

            if (!ranksFile.exists()) {
                saveDefaultRanksFile(ranksFile)
            }

            val config = YamlConfiguration.loadConfiguration(ranksFile)
            loadRanksFromConfig(config)

            plugin.logger.info("Loaded ${ranksById.size} ranks from configuration")
            isLoaded = true
            true
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to load ranks configuration", e)
            isLoaded = false
            false
        }
    }

    /**
     * Reload ranks from configuration file.
     *
     * @return true if reload was successful
     */
    fun reload(): Boolean {
        ranksById.clear()
        ranksByOrder.clear()
        return load()
    }

    /**
     * Load ranks from a configuration section.
     */
    private fun loadRanksFromConfig(config: YamlConfiguration) {
        val ranksSection = config.getConfigurationSection("ranks")
            ?: throw IllegalStateException("No 'ranks' section found in ranks.yml")

        defaultRankId = config.getString("default-rank", "default") ?: "default"

        // First, load all ranks to a temporary list
        val allRanks = mutableListOf<RankData>()
        ranksSection.getKeys(false).forEach { rankId ->
            val rankSection = ranksSection.getConfigurationSection(rankId) ?: return@forEach
            val rank = parseRankData(rankId, rankSection)
            allRanks.add(rank)
        }

        // Sort by order before applying FREE limitation
        val sortedRanks = allRanks.sortedBy { it.order }

        // Apply FREE version limitation
        val ranksToLoad = if (BuildConfig.isFreeVersion()) {
            val maxRanks = BuildConfig.FREE_MAX_RANKS
            if (sortedRanks.size > maxRanks) {
                plugin.logger.warning("=".repeat(60))
                plugin.logger.warning("FREE VERSION: Rank limit reached!")
                plugin.logger.warning("Found ${sortedRanks.size} ranks, but FREE version only supports $maxRanks.")
                plugin.logger.warning("Only the first $maxRanks ranks (by order) will be loaded.")
                plugin.logger.warning("Upgrade to PREMIUM for unlimited ranks.")
                plugin.logger.warning("=".repeat(60))
                sortedRanks.take(maxRanks)
            } else {
                sortedRanks
            }
        } else {
            sortedRanks
        }

        // Now add the filtered ranks to the maps
        ranksToLoad.forEach { rank ->
            ranksById[rank.id] = rank
            ranksByOrder[rank.order] = rank
        }

        // Determine max rank (highest order from loaded ranks)
        maxRankId = ranksByOrder.maxByOrNull { it.key }?.value?.id ?: defaultRankId

        // Validate default rank exists
        if (!ranksById.containsKey(defaultRankId)) {
            throw IllegalStateException("Default rank '$defaultRankId' not found in configuration")
        }
    }

    /**
     * Parse a rank from configuration section.
     * Supports full customization including per-rank title/subtitle/sound.
     *
     * Disable mechanism:
     * - Empty string ("") or hash prefix ("#...") = disabled
     * - null or omitted = use default from language file
     */
    private fun parseRankData(rankId: String, section: ConfigurationSection): RankData {
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

        return RankData(
            id = rankId,
            displayName = section.getString("display-name") ?: rankId,
            prefix = section.getString("prefix") ?: "",
            cost = section.getDouble("cost", 0.0),
            order = section.getInt("order", 0),
            permission = section.getString("permission"),
            icon = parseMaterial(section.getString("icon", "PAPER")),
            commands = section.getStringList("commands"),
            broadcastMessage = if (broadcastDisabled) null else rawBroadcast,
            requirements = parseRequirements(section.getConfigurationSection("requirements")),

            // NEW: Parse additional customization fields
            description = section.getStringList("description"),
            lore = section.getStringList("lore"),
            glow = section.getBoolean("glow", false),
            title = if (titleDisabled) null else rawTitle,
            subtitle = if (subtitleDisabled) null else rawSubtitle,
            sound = if (soundDisabled) null else rawSound,
            titleDisabled = titleDisabled,
            subtitleDisabled = subtitleDisabled,
            soundDisabled = soundDisabled,
            broadcastDisabled = broadcastDisabled
        )
    }

    /**
     * Parse rank requirements from configuration.
     */
    private fun parseRequirements(section: ConfigurationSection?): RankRequirements {
        if (section == null) return RankRequirements()

        return RankRequirements(
            minPlaytime = section.getLong("min-playtime", 0L),
            minKills = section.getInt("min-kills", 0),
            minLevel = section.getInt("min-level", 0),
            requiredPermissions = section.getStringList("permissions"),
            requiredItems = parseRequiredItems(section.getConfigurationSection("items"))
        )
    }

    /**
     * Parse required items from configuration.
     */
    private fun parseRequiredItems(section: ConfigurationSection?): Map<Material, Int> {
        if (section == null) return emptyMap()

        return section.getKeys(false).mapNotNull { key ->
            val material = parseMaterial(key)
            val amount = section.getInt(key, 1)
            if (material != Material.AIR) material to amount else null
        }.toMap()
    }

    /**
     * Parse material from string safely.
     */
    private fun parseMaterial(value: String?): Material {
        if (value.isNullOrBlank()) return Material.PAPER

        return try {
            Material.valueOf(value.uppercase().replace("-", "_").replace(" ", "_"))
        } catch (e: IllegalArgumentException) {
            plugin.logger.warning("Unknown material '$value', using PAPER as default")
            Material.PAPER
        }
    }

    /**
     * Get a rank by its ID.
     *
     * @param rankId The rank ID
     * @return The rank data or null if not found
     */
    fun getRank(rankId: String): RankData? = ranksById[rankId]

    /**
     * Get a rank by its order number.
     *
     * @param order The order number
     * @return The rank data or null if not found
     */
    fun getRankByOrder(order: Int): RankData? = ranksByOrder[order]

    /**
     * Get the next rank after the specified rank.
     *
     * @param currentRankId The current rank ID
     * @return The next rank or null if at max rank
     */
    fun getNextRank(currentRankId: String): RankData? {
        val currentRank = ranksById[currentRankId] ?: return null
        return ranksByOrder[currentRank.order + 1]
    }

    /**
     * Get the previous rank before the specified rank.
     *
     * @param currentRankId The current rank ID
     * @return The previous rank or null if at first rank
     */
    fun getPreviousRank(currentRankId: String): RankData? {
        val currentRank = ranksById[currentRankId] ?: return null
        return ranksByOrder[currentRank.order - 1]
    }

    /**
     * Get all ranks sorted by order.
     *
     * @return List of all ranks sorted by order (ascending)
     */
    fun getAllRanks(): List<RankData> = ranksByOrder.values.sortedBy { it.order }

    /**
     * Get all ranks up to and including the specified rank.
     *
     * @param rankId The rank ID (inclusive)
     * @return List of ranks from first to specified rank
     */
    fun getRanksUpTo(rankId: String): List<RankData> {
        val targetRank = ranksById[rankId] ?: return emptyList()
        return ranksByOrder.values
            .filter { it.order <= targetRank.order }
            .sortedBy { it.order }
    }

    /**
     * Get all ranks after the specified rank.
     *
     * @param rankId The rank ID (exclusive)
     * @return List of ranks after the specified rank
     */
    fun getRanksAfter(rankId: String): List<RankData> {
        val currentRank = ranksById[rankId] ?: return emptyList()
        return ranksByOrder.values
            .filter { it.order > currentRank.order }
            .sortedBy { it.order }
    }

    /**
     * Get the default starting rank.
     *
     * @return The default rank data
     */
    fun getDefaultRank(): RankData? = ranksById[defaultRankId]

    /**
     * Get the default rank ID.
     *
     * @return The default rank ID string
     */
    fun getDefaultRankId(): String = defaultRankId

    /**
     * Get the maximum rank.
     *
     * @return The highest rank data
     */
    fun getMaxRank(): RankData? = ranksById[maxRankId]

    /**
     * Get the maximum rank ID.
     *
     * @return The maximum rank ID string
     */
    fun getMaxRankId(): String = maxRankId

    /**
     * Check if a rank exists.
     *
     * @param rankId The rank ID to check
     * @return true if the rank exists
     */
    fun exists(rankId: String): Boolean = ranksById.containsKey(rankId)

    /**
     * Check if the specified rank is the maximum rank.
     *
     * @param rankId The rank ID to check
     * @return true if this is the max rank
     */
    fun isMaxRank(rankId: String): Boolean = rankId == maxRankId

    /**
     * Check if the specified rank is the default rank.
     *
     * @param rankId The rank ID to check
     * @return true if this is the default rank
     */
    fun isDefaultRank(rankId: String): Boolean = rankId == defaultRankId

    /**
     * Get total number of ranks.
     *
     * @return The number of configured ranks
     */
    fun count(): Int = ranksById.size

    /**
     * Check if ranks are loaded.
     *
     * @return true if ranks have been loaded
     */
    fun isLoaded(): Boolean = isLoaded

    /**
     * Calculate total cost to reach a rank from the default rank.
     *
     * @param targetRankId The target rank ID
     * @return Total cost or null if rank doesn't exist
     */
    fun getTotalCostToRank(targetRankId: String): Double? {
        val ranks = getRanksUpTo(targetRankId)
        if (ranks.isEmpty()) return null

        // Sum costs of all ranks except the first (default has no cost)
        return ranks.drop(1).sumOf { it.cost }
    }

    /**
     * Calculate remaining cost from current rank to target rank.
     *
     * @param currentRankId The current rank ID
     * @param targetRankId The target rank ID
     * @return Remaining cost or null if invalid ranks
     */
    fun getRemainingCost(currentRankId: String, targetRankId: String): Double? {
        val currentRank = ranksById[currentRankId] ?: return null
        val targetRank = ranksById[targetRankId] ?: return null

        if (targetRank.order <= currentRank.order) return 0.0

        return ranksByOrder.values
            .filter { it.order > currentRank.order && it.order <= targetRank.order }
            .sumOf { it.cost }
    }

    /**
     * Get progress percentage to next rank based on money.
     *
     * @param currentRankId The current rank ID
     * @param currentBalance The player's current balance
     * @return Progress percentage (0.0 to 1.0) or null if at max rank
     */
    fun getProgressToNextRank(currentRankId: String, currentBalance: Double): Double? {
        val nextRank = getNextRank(currentRankId) ?: return null
        if (nextRank.cost <= 0) return 1.0

        return (currentBalance / nextRank.cost).coerceIn(0.0, 1.0)
    }

    /**
     * Save the default ranks.yml file.
     */
    private fun saveDefaultRanksFile(file: File) {
        file.parentFile?.mkdirs()

        val defaultConfig = """
#  ____  _     _             _           ____             _
# / ___|| |__ (_)_ __   ___ | |__  _   _|  _ \ __ _ _ __ | | ___   _ _ __
# \___ \| '_ \| | '_ \ / _ \| '_ \| | | | |_) / _` | '_ \| |/ / | | | '_ \
#  ___) | | | | | | | | (_) | |_) | |_| |  _ < (_| | | | |   <| |_| | |_) |
# |____/|_| |_|_|_| |_|\___/|_.__/ \__,_|_| \_\__,_|_| |_|_|\_\\__,_| .__/
#                                                                   |_|
# Ranks Configuration File - 100% CUSTOMIZABLE
# The Journey of a Thousand Miles Begins with a Single Step

# The default starting rank for new players
default-rank: "default"

# ============================================
#              RANK DEFINITIONS
# ============================================
# Each rank requires:
#   - display-name: The colored name shown to players
#   - prefix: Chat prefix (supports color codes)
#   - cost: Money required to reach this rank
#   - order: Rank order (higher = better rank)
#   - icon: Material for GUI display
#   - commands: Commands executed on rankup (use {player} placeholder)
#
# Optional:
#   - permission: Permission node granted with this rank
#   - broadcast-message: Custom broadcast message
#   - requirements: Additional requirements (playtime, kills, etc.)
#
# ============================================
#      PER-RANK CUSTOMIZATION (NEW!)
# ============================================
# You can customize title/subtitle/sound/broadcast for EACH rank:
#
#   description:           # Custom lore lines shown in GUI
#     - "&7Line 1"
#     - "&8Line 2"
#   glow: true             # Enchantment glow effect in GUI
#   title: "&a&lCUSTOM"    # Custom title on rankup (null = default)
#   subtitle: "&7Custom"   # Custom subtitle on rankup (null = default)
#   sound: "ENTITY_..."    # Custom sound on rankup (null = default)
#
# TO DISABLE any effect for a specific rank, use:
#   title: ""              # Empty string = disabled
#   subtitle: "#hidden"    # Hash prefix = disabled
#   sound: ""              # No sound for this rank
#   broadcast-message: ""  # No broadcast for this rank
#
# ============================================

ranks:
  # ================== TIER 1: NOVICE ==================
  default:
    display-name: "<gray>Novice</gray>"
    prefix: "&7[Novice] "
    cost: 0
    order: 1
    icon: WOODEN_SWORD
    permission: "rank.novice"
    commands: []

  apprentice:
    display-name: "<white>Apprentice</white>"
    prefix: "&f[Apprentice] "
    cost: 1000
    order: 2
    icon: STONE_SWORD
    permission: "rank.apprentice"
    commands:
      - "give {player} diamond 1"

  warrior:
    display-name: "<yellow>Warrior</yellow>"
    prefix: "&e[Warrior] "
    cost: 2500
    order: 3
    icon: IRON_SWORD
    permission: "rank.warrior"
    commands:
      - "give {player} diamond 2"

  # ================== TIER 2: ADVANCED ==================
  knight:
    display-name: "<gold>Knight</gold>"
    prefix: "&6[Knight] "
    cost: 5000
    order: 4
    icon: GOLDEN_SWORD
    permission: "rank.knight"
    commands:
      - "give {player} diamond 3"
      - "give {player} golden_apple 1"

  guardian:
    display-name: "<aqua>Guardian</aqua>"
    prefix: "&b[Guardian] "
    cost: 10000
    order: 5
    icon: DIAMOND_SWORD
    permission: "rank.guardian"
    commands:
      - "give {player} diamond 5"

  champion:
    display-name: "<green>Champion</green>"
    prefix: "&a[Champion] "
    cost: 25000
    order: 6
    icon: DIAMOND_CHESTPLATE
    permission: "rank.champion"
    commands:
      - "give {player} diamond 10"
      - "give {player} golden_apple 3"

  # ================== TIER 3: ELITE ==================
  hero:
    display-name: "<blue>Hero</blue>"
    prefix: "&9[Hero] "
    cost: 50000
    order: 7
    icon: NETHERITE_SWORD
    permission: "rank.hero"
    commands:
      - "give {player} diamond_block 1"
      - "give {player} enchanted_golden_apple 1"

  legend:
    display-name: "<light_purple>Legend</light_purple>"
    prefix: "&d[Legend] "
    cost: 100000
    order: 8
    icon: NETHERITE_CHESTPLATE
    permission: "rank.legend"
    commands:
      - "give {player} diamond_block 3"
      - "give {player} enchanted_golden_apple 2"

  # ================== TIER 4: MYTHIC ==================
  mythic:
    display-name: "<gradient:#FFD700:#FFA500>Mythic</gradient>"
    prefix: "&6&l[Mythic] "
    cost: 250000
    order: 9
    icon: NETHER_STAR
    permission: "rank.mythic"
    commands:
      - "give {player} diamond_block 5"
      - "give {player} enchanted_golden_apple 3"
      - "give {player} totem_of_undying 1"

  immortal:
    display-name: "<gradient:#FF6B6B:#FFD700>Immortal</gradient>"
    prefix: "&c&l[Immortal] "
    cost: 500000
    order: 10
    icon: END_CRYSTAL
    permission: "rank.immortal"
    commands:
      - "give {player} diamond_block 10"
      - "give {player} enchanted_golden_apple 5"
      - "give {player} totem_of_undying 2"
    broadcast-message: "<gradient:#FF6B6B:#FFD700><bold>★★★</bold></gradient> <gold>{player}</gold> <yellow>has achieved <gradient:#FF6B6B:#FFD700><bold>IMMORTAL</bold></gradient> <yellow>status!</yellow> <gradient:#FF6B6B:#FFD700><bold>★★★</bold></gradient>"

  # ================== TIER 5: DIVINE ==================
  divine:
    display-name: "<gradient:#00CED1:#9400D3>Divine</gradient>"
    prefix: "&b&l[Divine] "
    cost: 1000000
    order: 11
    icon: BEACON
    permission: "rank.divine"
    commands:
      - "give {player} diamond_block 20"
      - "give {player} enchanted_golden_apple 10"
      - "give {player} totem_of_undying 3"
      - "give {player} beacon 1"
    broadcast-message: "<gradient:#00CED1:#9400D3><bold>✦✦✦</bold></gradient> <aqua>{player}</aqua> <white>has transcended to</white> <gradient:#00CED1:#9400D3><bold>DIVINE</bold></gradient><white>!</white> <gradient:#00CED1:#9400D3><bold>✦✦✦</bold></gradient>"
    requirements:
      min-playtime: 86400  # 24 hours in seconds
""".trimIndent()

        file.writeText(defaultConfig)
        plugin.logger.info("Created default ranks.yml")
    }
}
