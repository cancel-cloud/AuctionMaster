package service

import config.AuctionConfig
import config.Messages
import database.AuctionManager
import model.*
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * Validation result
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Failure(val reason: String) : ValidationResult()

    fun isSuccess(): Boolean = this is Success
    fun getErrorMessage(): String = (this as? Failure)?.reason ?: ""
}

/**
 * Processing result
 */
sealed class ProcessResult {
    data class Success(val message: String? = null) : ProcessResult()
    data class Failure(val reason: String) : ProcessResult()

    fun isSuccess(): Boolean = this is Success
    fun getMessage(): String = when (this) {
        is Success -> message ?: ""
        is Failure -> reason
    }
}

/**
 * Service for auction business logic and validation
 */
class AuctionService(private val config: AuctionConfig = AuctionConfig.default()) {

    // ========== VALIDATION ==========

    /**
     * Validate auction creation
     */
    suspend fun validateAuctionCreation(
        player: Player,
        item: ItemStack,
        startPrice: Double,
        duration: Long,
        buyNowPrice: Double? = null
    ): ValidationResult {
        // Check item validity
        if (!config.isMaterialAllowed(item.type)) {
            return ValidationResult.Failure(Messages.ITEM_BLACKLISTED)
        }

        // Check active auction limit
        val activeAuctions = AuctionManager.getAuctions(
            AuctionFilter.forSeller(player.uniqueId).copy(status = AuctionStatus.ACTIVE)
        )

        if (activeAuctions.size >= config.maxActivePerPlayer) {
            return ValidationResult.Failure(
                Messages.format(Messages.MAX_AUCTIONS_REACHED, "max" to config.maxActivePerPlayer)
            )
        }

        // Validate price ranges
        if (startPrice < config.minStartPrice) {
            return ValidationResult.Failure(
                Messages.format(Messages.PRICE_TOO_LOW, "minPrice" to config.minStartPrice)
            )
        }

        if (startPrice > config.maxStartPrice) {
            return ValidationResult.Failure(
                Messages.format(Messages.PRICE_TOO_HIGH, "maxPrice" to config.maxStartPrice)
            )
        }

        // Validate buy now price
        if (buyNowPrice != null && buyNowPrice <= startPrice) {
            return ValidationResult.Failure("Buy-now price must be higher than start price!")
        }

        // Validate duration
        if (duration < config.getMinDurationMillis()) {
            return ValidationResult.Failure("Duration must be at least ${config.minDurationHours} hour(s)!")
        }

        if (duration > config.getMaxDurationMillis()) {
            return ValidationResult.Failure("Duration cannot exceed ${config.maxDurationHours} hours!")
        }

        // TODO: Check economy balance for listing fee
        // val listingFee = config.calculateListingFee(startPrice)

        return ValidationResult.Success
    }

    /**
     * Validate bid placement
     */
    suspend fun validateBid(
        auction: Auction,
        bidder: Player,
        amount: Double
    ): ValidationResult {
        // Check if bidder is not the seller
        if (auction.sellerId == bidder.uniqueId) {
            return ValidationResult.Failure(Messages.CANNOT_BID_OWN)
        }

        // Check if auction is active
        if (!auction.isActive()) {
            return ValidationResult.Failure(Messages.AUCTION_NOT_ACTIVE)
        }

        // Check if auction has expired
        if (auction.isExpired()) {
            return ValidationResult.Failure(Messages.AUCTION_EXPIRED)
        }

        // Check if bid meets minimum
        val minBid = auction.getMinimumBid(config.minBidIncrement)
        if (amount < minBid) {
            return ValidationResult.Failure(
                Messages.format(Messages.BID_TOO_LOW, "minBid" to minBid)
            )
        }

        // TODO: Check economy balance
        // if (!economy.has(bidder, amount)) {
        //     return ValidationResult.Failure(
        //         Messages.format(Messages.INSUFFICIENT_FUNDS_BID, "amount" to amount)
        //     )
        // }

        return ValidationResult.Success
    }

    /**
     * Validate buy-now purchase
     */
    suspend fun validateBuyNow(auction: Auction, buyer: Player): ValidationResult {
        // Check if buy-now is available
        if (auction.buyNowPrice == null) {
            return ValidationResult.Failure(Messages.NO_BUY_NOW)
        }

        // Check if buyer is not the seller
        if (auction.sellerId == buyer.uniqueId) {
            return ValidationResult.Failure("You cannot buy your own auction!")
        }

        // Check if auction is active
        if (!auction.isActive()) {
            return ValidationResult.Failure(Messages.AUCTION_NOT_ACTIVE)
        }

        // TODO: Check economy balance

        return ValidationResult.Success
    }

