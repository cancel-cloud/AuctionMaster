package cloud.coffeesystems.auctionmaster.util

import cloud.coffeesystems.auctionmaster.AuctionMaster

object TimeUtil {

    /** Format time remaining */
    fun formatTime(plugin: AuctionMaster, milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> plugin.messageManager.getRaw("time.days", days)
            hours > 0 -> plugin.messageManager.getRaw("time.hours", hours)
            minutes > 0 -> plugin.messageManager.getRaw("time.minutes", minutes)
            seconds > 0 -> plugin.messageManager.getRaw("time.seconds", seconds)
            else -> plugin.messageManager.getRaw("time.expired")
        }
    }

    /** Parse duration string (e.g. "2h", "30m", "1d") to milliseconds Returns null if invalid */
    fun parseDuration(input: String): Long? {
        if (input.isEmpty()) return null

        val regex = Regex("(\\d+)([dhms])")
        val match = regex.matchEntire(input.lowercase()) ?: return null

        val (valueStr, unit) = match.destructured
        val value = valueStr.toLongOrNull() ?: return null

        return when (unit) {
            "d" -> value * 24 * 60 * 60 * 1000
            "h" -> value * 60 * 60 * 1000
            "m" -> value * 60 * 1000
            "s" -> value * 1000
            else -> null
        }
    }
}
