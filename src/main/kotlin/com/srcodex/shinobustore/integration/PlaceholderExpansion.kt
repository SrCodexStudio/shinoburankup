package com.srcodex.shinobustore.integration

import com.srcodex.shinobustore.ShinobuStore
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.entity.Player
import java.util.Locale

/**
 * PlaceholderAPI expansion for ShinobuStore.
 *
 * This class is only instantiated when PlaceholderAPI is confirmed present
 * on the server. Registration is handled in [ShinobuStore.onEnable] with
 * a soft-dependency check:
 * ```kotlin
 * if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
 *     ShinobuStorePlaceholders(this).register()
 * }
 * ```
 *
 * Available placeholders:
 * - `%shinobustore_pending_count%`   - Number of active pending transactions for the player
 * - `%shinobustore_has_pending%`     - "true" if player has active pending transactions
 * - `%shinobustore_total_purchases%` - Total number of completed purchases by the player
 * - `%shinobustore_total_spent%`     - Total USD amount spent by the player (formatted to 2 decimals)
 * - `%shinobustore_store_configured%`- "true" if PayPal credentials are configured
 * - `%shinobustore_total_revenue%`   - Total store revenue across all players (admin use)
 * - `%shinobustore_today_sales%`     - Number of sales completed today
 * - `%shinobustore_today_revenue%`   - Revenue generated today (formatted to 2 decimals)
 */
class ShinobuStorePlaceholders(
    private val plugin: ShinobuStore
) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "shinobustore"

    override fun getAuthor(): String = "SrCodexStudio"

    override fun getVersion(): String = plugin.description.version

    /**
     * Keeps the expansion registered across PlaceholderAPI reloads
     * since this expansion is owned by the plugin, not a separate jar.
     */
    override fun persist(): Boolean = true

    override fun onPlaceholderRequest(player: Player?, params: String): String? {
        return when (params.lowercase(Locale.ROOT)) {
            "pending_count" -> {
                if (player == null) return "0"
                plugin.transactionManager.getPendingCount(player.uniqueId).toString()
            }

            "has_pending" -> {
                if (player == null) return "false"
                plugin.transactionManager.hasPending(player.uniqueId).toString()
            }

            "total_purchases" -> {
                if (player == null) return "0"
                plugin.transactionManager.getHistoryForPlayer(player.uniqueId).size.toString()
            }

            "total_spent" -> {
                if (player == null) return "0.00"
                val total = plugin.transactionManager
                    .getHistoryForPlayer(player.uniqueId)
                    .sumOf { it.total }
                String.format(Locale.US, "%.2f", total)
            }

            "store_configured" -> {
                plugin.configManager.isPayPalConfigured().toString()
            }

            "total_revenue" -> {
                val stats = plugin.transactionManager.getStatistics()
                String.format(Locale.US, "%.2f", stats.totalRevenue)
            }

            "today_sales" -> {
                plugin.transactionManager.getStatistics().todaySales.toString()
            }

            "today_revenue" -> {
                val stats = plugin.transactionManager.getStatistics()
                String.format(Locale.US, "%.2f", stats.todayRevenue)
            }

            else -> null
        }
    }
}