    /**
     * Validate auction cancellation
     */
    suspend fun validateCancellation(
        auction: Auction,
        canceller: Player,
        isAdmin: Boolean = false
    ): ValidationResult {
        // Admins can always cancel
        if (isAdmin) {
            return ValidationResult.Success
        }

        // Check if cancellation is allowed
        if (!config.allowSellerCancel) {
            return ValidationResult.Failure(Messages.CANCEL_NOT_ALLOWED)
        }

        // Check if canceller is the seller
        if (auction.sellerId != canceller.uniqueId) {
            return ValidationResult.Failure("You are not the seller of this auction!")
        }

        // Check if auction is still active
        if (auction.status != AuctionStatus.ACTIVE) {
            return ValidationResult.Failure("This auction cannot be cancelled!")
        }

        return ValidationResult.Success
    }

    // ========== PROCESSING ==========

    /**
     * Process a new bid
     */
    suspend fun processBid(auction: Auction, bid: Bid): ProcessResult {
        try {
            // 1. TODO: Charge new bidder (economy integration)
            // economy.withdrawPlayer(bidder, bid.amount)

            // 2. Refund previous bidder if exists
            auction.currentBidderId?.let { previousBidderId ->
                auction.currentBidderName?.let { previousBidderName ->
                    val refundClaim = Claim(
                        playerId = previousBidderId,
                        playerName = previousBidderName,
                        money = auction.currentBid,
                        reason = ClaimReason.OUTBID_REFUND
                    )
                    AuctionManager.addClaim(refundClaim)
                }
            }

            // 3. Update auction with new bid
            val result = AuctionManager.placeBid(auction.id, bid)

            return if (result.isSuccess) {
                ProcessResult.Success(
                    Messages.format(
                        Messages.BID_PLACED,
                        "amount" to bid.amount,
                        "item" to auction.itemStack.getDisplayString()
                    )
                )
            } else {
                ProcessResult.Failure("Failed to save bid")
            }
        } catch (e: Exception) {
            return ProcessResult.Failure(e.message ?: "Unknown error")
        }
    }

    /**
     * Process buy-now purchase
     */
    suspend fun processBuyNow(auction: Auction, buyer: Player): ProcessResult {
        try {
            val buyNowPrice = auction.buyNowPrice
                ?: return ProcessResult.Failure("No buy-now price set")

            // 1. TODO: Charge buyer
            // economy.withdrawPlayer(buyer, buyNowPrice)

            // 2. Refund current bidder if exists
            auction.currentBidderId?.let { bidderId ->
                auction.currentBidderName?.let { bidderName ->
                    val refundClaim = Claim(
                        playerId = bidderId,
                        playerName = bidderName,
                        money = auction.currentBid,
                        reason = ClaimReason.OUTBID_REFUND
                    )
                    AuctionManager.addClaim(refundClaim)
                }
            }

            // 3. Add item to buyer's claims
            val itemClaim = Claim(
                playerId = buyer.uniqueId,
                playerName = buyer.name,
                items = listOf(auction.itemStack),
                reason = ClaimReason.AUCTION_WON
            )
            AuctionManager.addClaim(itemClaim)

            // 4. Add money to seller's claims
            val moneyClaim = Claim(
                playerId = auction.sellerId,
                playerName = auction.sellerName,
                money = buyNowPrice,
                reason = ClaimReason.AUCTION_SOLD
            )
            AuctionManager.addClaim(moneyClaim)

            // 5. Mark auction as sold
            AuctionManager.updateAuction(auction.withStatus(AuctionStatus.SOLD))

            return ProcessResult.Success(
                Messages.format(
                    Messages.BOUGHT_NOW,
                    "item" to auction.itemStack.getDisplayString(),
                    "amount" to buyNowPrice
                )
            )
        } catch (e: Exception) {
            return ProcessResult.Failure(e.message ?: "Unknown error")
        }
    }

