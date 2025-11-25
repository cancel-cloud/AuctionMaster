package cloud.coffeesystems.auctionmaster.listeners

import cloud.coffeesystems.auctionmaster.AuctionMaster
import cloud.coffeesystems.auctionmaster.database.PendingPayments
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class PendingPaymentListener(private val plugin: AuctionMaster) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // Check for pending payments asynchronously
        plugin.server.scheduler.runTaskAsynchronously(
                plugin,
                Runnable {
                    val pendingPayments = mutableListOf<PendingPayment>()

                    transaction(plugin.databaseManager.getDatabase()) {
                        PendingPayments.selectAll()
                                .where { PendingPayments.sellerUuid eq player.uniqueId.toString() }
                                .forEach { row ->
                                    pendingPayments.add(
                                            PendingPayment(
                                                    id = row[PendingPayments.id],
                                                    itemName = row[PendingPayments.itemName],
                                                    amount = row[PendingPayments.amount],
                                                    timestamp = row[PendingPayments.timestamp]
                                            )
                                    )
                                }
                    }

                    // Process payments on main thread
                    if (pendingPayments.isNotEmpty()) {
                        plugin.server.scheduler.runTask(
                                plugin,
                                Runnable {
                                    for (payment in pendingPayments) {
                                        // Deposit money
                                        if (plugin.economyHook.deposit(player, payment.amount)) {
                                            // Send notification
                                            plugin.messageManager.send(
                                                    player,
                                                    "auction-sold.notification",
                                                    payment.itemName,
                                                    payment.amount
                                            )

                                            // Remove from database
                                            plugin.server.scheduler.runTaskAsynchronously(
                                                    plugin,
                                                    Runnable {
                                                        transaction(
                                                                plugin.databaseManager.getDatabase()
                                                        ) {
                                                            PendingPayments.deleteWhere {
                                                                PendingPayments.id eq payment.id
                                                            }
                                                        }
                                                    }
                                            )
                                        }
                                    }
                                }
                        )
                    }
                }
        )
    }

    data class PendingPayment(
            val id: Int,
            val itemName: String,
            val amount: Double,
            val timestamp: Long
    )
}
