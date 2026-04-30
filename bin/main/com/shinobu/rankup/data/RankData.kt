package com.shinobu.rankup.data

import org.bukkit.Material

/**
 * Represents a rank configuration.
 * Immutable data class for thread-safe access.
 *
 * Customization options:
 * - title/subtitle/sound: null = use default from language file
 * - titleDisabled/subtitleDisabled/soundDisabled/broadcastDisabled: true = skip effect entirely
 */
data class RankData(
    val id: String,
    val displayName: String,
    val prefix: String,
    val cost: Double,
    val order: Int,
    val permission: String? = null,
    val icon: Material = Material.PAPER,
    val commands: List<String> = emptyList(),
    val broadcastMessage: String? = null,
    val requirements: RankRequirements = RankRequirements(),

    // NEW: Per-rank customization fields
    /** Custom description lines shown in GUI lore */
    val description: List<String> = emptyList(),
    /** Custom lore template override for GUI (empty = use default status template) */
    val lore: List<String> = emptyList(),
    /** Whether to apply enchantment glow in GUI */
    val glow: Boolean = false,
    /** Custom title text (null = use default from lang file) */
    val title: String? = null,
    /** Custom subtitle text (null = use default from lang file) */
    val subtitle: String? = null,
    /** Custom sound name (null = use default from config) */
    val sound: String? = null,
    /** If true, title is disabled for this rank */
    val titleDisabled: Boolean = false,
    /** If true, subtitle is disabled for this rank */
    val subtitleDisabled: Boolean = false,
    /** If true, sound is disabled for this rank */
    val soundDisabled: Boolean = false,
    /** If true, broadcast is disabled for this rank */
    val broadcastDisabled: Boolean = false
) : Comparable<RankData> {

    override fun compareTo(other: RankData): Int = order.compareTo(other.order)

    /**
     * Check if this rank is higher than another rank.
     */
    fun isHigherThan(other: RankData): Boolean = order > other.order

    /**
     * Check if this rank is lower than another rank.
     */
    fun isLowerThan(other: RankData): Boolean = order < other.order

    /**
     * Check if this is the maximum rank (no next rank available).
     */
    fun isMaxRank(allRanks: List<RankData>): Boolean {
        return allRanks.none { it.order > this.order }
    }
}

/**
 * Additional requirements for ranking up.
 */
data class RankRequirements(
    val minPlaytime: Long = 0L, // In seconds
    val minKills: Int = 0,
    val minLevel: Int = 0,
    val requiredPermissions: List<String> = emptyList(),
    val requiredItems: Map<Material, Int> = emptyMap()
)
