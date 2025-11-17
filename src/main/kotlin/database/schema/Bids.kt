package database.schema

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column

object Bids : UUIDTable("bids") {
    // Reference to auction
    val auctionId: Column<String> = varchar("auction_id", 36).references(Auctions.id)

    // Bidder information
    val bidderId: Column<String> = varchar("bidder_id", 36)
    val bidderName: Column<String> = varchar("bidder_name", 16)

    // Bid details
    val amount: Column<Double> = double("amount")
    val timestamp: Column<Long> = long("timestamp")

    init {
        // Index for faster queries
        index(isUnique = false, auctionId, timestamp)
    }
}
