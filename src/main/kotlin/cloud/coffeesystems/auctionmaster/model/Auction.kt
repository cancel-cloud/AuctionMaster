package cloud.coffeesystems.auctionmaster.model

import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * Represents an auction
 */
data class Auction(
    val id: Int,
    val sellerUuid: UUID,
    val sellerName: String,
    val item: ItemStack,
    val price: Double,
    val createdAt: Long,
    val expiresAt: Long,
    var status: AuctionStatus
) {
    /**
     * Check if the auction is expired
     */
    fun isExpired(): Boolean {
        return System.currentTimeMillis() > expiresAt && status == AuctionStatus.ACTIVE
    }

    /**
     * Get remaining time in milliseconds
     */
    fun getRemainingTime(): Long {
        val remaining = expiresAt - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }

    /**
     * Check if a UUID is the seller
     */
    fun isSeller(uuid: UUID): Boolean {
        return sellerUuid == uuid
    }
}

/**
 * Auction status enum
 */
enum class AuctionStatus {
    ACTIVE,     // Auction is live and can be purchased
    SOLD,       // Auction was purchased
    EXPIRED,    // Auction expired without being sold
    CANCELLED   // Auction was cancelled by seller or admin
}
