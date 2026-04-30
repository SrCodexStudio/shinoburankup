package com.srcodex.shinobustore.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Utility object for time formatting and calculations.
 */
object TimeUtil {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * Formats milliseconds into a human-readable string.
     * Example: "1h 30m 45s"
     */
    fun formatMillis(millis: Long): String {
        if (millis <= 0) return "0s"

        val duration = Duration.ofMillis(millis)
        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60
        val seconds = duration.seconds % 60

        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            if (seconds > 0 || isEmpty()) append("${seconds}s")
        }.trim()
    }

    /**
     * Formats seconds into a human-readable string.
     */
    fun formatSeconds(seconds: Long): String {
        return formatMillis(seconds * 1000)
    }

    /**
     * Formats a timestamp to a readable date-time string.
     */
    fun formatTimestamp(timestamp: Long): String {
        val instant = Instant.ofEpochMilli(timestamp)
        val dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
        return dateTime.format(dateTimeFormatter)
    }

    /**
     * Calculates time remaining until expiration.
     * Returns null if already expired.
     */
    fun getTimeRemaining(expirationTime: Long): Long? {
        val remaining = expirationTime - System.currentTimeMillis()
        return if (remaining > 0) remaining else null
    }

    /**
     * Checks if a timestamp has expired.
     */
    fun isExpired(expirationTime: Long): Boolean {
        return System.currentTimeMillis() > expirationTime
    }

    /**
     * Gets the current timestamp.
     */
    fun now(): Long = System.currentTimeMillis()

    /**
     * Calculates expiration time from now.
     */
    fun expiresIn(millis: Long): Long = now() + millis

    /**
     * Parses a duration string (e.g., "1h", "30m", "1d") to milliseconds.
     */
    fun parseDuration(duration: String): Long {
        val regex = Regex("(\\d+)([smhd])")
        val match = regex.find(duration.lowercase()) ?: return 0

        val value = match.groupValues[1].toLongOrNull() ?: return 0
        val unit = match.groupValues[2]

        return when (unit) {
            "s" -> value * 1000
            "m" -> value * 60 * 1000
            "h" -> value * 60 * 60 * 1000
            "d" -> value * 24 * 60 * 60 * 1000
            else -> 0
        }
    }
}
