package database.schema

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Column

object Claims : UUIDTable("claims") {
    // Player information
    val playerId: Column<String> = varchar("player_id", 36)
    val playerName: Column<String> = varchar("player_name", 16)

    // Claimable items (JSON array)
    val items: Column<String> = text("items").default("[]")

    // Claimable money
    val money: Column<Double> = double("money").default(0.0)

    // Reason for claim
    val reason: Column<String> = varchar("reason", 50)

    // Timestamp
    val timestamp: Column<Long> = long("timestamp")

    init {
        // Index for faster player lookups
        index(isUnique = false, playerId)
    }
}
