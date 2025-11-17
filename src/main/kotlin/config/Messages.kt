package config

/**
 * Centralized message configuration
 */
object Messages {
    const val PREFIX = "&6[AuctionMaster]&r "

    // Creation messages
    const val AUCTION_CREATED = "${PREFIX}&aAuction created for {item}!"
    const val AUCTION_CREATE_FAILED = "${PREFIX}&cFailed to create auction: {reason}"
    const val NO_ITEM_IN_HAND = "${PREFIX}&cYou need to hold an item in your hand!"
    const val ITEM_BLACKLISTED = "${PREFIX}&cThis item cannot be auctioned!"
    const val MAX_AUCTIONS_REACHED = "${PREFIX}&cYou already have {max} active auctions!"
    const val PRICE_TOO_LOW = "${PREFIX}&cStart price must be at least ${minPrice}!"
    const val PRICE_TOO_HIGH = "${PREFIX}&cStart price cannot exceed ${maxPrice}!"
    const val INSUFFICIENT_FUNDS_FEE = "${PREFIX}&cYou need ${fee} to list this auction!"

    // Bidding messages
    const val BID_PLACED = "${PREFIX}&aYou bid ${amount} on {item}!"
    const val BID_PLACED_FAILED = "${PREFIX}&cFailed to place bid: {reason}"
    const val OUTBID = "${PREFIX}&cYou were outbid on {item} by {bidder}! Your money has been refunded."
    const val BID_TOO_LOW = "${PREFIX}&cYour bid must be at least ${minBid}!"
    const val INSUFFICIENT_FUNDS_BID = "${PREFIX}&cYou need ${amount} to place this bid!"
    const val CANNOT_BID_OWN = "${PREFIX}&cYou cannot bid on your own auction!"
    const val AUCTION_EXPIRED = "${PREFIX}&cThis auction has expired!"
    const val AUCTION_NOT_ACTIVE = "${PREFIX}&cThis auction is not active!"

    // Buy now messages
    const val BOUGHT_NOW = "${PREFIX}&aYou bought {item} for ${amount}!"
    const val BUY_NOW_FAILED = "${PREFIX}&cFailed to buy item: {reason}"
    const val NO_BUY_NOW = "${PREFIX}&cThis auction doesn't have a buy-now price!"

    // Claim messages
    const val CLAIM_RECEIVED_ITEM = "${PREFIX}&aYou received {item}!"
    const val CLAIM_RECEIVED_MONEY = "${PREFIX}&aYou received ${amount}!"
    const val CLAIM_ALL_SUCCESS = "${PREFIX}&aClaimed all items and money!"
    const val NO_CLAIMS = "${PREFIX}&cYou have no pending claims!"
    const val INVENTORY_FULL = "${PREFIX}&cYour inventory is full! Free up space and try again."

    // Auction end messages
    const val AUCTION_WON = "${PREFIX}&aCongratulations! You won the auction for {item}!"
    const val AUCTION_SOLD = "${PREFIX}&aYour auction for {item} sold for ${amount}!"
    const val AUCTION_EXPIRED_NO_BIDS = "${PREFIX}&cYour auction for {item} expired without any bids."

    // Cancellation messages
    const val AUCTION_CANCELLED = "${PREFIX}&aAuction cancelled."
    const val AUCTION_CANCEL_FAILED = "${PREFIX}&cFailed to cancel auction: {reason}"
    const val CANCEL_NOT_ALLOWED = "${PREFIX}&cYou cannot cancel this auction!"
    const val CANCEL_FEE_CHARGED = "${PREFIX}&cCancellation fee of ${fee} was charged."

    // Admin messages
    const val ADMIN_AUCTION_DELETED = "${PREFIX}&aAuction deleted."
    const val ADMIN_DATABASE_CLEANED = "${PREFIX}&aCleaned up {count} old auctions."
    const val ADMIN_BACKUP_CREATED = "${PREFIX}&aBackup created: {file}"
    const val ADMIN_BACKUP_RESTORED = "${PREFIX}&aBackup restored successfully!"

    // Generic messages
    const val PERMISSION_DENIED = "${PREFIX}&cYou don't have permission to do this!"
    const val PLAYER_ONLY = "${PREFIX}&cThis command can only be used by players!"
    const val INVALID_AMOUNT = "${PREFIX}&cInvalid amount specified!"
    const val ERROR_OCCURRED = "${PREFIX}&cAn error occurred. Please try again later."

    /**
     * Replace placeholders in a message
     */
    fun format(message: String, vararg replacements: Pair<String, Any>): String {
        var result = message
        for ((key, value) in replacements) {
            result = result.replace("{$key}", value.toString())
        }
        return result
    }
}
