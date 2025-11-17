package model

import java.util.UUID

/**
 * Filter criteria for searching auctions
 */
data class AuctionFilter(
    val category: AuctionCategory? = null,
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val sellerId: UUID? = null,
    val bidderId: UUID? = null,
    val status: AuctionStatus? = null,
    val searchText: String? = null, // Search in item name
    val sortOrder: SortOrder = SortOrder.NEWEST,
    val limit: Int = 45, // Default to 45 items (5 rows in GUI)
    val offset: Int = 0
) {
    companion object {
        /**
         * Create a filter for a specific seller's auctions
         */
        fun forSeller(sellerId: UUID): AuctionFilter {
            return AuctionFilter(sellerId = sellerId)
        }

        /**
         * Create a filter for auctions a player has bid on
         */
        fun forBidder(bidderId: UUID): AuctionFilter {
            return AuctionFilter(bidderId = bidderId)
        }

        /**
         * Create a filter for active auctions only
         */
        fun activeOnly(): AuctionFilter {
            return AuctionFilter(status = AuctionStatus.ACTIVE)
        }
    }
}
