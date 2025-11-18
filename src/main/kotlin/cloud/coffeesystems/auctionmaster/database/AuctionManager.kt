package cloud.coffeesystems.auctionmaster.database

import cloud.coffeesystems.auctionmaster.model.Auction
import cloud.coffeesystems.auctionmaster.model.AuctionStatus
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.sql.ResultSet
import java.util.*

/**
 * Manages auction data operations
 */
class AuctionManager(private val databaseManager: DatabaseManager) {

    /**
     * Create a new auction
     */
    fun createAuction(sellerUuid: UUID, sellerName: String, item: ItemStack, price: Double, duration: Long): Int? {
        val sql = """
            INSERT INTO auctions (seller_uuid, seller_name, item_data, price, created_at, expires_at, status)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { statement ->
                    val now = System.currentTimeMillis()
                    val expiresAt = now + duration

                    statement.setString(1, sellerUuid.toString())
                    statement.setString(2, sellerName)
                    statement.setString(3, serializeItem(item))
                    statement.setDouble(4, price)
                    statement.setLong(5, now)
                    statement.setLong(6, expiresAt)
                    statement.setString(7, AuctionStatus.ACTIVE.name)

                    statement.executeUpdate()

                    val generatedKeys = statement.generatedKeys
                    if (generatedKeys.next()) {
                        generatedKeys.getInt(1)
                    } else null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Get all active auctions
     */
    fun getActiveAuctions(): List<Auction> {
        val sql = "SELECT * FROM auctions WHERE status = ? ORDER BY created_at DESC"

        return try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, AuctionStatus.ACTIVE.name)
                    val resultSet = statement.executeQuery()
                    parseAuctions(resultSet)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Get auctions by seller
     */
    fun getAuctionsBySeller(sellerUuid: UUID): List<Auction> {
        val sql = "SELECT * FROM auctions WHERE seller_uuid = ? AND status = ? ORDER BY created_at DESC"

        return try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, sellerUuid.toString())
                    statement.setString(2, AuctionStatus.ACTIVE.name)
                    val resultSet = statement.executeQuery()
                    parseAuctions(resultSet)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Get auction by ID
     */
    fun getAuctionById(id: Int): Auction? {
        val sql = "SELECT * FROM auctions WHERE id = ?"

        return try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setInt(1, id)
                    val resultSet = statement.executeQuery()
                    if (resultSet.next()) {
                        parseAuction(resultSet)
                    } else null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Update auction status
     */
    fun updateAuctionStatus(id: Int, status: AuctionStatus): Boolean {
        val sql = "UPDATE auctions SET status = ? WHERE id = ?"

        return try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, status.name)
                    statement.setInt(2, id)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Delete auction
     */
    fun deleteAuction(id: Int): Boolean {
        val sql = "DELETE FROM auctions WHERE id = ?"

        return try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setInt(1, id)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Get expired auctions
     */
    fun getExpiredAuctions(): List<Auction> {
        val sql = "SELECT * FROM auctions WHERE status = ? AND expires_at < ?"

        return try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, AuctionStatus.ACTIVE.name)
                    statement.setLong(2, System.currentTimeMillis())
                    val resultSet = statement.executeQuery()
                    parseAuctions(resultSet)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Record a transaction
     */
    fun recordTransaction(auctionId: Int, buyerUuid: UUID, buyerName: String, price: Double): Boolean {
        val sql = """
            INSERT INTO transactions (auction_id, buyer_uuid, buyer_name, price, timestamp)
            VALUES (?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setInt(1, auctionId)
                    statement.setString(2, buyerUuid.toString())
                    statement.setString(3, buyerName)
                    statement.setDouble(4, price)
                    statement.setLong(5, System.currentTimeMillis())
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Get count of active auctions by seller
     */
    fun getActiveAuctionCount(sellerUuid: UUID): Int {
        val sql = "SELECT COUNT(*) FROM auctions WHERE seller_uuid = ? AND status = ?"

        return try {
            databaseManager.getConnection().use { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, sellerUuid.toString())
                    statement.setString(2, AuctionStatus.ACTIVE.name)
                    val resultSet = statement.executeQuery()
                    if (resultSet.next()) {
                        resultSet.getInt(1)
                    } else 0
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    /**
     * Parse auctions from result set
     */
    private fun parseAuctions(resultSet: ResultSet): List<Auction> {
        val auctions = mutableListOf<Auction>()
        while (resultSet.next()) {
            parseAuction(resultSet)?.let { auctions.add(it) }
        }
        return auctions
    }

    /**
     * Parse single auction from result set
     */
    private fun parseAuction(resultSet: ResultSet): Auction? {
        return try {
            Auction(
                id = resultSet.getInt("id"),
                sellerUuid = UUID.fromString(resultSet.getString("seller_uuid")),
                sellerName = resultSet.getString("seller_name"),
                item = deserializeItem(resultSet.getString("item_data")),
                price = resultSet.getDouble("price"),
                createdAt = resultSet.getLong("created_at"),
                expiresAt = resultSet.getLong("expires_at"),
                status = AuctionStatus.valueOf(resultSet.getString("status"))
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Serialize ItemStack to Base64 string
     */
    private fun serializeItem(item: ItemStack): String {
        ByteArrayOutputStream().use { outputStream ->
            BukkitObjectOutputStream(outputStream).use { dataOutput ->
                dataOutput.writeObject(item)
                return Base64.getEncoder().encodeToString(outputStream.toByteArray())
            }
        }
    }

    /**
     * Deserialize ItemStack from Base64 string
     */
    private fun deserializeItem(data: String): ItemStack {
        ByteArrayInputStream(Base64.getDecoder().decode(data)).use { inputStream ->
            BukkitObjectInputStream(inputStream).use { dataInput ->
                return dataInput.readObject() as ItemStack
            }
        }
    }
}
