package com.shinobu.rankup.util

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple rate limiter to prevent command spam.
 * Thread-safe implementation using ConcurrentHashMap.
 *
 * @property cooldownMs Cooldown period in milliseconds
 * @property maxAttempts Maximum attempts before hard block (0 = no limit)
 */
class RateLimiter(
    private val cooldownMs: Long = 1000L,
    private val maxAttempts: Int = 0
) {
    private val lastAttempts = ConcurrentHashMap<UUID, Long>()
    private val attemptCounts = ConcurrentHashMap<UUID, Int>()

    companion object {
        /** Default instance for command rate limiting (1 second cooldown) */
        val COMMAND_LIMITER = RateLimiter(cooldownMs = 1000L, maxAttempts = 10)

        /** Strict limiter for economy operations (2 seconds cooldown) */
        val ECONOMY_LIMITER = RateLimiter(cooldownMs = 2000L, maxAttempts = 5)
    }

    /**
     * Check if the player is allowed to perform the action.
     *
     * @param uuid Player's UUID
     * @return true if allowed, false if rate limited
     */
    fun isAllowed(uuid: UUID): Boolean {
        val now = System.currentTimeMillis()
        val lastAttempt = lastAttempts[uuid] ?: 0L

        // Check cooldown
        if (now - lastAttempt < cooldownMs) {
            // Increment attempt count for potential hard block
            if (maxAttempts > 0) {
                val count = attemptCounts.compute(uuid) { _, v -> (v ?: 0) + 1 } ?: 1
                if (count > maxAttempts) {
                    return false // Hard blocked
                }
            }
            return false
        }

        // Reset attempt count on successful cooldown
        attemptCounts.remove(uuid)
        lastAttempts[uuid] = now
        return true
    }

    /**
     * Get remaining cooldown in milliseconds.
     *
     * @param uuid Player's UUID
     * @return Remaining cooldown in ms, 0 if none
     */
    fun getRemainingCooldown(uuid: UUID): Long {
        val now = System.currentTimeMillis()
        val lastAttempt = lastAttempts[uuid] ?: return 0L
        val remaining = cooldownMs - (now - lastAttempt)
        return remaining.coerceAtLeast(0L)
    }

    /**
     * Get remaining cooldown in seconds (rounded up).
     */
    fun getRemainingCooldownSeconds(uuid: UUID): Int {
        return ((getRemainingCooldown(uuid) + 999) / 1000).toInt()
    }

    /**
     * Check if player is hard blocked due to excessive attempts.
     */
    fun isHardBlocked(uuid: UUID): Boolean {
        if (maxAttempts <= 0) return false
        return (attemptCounts[uuid] ?: 0) > maxAttempts
    }

    /**
     * Reset rate limit for a player.
     */
    fun reset(uuid: UUID) {
        lastAttempts.remove(uuid)
        attemptCounts.remove(uuid)
    }

    /**
     * Clean up old entries to prevent memory leaks.
     * Call this periodically.
     */
    fun cleanup() {
        val now = System.currentTimeMillis()
        val expireTime = cooldownMs * 10 // Clean up entries older than 10x cooldown

        lastAttempts.entries.removeIf { (_, time) ->
            now - time > expireTime
        }

        // Also cleanup attempt counts for players not in lastAttempts
        attemptCounts.keys.removeIf { uuid ->
            !lastAttempts.containsKey(uuid)
        }
    }

    /**
     * Clear all data (call on plugin disable).
     */
    fun clear() {
        lastAttempts.clear()
        attemptCounts.clear()
    }
}
