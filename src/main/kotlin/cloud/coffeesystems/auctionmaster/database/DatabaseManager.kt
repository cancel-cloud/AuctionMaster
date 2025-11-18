package cloud.coffeesystems.auctionmaster.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.plugin.Plugin
import java.io.File
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.TimeUnit

/**
 * Manages database connections using HikariCP connection pooling
 */
class DatabaseManager(private val plugin: Plugin) {

    private var dataSource: HikariDataSource? = null
    private var databaseType: DatabaseType = DatabaseType.SQLITE
    var isConnected: Boolean = false
        private set

    enum class DatabaseType {
        SQLITE,
        MYSQL
    }

    /**
     * Initialize the database connection
     */
    fun connect(): Boolean {
        try {
            val config = plugin.config
            val dbTypeString = config.getString("database.type", "SQLITE")?.uppercase() ?: "SQLITE"
            databaseType = try {
                DatabaseType.valueOf(dbTypeString)
            } catch (e: IllegalArgumentException) {
                plugin.logger.warning("Invalid database type: $dbTypeString, using SQLITE")
                DatabaseType.SQLITE
            }

            val hikariConfig = HikariConfig()

            when (databaseType) {
                DatabaseType.SQLITE -> configureSQLite(hikariConfig)
                DatabaseType.MYSQL -> configureMySQL(hikariConfig)
            }

            // Common HikariCP settings
            hikariConfig.maximumPoolSize = config.getInt("database.pool.maximum-pool-size", 10)
            hikariConfig.minimumIdle = config.getInt("database.pool.minimum-idle", 2)
            hikariConfig.connectionTimeout = config.getLong("database.pool.connection-timeout", 30000)
            hikariConfig.idleTimeout = config.getLong("database.pool.idle-timeout", 600000)
            hikariConfig.maxLifetime = config.getLong("database.pool.max-lifetime", 1800000)

            dataSource = HikariDataSource(hikariConfig)

            // Test connection
            testConnection()

            isConnected = true
            plugin.logger.info("Successfully connected to ${databaseType.name} database")
            return true

        } catch (e: Exception) {
            isConnected = false
            plugin.logger.severe("Failed to connect to database: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Configure SQLite connection
     */
    private fun configureSQLite(config: HikariConfig) {
        val dbFile = File(plugin.dataFolder, plugin.config.getString("database.sqlite.file", "auctions.db") ?: "auctions.db")

        if (!dbFile.exists()) {
            dbFile.parentFile.mkdirs()
        }

        config.jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"
        config.driverClassName = "org.sqlite.JDBC"
        config.connectionTestQuery = "SELECT 1"

        // SQLite specific settings
        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "250")
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
    }

    /**
     * Configure MySQL connection
     */
    private fun configureMySQL(config: HikariConfig) {
        val mysqlConfig = plugin.config.getConfigurationSection("database.mysql")
            ?: throw IllegalStateException("MySQL configuration not found")

        val host = mysqlConfig.getString("host", "localhost") ?: "localhost"
        val port = mysqlConfig.getInt("port", 3306)
        val database = mysqlConfig.getString("database", "auctionmaster") ?: "auctionmaster"
        val username = mysqlConfig.getString("username", "root") ?: "root"
        val password = mysqlConfig.getString("password", "password") ?: "password"

        config.jdbcUrl = "jdbc:mysql://$host:$port/$database"
        config.username = username
        config.password = password
        config.driverClassName = "com.mysql.cj.jdbc.Driver"

        // MySQL specific settings
        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "250")
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        config.addDataSourceProperty("useServerPrepStmts", "true")
        config.addDataSourceProperty("useLocalSessionState", "true")
        config.addDataSourceProperty("rewriteBatchedStatements", "true")
        config.addDataSourceProperty("cacheResultSetMetadata", "true")
        config.addDataSourceProperty("cacheServerConfiguration", "true")
        config.addDataSourceProperty("elideSetAutoCommits", "true")
        config.addDataSourceProperty("maintainTimeStats", "false")
    }

    /**
     * Test the database connection
     */
    fun testConnection(): Boolean {
        return try {
            getConnection().use { connection ->
                connection.isValid(5)
            }
        } catch (e: SQLException) {
            plugin.logger.severe("Database connection test failed: ${e.message}")
            false
        }
    }

    /**
     * Get a connection from the pool
     */
    fun getConnection(): Connection {
        if (dataSource == null || dataSource!!.isClosed) {
            throw SQLException("Database connection is not available")
        }
        return dataSource!!.connection
    }

    /**
     * Initialize database tables
     */
    fun initializeTables() {
        try {
            getConnection().use { connection ->
                connection.createStatement().use { statement ->

                    // Auctions table
                    val createAuctionsTable = when (databaseType) {
                        DatabaseType.SQLITE -> """
                            CREATE TABLE IF NOT EXISTS auctions (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                seller_uuid TEXT NOT NULL,
                                seller_name TEXT NOT NULL,
                                item_data TEXT NOT NULL,
                                price REAL NOT NULL,
                                created_at INTEGER NOT NULL,
                                expires_at INTEGER NOT NULL,
                                status TEXT NOT NULL DEFAULT 'ACTIVE'
                            )
                        """.trimIndent()

                        DatabaseType.MYSQL -> """
                            CREATE TABLE IF NOT EXISTS auctions (
                                id INT AUTO_INCREMENT PRIMARY KEY,
                                seller_uuid VARCHAR(36) NOT NULL,
                                seller_name VARCHAR(16) NOT NULL,
                                item_data TEXT NOT NULL,
                                price DOUBLE NOT NULL,
                                created_at BIGINT NOT NULL,
                                expires_at BIGINT NOT NULL,
                                status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                                INDEX idx_status (status),
                                INDEX idx_expires (expires_at)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """.trimIndent()
                    }

                    statement.executeUpdate(createAuctionsTable)

                    // Transactions table (for history)
                    val createTransactionsTable = when (databaseType) {
                        DatabaseType.SQLITE -> """
                            CREATE TABLE IF NOT EXISTS transactions (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                auction_id INTEGER NOT NULL,
                                buyer_uuid TEXT NOT NULL,
                                buyer_name TEXT NOT NULL,
                                price REAL NOT NULL,
                                timestamp INTEGER NOT NULL
                            )
                        """.trimIndent()

                        DatabaseType.MYSQL -> """
                            CREATE TABLE IF NOT EXISTS transactions (
                                id INT AUTO_INCREMENT PRIMARY KEY,
                                auction_id INT NOT NULL,
                                buyer_uuid VARCHAR(36) NOT NULL,
                                buyer_name VARCHAR(16) NOT NULL,
                                price DOUBLE NOT NULL,
                                timestamp BIGINT NOT NULL,
                                INDEX idx_buyer (buyer_uuid),
                                INDEX idx_auction (auction_id)
                            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """.trimIndent()
                    }

                    statement.executeUpdate(createTransactionsTable)

                    plugin.logger.info("Database tables initialized successfully")
                }
            }
        } catch (e: SQLException) {
            plugin.logger.severe("Failed to initialize database tables: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Close the database connection
     */
    fun disconnect() {
        try {
            dataSource?.close()
            isConnected = false
            plugin.logger.info("Database connection closed")
        } catch (e: Exception) {
            plugin.logger.warning("Error closing database: ${e.message}")
        }
    }

    /**
     * Get the database type
     */
    fun getDatabaseType(): DatabaseType = databaseType
}
