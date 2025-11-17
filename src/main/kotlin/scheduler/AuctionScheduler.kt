package scheduler

import config.AuctionConfig
import database.AuctionManager
import database.backup.JsonBackupService
import kotlinx.coroutines.*
import service.AuctionService
import java.io.File

/**
 * Scheduler for periodic auction tasks
 */
class AuctionScheduler(
    private val config: AuctionConfig,
    private val service: AuctionService,
    private val backupDir: File,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private var expirationJob: Job? = null
    private var backupJob: Job? = null
    private var cacheRefreshJob: Job? = null
    private var cleanupJob: Job? = null

    /**
     * Start all scheduled tasks
     */
    fun start() {
        startExpirationTask()
        startBackupTask()
        startCacheRefreshTask()
        startCleanupTask()
    }

    /**
     * Stop all scheduled tasks
     */
    fun stop() {
        expirationJob?.cancel()
        backupJob?.cancel()
        cacheRefreshJob?.cancel()
        cleanupJob?.cancel()
        scope.cancel()
    }

    /**
     * Task to check for and process expired auctions
     * Runs every 30 seconds
     */
    private fun startExpirationTask() {
        expirationJob = scope.launch {
            while (isActive) {
                try {
                    val expired = AuctionManager.expireAuctions()

                    expired.forEach { auction ->
                        service.processExpiration(auction)
                    }

                    if (expired.isNotEmpty()) {
                        println("[AuctionMaster] Processed ${expired.size} expired auction(s)")
                    }
                } catch (e: Exception) {
                    println("[AuctionMaster] Error in expiration task: ${e.message}")
                    e.printStackTrace()
                }

                delay(30_000) // 30 seconds
            }
        }
    }

    /**
     * Task to create database backups
     * Runs every hour (configurable)
     */
    private fun startBackupTask() {
        backupJob = scope.launch {
            while (isActive) {
                delay(config.backupIntervalHours * 3600000L) // Convert hours to millis

                try {
                    val result = JsonBackupService.createBackup(backupDir)

                    if (result.isSuccess) {
                        println("[AuctionMaster] Database backup created: ${result.getOrNull()?.name}")
                    } else {
                        println("[AuctionMaster] Backup failed: ${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    println("[AuctionMaster] Error in backup task: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Task to refresh the active auction cache
     * Runs every 5 minutes
     */
    private fun startCacheRefreshTask() {
        cacheRefreshJob = scope.launch {
            while (isActive) {
                delay(300_000) // 5 minutes

                try {
                    AuctionManager.loadActiveToCache()
                    println("[AuctionMaster] Cache refreshed")
                } catch (e: Exception) {
                    println("[AuctionMaster] Error in cache refresh task: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Task to cleanup old expired/claimed auctions
     * Runs every 24 hours
     */
    private fun startCleanupTask() {
        cleanupJob = scope.launch {
            while (isActive) {
                delay(86400000) // 24 hours

                try {
                    val count = AuctionManager.cleanupExpired(config.getCleanupThresholdMillis())

                    if (count > 0) {
                        println("[AuctionMaster] Cleaned up $count old auction(s)")
                    }
                } catch (e: Exception) {
                    println("[AuctionMaster] Error in cleanup task: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Manually trigger expiration check
     */
    suspend fun triggerExpirationCheck() {
        val expired = AuctionManager.expireAuctions()
        expired.forEach { auction ->
            service.processExpiration(auction)
        }
    }

    /**
     * Manually trigger backup
     */
    suspend fun triggerBackup(): Result<File> {
        return JsonBackupService.createBackup(backupDir)
    }

    /**
     * Manually trigger cache refresh
     */
    suspend fun triggerCacheRefresh() {
        AuctionManager.loadActiveToCache()
    }

    /**
     * Manually trigger cleanup
     */
    suspend fun triggerCleanup(): Int {
        return AuctionManager.cleanupExpired(config.getCleanupThresholdMillis())
    }
}
