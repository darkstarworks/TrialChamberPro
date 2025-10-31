package io.github.darkstarworks.trialChamberPro.utils

import java.util.concurrent.TimeUnit

/**
 * Utility class for formatting messages and time values.
 */
object MessageUtil {

    /**
     * Formats a time duration in milliseconds to a human-readable string.
     *
     * @param milliseconds Time in milliseconds
     * @return Formatted time string (e.g., "2d 3h 45m")
     */
    fun formatTime(milliseconds: Long): String {
        if (milliseconds <= 0) return "0s"

        val days = TimeUnit.MILLISECONDS.toDays(milliseconds)
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds) % 24
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60

        val parts = mutableListOf<String>()
        if (days > 0) parts.add("${days}d")
        if (hours > 0) parts.add("${hours}h")
        if (minutes > 0) parts.add("${minutes}m")
        if (seconds > 0 && days == 0L) parts.add("${seconds}s") // Only show seconds if less than a day

        return if (parts.isEmpty()) "0s" else parts.joinToString(" ")
    }

    /**
     * Formats a time duration in seconds to a human-readable string.
     *
     * @param seconds Time in seconds
     * @return Formatted time string
     */
    fun formatTimeSeconds(seconds: Long): String {
        return formatTime(seconds * 1000)
    }

    /**
     * Formats a timestamp to a relative time string (e.g., "2 hours ago").
     *
     * @param timestamp The timestamp in milliseconds
     * @return Formatted relative time string
     */
    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        if (diff < 0) return "in the future"
        if (diff < 60000) return "just now"

        return "${formatTime(diff)} ago"
    }

    /**
     * Converts color codes in a string.
     *
     * @param message The message with & color codes
     * @return Message with § color codes
     */
    fun colorize(message: String): String {
        return message.replace('&', '§')
    }

    /**
     * Strips color codes from a string.
     *
     * @param message The message with color codes
     * @return Message without color codes
     */
    fun stripColor(message: String): String {
        return message.replace(Regex("[&§][0-9a-fk-or]"), "")
    }
}
