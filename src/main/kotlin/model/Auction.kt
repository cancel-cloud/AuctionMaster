package model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Auction(
    @Serializable(with = util.UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),

    // Seller information
    @Serializable(with = util.UUIDSerializer::class)
    val sellerId: UUID,
    val sellerName: String,

    // Item data
    val itemStack: SerializableItemStack,

    // Pricing
    val startPrice: Double,
    val currentBid: Double = startPrice,
    val buyNowPrice: Double? = null, // Optional instant buy

    // Bidding
    @Serializable(with = util.UUIDSerializer::class)
    val currentBidderId: UUID? = null,
    val currentBidderName: String? = null,
    val bidHistory: List<Bid> = emptyList(),

    // Timing
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long,
    val duration: Long, // Duration in milliseconds

    // Status
    val status: AuctionStatus = AuctionStatus.ACTIVE,

    // Metadata
    val category: AuctionCategory = AuctionCategory.OTHER,
    val claimed: Boolean = false
) {
    /**
     * Check if this auction has expired
     */
    fun isExpired(): Boolean {
        return System.currentTimeMillis() >= expiresAt
    }

    /**
     * Check if this auction is still active and accepting bids
     */
    fun isActive(): Boolean {
        return status == AuctionStatus.ACTIVE && !isExpired()
    }

    /**
     * Get time remaining in milliseconds
     */
    fun getTimeRemaining(): Long {
        return maxOf(0, expiresAt - System.currentTimeMillis())
    }

    /**
     * Check if a player can bid on this auction
     */
    fun canPlayerBid(playerId: UUID): Boolean {
        return isActive() && playerId != sellerId
    }

    /**
     * Get the minimum valid bid amount
     */
    fun getMinimumBid(minIncrement: Double): Double {
        return currentBid + minIncrement
    }

    /**
     * Create a copy with a new bid
     */
    fun withBid(bid: Bid): Auction {
        return copy(
            currentBid = bid.amount,
            currentBidderId = bid.bidderId,
            currentBidderName = bid.bidderName,
            bidHistory = bidHistory + bid
        )
    }

    /**
     * Create a copy with updated status
     */
    fun withStatus(newStatus: AuctionStatus): Auction {
        return copy(status = newStatus)
    }

    /**
     * Create a copy marked as claimed
     */
    fun withClaimed(): Auction {
        return copy(claimed = true, status = AuctionStatus.CLAIMED)
    }
}
