package database.schema

import model.AuctionCategory
import model.AuctionStatus
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column

object Auctions : UUIDTable("auctions") {
    // Seller information
    val sellerId: Column<String> = varchar("seller_id", 36)
    val sellerName: Column<String> = varchar("seller_name", 16)

    // Item data (JSON serialized)
    val itemData: Column<String> = text("item_data")

    // Pricing
    val startPrice: Column<Double> = double("start_price")
    val currentBid: Column<Double> = double("current_bid")
    val buyNowPrice: Column<Double?> = double("buy_now_price").nullable()

    // Current bidder
    val currentBidderId: Column<String?> = varchar("current_bidder_id", 36).nullable()
    val currentBidderName: Column<String?> = varchar("current_bidder_name", 16).nullable()

    // Timing
    val createdAt: Column<Long> = long("created_at")
    val expiresAt: Column<Long> = long("expires_at")
    val duration: Column<Long> = long("duration")

    // Status
    val status: Column<String> = varchar("status", 20).default(AuctionStatus.ACTIVE.name)

    // Metadata
    val category: Column<String> = varchar("category", 20).default(AuctionCategory.OTHER.name)
    val claimed: Column<Boolean> = bool("claimed").default(false)
}
