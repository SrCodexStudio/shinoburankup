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
 */
class RequirementChecker {

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

        // Check kills
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
                requirements.requiredItems.isNotEmpty()
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
}
