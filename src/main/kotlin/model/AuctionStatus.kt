package model

import kotlinx.serialization.Serializable

@Serializable
enum class AuctionStatus {
    ACTIVE,      // Currently accepting bids
    EXPIRED,     // Time ran out
    SOLD,        // Buy-now used or auction won
    CANCELLED,   // Cancelled by seller/admin
    CLAIMED      // Items/money claimed by parties
}
