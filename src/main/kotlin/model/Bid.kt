package model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Bid(
    @Serializable(with = util.UUIDSerializer::class)
    val id: UUID = UUID.randomUUID(),
    @Serializable(with = util.UUIDSerializer::class)
    val auctionId: UUID,
    @Serializable(with = util.UUIDSerializer::class)
    val bidderId: UUID,
    val bidderName: String,
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis()
)
