package cloud.coffeesystems.auctionmaster.listeners

import cloud.coffeesystems.auctionmaster.AuctionMaster
import cloud.coffeesystems.auctionmaster.database.PendingPayments
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.event.HoverEvent
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class PendingPaymentListener(private val plugin: AuctionMaster) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // Refresh notification settings cache for multi-server sync
        plugin.notificationSettings.refreshSettingsAsync(player.uniqueId)

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
                                    timestamp = row[PendingPayments.timestamp],
                                    paid = row[PendingPayments.paid]
                                )
                            )
                        }
                }

                // Process payments on main thread with delay
                if (pendingPayments.isNotEmpty()) {
                    plugin.server.scheduler.runTaskLater(
                        plugin,
                        Runnable {
                            // Re-verify player is online
                            if (!player.isOnline) return@Runnable

                            val processedPayments = mutableListOf<PendingPayment>()
                            var totalAmount = 0.0

                            for (payment in pendingPayments) {
                                var success = true

                                // Deposit money if not already paid
                                if (!payment.paid) {
                                    success =
                                        plugin.economyHook.deposit(
                                            player,
                                            payment.amount
                                        )
                                }

                                if (success) {
                                    processedPayments.add(payment)
                                    totalAmount += payment.amount
                                }
                            }

                            if (processedPayments.isEmpty()) return@Runnable

                            // Send notifications
                            if (processedPayments.size > 3) {
                                // Summary message
                                val itemLines = processedPayments.map { payment ->
                                    plugin.messageManager.get(
                                        "auction-sold.summary-item-format",
                                        payment.itemName,
                                        payment.amount
                                    )
                                }

                                val hoverContent = Component.join(JoinConfiguration.newlines(), itemLines)
                                val summaryMsg = plugin.messageManager.get(
                                    "auction-sold.summary",
                                    processedPayments.size,
                                    totalAmount
                                ).hoverEvent(HoverEvent.showText(hoverContent))

                                plugin.messageManager.sendComponent(player, summaryMsg)

                            } else {
                                // Individual messages
                                for (payment in processedPayments) {
                                    plugin.messageManager.send(
                                        player,
                                        "auction-sold.notification",
                                        payment.itemName,
                                        payment.amount
                                    )
                                }
                            }

                            plugin.notificationSettings.playLoginPayoutSound(player)

                            // Remove from database (async)
                            plugin.server.scheduler.runTaskAsynchronously(
                                plugin,
                                Runnable {
                                    transaction(
                                        plugin.databaseManager.getDatabase()
                                    ) {
                                        for (payment in processedPayments) {
                                            PendingPayments.deleteWhere {
                                                PendingPayments.id eq payment.id
                                            }
                                        }
                                    }
                                }
                            )
                        },
                        60L // 3 seconds delay
                    )
                }
            }
        )
    }

    data class PendingPayment(
            val id: Int,
            val itemName: String,
            val amount: Double,
            val timestamp: Long,
            val paid: Boolean
    )
}
