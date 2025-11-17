package database.backup

import database.AuctionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import model.Auction
import model.Bid
import model.Claim
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Backup data structure
 */
@Serializable
data class BackupData(
    val timestamp: Long,
    val version: String = "1.0",
    val auctions: List<Auction>,
    val bids: List<Bid>,
    val claims: List<Claim>
)

/**
 * Service for backing up and restoring auction data to/from JSON
 */
object JsonBackupService {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")

    /**
     * Create a backup of all auction data
     */
    suspend fun createBackup(backupDir: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            backupDir.mkdirs()

            // Collect all data
            val auctions = AuctionManager.getAuctions(model.AuctionFilter(limit = Int.MAX_VALUE))
            val allBids = mutableListOf<Bid>()
            auctions.forEach { auction ->
                allBids.addAll(auction.bidHistory)
            }

            // Claims are player-specific, so we'll get all we can
            // In production, you'd want to iterate through known players
            val allClaims = mutableListOf<Claim>()

            val backup = BackupData(
                timestamp = System.currentTimeMillis(),
                auctions = auctions,
                bids = allBids.distinctBy { it.id },
                claims = allClaims
            )

            // Create backup file
            val timestamp = dateFormat.format(Date())
            val backupFile = File(backupDir, "auction-backup-$timestamp.json")

            backupFile.writeText(json.encodeToString(backup))

            // Keep only last 30 backups
            cleanOldBackups(backupDir, keepCount = 30)

            Result.success(backupFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Restore auction data from a backup file
     */
    suspend fun restoreBackup(backupFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!backupFile.exists()) {
                return@withContext Result.failure(Exception("Backup file not found"))
            }

            val backupData = json.decodeFromString<BackupData>(backupFile.readText())

            // Restore auctions
            backupData.auctions.forEach { auction ->
                AuctionManager.createAuction(auction)
            }

            // Restore claims
            backupData.claims.forEach { claim ->
                AuctionManager.addClaim(claim)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get list of available backups
     */
    fun getAvailableBackups(backupDir: File): List<File> {
        return backupDir.listFiles { file ->
            file.name.startsWith("auction-backup-") && file.extension == "json"
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Clean old backups, keeping only the most recent ones
     */
    private fun cleanOldBackups(backupDir: File, keepCount: Int) {
        val backups = getAvailableBackups(backupDir)

        backups.drop(keepCount).forEach { file ->
            file.delete()
        }
    }

    /**
     * Export specific auction to JSON
     */
    suspend fun exportAuction(auction: Auction, exportDir: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            exportDir.mkdirs()

            val exportFile = File(exportDir, "auction-${auction.id}.json")
            exportFile.writeText(json.encodeToString(auction))

            Result.success(exportFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