    /**
     * Process auction expiration
     */
    suspend fun processExpiration(auction: Auction): ProcessResult {
        try {
            if (auction.currentBidderId != null) {
                // Auction has bids - winner gets item, seller gets money
                val itemClaim = Claim(
                    playerId = auction.currentBidderId,
                    playerName = auction.currentBidderName!!,
                    items = listOf(auction.itemStack),
                    reason = ClaimReason.AUCTION_WON
                )
                AuctionManager.addClaim(itemClaim)

                val moneyClaim = Claim(
                    playerId = auction.sellerId,
                    playerName = auction.sellerName,
                    money = auction.currentBid,
                    reason = ClaimReason.AUCTION_SOLD
                )
                AuctionManager.addClaim(moneyClaim)

                // Mark as sold
                AuctionManager.updateAuction(auction.withStatus(AuctionStatus.SOLD))
            } else {
                // No bids - return item to seller
                val itemClaim = Claim(
                    playerId = auction.sellerId,
                    playerName = auction.sellerName,
                    items = listOf(auction.itemStack),
                    reason = ClaimReason.AUCTION_UNSOLD
                )
                AuctionManager.addClaim(itemClaim)

                // Mark as expired
                AuctionManager.updateAuction(auction.withStatus(AuctionStatus.EXPIRED))
            }

            return ProcessResult.Success()
        } catch (e: Exception) {
            return ProcessResult.Failure(e.message ?: "Unknown error")
        }
    }

    /**
     * Process auction cancellation
     */
    suspend fun processCancellation(
        auction: Auction,
        canceller: Player,
        reason: String = "Cancelled by seller"
    ): ProcessResult {
        try {
            // Refund current bidder if exists
            auction.currentBidderId?.let { bidderId ->
                auction.currentBidderName?.let { bidderName ->
                    val refundClaim = Claim(
                        playerId = bidderId,
                        playerName = bidderName,
                        money = auction.currentBid,
                        reason = ClaimReason.AUCTION_CANCELLED
                    )
                    AuctionManager.addClaim(refundClaim)
                }
            }

            // Return item to seller
            val itemClaim = Claim(
                playerId = auction.sellerId,
                playerName = auction.sellerName,
                items = listOf(auction.itemStack),
                reason = ClaimReason.AUCTION_CANCELLED
            )
            AuctionManager.addClaim(itemClaim)

            // TODO: Apply cancellation fee if configured
            // val fee = config.calculateCancellationFee(auction.currentBid)
            // economy.withdrawPlayer(canceller, fee)

            // Mark as cancelled
            AuctionManager.updateAuction(auction.withStatus(AuctionStatus.CANCELLED))

            return ProcessResult.Success(Messages.AUCTION_CANCELLED)
        } catch (e: Exception) {
            return ProcessResult.Failure(e.message ?: "Unknown error")
        }
    }

    /**
     * Process a claim
     */
    suspend fun processClaim(claim: Claim, player: Player): ProcessResult {
        try {
            // Give items
            claim.items.forEach { serializedItem ->
                val item = SerializableItemStack.toItemStack(serializedItem)

                // Check if player has inventory space
                if (player.inventory.firstEmpty() == -1 && !canStackItem(player, item)) {
                    return ProcessResult.Failure(Messages.INVENTORY_FULL)
                }

                player.inventory.addItem(item)
            }

            // Give money
            if (claim.money > 0) {
                // TODO: Economy integration
                // economy.depositPlayer(player, claim.money)
            }

            // Delete the claim
            AuctionManager.deleteClaim(claim.id)

            // Build result message
            val messages = mutableListOf<String>()
            if (claim.items.isNotEmpty()) {
                messages.add(Messages.format(
                    Messages.CLAIM_RECEIVED_ITEM,
                    "item" to claim.items.joinToString { it.getDisplayString() }
                ))
            }
            if (claim.money > 0) {
                messages.add(Messages.format(Messages.CLAIM_RECEIVED_MONEY, "amount" to claim.money))
            }

            return ProcessResult.Success(messages.joinToString("\n"))
        } catch (e: Exception) {
            return ProcessResult.Failure(e.message ?: "Unknown error")
        }
    }

    // Helper methods

    private fun canStackItem(player: Player, item: ItemStack): Boolean {
        return player.inventory.contents.any { inventoryItem ->
            inventoryItem != null &&
            inventoryItem.isSimilar(item) &&
            inventoryItem.amount < inventoryItem.maxStackSize
        }
    }
}
