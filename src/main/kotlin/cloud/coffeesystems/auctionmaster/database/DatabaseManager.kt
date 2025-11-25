package cloud.coffeesystems.auctionmaster.database

import java.io.File
import org.bukkit.plugin.Plugin
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

/** Manages database connections using Exposed framework with HikariCP */
class DatabaseManager(private val plugin: Plugin) {

    private var database: Database? = null
    private var databaseType: DatabaseType = DatabaseType.SQLITE
    var isConnected: Boolean = false
        private set

    enum class DatabaseType {
        SQLITE,
        MYSQL,
        POSTGRESQL
    }

    /** Initialize the database connection */
    fun connect(): Boolean {
        try {
            val config = plugin.config
            val dbTypeString = config.getString("database.type", "SQLITE")?.uppercase() ?: "SQLITE"
            databaseType =
                    try {
                        DatabaseType.valueOf(dbTypeString)
                    } catch (e: IllegalArgumentException) {
                        plugin.logger.warning("Invalid database type: $dbTypeString, using SQLITE")
                        DatabaseType.SQLITE
                    }

            plugin.logger.info("Connecting to ${databaseType.name} database...")

            database =
                    when (databaseType) {
                        DatabaseType.SQLITE -> connectSQLite()
                        DatabaseType.MYSQL -> connectMySQL()
                        DatabaseType.POSTGRESQL -> connectPostgreSQL()
                    }

            // Test connection and initialize tables
            transaction(database!!) {
                SchemaUtils.create(
                        Auctions,
                        Transactions,
                        AuctionHistory,
                        PendingPayments,
                        PendingExpiredItems
                )
            }

            isConnected = true
            plugin.logger.info("Successfully connected to ${databaseType.name} database")
            plugin.logger.info("Database tables initialized successfully")
            return true
        } catch (e: Exception) {
            isConnected = false
            plugin.logger.severe("Failed to connect to database: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /** Configure and connect to SQLite database */
    private fun connectSQLite(): Database {
        val dbFile =
                File(
                        plugin.dataFolder,
                        plugin.config.getString("database.sqlite.file", "auctions.db")
                                ?: "auctions.db"
                )

        // Ensure parent directory exists
        if (!plugin.dataFolder.exists()) {
            plugin.dataFolder.mkdirs()
            plugin.logger.info("Created plugin data folder: ${plugin.dataFolder.absolutePath}")
        }

        // Create empty database file if it doesn't exist
        if (!dbFile.exists()) {
            dbFile.createNewFile()
            plugin.logger.info("Created new SQLite database file: ${dbFile.absolutePath}")
        }

        val jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
        plugin.logger.info("SQLite JDBC URL: $jdbcUrl")

        return Database.connect(url = jdbcUrl, driver = "org.sqlite.JDBC")
    }

    /** Configure and connect to MySQL database */
    private fun connectMySQL(): Database {
        val mysqlConfig =
                plugin.config.getConfigurationSection("database.mysql")
                        ?: throw IllegalStateException("MySQL configuration not found")

        val host = mysqlConfig.getString("host", "localhost") ?: "localhost"
        val port = mysqlConfig.getInt("port", 3306)
        val databaseName = mysqlConfig.getString("database", "auctionmaster") ?: "auctionmaster"
        val username = mysqlConfig.getString("username", "root") ?: "root"
        val password = mysqlConfig.getString("password", "password") ?: "password"

        val jdbcUrl = "jdbc:mysql://$host:$port/$databaseName"

        return Database.connect(
                url = jdbcUrl,
                driver = "com.mysql.cj.jdbc.Driver",
                user = username,
                password = password
        )
    }

    /** Configure and connect to PostgreSQL database */
    private fun connectPostgreSQL(): Database {
        val pgConfig =
                plugin.config.getConfigurationSection("database.postgresql")
                        ?: throw IllegalStateException("PostgreSQL configuration not found")

        val host = pgConfig.getString("host", "localhost") ?: "localhost"
        val port = pgConfig.getInt("port", 5432)
        val databaseName = pgConfig.getString("database", "auctionmaster") ?: "auctionmaster"
        val username = pgConfig.getString("username", "postgres") ?: "postgres"
        val password = pgConfig.getString("password", "password") ?: "password"

        val jdbcUrl = "jdbc:postgresql://$host:$port/$databaseName"

        return Database.connect(
                url = jdbcUrl,
                driver = "org.postgresql.Driver",
                user = username,
                password = password
        )
    }

    /** Get a standalone PostgreSQL Database instance for migration */
    fun getPostgreSQLDatabase(): Database {
        return connectPostgreSQL()
    }

    /** Test the database connection */
    fun testConnection(): Boolean {
        return try {
            transaction(database!!) {
                // Simple query to test connection
                exec("SELECT 1") {}
            }
            true
        } catch (e: Exception) {
            plugin.logger.severe("Database connection test failed: ${e.message}")
            false
        }
    }

    /** Get the database instance for transactions */
    fun getDatabase(): Database {
        return database ?: throw IllegalStateException("Database is not connected")
    }

    /** Initialize database tables */
    fun initializeTables() {
        try {
            transaction(database!!) {
                SchemaUtils.create(
                        Auctions,
                        Transactions,
                        AuctionHistory,
                        PendingPayments,
                        PendingExpiredItems
                )
            }
            plugin.logger.info("Database tables initialized successfully")
        } catch (e: Exception) {
            plugin.logger.severe("Failed to initialize database tables: ${e.message}")
            e.printStackTrace()
        }
    }

    /** Close the database connection */
    fun disconnect() {
        try {
            // Exposed doesn't have an explicit close method
            // The connection pool is managed automatically
            isConnected = false
            plugin.logger.info("Database connection closed")
        } catch (e: Exception) {
            plugin.logger.warning("Error closing database: ${e.message}")
        }
    }

    /** Get the database type */
    fun getDatabaseType(): DatabaseType = databaseType
}
