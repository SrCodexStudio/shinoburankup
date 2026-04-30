package com.shinobu.rankup.service

import com.shinobu.rankup.data.RankRequirements
import org.bukkit.Material
import org.bukkit.Statistic
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Result of a single requirement check.
 */
data class RequirementFailure(
    val type: String,
    val required: String,
    val current: String
)

/**
 * Checks if a player meets rank requirements.
 * Thread-safe: all checks are read-only against the player's state.
 *
 * @param papiResolver Optional PlaceholderAPI resolver function. When provided,
 *        enables placeholder-based requirements (e.g. %statistic_mob_kills% >= 100).
 */
class RequirementChecker(
    private val papiResolver: ((Player, String) -> String)? = null
) {

    /**
     * Check all requirements for a player.
     * Returns empty list if all requirements are met.
     */
    fun check(player: Player, requirements: RankRequirements): List<RequirementFailure> {
        val failures = mutableListOf<RequirementFailure>()

        // Check playtime (PLAY_ONE_MINUTE statistic is in ticks, divide by 20 for seconds)
        if (requirements.minPlaytime > 0) {
            val playtimeSeconds = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L
            if (playtimeSeconds < requirements.minPlaytime) {
                failures.add(RequirementFailure(
                    type = "playtime",
                    required = formatTime(requirements.minPlaytime),
                    current = formatTime(playtimeSeconds)
                ))
            }
        }

        // Check total kills (PvP + PvE combined)
        if (requirements.minKills > 0) {
            val kills = player.getStatistic(Statistic.PLAYER_KILLS) + player.getStatistic(Statistic.MOB_KILLS)
            if (kills < requirements.minKills) {
                failures.add(RequirementFailure(
                    type = "kills",
                    required = requirements.minKills.toString(),
                    current = kills.toString()
                ))
            }
        }

        // Check PvP kills only
        if (requirements.minPlayerKills > 0) {
            val pvpKills = player.getStatistic(Statistic.PLAYER_KILLS)
            if (pvpKills < requirements.minPlayerKills) {
                failures.add(RequirementFailure(
                    type = "player_kills",
                    required = requirements.minPlayerKills.toString(),
                    current = pvpKills.toString()
                ))
            }
        }

        // Check PvE kills only
        if (requirements.minMobKills > 0) {
            val mobKills = player.getStatistic(Statistic.MOB_KILLS)
            if (mobKills < requirements.minMobKills) {
                failures.add(RequirementFailure(
                    type = "mob_kills",
                    required = requirements.minMobKills.toString(),
                    current = mobKills.toString()
                ))
            }
        }

        // Check level
        if (requirements.minLevel > 0) {
            if (player.level < requirements.minLevel) {
                failures.add(RequirementFailure(
                    type = "level",
                    required = requirements.minLevel.toString(),
                    current = player.level.toString()
                ))
            }
        }

        // Check permissions
        for (permission in requirements.requiredPermissions) {
            if (!player.hasPermission(permission)) {
                failures.add(RequirementFailure(
                    type = "permission",
                    required = permission,
                    current = "not granted"
                ))
            }
        }

        // Check items
        for ((material, amount) in requirements.requiredItems) {
            val count = countItems(player, material)
            if (count < amount) {
                failures.add(RequirementFailure(
                    type = "items",
                    required = "${amount}x ${material.name.lowercase().replace('_', ' ')}",
                    current = count.toString()
                ))
            }
        }

        // Check PlaceholderAPI requirements
        if (requirements.placeholders.isNotEmpty() && papiResolver != null) {
            for ((placeholder, expression) in requirements.placeholders) {
                val resolved = papiResolver.invoke(player, placeholder)
                val parsed = parseComparison(expression)
                if (parsed != null) {
                    val (operator, expected) = parsed
                    val actual = resolved.toDoubleOrNull()
                    if (actual == null || !evaluateComparison(actual, operator, expected)) {
                        failures.add(RequirementFailure(
                            type = "placeholder",
                            required = "$placeholder $expression",
                            current = resolved
                        ))
                    }
                }
            }
        }

        return failures
    }

    /**
     * Check if all requirements are met (shortcut).
     */
    fun isMet(player: Player, requirements: RankRequirements): Boolean {
        return check(player, requirements).isEmpty()
    }

    /**
     * Check if requirements are trivial (all defaults/zeros).
     */
    fun hasRequirements(requirements: RankRequirements): Boolean {
        return requirements.minPlaytime > 0 ||
                requirements.minKills > 0 ||
                requirements.minLevel > 0 ||
                requirements.requiredPermissions.isNotEmpty() ||
                requirements.requiredItems.isNotEmpty() ||
                requirements.minPlayerKills > 0 ||
                requirements.minMobKills > 0 ||
                requirements.placeholders.isNotEmpty()
    }

    private fun countItems(player: Player, material: Material): Int {
        return player.inventory.contents
            .filterNotNull()
            .filter { it.type == material }
            .sumOf { it.amount }
    }

    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    /**
     * Parse a comparison expression like ">=100", ">50", "==10", etc.
     * Returns the operator and numeric value, or null if unparseable.
     */
    private fun parseComparison(expression: String): Pair<String, Double>? {
        val pattern = Regex("""^\s*(>=|<=|!=|==|>|<)\s*(-?\d+\.?\d*)\s*$""")
        val match = pattern.matchEntire(expression.trim()) ?: return null
        return match.groupValues[1] to match.groupValues[2].toDouble()
    }

    /**
     * Evaluate a numeric comparison.
     */
    private fun evaluateComparison(actual: Double, operator: String, expected: Double): Boolean {
        return when (operator) {
            ">=" -> actual >= expected
            ">" -> actual > expected
            "<=" -> actual <= expected
            "<" -> actual < expected
            "==" -> actual == expected
            "!=" -> actual != expected
            else -> false
        }
    }
}
