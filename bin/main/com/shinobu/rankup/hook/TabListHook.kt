package com.shinobu.rankup.hook

import com.shinobu.rankup.data.RankData
import com.shinobu.rankup.util.ColorUtil
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.logging.Level

/**
 * Manages player tab list names with rank prefixes.
 */
class TabListHook(
    private val plugin: Plugin,
    private val format: String = "{rank_prefix}{player}"
) {

    private var enabled = false

    fun enable() {
        enabled = true
        plugin.logger.info("TabList integration enabled")
    }

    fun disable() {
        enabled = false
    }

    fun isEnabled(): Boolean = enabled

    /**
     * Update a player's tab list name with their rank.
     */
    fun updatePlayerTab(player: Player, rank: RankData?) {
        if (!enabled || rank == null) return

        try {
            val prefix = rank.prefix.ifBlank { "" }
            val displayName = format
                .replace("{rank_prefix}", prefix)
                .replace("{rank_display}", rank.displayName)
                .replace("{player}", player.name)
                .replace("{rank}", rank.id)

            val colorized = ColorUtil.colorize(displayName)
            player.setPlayerListName(colorized)
        } catch (e: Exception) {
            plugin.logger.log(Level.FINE, "Failed to update tab for ${player.name}", e)
        }
    }

    /**
     * Reset a player's tab list name to default.
     */
    fun resetPlayerTab(player: Player) {
        if (!enabled) return
        try {
            player.setPlayerListName(null) // Reset to default
        } catch (_: Exception) {}
    }

    /**
     * Update all online players' tab list names.
     */
    fun updateAll(playerRankProvider: (Player) -> RankData?) {
        if (!enabled) return
        for (player in plugin.server.onlinePlayers) {
            val rank = playerRankProvider(player)
            updatePlayerTab(player, rank)
        }
    }
}
