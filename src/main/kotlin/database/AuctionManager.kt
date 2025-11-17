package database

import database.schema.Auctions
import database.schema.Bids
import database.schema.Claims
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manager for auction CRUD operations with caching
 */
object AuctionManager {
    private val activeCache = ConcurrentHashMap<UUID, Auction>()
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    // CRUD Operations for Auctions

    /**
     * Create a new auction
     */
    suspend fun createAuction(auction: Auction): Result<Auction> = withContext(Dispatchers.IO) {
        try {
            transaction(DatabaseManager.getDatabase()) {
                Auctions.insert {
                    it[id] = auction.id
                    it[sellerId] = auction.sellerId.toString()
                    it[sellerName] = auction.sellerName
                    it[itemData] = json.encodeToString(auction.itemStack)
                    it[startPrice] = auction.startPrice
                    it[currentBid] = auction.currentBid
                    it[buyNowPrice] = auction.buyNowPrice
                    it[currentBidderId] = auction.currentBidderId?.toString()
                    it[currentBidderName] = auction.currentBidderName
                    it[createdAt] = auction.createdAt
                    it[expiresAt] = auction.expiresAt
                    it[duration] = auction.duration
                    it[status] = auction.status.name
                    it[category] = auction.category.name
                    it[claimed] = auction.claimed
                }
            }

            // Add to cache if active
            if (auction.isActive()) {
                activeCache[auction.id] = auction
            }

            Result.success(auction)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get an auction by ID
     */
    suspend fun getAuction(id: UUID): Auction? = withContext(Dispatchers.IO) {
        // Check cache first
        activeCache[id]?.let { return@withContext it }

        // Query database
        transaction(DatabaseManager.getDatabase()) {
            Auctions.select { Auctions.id eq id }
                .singleOrNull()
                ?.toAuction()
        }
    }

    /**
     * Get auctions with filters
     */
    suspend fun getAuctions(filter: AuctionFilter): List<Auction> = withContext(Dispatchers.IO) {
        transaction(DatabaseManager.getDatabase()) {
            var query = Auctions.selectAll()

            // Apply filters
            filter.category?.let { category ->
                query = query.andWhere { Auctions.category eq category.name }
            }

            filter.status?.let { status ->
                query = query.andWhere { Auctions.status eq status.name }
            }

            filter.sellerId?.let { sellerId ->
                query = query.andWhere { Auctions.sellerId eq sellerId.toString() }
            }

            filter.minPrice?.let { minPrice ->
                query = query.andWhere { Auctions.currentBid greaterEq minPrice }
            }

            filter.maxPrice?.let { maxPrice ->
                query = query.andWhere { Auctions.currentBid lessEq maxPrice }
            }

            // Search in item data (basic text search)
            filter.searchText?.let { searchText ->
                query = query.andWhere {
                    Auctions.itemData like "%${searchText}%"
                }
            }

            // Apply sorting
            query = when (filter.sortOrder) {
                SortOrder.NEWEST -> query.orderBy(Auctions.createdAt, SortOrder.DESC)
                SortOrder.OLDEST -> query.orderBy(Auctions.createdAt, SortOrder.ASC)
                SortOrder.PRICE_LOW -> query.orderBy(Auctions.currentBid, SortOrder.ASC)
                SortOrder.PRICE_HIGH -> query.orderBy(Auctions.currentBid, SortOrder.DESC)
                SortOrder.ENDING_SOON -> query.orderBy(Auctions.expiresAt, SortOrder.ASC)
                SortOrder.MOST_BIDS -> query.orderBy(Auctions.createdAt, SortOrder.DESC) // TODO: count bids
            }

            // Apply pagination
            query.limit(filter.limit, filter.offset.toLong())
                .map { it.toAuction() }
                .let { auctions ->
                    // Additional filter for bidder (requires checking bid history)
                    filter.bidderId?.let { bidderId ->
                        auctions.filter { it.bidHistory.any { bid -> bid.bidderId == bidderId } }
                    } ?: auctions
                }
        }
    }

    /**
     * Get active auctions (optimized for frequent calls)
     */
    suspend fun getActiveAuctions(filter: AuctionFilter = AuctionFilter.activeOnly()): List<Auction> {
        return getAuctions(filter.copy(status = AuctionStatus.ACTIVE))
    }

    /**
     * Update an auction
     */
    suspend fun updateAuction(auction: Auction): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction(DatabaseManager.getDatabase()) {
                Auctions.update({ Auctions.id eq auction.id }) {
                    it[sellerId] = auction.sellerId.toString()
                    it[sellerName] = auction.sellerName
                    it[itemData] = json.encodeToString(auction.itemStack)
                    it[startPrice] = auction.startPrice
                    it[currentBid] = auction.currentBid
                    it[buyNowPrice] = auction.buyNowPrice
                    it[currentBidderId] = auction.currentBidderId?.toString()
                    it[currentBidderName] = auction.currentBidderName
                    it[createdAt] = auction.createdAt
                    it[expiresAt] = auction.expiresAt
                    it[duration] = auction.duration
                    it[status] = auction.status.name
                    it[category] = auction.category.name
                    it[claimed] = auction.claimed
                }
            }

            // Update cache
            if (auction.isActive()) {
                activeCache[auction.id] = auction
            } else {
                activeCache.remove(auction.id)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete an auction
     */
    suspend fun deleteAuction(id: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction(DatabaseManager.getDatabase()) {
                // Delete associated bids first
                Bids.deleteWhere { auctionId eq id.toString() }
                // Delete auction
                Auctions.deleteWhere { Auctions.id eq id }
            }

            activeCache.remove(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Bid Operations

    /**
     * Place a bid on an auction
     */
    suspend fun placeBid(auctionId: UUID, bid: Bid): Result<Auction> = withContext(Dispatchers.IO) {
        try {
            val auction = getAuction(auctionId)
                ?: return@withContext Result.failure(Exception("Auction not found"))

            val updatedAuction = auction.withBid(bid)

            // Save bid to database
            transaction(DatabaseManager.getDatabase()) {
                Bids.insert {
                    it[id] = bid.id
                    it[Bids.auctionId] = bid.auctionId.toString()
                    it[bidderId] = bid.bidderId.toString()
                    it[bidderName] = bid.bidderName
                    it[amount] = bid.amount
                    it[timestamp] = bid.timestamp
                }
            }

            // Update auction
            updateAuction(updatedAuction)

            Result.success(updatedAuction)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get bid history for an auction
     */
    suspend fun getBidHistory(auctionId: UUID): List<Bid> = withContext(Dispatchers.IO) {
        transaction(DatabaseManager.getDatabase()) {
            Bids.select { Bids.auctionId eq auctionId.toString() }
                .orderBy(Bids.timestamp, SortOrder.DESC)
                .map { it.toBid() }
        }
    }

    // Claim Operations

    /**
     * Add a new claim
     */
    suspend fun addClaim(claim: Claim): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction(DatabaseManager.getDatabase()) {
                Claims.insert {
                    it[id] = claim.id
                    it[playerId] = claim.playerId.toString()
                    it[playerName] = claim.playerName
                    it[items] = json.encodeToString(claim.items)
                    it[money] = claim.money
                    it[reason] = claim.reason.name
                    it[timestamp] = claim.timestamp
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all claims for a player
     */
    suspend fun getClaims(playerId: UUID): List<Claim> = withContext(Dispatchers.IO) {
        transaction(DatabaseManager.getDatabase()) {
            Claims.select { Claims.playerId eq playerId.toString() }
                .orderBy(Claims.timestamp, SortOrder.DESC)
                .map { it.toClaim() }
        }
    }

    /**
     * Delete a claim (after it's been processed)
     */
    suspend fun deleteClaim(claimId: UUID): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            transaction(DatabaseManager.getDatabase()) {
                Claims.deleteWhere { id eq claimId }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Lifecycle Management

    /**
     * Find and mark expired auctions
     */
    suspend fun expireAuctions(): List<Auction> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        transaction(DatabaseManager.getDatabase()) {
            Auctions.select {
                (Auctions.status eq AuctionStatus.ACTIVE.name) and
                (Auctions.expiresAt lessEq now)
            }
            .map { it.toAuction() }
            .also { expired ->
                expired.forEach { auction ->
                    updateAuction(auction.withStatus(AuctionStatus.EXPIRED))
                }
            }
        }
    }

    /**
     * Delete old expired/claimed auctions
     */
    suspend fun cleanupExpired(olderThanMillis: Long): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - olderThanMillis

        transaction(DatabaseManager.getDatabase()) {
            Auctions.deleteWhere {
                (status inList listOf(AuctionStatus.EXPIRED.name, AuctionStatus.CLAIMED.name)) and
                (expiresAt less cutoff)
            }
        }
    }

    // Cache Management

    /**
     * Invalidate the entire cache
     */
    fun invalidateCache() {
        activeCache.clear()
    }

    /**
     * Load active auctions into cache
     */
    suspend fun loadActiveToCache() {
        val active = getActiveAuctions()
        activeCache.clear()
        active.forEach { activeCache[it.id] = it }
    }

    // Helper Extensions

    private fun ResultRow.toAuction(): Auction {
        // Load bids from separate table
        val auctionId = this[Auctions.id].value
        val bids = transaction(DatabaseManager.getDatabase()) {
            Bids.select { Bids.auctionId eq auctionId.toString() }
                .map { it.toBid() }
        }

        return Auction(
            id = auctionId,
            sellerId = UUID.fromString(this[Auctions.sellerId]),
            sellerName = this[Auctions.sellerName],
            itemStack = json.decodeFromString(this[Auctions.itemData]),
            startPrice = this[Auctions.startPrice],
            currentBid = this[Auctions.currentBid],
            buyNowPrice = this[Auctions.buyNowPrice],
            currentBidderId = this[Auctions.currentBidderId]?.let { UUID.fromString(it) },
            currentBidderName = this[Auctions.currentBidderName],
            bidHistory = bids,
            createdAt = this[Auctions.createdAt],
            expiresAt = this[Auctions.expiresAt],
            duration = this[Auctions.duration],
            status = AuctionStatus.valueOf(this[Auctions.status]),
            category = AuctionCategory.valueOf(this[Auctions.category]),
            claimed = this[Auctions.claimed]
        )
    }

    private fun ResultRow.toBid(): Bid {
        return Bid(
            id = this[Bids.id].value,
            auctionId = UUID.fromString(this[Bids.auctionId]),
            bidderId = UUID.fromString(this[Bids.bidderId]),
            bidderName = this[Bids.bidderName],
            amount = this[Bids.amount],
            timestamp = this[Bids.timestamp]
        )
    }

    private fun ResultRow.toClaim(): Claim {
        return Claim(
            id = this[Claims.id].value,
            playerId = UUID.fromString(this[Claims.playerId]),
            playerName = this[Claims.playerName],
            items = json.decodeFromString(this[Claims.items]),
            money = this[Claims.money],
            reason = ClaimReason.valueOf(this[Claims.reason]),
            timestamp = this[Claims.timestamp]
        )
    }
}
