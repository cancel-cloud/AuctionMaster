package util

/**
 * Utility functions for time formatting
 */
object TimeUtil {
    /**
     * Format milliseconds into a human-readable time string
     */
    fun formatTimeRemaining(millis: Long): String {
        if (millis <= 0) return "Expired"

        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "${days}d ${hours % 24}h"
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }

    /**
     * Format duration in hours to milliseconds
     */
    fun hoursToMillis(hours: Int): Long {
        return hours * 3600000L
    }

    /**
     * Format duration in days to milliseconds
     */
    fun daysToMillis(days: Int): Long {
        return days * 86400000L
    }

    /**
     * Parse time string like "1h", "30m", "2d" to milliseconds
     */
    fun parseTimeString(timeStr: String): Long? {
        val regex = Regex("(\\d+)([smhd])")
        val match = regex.matchEntire(timeStr.lowercase()) ?: return null

        val amount = match.groupValues[1].toLongOrNull() ?: return null
        val unit = match.groupValues[2]

        return when (unit) {
            "s" -> amount * 1000
            "m" -> amount * 60000
            "h" -> amount * 3600000
            "d" -> amount * 86400000
            else -> null
        }
    }
}
