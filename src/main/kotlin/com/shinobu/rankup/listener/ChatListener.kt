package com.shinobu.rankup.listener

import com.shinobu.rankup.api.ShinobuRankupAPI
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent

/**
 * Chat listener placeholder - disabled by default.
 *
 * Rank prefixes should be handled by external chat plugins (AlonsoChat, EssentialsChat, etc.)
 * using PlaceholderAPI placeholders:
 * - %shinobu_rank% - Rank display name
 * - %shinobu_rank_prefix% - Rank prefix
 * - %shinobu_rank_display% - Rank display name (alias)
 */
@Suppress("DEPRECATION", "UNUSED_PARAMETER")
class ChatListener(
    private val apiProvider: () -> ShinobuRankupAPI?
) : Listener {

    /**
     * Chat listener - disabled.
     * Rank prefixes should be handled by external chat plugins using PlaceholderAPI.
     * Available placeholders: %shinobu_rank%, %shinobu_rank_prefix%, %shinobu_rank_display%
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        // Disabled - let external chat plugins handle formatting via PAPI
    }
}
