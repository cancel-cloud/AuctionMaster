package cloud.coffeesystems.auctionmaster.model

import cloud.coffeesystems.auctionmaster.AuctionMaster

/** Represents predefined auction duration options with associated fees */
enum class AuctionDuration(
        val displayName: String,
        val configKey: String,
        val durationMillis: Long
) {
    FOUR_HOURS("4 Hours", "4h", 4 * 60 * 60 * 1000L),
    SIX_HOURS("6 Hours", "6h", 6 * 60 * 60 * 1000L),
    EIGHT_HOURS("8 Hours", "8h", 8 * 60 * 60 * 1000L),
    TWELVE_HOURS("12 Hours", "12h", 12 * 60 * 60 * 1000L),
    ONE_DAY("1 Day", "1d", 24 * 60 * 60 * 1000L),
    TWO_DAYS("2 Days", "2d", 2 * 24 * 60 * 60 * 1000L),
    SEVEN_DAYS("7 Days", "7d", 7 * 24 * 60 * 60 * 1000L);

    /**
     * Get the base fee for this duration from config Returns 0.0 if fees are disabled or not
     * configured
     */
    fun getBaseFee(plugin: AuctionMaster): Double {
        // Check if fees section exists first
        if (!plugin.config.contains("auction.creation-fees.enabled")) {
            return 0.0
        }
        if (!plugin.config.getBoolean("auction.creation-fees.enabled", false)) {
            return 0.0
        }
        return plugin.config.getDouble(
                "auction.creation-fees.durations.$configKey.base-fee",
                0.0
        )
    }

    /**
     * Get the percentage fee for this duration from config Returns 0.0 if fees are disabled or
     * not configured
     */
    fun getFeePercentage(plugin: AuctionMaster): Double {
        // Check if fees section exists first
        if (!plugin.config.contains("auction.creation-fees.enabled")) {
            return 0.0
        }
        if (!plugin.config.getBoolean("auction.creation-fees.enabled", false)) {
            return 0.0
        }
        return plugin.config.getDouble(
                "auction.creation-fees.durations.$configKey.fee-percentage",
                0.0
        )
    }

    /**
     * Calculate the total fee for creating an auction with this duration
     *
     * @param plugin The plugin instance for config access
     * @param price The auction price
     * @return The total fee (base fee + percentage of price)
     */
    fun calculateFee(plugin: AuctionMaster, price: Double): Double {
        val baseFee = getBaseFee(plugin)
        val feePercentage = getFeePercentage(plugin)
        return baseFee + (price * (feePercentage / 100.0))
    }

    /**
     * Check if this duration is free (no fees)
     *
     * @param plugin The plugin instance for config access
     * @return True if both base fee and percentage are 0
     */
    fun isFree(plugin: AuctionMaster): Boolean {
        return getBaseFee(plugin) == 0.0 && getFeePercentage(plugin) == 0.0
    }

    companion object {
        /**
         * Get duration by config key
         *
         * @param key The config key (e.g., "4h", "1d")
         * @return The matching AuctionDuration or null if not found
         */
        fun fromConfigKey(key: String): AuctionDuration? {
            return entries.find { it.configKey.equals(key, ignoreCase = true) }
        }

        /** Get all durations as a list */
        fun all(): List<AuctionDuration> {
            return entries
        }
    }
}
