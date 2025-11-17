package database

import database.schema.Auctions
import database.schema.Bids
import database.schema.Claims
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

/**
 * Manages database connections and schema creation
 */
object DatabaseManager {
    private var database: Database? = null

    /**
     * Initialize the database connection
     */
    fun initialize(dataFolder: File) {
        val dbFile = File(dataFolder, "database/auctions.db")
        dbFile.parentFile.mkdirs()

        database = Database.connect(
            url = "jdbc:h2:file:${dbFile.absolutePath};MODE=MySQL",
            driver = "org.h2.Driver"
        )

        // Create tables if they don't exist
        transaction(database) {
            SchemaUtils.create(Auctions, Bids, Claims)
        }
    }

    /**
     * Get the database instance
     */
    fun getDatabase(): Database {
        return database ?: throw IllegalStateException("Database not initialized")
    }

    /**
     * Close the database connection
     */
    fun close() {
        // H2 embedded doesn't need explicit closing in most cases
        // but we can do cleanup here if needed
    }

    /**
     * Drop all tables (for testing/development)
     */
    fun dropTables() {
        transaction(database) {
            SchemaUtils.drop(Claims, Bids, Auctions)
        }
    }
}
