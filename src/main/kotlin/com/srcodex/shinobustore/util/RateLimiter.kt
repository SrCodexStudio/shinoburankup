package com.srcodex.shinobustore.util

import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Rate limiter implementation using ConcurrentHashMap with periodic cleanup.
 * Provides protection against abuse and spam.
 */
class RateLimiter(
    private val plugin: JavaPlugin,
    private val maxRequestsPerMinute: Int = 10,
    private val cooldownMillis: Long = 3000,
    private val abuseThreshold: Int = 50,
    private val abuseBanDurationMinutes: Long = 15
) {

    /**
     * Tracks request counts per player with timestamp.
     */
    private val requestCounts = ConcurrentHashMap<UUID, RequestData>()

    /**
     * Tracks last action time per player per action type.
     */
    private val lastActions = ConcurrentHashMap<String, Long>()

    /**
     * Tracks banned players with expiry time.
     */
    private val bannedPlayers = ConcurrentHashMap<UUID, Long>()

    /**
     * Task ID for cleanup task.
     */
    private var cleanupTaskId: Int = -1

    /**
     * Data class to track request count with reset time.
     */
    private data class RequestData(
        val count: AtomicInteger,
        @Volatile var resetTime: Long
    )

    /**
     * Starts the periodic cleanup task.
     */
    fun startCleanupTask() {
        // Run cleanup every minute (1200 ticks)
        cleanupTaskId = plugin.server.scheduler.runTaskTimerAsynchronously(
            plugin,
            Runnable { cleanup() },
            1200L, // 1 minute delay
            1200L  // 1 minute period
        ).taskId
    }

    /**
     * Stops the cleanup task.
     */
    fun stopCleanupTask() {
        if (cleanupTaskId != -1) {
            plugin.server.scheduler.cancelTask(cleanupTaskId)
            cleanupTaskId = -1
        }
    }

    /**
     * Cleans up expired entries.
     */
    private fun cleanup() {
        try {
            val now = System.currentTimeMillis()

            // Clean request counts older than 1 minute
            requestCounts.entries.removeIf { (_, data) ->
                now > data.resetTime
            }

            // Clean last actions older than 1 hour
            val oneHourAgo = now - 3600000
            lastActions.entries.removeIf { (_, time) ->
                time < oneHourAgo
            }

            // Clean expired bans
            bannedPlayers.entries.removeIf { (_, expiry) ->
                now > expiry
            }
        } catch (e: Exception) {
            // Prevent cleanup failure from crashing the repeating task
        }
    }

    /**
     * Result of a rate limit check.
     */
    sealed class RateLimitResult {
        object Allowed : RateLimitResult()
        data class Cooldown(val remainingMillis: Long) : RateLimitResult()
        data class TooManyRequests(val remainingMillis: Long) : RateLimitResult()
        data class Banned(val remainingMillis: Long) : RateLimitResult()
    }

    /**
     * Checks if a player is allowed to perform an action.
     *
     * @param playerId The player's UUID
     * @param actionKey A unique key for the action type (e.g., "store_open", "purchase")
     * @param customCooldown Optional custom cooldown for this specific action
     * @return RateLimitResult indicating if the action is allowed
     */
    fun check(
        playerId: UUID,
        actionKey: String,
        customCooldown: Long = cooldownMillis,
        dryRun: Boolean = false
    ): RateLimitResult {
        val now = System.currentTimeMillis()

        // Check if player is banned
        bannedPlayers[playerId]?.let { banExpiry ->
            val remaining = banExpiry - now
            if (remaining > 0) {
                return RateLimitResult.Banned(remaining)
            } else {
                bannedPlayers.remove(playerId)
            }
        }

        // Get or create request data
        val requestData = requestCounts.compute(playerId) { _, existing ->
            if (existing == null || now > existing.resetTime) {
                // Create new or reset expired
                RequestData(AtomicInteger(0), now + 60000)
            } else {
                existing
            }
        }!!

        // Don't increment counter if dryRun (used by canPerform to peek without side effects)
        val currentCount = if (dryRun) {
            requestData.count.get()
        } else {
            requestData.count.incrementAndGet()
        }

        // Check for abuse
        if (currentCount > abuseThreshold) {
            if (!dryRun) {
                val banExpiry = now + (abuseBanDurationMinutes * 60 * 1000)
                bannedPlayers[playerId] = banExpiry
            }
            return RateLimitResult.Banned(abuseBanDurationMinutes * 60 * 1000)
        }

        // Check rate limit
        if (currentCount > maxRequestsPerMinute) {
            val remaining = requestData.resetTime - now
            return RateLimitResult.TooManyRequests(remaining.coerceAtLeast(1000))
        }

        // Check cooldown for specific action
        val cacheKey = "${playerId}_$actionKey"
        val lastAction = lastActions[cacheKey]

        if (lastAction != null) {
            val elapsed = now - lastAction
            if (elapsed < customCooldown) {
                return RateLimitResult.Cooldown(customCooldown - elapsed)
            }
        }

        // Update last action time only if not a dry run
        if (!dryRun) {
            lastActions[cacheKey] = now
        }

        return RateLimitResult.Allowed
    }

    /**
     * Checks if a player can perform an action (simple boolean check).
     */
    fun canPerform(playerId: UUID, actionKey: String, customCooldown: Long = cooldownMillis): Boolean {
        return check(playerId, actionKey, customCooldown, dryRun = true) is RateLimitResult.Allowed
    }

    /**
     * Resets rate limit data for a player.
     */
    fun reset(playerId: UUID) {
        requestCounts.remove(playerId)
        bannedPlayers.remove(playerId)
        // Also clear action-specific entries
        lastActions.keys.removeIf { it.startsWith("${playerId}_") }
    }

    /**
     * Manually bans a player temporarily.
     */
    fun ban(playerId: UUID, durationMinutes: Long = abuseBanDurationMinutes) {
        val banExpiry = System.currentTimeMillis() + (durationMinutes * 60 * 1000)
        bannedPlayers[playerId] = banExpiry
    }

    /**
     * Unbans a player.
     */
    fun unban(playerId: UUID) {
        bannedPlayers.remove(playerId)
    }

    /**
     * Checks if a player is currently banned.
     */
    fun isBanned(playerId: UUID): Boolean {
        val banExpiry = bannedPlayers[playerId] ?: return false
        if (System.currentTimeMillis() >= banExpiry) {
            bannedPlayers.remove(playerId)
            return false
        }
        return true
    }

    /**
     * Gets the remaining ban time for a player.
     */
    fun getBanRemaining(playerId: UUID): Long? {
        val banExpiry = bannedPlayers[playerId] ?: return null
        val remaining = banExpiry - System.currentTimeMillis()
        return if (remaining > 0) remaining else {
            bannedPlayers.remove(playerId)
            null
        }
    }

    /**
     * Clears all rate limit data.
     */
    fun clearAll() {
        requestCounts.clear()
        lastActions.clear()
        bannedPlayers.clear()
    }

    /**
     * Gets statistics about current rate limit state.
     */
    fun getStats(): RateLimiterStats {
        return RateLimiterStats(
            trackedPlayers = requestCounts.size,
            trackedActions = lastActions.size,
            bannedPlayers = bannedPlayers.size
        )
    }

    data class RateLimiterStats(
        val trackedPlayers: Int,
        val trackedActions: Int,
        val bannedPlayers: Int
    )

    companion object {
        // Action keys for consistent usage
        const val ACTION_STORE_OPEN = "store_open"
        const val ACTION_PURCHASE = "purchase"
        const val ACTION_CANCEL = "cancel"
        const val ACTION_COMMAND = "command"
    }
}
