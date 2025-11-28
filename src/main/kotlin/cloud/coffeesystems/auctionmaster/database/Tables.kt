package cloud.coffeesystems.auctionmaster.database

import org.jetbrains.exposed.sql.Table

/** Auctions table schema using Exposed framework */
object Auctions : Table("auctions") {
    val id = integer("id").autoIncrement()
    val sellerUuid = varchar("seller_uuid", 36)
    val sellerName = varchar("seller_name", 16)
    val itemData = text("item_data")
    val price = double("price")
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")
    val status = varchar("status", 20).default("ACTIVE")

    override val primaryKey = PrimaryKey(id)
}

/** Transactions table schema using Exposed framework */
object Transactions : Table("transactions") {
    val id = integer("id").autoIncrement()
    val auctionId = integer("auction_id")
    val buyerUuid = varchar("buyer_uuid", 36)
    val buyerName = varchar("buyer_name", 16)
    val price = double("price")
    val timestamp = long("timestamp")

    override val primaryKey = PrimaryKey(id)
}

/** Auction History table schema using Exposed framework */
object AuctionHistory : Table("auction_history") {
    val id = integer("id").autoIncrement()
    val buyerUuid = varchar("buyer_uuid", 36)
    val buyerName = varchar("buyer_name", 16)
    val sellerUuid = varchar("seller_uuid", 36)
    val sellerName = varchar("seller_name", 16)
    val itemData = text("item_data")
    val price = double("price")
    val timestamp = long("timestamp")

    override val primaryKey = PrimaryKey(id)
}

/** Pending Payments table for offline sellers */
object PendingPayments : Table("pending_payments") {
    val id = integer("id").autoIncrement()
    val sellerUuid = varchar("seller_uuid", 36)
    val sellerName = varchar("seller_name", 16)
    val itemName = varchar("item_name", 100)
    val amount = double("amount")
    val timestamp = long("timestamp")
    val paid = bool("paid").default(false)

    override val primaryKey = PrimaryKey(id)
}

/** Pending Expired Items table for returning items from expired auctions */
object PendingExpiredItems : Table("pending_expired_items") {
    val id = integer("id").autoIncrement()
    val sellerUuid = varchar("seller_uuid", 36)
    val sellerName = varchar("seller_name", 16)
    val auctionId = integer("auction_id")
    val itemData = text("item_data")
    val timestamp = long("timestamp")

    override val primaryKey = PrimaryKey(id)
}

/** Notification settings per player */
object NotificationSettings : Table("auction_notification_settings") {
    val playerUuid = varchar("player_uuid", 36)
    val soundOnAuctionCreate = bool("sound_on_auction_create").default(true)
    val soundOnAuctionBuy = bool("sound_on_auction_buy").default(true)
    val soundOnAuctionSell = bool("sound_on_auction_sell").default(true)
    val soundOnLoginPayout = bool("sound_on_login_payout").default(true)

    override val primaryKey = PrimaryKey(playerUuid)
}
