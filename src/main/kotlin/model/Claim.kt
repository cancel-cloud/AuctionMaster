package model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Claim(
    @Serializable(with = util.UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    @Serializable(with = util.UUIDSerializer::class)
    val playerId: UUID,
    val playerName: String,
    val items: List<SerializableItemStack> = emptyList(),
    val money: Double = 0.0,
    val reason: ClaimReason,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
enum class ClaimReason {
    AUCTION_WON,      // Won an auction
    OUTBID_REFUND,    // Got outbid, refund the bid
    AUCTION_SOLD,     // Auction sold, get money
    AUCTION_UNSOLD,   // Auction expired without bids, return item
    AUCTION_CANCELLED // Auction cancelled, return item/money
}
