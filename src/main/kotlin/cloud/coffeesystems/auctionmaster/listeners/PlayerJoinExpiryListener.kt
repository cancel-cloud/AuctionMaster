package cloud.coffeesystems.auctionmaster.listeners

import cloud.coffeesystems.auctionmaster.AuctionMaster
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

/** Listener to check for expired auctions when a player joins */
class PlayerJoinExpiryListener(private val plugin: AuctionMaster) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // Check if expiry check on join is enabled
        if (!plugin.config.getBoolean("auction.expiry-check-on-join", true)) {
            return
        }

        // Run async to avoid blocking login
        plugin.server.scheduler.runTaskAsynchronously(
                plugin,
                Runnable {
                    try {
                        // Get player's expired auctions
                        val expiredAuctions =
                                plugin.auctionManager.getExpiredAuctionsForSeller(player.uniqueId)

                        if (expiredAuctions.isEmpty()) {
                            return@Runnable
                        }

                        // Move expired auctions to pending items
                        for (auction in expiredAuctions) {
                            plugin.auctionManager.moveToExpired(
                                    auction.id,
                                    player.uniqueId,
                                    player.name
                            )
                            plugin.logger.info(
                                    "Moved auction ${auction.id} to expired for ${player.name}"
                            )
                        }

                        // Count pending payments (items sold while offline)
                        val pendingSales =
                                plugin.server.scheduler
                                        .callSyncMethod(plugin) {
                                            // Access database synchronously
                                            plugin.auctionManager.getPendingExpiredItems(
                                                    player.uniqueId
                                            )
                                        }
                                        .get()
                                        .size

                        // Send join summary (sync on main thread)
                        if (plugin.config.getBoolean("notifications.join-summary", true)) {
                            plugin.server.scheduler.runTask(
                                    plugin,
                                    Runnable {
                                        sendJoinSummary(
                                                player,
                                                pendingSales,
                                                expiredAuctions.size
                                        )
                                    }
                            )
                        }
                    } catch (e: Exception) {
                        plugin.logger.warning(
                                "Error processing expired auctions for ${player.name}: ${e.message}"
                        )
                        e.printStackTrace()
                    }
                }
        )
    }

    private fun sendJoinSummary(
            player: org.bukkit.entity.Player,
            soldCount: Int,
            expiredCount: Int
    ) {
        if (soldCount == 0 && expiredCount == 0) {
            return
        }

        // Send formatted summary message
        val message = plugin.messageManager.get("notifications.join-summary", soldCount, expiredCount)

        player.sendMessage(Component.empty())
        player.sendMessage(plugin.messageManager.get("prefix").append(Component.text(" ")).append(message))
        player.sendMessage(Component.empty())

        // Send action bar as well
        if (plugin.config.getBoolean("notifications.expiry-notification", true)) {
            if (expiredCount > 0) {
                player.sendActionBar(
                        plugin.messageManager.get("notifications.expired-summary", expiredCount)
                )
            }
        }
    }
}
