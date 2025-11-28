package cloud.coffeesystems.auctionmaster

import cloud.coffeesystems.auctionmaster.bootstrap.ListenerRegistrar
import cloud.coffeesystems.auctionmaster.bootstrap.LocalizationBootstrap
import cloud.coffeesystems.auctionmaster.commands.AuctionCommand
import cloud.coffeesystems.auctionmaster.commands.PaperCommandRegistrar
import cloud.coffeesystems.auctionmaster.database.AuctionManager
import cloud.coffeesystems.auctionmaster.database.DatabaseManager
import cloud.coffeesystems.auctionmaster.hooks.EconomyHook
import cloud.coffeesystems.auctionmaster.migration.DatabaseMigrationService
import cloud.coffeesystems.auctionmaster.notifications.NotificationSettingsService
import cloud.coffeesystems.auctionmaster.util.MessageManager
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

/** Main plugin class for AuctionMaster */
class AuctionMaster : JavaPlugin() {

        lateinit var databaseManager: DatabaseManager
                private set

        lateinit var auctionManager: AuctionManager
                private set

        lateinit var messageManager: MessageManager
                private set

        lateinit var economyHook: EconomyHook
                private set

        lateinit var notificationSettings: NotificationSettingsService
                private set

        private lateinit var migrationService: DatabaseMigrationService

        var databaseConnectionFailed: Boolean = false
                private set

        var lockdownMode: Boolean = false
                private set

        companion object {
                lateinit var instance: AuctionMaster
                        private set
        }

        override fun onEnable() {
                instance = this

                saveDefaultConfig()

                LocalizationBootstrap(this).ensureLanguageFiles()

                messageManager = MessageManager(this)
                logger.info(
                        "Message manager initialized with language: ${messageManager.getLanguage()}"
                )

                economyHook = EconomyHook(this)

                databaseManager = DatabaseManager(this)
                migrationService = DatabaseMigrationService(this)
                messageManager.sendRaw(Bukkit.getConsoleSender(), "database.connecting")

                val connected = databaseManager.connect()

                if (connected) {
                        messageManager.sendRaw(Bukkit.getConsoleSender(), "database.connected")

                        // Initialize database tables
                        databaseManager.initializeTables()

                        // Initialize auction manager
                        auctionManager = AuctionManager(databaseManager)
                        notificationSettings = NotificationSettingsService(this)

                        databaseConnectionFailed = false

                        // Check lockdown mode
                        lockdownMode = config.getBoolean("lockdown-enabled", false)
                        if (lockdownMode) {
                                logger.warning("AuctionMaster is currently in LOCKDOWN mode!")
                        }
                } else {
                        messageManager.sendRaw(
                                Bukkit.getConsoleSender(),
                                "database.connection-failed"
                        )
                        messageManager.sendRaw(
                                Bukkit.getConsoleSender(),
                                "database.connection-error"
                        )

                        databaseConnectionFailed = true

                        // Log to console with more details
                        logger.severe("═══════════════════════════════════════════════════════")
                        logger.severe("DATABASE CONNECTION FAILED!")
                        logger.severe(
                                "AuctionMaster will not function properly without a database."
                        )
                        logger.severe("Please check your configuration and restart the server.")
                        logger.severe("═══════════════════════════════════════════════════════")
                }

                ListenerRegistrar(this).registerCoreListeners()
                PaperCommandRegistrar(this, AuctionCommand(this)).register()

                logger.info("AuctionMaster v${pluginMeta.version} has been enabled!")
        }

        override fun onDisable() {
                // Close database connection
                if (::databaseManager.isInitialized) {
                        databaseManager.disconnect()
                }

                logger.info("AuctionMaster has been disabled!")
        }

        /** Reload plugin configuration */
        fun reloadPlugin() {
                reloadConfig()
                messageManager.loadMessages()
                lockdownMode = config.getBoolean("lockdown-enabled", false)
                logger.info("Configuration reloaded")
        }

        /** Set lockdown mode */
        fun setLockdown(enabled: Boolean) {
                lockdownMode = enabled
                config.set("lockdown-enabled", enabled)
                saveConfig()

                if (enabled) {
                        logger.warning("AuctionMaster has been put into LOCKDOWN mode!")
                        Bukkit.broadcast(messageManager.get("lockdown.enabled"))
                } else {
                        logger.info("AuctionMaster lockdown mode disabled")
                        Bukkit.broadcast(messageManager.get("lockdown.disabled"))
                }
        }

        /** Migrate database from SQLite to PostgreSQL */
        fun migrateDatabase(sender: org.bukkit.command.CommandSender, confirm: Boolean = false) {
                migrationService.migrate(sender, confirm)
        }

        fun hasAuctionManager(): Boolean = this::auctionManager.isInitialized
}
