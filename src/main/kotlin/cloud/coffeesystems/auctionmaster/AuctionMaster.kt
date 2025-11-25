package cloud.coffeesystems.auctionmaster

import cloud.coffeesystems.auctionmaster.commands.AuctionCommand
import cloud.coffeesystems.auctionmaster.database.AuctionManager
import cloud.coffeesystems.auctionmaster.database.Auctions
import cloud.coffeesystems.auctionmaster.database.DatabaseManager
import cloud.coffeesystems.auctionmaster.database.Transactions
import cloud.coffeesystems.auctionmaster.hooks.EconomyHook
import cloud.coffeesystems.auctionmaster.listeners.DatabaseWarningListener
import cloud.coffeesystems.auctionmaster.util.MessageManager
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import java.io.File
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

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

                // Save default config
                saveDefaultConfig()

                // Copy language files if they don't exist
                copyLanguageFiles()

                // Initialize message manager
                messageManager = MessageManager(this)
                logger.info(
                        "Message manager initialized with language: ${messageManager.getLanguage()}"
                )

                // Initialize economy hook
                economyHook = EconomyHook(this)

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

                // Register event listeners
                registerListeners()

                // Register commands
                registerCommands()

                logger.info("AuctionMaster v${pluginMeta.version} has been enabled!")
        }

        override fun onDisable() {
                // Close database connection
                if (::databaseManager.isInitialized) {
                        databaseManager.disconnect()
                }

                logger.info("AuctionMaster has been disabled!")
        }

        /** Copy language files from resources if they don't exist */
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

        /** Register event listeners */
        private fun registerListeners() {
                // Register database warning listener
                server.pluginManager.registerEvents(DatabaseWarningListener(this), this)

                // Register pending payment listener
                server.pluginManager.registerEvents(
                        cloud.coffeesystems.auctionmaster.listeners.PendingPaymentListener(this),
                        this
                )

                // Register player join expiry listener
                server.pluginManager.registerEvents(
                        cloud.coffeesystems.auctionmaster.listeners.PlayerJoinExpiryListener(this),
                        this
                )
        }

        /** Register commands */
        /** Register commands */
        @Suppress("UnstableApiUsage")
        private fun registerCommands() {
                val command = AuctionCommand(this)

                // Modern Paper Command Registration
                val lifecycleManager = this.lifecycleManager
                lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
                        val commands = event.registrar()

                        // Helper to delegate to the existing CommandExecutor
                        fun runDelegate(
                                ctx:
                                        com.mojang.brigadier.context.CommandContext<
                                                io.papermc.paper.command.brigadier.CommandSourceStack>,
                                args: Array<String>
                        ): Int {
                                val sender = ctx.source.sender
                                val input = ctx.input.trim()
                                val label = input.substringBefore(" ").ifEmpty { "auction" }

                                // Create a dummy command since AuctionCommand doesn't use it
                                val dummyCommand =
                                        object : org.bukkit.command.Command(label) {
                                                override fun execute(
                                                        sender: org.bukkit.command.CommandSender,
                                                        commandLabel: String,
                                                        args: Array<out String>
                                                ): Boolean {
                                                        return true
                                                }
                                        }
                                command.onCommand(sender, dummyCommand, label, args)
                                return Command.SINGLE_SUCCESS
                        }

                        val root =
                                Commands.literal("auction")
                                        .requires { source ->
                                                source.sender.hasPermission("auctionmaster.use")
                                        }
                                        .executes { ctx -> runDelegate(ctx, emptyArray()) }

                        // Create
                        root.then(
                                Commands.literal("create")
                                        .executes { ctx ->
                                                // No arguments - opens GUI
                                                runDelegate(ctx, arrayOf("create"))
                                        }
                                        .then(
                                                Commands.argument(
                                                                "price",
                                                                DoubleArgumentType.doubleArg(0.0)
                                                        )
                                                        .executes { ctx ->
                                                                val price =
                                                                        DoubleArgumentType
                                                                                .getDouble(
                                                                                        ctx,
                                                                                        "price"
                                                                                )
                                                                runDelegate(
                                                                        ctx,
                                                                        arrayOf(
                                                                                "create",
                                                                                price.toString()
                                                                        )
                                                                )
                                                        }
                                                        .then(
                                                                Commands.argument(
                                                                                "duration",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .executes { ctx ->
                                                                                val price =
                                                                                        DoubleArgumentType
                                                                                                .getDouble(
                                                                                                        ctx,
                                                                                                        "price"
                                                                                                )
                                                                                val duration =
                                                                                        StringArgumentType
                                                                                                .getString(
                                                                                                        ctx,
                                                                                                        "duration"
                                                                                                )
                                                                                runDelegate(
                                                                                        ctx,
                                                                                        arrayOf(
                                                                                                "create",
                                                                                                price.toString(),
                                                                                                duration
                                                                                        )
                                                                                )
                                                                        }
                                                        )
                                        )
                        )

                        // List/Browse/Shop
                        val listHandler =
                                Command<io.papermc.paper.command.brigadier.CommandSourceStack> { ctx
                                        ->
                                        runDelegate(ctx, arrayOf("list"))
                                }
                        root.then(Commands.literal("list").executes(listHandler))
                        root.then(Commands.literal("browse").executes(listHandler))
                        root.then(Commands.literal("shop").executes(listHandler))

                        // View
                        root.then(
                                Commands.literal("view")
                                        .then(
                                                Commands.argument(
                                                                "player",
                                                                com.mojang.brigadier.arguments
                                                                        .StringArgumentType.word()
                                                        )
                                                        .suggests { ctx, builder ->
                                                                // Get unique seller names from database
                                                                val sellers = auctionManager.getUniqueSellers()
                                                                val input = builder.remaining.lowercase()

                                                                // Filter sellers by input (case-insensitive)
                                                                sellers
                                                                        .filter { it.lowercase().startsWith(input) }
                                                                        .forEach { builder.suggest(it) }

                                                                builder.buildFuture()
                                                        }
                                                        .executes { ctx ->
                                                                val player =
                                                                        com.mojang.brigadier
                                                                                .arguments
                                                                                .StringArgumentType
                                                                                .getString(
                                                                                        ctx,
                                                                                        "player"
                                                                                )
                                                                runDelegate(
                                                                        ctx,
                                                                        arrayOf("view", player)
                                                                )
                                                        }
                                        )
                        )

                        // Cancel
                        val cancelNode =
                                Commands.literal("cancel")
                                        .executes { ctx -> runDelegate(ctx, arrayOf("cancel")) }
                                        .then(
                                                Commands.argument(
                                                                "id",
                                                                IntegerArgumentType.integer()
                                                        )
                                                        .executes { ctx ->
                                                                val id =
                                                                        IntegerArgumentType
                                                                                .getInteger(
                                                                                        ctx,
                                                                                        "id"
                                                                                )
                                                                runDelegate(
                                                                        ctx,
                                                                        arrayOf(
                                                                                "cancel",
                                                                                id.toString()
                                                                        )
                                                                )
                                                        }
                                        )
                                        .build()

                        root.then(cancelNode)
                        // Remove (redirect to cancel)
                        root.then(Commands.literal("remove").redirect(cancelNode))

                        // Info
                        root.then(
                                Commands.literal("info")
                                        .then(
                                                Commands.argument(
                                                                "id",
                                                                IntegerArgumentType.integer()
                                                        )
                                                        .executes { ctx ->
                                                                val id =
                                                                        IntegerArgumentType
                                                                                .getInteger(
                                                                                        ctx,
                                                                                        "id"
                                                                                )
                                                                runDelegate(
                                                                        ctx,
                                                                        arrayOf(
                                                                                "info",
                                                                                id.toString()
                                                                        )
                                                                )
                                                        }
                                        )
                        )

                        // Help
                        root.then(
                                Commands.literal("help").executes { ctx ->
                                        runDelegate(ctx, arrayOf("help"))
                                }
                        )

                        // Reload
                        root.then(
                                Commands.literal("reload")
                                        .requires { source ->
                                                source.sender.hasPermission("auctionmaster.admin")
                                        }
                                        .executes { ctx -> runDelegate(ctx, arrayOf("reload")) }
                        )

                        // Clear
                        root.then(
                                Commands.literal("clear")
                                        .requires { source ->
                                                source.sender.hasPermission("auctionmaster.admin")
                                        }
                                        .executes { ctx -> runDelegate(ctx, arrayOf("clear")) }
                        )

                        // History
                        root.then(
                                Commands.literal("history")
                                        .executes { ctx -> runDelegate(ctx, arrayOf("history")) }
                                        .then(
                                                Commands.argument("player", StringArgumentType.word())
                                                        .suggests { ctx, builder ->
                                                                // Suggest all online players for history lookup
                                                                val input = builder.remaining.lowercase()
                                                                server.onlinePlayers
                                                                        .map { it.name }
                                                                        .filter { it.lowercase().startsWith(input) }
                                                                        .forEach { builder.suggest(it) }

                                                                builder.buildFuture()
                                                        }
                                                        .executes { ctx ->
                                                                val player =
                                                                        StringArgumentType
                                                                                .getString(
                                                                                        ctx,
                                                                                        "player"
                                                                                )
                                                                runDelegate(
                                                                        ctx,
                                                                        arrayOf("history", player)
                                                                )
                                                        }
                                        )
                        )

                        commands.register(
                                root.build(),
                                "Main auction command",
                                listOf("auctionhouse", "auctions", "am", "ah")
                        )
                }
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
                if (databaseManager.getDatabaseType() != DatabaseManager.DatabaseType.SQLITE) {
                        messageManager.send(sender, "migration.sqlite-only")
                        return
                }

                if (!confirm) {
                        // Step 1: Pre-check and Stats

                        // Enable lockdown if not already enabled
                        if (!lockdownMode) {
                                setLockdown(true)
                                sender.sendMessage(
                                        net.kyori.adventure.text.Component.text(
                                                "Lockdown mode enabled for migration.",
                                                net.kyori.adventure.text.format.NamedTextColor
                                                        .YELLOW
                                        )
                                )
                        }

                        // Get stats
                        Bukkit.getScheduler()
                                .runTaskAsynchronously(
                                        this,
                                        Runnable {
                                                try {
                                                        val auctionCount =
                                                                org.jetbrains.exposed.sql
                                                                        .transactions.transaction(
                                                                        databaseManager
                                                                                .getDatabase()
                                                                ) {
                                                                        cloud.coffeesystems
                                                                                .auctionmaster
                                                                                .database.Auctions
                                                                                .selectAll()
                                                                                .count()
                                                                                .toInt()
                                                                }
                                                        val transactionCount =
                                                                org.jetbrains.exposed.sql
                                                                        .transactions.transaction(
                                                                        databaseManager
                                                                                .getDatabase()
                                                                ) {
                                                                        cloud.coffeesystems
                                                                                .auctionmaster
                                                                                .database
                                                                                .Transactions
                                                                                .selectAll()
                                                                                .count()
                                                                                .toInt()
                                                                }

                                                        sender.sendMessage(
                                                                net.kyori.adventure.text.Component
                                                                        .text(
                                                                                "Migration Stats:",
                                                                                net.kyori.adventure
                                                                                        .text.format
                                                                                        .NamedTextColor
                                                                                        .GOLD
                                                                        )
                                                        )
                                                        sender.sendMessage(
                                                                net.kyori.adventure.text.Component
                                                                        .text(
                                                                                "- Auctions: $auctionCount",
                                                                                net.kyori.adventure
                                                                                        .text.format
                                                                                        .NamedTextColor
                                                                                        .YELLOW
                                                                        )
                                                        )
                                                        sender.sendMessage(
                                                                net.kyori.adventure.text.Component
                                                                        .text(
                                                                                "- Transactions: $transactionCount",
                                                                                net.kyori.adventure
                                                                                        .text.format
                                                                                        .NamedTextColor
                                                                                        .YELLOW
                                                                        )
                                                        )

                                                        // Test PostgreSQL connection
                                                        sender.sendMessage(
                                                                net.kyori.adventure.text.Component
                                                                        .text(
                                                                                "Testing PostgreSQL connection...",
                                                                                net.kyori.adventure
                                                                                        .text.format
                                                                                        .NamedTextColor
                                                                                        .GRAY
                                                                        )
                                                        )

                                                        try {
                                                                val pgDatabase =
                                                                        databaseManager
                                                                                .getPostgreSQLDatabase()
                                                                val isValid =
                                                                        org.jetbrains.exposed.sql
                                                                                .transactions
                                                                                .transaction(
                                                                                        pgDatabase
                                                                                ) {
                                                                                        // Simple
                                                                                        // connection test
                                                                                        exec(
                                                                                                "SELECT 1"
                                                                                        ) {}
                                                                                        true
                                                                                }
                                                                if (isValid) {
                                                                        sender.sendMessage(
                                                                                net.kyori.adventure
                                                                                        .text
                                                                                        .Component
                                                                                        .text(
                                                                                                "PostgreSQL connection successful!",
                                                                                                net.kyori
                                                                                                        .adventure
                                                                                                        .text
                                                                                                        .format
                                                                                                        .NamedTextColor
                                                                                                        .GREEN
                                                                                        )
                                                                        )

                                                                        val confirmComponent =
                                                                                net.kyori.adventure
                                                                                        .text
                                                                                        .Component
                                                                                        .text(
                                                                                                "Click here to CONFIRM MIGRATION",
                                                                                                net.kyori
                                                                                                        .adventure
                                                                                                        .text
                                                                                                        .format
                                                                                                        .NamedTextColor
                                                                                                        .RED,
                                                                                                net.kyori
                                                                                                        .adventure
                                                                                                        .text
                                                                                                        .format
                                                                                                        .TextDecoration
                                                                                                        .BOLD
                                                                                        )
                                                                                        .clickEvent(
                                                                                                net.kyori
                                                                                                        .adventure
                                                                                                        .text
                                                                                                        .event
                                                                                                        .ClickEvent
                                                                                                        .runCommand(
                                                                                                                "/auction migrate confirm"
                                                                                                        )
                                                                                        )
                                                                                        .hoverEvent(
                                                                                                net.kyori
                                                                                                        .adventure
                                                                                                        .text
                                                                                                        .event
                                                                                                        .HoverEvent
                                                                                                        .showText(
                                                                                                                net.kyori
                                                                                                                        .adventure
                                                                                                                        .text
                                                                                                                        .Component
                                                                                                                        .text(
                                                                                                                                "This will overwrite data in the target database!",
                                                                                                                                net.kyori
                                                                                                                                        .adventure
                                                                                                                                        .text
                                                                                                                                        .format
                                                                                                                                        .NamedTextColor
                                                                                                                                        .RED
                                                                                                                        )
                                                                                                        )
                                                                                        )

                                                                        sender.sendMessage(
                                                                                confirmComponent
                                                                        )
                                                                } else {
                                                                        sender.sendMessage(
                                                                                net.kyori.adventure
                                                                                        .text
                                                                                        .Component
                                                                                        .text(
                                                                                                "PostgreSQL connection failed!",
                                                                                                net.kyori
                                                                                                        .adventure
                                                                                                        .text
                                                                                                        .format
                                                                                                        .NamedTextColor
                                                                                                        .RED
                                                                                        )
                                                                        )
                                                                }
                                                        } catch (e: Exception) {
                                                                sender.sendMessage(
                                                                        net.kyori.adventure.text
                                                                                .Component.text(
                                                                                "PostgreSQL connection error: ${e.message}",
                                                                                net.kyori.adventure
                                                                                        .text.format
                                                                                        .NamedTextColor
                                                                                        .RED
                                                                        )
                                                                )
                                                                e.printStackTrace()
                                                        }
                                                } catch (e: Exception) {
                                                        sender.sendMessage(
                                                                net.kyori.adventure.text.Component
                                                                        .text(
                                                                                "Error getting stats: ${e.message}",
                                                                                net.kyori.adventure
                                                                                        .text.format
                                                                                        .NamedTextColor
                                                                                        .RED
                                                                        )
                                                        )
                                                        e.printStackTrace()
                                                }
                                        }
                                )
                } else {
                        // Step 2: Perform Migration
                        performMigration(sender)
                }
        }

        private fun performMigration(sender: org.bukkit.command.CommandSender) {
                sender.sendMessage(
                        net.kyori.adventure.text.Component.text(
                                "Starting migration...",
                                net.kyori.adventure.text.format.NamedTextColor.GREEN
                        )
                )

                Bukkit.getScheduler()
                        .runTaskAsynchronously(
                                this,
                                Runnable {
                                        try {
                                                // Get all data from SQLite
                                                val auctionsData =
                                                        org.jetbrains.exposed.sql.transactions
                                                                .transaction(
                                                                        databaseManager
                                                                                .getDatabase()
                                                                ) {
                                                                        cloud.coffeesystems
                                                                                .auctionmaster
                                                                                .database.Auctions
                                                                                .selectAll()
                                                                                .map { row ->
                                                                                        mapOf(
                                                                                                "id" to
                                                                                                        row[
                                                                                                                cloud.coffeesystems
                                                                                                                        .auctionmaster
                                                                                                                        .database
                                                                                                                        .Auctions
                                                                                                                        .id],
                                                                                                "seller_uuid" to
                                                                                                        row[
                                                                                                                cloud.coffeesystems
                                                                                                                        .auctionmaster
                                                                                                                        .database
                                                                                                                        .Auctions
                                                                                                                        .sellerUuid],
                                                                                                "seller_name" to
                                                                                                        row[
                                                                                                                cloud.coffeesystems
                                                                                                                        .auctionmaster
                                                                                                                        .database
                                                                                                                        .Auctions
                                                                                                                        .sellerName],
                                                                                                "item_data" to
                                                                                                        row[
                                                                                                                cloud.coffeesystems
                                                                                                                        .auctionmaster
                                                                                                                        .database
                                                                                                                        .Auctions
                                                                                                                        .itemData],
                                                                                                "price" to
                                                                                                        row[
                                                                                                                cloud.coffeesystems
                                                                                                                        .auctionmaster
                                                                                                                        .database
                                                                                                                        .Auctions
                                                                                                                        .price],
                                                                                                "created_at" to
                                                                                                        row[
                                                                                                                cloud.coffeesystems
                                                                                                                        .auctionmaster
                                                                                                                        .database
                                                                                                                        .Auctions
                                                                                                                        .createdAt],
                                                                                                "expires_at" to
                                                                                                        row[
                                                                                                                cloud.coffeesystems
                                                                                                                        .auctionmaster
                                                                                                                        .database
                                                                                                                        .Auctions
                                                                                                                        .expiresAt],
                                                                                                "status" to
                                                                                                        row[
                                                                                                                cloud.coffeesystems
                                                                                                                        .auctionmaster
                                                                                                                        .database
                                                                                                                        .Auctions
                                                                                                                        .status]
                                                                                        )
                                                                                }
                                                                }

                                                val transactionsData =
                                                        org.jetbrains.exposed.sql.transactions
                                                                .transaction(
                                                                        databaseManager
                                                                                .getDatabase()
                                                                ) {
                                                                        cloud.coffeesystems
                                                                                .auctionmaster
                                                                                .database
                                                                                .Transactions
                                                                                .selectAll()
                                                                                .map { row ->
                                                                                        mapOf(
                                                                                                "id" to
                                                                                                        row[
                                                                                                                cloud.coffeesystems
                                                                                                                        .auctionmaster
                                                                                                                        .database
                                                                                                                        .Transactions
                                                                                                                        .id],
                                                                                                "auction_id" to
                                                                                                        row[
                                                                                                                cloud.coffeesystems
                                                                                                                        .auctionmaster
                                                                                                                        .database
                                                                                                                        .Transactions
                                                                                                                        .auctionId],
                                                                                                "buyer_uuid" to
                                                                                                        row[
                                                                                                                cloud.coffeesystems
                                                                                                                        .auctionmaster
                                                                                                                        .database
                                                                                                                        .Transactions
                                                                                                                        .buyerUuid],
                                                                                                "buyer_name" to
                                                                                                        row[
                                                                                                                cloud.coffeesystems
                                                                                                                        .auctionmaster
                                                                                                                        .database
                                                                                                                        .Transactions
                                                                                                                        .buyerName],
                                                                                                "price" to
                                                                                                        row[
                                                                                                                cloud.coffeesystems
                                                                                                                        .auctionmaster
                                                                                                                        .database
                                                                                                                        .Transactions
                                                                                                                        .price],
                                                                                                "timestamp" to
                                                                                                        row[
                                                                                                                cloud.coffeesystems
                                                                                                                        .auctionmaster
                                                                                                                        .database
                                                                                                                        .Transactions
                                                                                                                        .timestamp]
                                                                                        )
                                                                                }
                                                                }

                                                // Insert into PostgreSQL
                                                val pgDatabase =
                                                        databaseManager.getPostgreSQLDatabase()
                                                org.jetbrains.exposed.sql.transactions.transaction(
                                                        pgDatabase
                                                ) {
                                                        // Migrate Auctions
                                                        var auctionCount = 0
                                                        auctionsData.forEach { data ->
                                                                cloud.coffeesystems.auctionmaster
                                                                        .database.Auctions.insert {
                                                                        it[
                                                                                cloud.coffeesystems
                                                                                        .auctionmaster
                                                                                        .database
                                                                                        .Auctions
                                                                                        .id] =
                                                                                data["id"] as Int
                                                                        it[
                                                                                cloud.coffeesystems
                                                                                        .auctionmaster
                                                                                        .database
                                                                                        .Auctions
                                                                                        .sellerUuid] =
                                                                                data[
                                                                                        "seller_uuid"] as
                                                                                        String
                                                                        it[
                                                                                cloud.coffeesystems
                                                                                        .auctionmaster
                                                                                        .database
                                                                                        .Auctions
                                                                                        .sellerName] =
                                                                                data[
                                                                                        "seller_name"] as
                                                                                        String
                                                                        it[
                                                                                cloud.coffeesystems
                                                                                        .auctionmaster
                                                                                        .database
                                                                                        .Auctions
                                                                                        .itemData] =
                                                                                data["item_data"] as
                                                                                        String
                                                                        it[
                                                                                cloud.coffeesystems
                                                                                        .auctionmaster
                                                                                        .database
                                                                                        .Auctions
                                                                                        .price] =
                                                                                data["price"] as
                                                                                        Double
                                                                        it[
                                                                                cloud.coffeesystems
                                                                                        .auctionmaster
                                                                                        .database
                                                                                        .Auctions
                                                                                        .createdAt] =
                                                                                data[
                                                                                        "created_at"] as
                                                                                        Long
                                                                        it[
                                                                                cloud.coffeesystems
                                                                                        .auctionmaster
                                                                                        .database
                                                                                        .Auctions
                                                                                        .expiresAt] =
                                                                                data[
                                                                                        "expires_at"] as
                                                                                        Long
                                                                        it[
                                                                                cloud.coffeesystems
                                                                                        .auctionmaster
                                                                                        .database
                                                                                        .Auctions
                                                                                        .status] =
                                                                                data["status"] as
                                                                                        String
                                                                }
                                                                auctionCount++
                                                                if (auctionCount % 100 == 0) {
                                                                        sender.sendMessage(
                                                                                net.kyori.adventure
                                                                                        .text
                                                                                        .Component
                                                                                        .text(
                                                                                                "Migrated $auctionCount auctions...",
                                                                                                net.kyori
                                                                                                        .adventure
                                                                                                        .text
                                                                                                        .format
                                                                                                        .NamedTextColor
                                                                                                        .GRAY
                                                                                        )
                                                                        )
                                                                }
                                                        }
                                                        sender.sendMessage(
                                                                net.kyori.adventure.text.Component
                                                                        .text(
                                                                                "Auctions migration completed ($auctionCount records).",
                                                                                net.kyori.adventure
                                                                                        .text.format
                                                                                        .NamedTextColor
                                                                                        .GREEN
                                                                        )
                                                        )

                                                        // Migrate Transactions
                                                        var transCount = 0
                                                        transactionsData.forEach { data ->
                                                                cloud.coffeesystems.auctionmaster
                                                                        .database.Transactions
                                                                        .insert {
                                                                                it[
                                                                                        cloud.coffeesystems
                                                                                                .auctionmaster
                                                                                                .database
                                                                                                .Transactions
                                                                                                .id] =
                                                                                        data[
                                                                                                "id"] as
                                                                                                Int
                                                                                it[
                                                                                        cloud.coffeesystems
                                                                                                .auctionmaster
                                                                                                .database
                                                                                                .Transactions
                                                                                                .auctionId] =
                                                                                        data[
                                                                                                "auction_id"] as
                                                                                                Int
                                                                                it[
                                                                                        cloud.coffeesystems
                                                                                                .auctionmaster
                                                                                                .database
                                                                                                .Transactions
                                                                                                .buyerUuid] =
                                                                                        data[
                                                                                                "buyer_uuid"] as
                                                                                                String
                                                                                it[
                                                                                        cloud.coffeesystems
                                                                                                .auctionmaster
                                                                                                .database
                                                                                                .Transactions
                                                                                                .buyerName] =
                                                                                        data[
                                                                                                "buyer_name"] as
                                                                                                String
                                                                                it[
                                                                                        cloud.coffeesystems
                                                                                                .auctionmaster
                                                                                                .database
                                                                                                .Transactions
                                                                                                .price] =
                                                                                        data[
                                                                                                "price"] as
                                                                                                Double
                                                                                it[
                                                                                        cloud.coffeesystems
                                                                                                .auctionmaster
                                                                                                .database
                                                                                                .Transactions
                                                                                                .timestamp] =
                                                                                        data[
                                                                                                "timestamp"] as
                                                                                                Long
                                                                        }
                                                                transCount++
                                                                if (transCount % 100 == 0) {
                                                                        sender.sendMessage(
                                                                                net.kyori.adventure
                                                                                        .text
                                                                                        .Component
                                                                                        .text(
                                                                                                "Migrated $transCount transactions...",
                                                                                                net.kyori
                                                                                                        .adventure
                                                                                                        .text
                                                                                                        .format
                                                                                                        .NamedTextColor
                                                                                                        .GRAY
                                                                                        )
                                                                        )
                                                                }
                                                        }
                                                        sender.sendMessage(
                                                                net.kyori.adventure.text.Component
                                                                        .text(
                                                                                "Transactions migration completed ($transCount records).",
                                                                                net.kyori.adventure
                                                                                        .text.format
                                                                                        .NamedTextColor
                                                                                        .GREEN
                                                                        )
                                                        )

                                                        // Reset sequences
                                                        exec(
                                                                "SELECT setval('auctions_id_seq', (SELECT MAX(id) FROM auctions))"
                                                        )
                                                        exec(
                                                                "SELECT setval('transactions_id_seq', (SELECT MAX(id) FROM transactions))"
                                                        )

                                                        sender.sendMessage(
                                                                net.kyori.adventure.text.Component
                                                                        .text(
                                                                                "Migration successfully completed! Please update config.yml to use POSTGRESQL and restart.",
                                                                                net.kyori.adventure
                                                                                        .text.format
                                                                                        .NamedTextColor
                                                                                        .GREEN,
                                                                                net.kyori.adventure
                                                                                        .text.format
                                                                                        .TextDecoration
                                                                                        .BOLD
                                                                        )
                                                        )
                                                }
                                        } catch (e: Exception) {
                                                sender.sendMessage(
                                                        net.kyori.adventure.text.Component.text(
                                                                "Migration failed: ${e.message}",
                                                                net.kyori.adventure.text.format
                                                                        .NamedTextColor.RED
                                                        )
                                                )
                                                e.printStackTrace()
                                        }
                                }
                        )
        }
}
