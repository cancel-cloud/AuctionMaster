package cloud.coffeesystems.auctionmaster

import cloud.coffeesystems.auctionmaster.commands.AuctionCommand
import cloud.coffeesystems.auctionmaster.database.AuctionManager
import cloud.coffeesystems.auctionmaster.database.DatabaseManager
import cloud.coffeesystems.auctionmaster.listeners.DatabaseWarningListener
import cloud.coffeesystems.auctionmaster.util.MessageManager
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * Main plugin class for AuctionMaster
 */
class AuctionMaster : JavaPlugin() {

    lateinit var databaseManager: DatabaseManager
        private set

    lateinit var auctionManager: AuctionManager
        private set

    lateinit var messageManager: MessageManager
        private set

    var databaseConnectionFailed: Boolean = false
        private set

    companion object {
        lateinit var instance: AuctionMaster
            private set
    }

    override fun onEnable() {
        instance = this

        // Save default config
        saveDefaultConfig()

        // Copy language files if they don't exist
        copyLanguageFiles()

        // Initialize message manager
        messageManager = MessageManager(this)
        logger.info("Message manager initialized with language: ${messageManager.getLanguage()}")

        // Initialize database
        databaseManager = DatabaseManager(this)
        messageManager.sendRaw(Bukkit.getConsoleSender(), "database.connecting")

        val connected = databaseManager.connect()

        if (connected) {
            messageManager.sendRaw(Bukkit.getConsoleSender(), "database.connected")

            // Initialize database tables
            databaseManager.initializeTables()

            // Initialize auction manager
            auctionManager = AuctionManager(databaseManager)

            databaseConnectionFailed = false
        } else {
            messageManager.sendRaw(Bukkit.getConsoleSender(), "database.connection-failed")
            messageManager.sendRaw(Bukkit.getConsoleSender(), "database.connection-error")

            databaseConnectionFailed = true

            // Log to console with more details
            logger.severe("═══════════════════════════════════════════════════════")
            logger.severe("DATABASE CONNECTION FAILED!")
            logger.severe("AuctionMaster will not function properly without a database.")
            logger.severe("Please check your configuration and restart the server.")
            logger.severe("═══════════════════════════════════════════════════════")
        }

        // Register event listeners
        registerListeners()

        // Register commands
        registerCommands()

        logger.info("AuctionMaster v${description.version} has been enabled!")
    }

    override fun onDisable() {
        // Close database connection
        if (::databaseManager.isInitialized) {
            databaseManager.disconnect()
        }

        logger.info("AuctionMaster has been disabled!")
    }

    /**
     * Copy language files from resources if they don't exist
     */
    private fun copyLanguageFiles() {
        val languages = listOf("en", "de")

        for (lang in languages) {
            val fileName = "messages_$lang.yml"
            val file = File(dataFolder, fileName)

            if (!file.exists()) {
                saveResource(fileName, false)
                logger.info("Created language file: $fileName")
            }
        }
    }

    /**
     * Register event listeners
     */
    private fun registerListeners() {
        // Register database warning listener
        server.pluginManager.registerEvents(DatabaseWarningListener(this), this)
    }

    /**
     * Register commands
     */
    private fun registerCommands() {
        // Get Paper's lifecycle manager for command registration
        val command = AuctionCommand(this)

        // Register using Paper's command manager
        val lifecycleManager = server.pluginManager.getPlugin("Paper")?.let {
            try {
                // Use reflection to get Paper's command manager
                val paperPlugin = Class.forName("io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager")
                // Register commands the modern way
                getCommand("auction")?.setExecutor(command)
                getCommand("auction")?.tabCompleter = command
                logger.info("Registered auction command")
            } catch (e: Exception) {
                // Fallback to traditional registration
                getCommand("auction")?.setExecutor(command)
                getCommand("auction")?.tabCompleter = command
                logger.info("Registered auction command (legacy mode)")
            }
        } ?: run {
            // Fallback if Paper plugin manager is not available
            getCommand("auction")?.setExecutor(command)
            getCommand("auction")?.tabCompleter = command
            logger.info("Registered auction command (fallback mode)")
        }
    }

    /**
     * Reload plugin configuration
     */
    fun reloadPlugin() {
        reloadConfig()
        messageManager.loadMessages()
        logger.info("Configuration reloaded")
    }
}
