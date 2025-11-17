import config.AuctionConfig
import database.AuctionManager
import database.DatabaseManager
import de.fruxz.sparkle.framework.infrastructure.app.App
import de.fruxz.sparkle.framework.infrastructure.app.AppCache
import de.fruxz.sparkle.framework.infrastructure.app.AppCompanion
import de.fruxz.sparkle.framework.infrastructure.app.update.AppUpdater
import interchange.AuctionInterchange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import scheduler.AuctionScheduler
import service.AuctionService
import java.io.File

class AuctionMaster : App() {

    override val appIdentity = "AuctionMaster"
    override val label: String = "AuctionMaster"
    override val updater: AppUpdater = AppUpdater.none()
    override val companion: AppCompanion<out App> = Companion
    override val appCache: AppCache = system.AppCache

    // Plugin components
    private lateinit var config: AuctionConfig
    private lateinit var service: AuctionService
    private lateinit var scheduler: AuctionScheduler
    private val pluginScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override suspend fun hello() {
        // Initialize configuration
        config = AuctionConfig.default()
        logger.info("Loaded configuration")

        // Initialize database
        try {
            val dbFolder = File(dataFolder, "database")
            dbFolder.mkdirs()
            DatabaseManager.initialize(dataFolder)
            logger.info("Database initialized successfully")
        } catch (e: Exception) {
            logger.severe("Failed to initialize database: ${e.message}")
            e.printStackTrace()
            return
        }

        // Load active auctions into cache
        try {
            AuctionManager.loadActiveToCache()
            logger.info("Loaded active auctions into cache")
        } catch (e: Exception) {
            logger.warning("Failed to load cache: ${e.message}")
        }

        // Initialize service
        service = AuctionService(config)
        logger.info("Auction service initialized")

        // Initialize and start scheduler
        val backupDir = File(dataFolder, "backups")
        backupDir.mkdirs()
        scheduler = AuctionScheduler(config, service, backupDir, pluginScope)
        scheduler.start()
        logger.info("Scheduler started")

        // Register commands
        add(AuctionInterchange())
        logger.info("Commands registered")

        logger.info("AuctionMaster v1.0 enabled successfully!")
        logger.info("Upgraded to Minecraft 1.21 with full auction system")
    }

    override suspend fun goodbye() {
        // Stop scheduler
        try {
            scheduler.stop()
            logger.info("Scheduler stopped")
        } catch (e: Exception) {
            logger.warning("Error stopping scheduler: ${e.message}")
        }

        // Cancel coroutine scope
        try {
            pluginScope.cancel()
            logger.info("Coroutine scope cancelled")
        } catch (e: Exception) {
            logger.warning("Error cancelling scope: ${e.message}")
        }

        // Close database
        try {
            DatabaseManager.close()
            logger.info("Database closed")
        } catch (e: Exception) {
            logger.warning("Error closing database: ${e.message}")
        }

        logger.info("AuctionMaster disabled")
    }

    companion object : AppCompanion<AuctionMaster>() {
        override val predictedIdentity = "auctionmaster"

        // Provide access to plugin instance
        lateinit var instance: AuctionMaster
            private set
    }

    init {
        instance = this
    }
}
