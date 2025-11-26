package cloud.coffeesystems.auctionmaster.migration

import cloud.coffeesystems.auctionmaster.AuctionMaster
import cloud.coffeesystems.auctionmaster.database.Auctions
import cloud.coffeesystems.auctionmaster.database.DatabaseManager
import cloud.coffeesystems.auctionmaster.database.Transactions
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class DatabaseMigrationService(private val plugin: AuctionMaster) {

        fun migrate(sender: CommandSender, confirm: Boolean = false) {
                val databaseManager = plugin.databaseManager
                if (databaseManager.getDatabaseType() != DatabaseManager.DatabaseType.SQLITE) {
                        plugin.messageManager.send(sender, "migration.sqlite-only")
                        return
                }

                if (!confirm) {
                        if (!plugin.lockdownMode) {
                                plugin.setLockdown(true)
                                plugin.messageManager.send(sender, "migration.lockdown-enabled")
                        }

                        Bukkit.getScheduler()
                                .runTaskAsynchronously(
                                        plugin,
                                        Runnable {
                                                try {
                                                        val auctionCount =
                                                                transaction(databaseManager.getDatabase()) {
                                                                        Auctions.selectAll().count().toInt()
                                                                }
                                                        val transactionCount =
                                                                transaction(databaseManager.getDatabase()) {
                                                                        Transactions.selectAll().count().toInt()
                                                                }

                                                        plugin.messageManager.send(
                                                                sender,
                                                                "migration.stats.header"
                                                        )
                                                        plugin.messageManager.send(
                                                                sender,
                                                                "migration.stats.auctions",
                                                                auctionCount
                                                        )
                                                        plugin.messageManager.send(
                                                                sender,
                                                                "migration.stats.transactions",
                                                                transactionCount
                                                        )

                                                        try {
                                                                val pgDatabase =
                                                                        databaseManager.getPostgreSQLDatabase()
                                                                if (pgDatabase != null) {
                                                                        transaction(pgDatabase) {
                                                                                exec("SELECT 1")
                                                                        }

                                                                        plugin.messageManager.send(
                                                                                sender,
                                                                                "migration.postgres.verified"
                                                                        )

                                                                        val confirmPrefix =
                                                                                plugin.messageManager.get(
                                                                                        "migration.confirm.prefix"
                                                                                )
                                                                        val confirmCommand =
                                                                                plugin.messageManager.get(
                                                                                        "migration.confirm.command"
                                                                                )
                                                                        val confirmSuffix =
                                                                                plugin.messageManager.get(
                                                                                        "migration.confirm.suffix"
                                                                                )
                                                                        val confirmWarning =
                                                                                plugin.messageManager.get(
                                                                                        "migration.confirm.warning"
                                                                                )

                                                                        val confirmComponent =
                                                                                plugin.messageManager
                                                                                        .get("prefix")
                                                                                        .append(Component.text(" "))
                                                                                        .append(confirmPrefix)
                                                                                        .append(
                                                                                                confirmCommand
                                                                                                        .clickEvent(
                                                                                                                ClickEvent.runCommand(
                                                                                                                        "/auction migrate confirm"
                                                                                                                )
                                                                                                        )
                                                                                                        .hoverEvent(
                                                                                                                HoverEvent.showText(
                                                                                                                        confirmWarning
                                                                                                                )
                                                                                                        )
                                                                                        )
                                                                                        .append(confirmSuffix)

                                                                        sender.sendMessage(confirmComponent)
                                                                } else {
                                                                        plugin.messageManager.send(
                                                                                sender,
                                                                                "migration.postgres.failed"
                                                                        )
                                                                }
                                                        } catch (e: Exception) {
                                                                plugin.messageManager.send(
                                                                        sender,
                                                                        "migration.postgres.error",
                                                                        e.message ?: "Unknown error"
                                                                )
                                                                e.printStackTrace()
                                                        }
                                                } catch (e: Exception) {
                                                        plugin.messageManager.send(
                                                                sender,
                                                                "migration.stats.error",
                                                                e.message ?: "Unknown error"
                                                        )
                                                        e.printStackTrace()
                                                }
                                        }
                                )
                } else {
                        performMigration(sender)
                }
        }

        private fun performMigration(sender: CommandSender) {
                plugin.messageManager.send(sender, "migration.starting")

                Bukkit.getScheduler()
                        .runTaskAsynchronously(
                                plugin,
                                Runnable {
                                        try {
                                                val auctionsData =
                                                        transaction(plugin.databaseManager.getDatabase()) {
                                                                Auctions.selectAll().map { row ->
                                                                        mapOf(
                                                                                "id" to row[Auctions.id],
                                                                                "seller_uuid" to row[Auctions.sellerUuid],
                                                                                "seller_name" to row[Auctions.sellerName],
                                                                                "item_data" to row[Auctions.itemData],
                                                                                "price" to row[Auctions.price],
                                                                                "created_at" to row[Auctions.createdAt],
                                                                                "expires_at" to row[Auctions.expiresAt],
                                                                                "status" to row[Auctions.status]
                                                                        )
                                                                }
                                                        }

                                                val transactionsData =
                                                        transaction(plugin.databaseManager.getDatabase()) {
                                                                Transactions.selectAll().map { row ->
                                                                        mapOf(
                                                                                "id" to row[Transactions.id],
                                                                                "auction_id" to row[Transactions.auctionId],
                                                                                "buyer_uuid" to row[Transactions.buyerUuid],
                                                                                "buyer_name" to row[Transactions.buyerName],
                                                                                "price" to row[Transactions.price],
                                                                                "timestamp" to row[Transactions.timestamp]
                                                                        )
                                                                }
                                                        }

                                                val pgDatabase =
                                                        plugin.databaseManager.getPostgreSQLDatabase()
                                                transaction(pgDatabase) {
                                                        var auctionCount = 0
                                                        auctionsData.forEach { data ->
                                                                Auctions.insert {
                                                                        it[Auctions.id] = data["id"] as Int
                                                                        it[Auctions.sellerUuid] =
                                                                                data["seller_uuid"] as String
                                                                        it[Auctions.sellerName] =
                                                                                data["seller_name"] as String
                                                                        it[Auctions.itemData] = data["item_data"] as String
                                                                        it[Auctions.price] = data["price"] as Double
                                                                        it[Auctions.createdAt] =
                                                                                data["created_at"] as Long
                                                                        it[Auctions.expiresAt] =
                                                                                data["expires_at"] as Long
                                                                        it[Auctions.status] = data["status"] as String
                                                                }
                                                                auctionCount++

                                                                if (auctionCount % 100 == 0) {
                                                                        plugin.messageManager.send(
                                                                                sender,
                                                                                "migration.progress.auctions",
                                                                                auctionCount
                                                                        )
                                                                }
                                                        }
                                                        plugin.messageManager.send(
                                                                sender,
                                                                "migration.auctions.complete",
                                                                auctionCount
                                                        )

                                                        var transCount = 0
                                                        transactionsData.forEach { data ->
                                                                Transactions.insert {
                                                                        it[Transactions.id] = data["id"] as Int
                                                                        it[Transactions.auctionId] =
                                                                                data["auction_id"] as Int
                                                                        it[Transactions.buyerUuid] =
                                                                                data["buyer_uuid"] as String
                                                                        it[Transactions.buyerName] =
                                                                                data["buyer_name"] as String
                                                                        it[Transactions.price] = data["price"] as Double
                                                                        it[Transactions.timestamp] =
                                                                                data["timestamp"] as Long
                                                                }
                                                                transCount++

                                                                if (transCount % 100 == 0) {
                                                                        plugin.messageManager.send(
                                                                                sender,
                                                                                "migration.progress.transactions",
                                                                                transCount
                                                                        )
                                                                }
                                                        }
                                                        plugin.messageManager.send(
                                                                sender,
                                                                "migration.transactions.complete",
                                                                transCount
                                                        )

                                                        exec(
                                                                "SELECT setval('auctions_id_seq', (SELECT MAX(id) FROM auctions))"
                                                        )
                                                        exec(
                                                                "SELECT setval('transactions_id_seq', (SELECT MAX(id) FROM transactions))"
                                                        )

                                                        plugin.messageManager.send(sender, "migration.success")
                                                }
                                        } catch (e: Exception) {
                                                plugin.messageManager.send(
                                                        sender,
                                                        "migration.failed",
                                                        e.message ?: "Unknown error"
                                                )
                                                e.printStackTrace()
                                        }
                                }
                        )
        }
}
