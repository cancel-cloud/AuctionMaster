package cloud.coffeesystems.auctionmaster.database

import cloud.coffeesystems.auctionmaster.model.Auction
import cloud.coffeesystems.auctionmaster.model.AuctionHistoryItem
import cloud.coffeesystems.auctionmaster.model.AuctionStatus
import java.util.*
import org.bukkit.inventory.ItemStack
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.transaction

/** Manages auction data operations using Exposed framework */
class AuctionManager(private val databaseManager: DatabaseManager) {

    /** Create a new auction */
    fun createAuction(
            sellerUuid: UUID,
            sellerName: String,
            item: ItemStack,
            price: Double,
            duration: Long
    ): Int? {
        return try {
            transaction(databaseManager.getDatabase()) {
                val now = System.currentTimeMillis()
                val expiresAt = now + duration

                Auctions.insert {
                    it[Auctions.sellerUuid] = sellerUuid.toString()
                    it[Auctions.sellerName] = sellerName
                    it[Auctions.itemData] = serializeItem(item)
                    it[Auctions.price] = price
                    it[Auctions.createdAt] = now
                    it[Auctions.expiresAt] = expiresAt
                    it[Auctions.status] = AuctionStatus.ACTIVE.name
                } get Auctions.id
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Get all active auctions (filters out expired ones) */
    fun getActiveAuctions(): List<Auction> {
        return try {
            transaction(databaseManager.getDatabase()) {
                val now = System.currentTimeMillis()
                Auctions.selectAll()
                        .where {
                            (Auctions.status eq AuctionStatus.ACTIVE.name) and
                                    (Auctions.expiresAt greater now)
                        }
                        .orderBy(Auctions.createdAt to SortOrder.DESC)
                        .mapNotNull { parseAuction(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** Get auctions by seller */
    fun getAuctionsBySeller(sellerUuid: UUID): List<Auction> {
        return try {
            transaction(databaseManager.getDatabase()) {
                Auctions.selectAll()
                        .where {
                            (Auctions.sellerUuid eq sellerUuid.toString()) and
                                    (Auctions.status eq AuctionStatus.ACTIVE.name)
                        }
                        .orderBy(Auctions.createdAt to SortOrder.DESC)
                        .mapNotNull { parseAuction(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** Get auction by ID */
    fun getAuctionById(id: Int): Auction? {
        return try {
            transaction(databaseManager.getDatabase()) {
                Auctions.selectAll().where { Auctions.id eq id }.singleOrNull()?.let {
                    parseAuction(it)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Update auction status */
    fun updateAuctionStatus(id: Int, status: AuctionStatus): Boolean {
        return try {
            transaction(databaseManager.getDatabase()) {
                Auctions.update({ Auctions.id eq id }) { it[Auctions.status] = status.name } > 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Delete auction */
    fun deleteAuction(id: Int): Boolean {
        return try {
            transaction(databaseManager.getDatabase()) {
                Auctions.deleteWhere { Auctions.id eq id } > 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Get expired auctions */
    fun getExpiredAuctions(): List<Auction> {
        return try {
            transaction(databaseManager.getDatabase()) {
                Auctions.selectAll()
                        .where {
                            (Auctions.status eq AuctionStatus.ACTIVE.name) and
                                    (Auctions.expiresAt less System.currentTimeMillis())
                        }
                        .mapNotNull { parseAuction(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** Record a transaction */
    fun recordTransaction(auction: Auction, buyerUuid: UUID, buyerName: String): Boolean {
        return try {
            transaction(databaseManager.getDatabase()) {
                // Insert into Transactions (Legacy/Simple)
                Transactions.insert {
                    it[Transactions.auctionId] = auction.id
                    it[Transactions.buyerUuid] = buyerUuid.toString()
                    it[Transactions.buyerName] = buyerName
                    it[Transactions.price] = auction.price
                    it[Transactions.timestamp] = System.currentTimeMillis()
                }

                // Insert into AuctionHistory (Detailed)
                AuctionHistory.insert {
                    it[AuctionHistory.buyerUuid] = buyerUuid.toString()
                    it[AuctionHistory.buyerName] = buyerName
                    it[AuctionHistory.sellerUuid] = auction.sellerUuid.toString()
                    it[AuctionHistory.sellerName] = auction.sellerName
                    it[AuctionHistory.itemData] = serializeItem(auction.item)
                    it[AuctionHistory.price] = auction.price
                    it[AuctionHistory.timestamp] = System.currentTimeMillis()
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Get purchase history for a player */
    fun getHistory(playerUuid: UUID): List<AuctionHistoryItem> {
        return try {
            transaction(databaseManager.getDatabase()) {
                AuctionHistory.selectAll()
                        .where { AuctionHistory.buyerUuid eq playerUuid.toString() }
                        .orderBy(AuctionHistory.timestamp to SortOrder.DESC)
                        .mapNotNull { parseHistoryItem(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseHistoryItem(row: ResultRow): AuctionHistoryItem? {
        return try {
            AuctionHistoryItem(
                    id = row[AuctionHistory.id],
                    buyerUuid = UUID.fromString(row[AuctionHistory.buyerUuid]),
                    buyerName = row[AuctionHistory.buyerName],
                    sellerUuid = UUID.fromString(row[AuctionHistory.sellerUuid]),
                    sellerName = row[AuctionHistory.sellerName],
                    item = deserializeItem(row[AuctionHistory.itemData]),
                    price = row[AuctionHistory.price],
                    timestamp = row[AuctionHistory.timestamp]
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Get count of active auctions by seller */
    fun getActiveAuctionCount(sellerUuid: UUID): Int {
        return try {
            transaction(databaseManager.getDatabase()) {
                Auctions.selectAll()
                        .where {
                            (Auctions.sellerUuid eq sellerUuid.toString()) and
                                    (Auctions.status eq AuctionStatus.ACTIVE.name)
                        }
                        .count()
                        .toInt()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    /** Parse single auction from result row */
    private fun parseAuction(row: ResultRow): Auction? {
        return try {
            Auction(
                    id = row[Auctions.id],
                    sellerUuid = UUID.fromString(row[Auctions.sellerUuid]),
                    sellerName = row[Auctions.sellerName],
                    item = deserializeItem(row[Auctions.itemData]),
                    price = row[Auctions.price],
                    createdAt = row[Auctions.createdAt],
                    expiresAt = row[Auctions.expiresAt],
                    status = AuctionStatus.valueOf(row[Auctions.status])
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Serialize ItemStack to Base64 string */
    private fun serializeItem(item: ItemStack): String {
        return Base64.getEncoder().encodeToString(item.serializeAsBytes())
    }

    /** Deserialize ItemStack from Base64 string */
    private fun deserializeItem(data: String): ItemStack {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(data))
    }

    /** Get list of unique seller names who have active auctions */
    fun getUniqueSellers(): List<String> {
        return try {
            transaction(databaseManager.getDatabase()) {
                Auctions.select(Auctions.sellerName)
                        .where { Auctions.status eq AuctionStatus.ACTIVE.name }
                        .withDistinct()
                        .map { it[Auctions.sellerName] }
                        .sorted()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** Get auctions by seller name */
    fun getAuctionsBySeller(sellerName: String): List<Auction> {
        return try {
            transaction(databaseManager.getDatabase()) {
                Auctions.selectAll()
                        .where {
                            (Auctions.sellerName eq sellerName) and
                                    (Auctions.status eq AuctionStatus.ACTIVE.name)
                        }
                        .mapNotNull { parseAuction(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** Get expired auctions for a specific seller */
    fun getExpiredAuctionsForSeller(sellerUuid: UUID): List<Auction> {
        return try {
            transaction(databaseManager.getDatabase()) {
                val now = System.currentTimeMillis()
                Auctions.selectAll()
                        .where {
                            (Auctions.sellerUuid eq sellerUuid.toString()) and
                                    (Auctions.status eq AuctionStatus.ACTIVE.name) and
                                    (Auctions.expiresAt less now)
                        }
                        .mapNotNull { parseAuction(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** Move an auction to expired status and add to pending items */
    fun moveToExpired(auctionId: Int, sellerUuid: UUID, sellerName: String): Boolean {
        return try {
            transaction(databaseManager.getDatabase()) {
                // Get the auction
                val auction =
                        Auctions.selectAll()
                                .where { Auctions.id eq auctionId }
                                .singleOrNull()
                                ?: return@transaction false

                // Update status to EXPIRED
                Auctions.update({ Auctions.id eq auctionId }) {
                    it[status] = AuctionStatus.EXPIRED.name
                }

                // Add to pending expired items
                PendingExpiredItems.insert {
                    it[PendingExpiredItems.sellerUuid] = sellerUuid.toString()
                    it[PendingExpiredItems.sellerName] = sellerName
                    it[PendingExpiredItems.auctionId] = auctionId
                    it[itemData] = auction[Auctions.itemData]
                    it[timestamp] = System.currentTimeMillis()
                }

                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Get pending expired items for a player */
    fun getPendingExpiredItems(playerUuid: UUID): List<Pair<Int, ItemStack>> {
        return try {
            transaction(databaseManager.getDatabase()) {
                PendingExpiredItems.selectAll()
                        .where { PendingExpiredItems.sellerUuid eq playerUuid.toString() }
                        .mapNotNull {
                            try {
                                val id = it[PendingExpiredItems.id]
                                val item = deserializeItem(it[PendingExpiredItems.itemData])
                                Pair(id, item)
                            } catch (e: Exception) {
                                null
                            }
                        }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** Claim an expired item and remove from pending */
    fun claimExpiredItem(pendingId: Int): Boolean {
        return try {
            transaction(databaseManager.getDatabase()) {
                PendingExpiredItems.deleteWhere { PendingExpiredItems.id eq pendingId } > 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Get auctions by seller with specific status */
    fun getAuctionsBySellerWithStatus(
            sellerUuid: UUID,
            status: AuctionStatus
    ): List<Auction> {
        return try {
            transaction(databaseManager.getDatabase()) {
                Auctions.selectAll()
                        .where {
                            (Auctions.sellerUuid eq sellerUuid.toString()) and
                                    (Auctions.status eq status.name)
                        }
                        .orderBy(Auctions.createdAt to SortOrder.DESC)
                        .mapNotNull { parseAuction(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** Get count of active auctions for a seller (not expired) */
    fun getActiveAuctionCountNotExpired(sellerUuid: UUID): Int {
        return try {
            transaction(databaseManager.getDatabase()) {
                val now = System.currentTimeMillis()
                Auctions.selectAll()
                        .where {
                            (Auctions.sellerUuid eq sellerUuid.toString()) and
                                    (Auctions.status eq AuctionStatus.ACTIVE.name) and
                                    (Auctions.expiresAt greater now)
                        }
                        .count()
                        .toInt()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }
}
