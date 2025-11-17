package config

import org.bukkit.Material

/**
 * Configuration for the auction system
 */
data class AuctionConfig(
    // Limits
    val maxActivePerPlayer: Int = 5,
    val maxDurationHours: Int = 168, // 1 week
    val minDurationHours: Int = 1,

    // Pricing
    val minStartPrice: Double = 1.0,
    val maxStartPrice: Double = 1000000.0,
    val minBidIncrement: Double = 5.0,
    val listingFee: Double = 10.0,
    val listingFeePercentage: Double = 0.05, // 5% of start price

    // Buy Now
    val allowBuyNow: Boolean = true,
    val requireBuyNow: Boolean = false,

    // Cancellation
    val allowSellerCancel: Boolean = true,
    val cancelFeePercentage: Double = 0.10, // 10% of current bid

    // Expiration
    val autoCleanupDays: Int = 30,

    // Items
    val blacklistedMaterials: Set<Material> = setOf(
        Material.BARRIER,
        Material.COMMAND_BLOCK,
        Material.CHAIN_COMMAND_BLOCK,
        Material.REPEATING_COMMAND_BLOCK,
        Material.BEDROCK
    ),

    // Database
    val backupIntervalHours: Int = 1
) {
    companion object {
        /**
         * Default configuration
         */
        fun default(): AuctionConfig = AuctionConfig()

        /**
         * Load configuration from file (to be implemented with Sparkle config)
         */
        fun load(): AuctionConfig {
            // TODO: Load from config file
            return default()
        }
    }

    /**
     * Validate a material is allowed for auction
     */
    fun isMaterialAllowed(material: Material): Boolean {
        return material !in blacklistedMaterials && material != Material.AIR
    }

    /**
     * Calculate listing fee for a given start price
     */
    fun calculateListingFee(startPrice: Double): Double {
        val percentageFee = startPrice * listingFeePercentage
        return maxOf(listingFee, percentageFee)
    }

    /**
     * Calculate cancellation fee
     */
    fun calculateCancellationFee(currentBid: Double): Double {
        return currentBid * cancelFeePercentage
    }

    /**
     * Get max duration in milliseconds
     */
    fun getMaxDurationMillis(): Long {
        return maxDurationHours * 3600000L
    }

    /**
     * Get min duration in milliseconds
     */
    fun getMinDurationMillis(): Long {
        return minDurationHours * 3600000L
    }

    /**
     * Get cleanup threshold in milliseconds
     */
    fun getCleanupThresholdMillis(): Long {
        return autoCleanupDays * 86400000L
    }
}
