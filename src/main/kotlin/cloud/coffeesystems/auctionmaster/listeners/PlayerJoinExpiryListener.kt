package cloud.coffeesystems.auctionmaster.listeners

import cloud.coffeesystems.auctionmaster.AuctionMaster
import net.kyori.adventure.text.Component
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
                        // Get player's expired auctions (DB call - safe in async)
                        val expiredAuctions =
                                plugin.auctionManager.getExpiredAuctionsForSeller(player.uniqueId)

                        // Move expired auctions to pending items (DB calls - safe in async)
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

                        // Get pending expired items count (DB call - safe in async)
                        val pendingSales = plugin.auctionManager.getPendingExpiredItems(player.uniqueId).size

                        // Send join summary (must be on main thread)
                        if (plugin.config.getBoolean("notifications.join-summary", true)) {
                            val finalExpiredCount = expiredAuctions.size
                            plugin.server.scheduler.runTask(
                                    plugin,
                                    Runnable {
                                        if (player.isOnline) {
                                            sendJoinSummary(player, pendingSales, finalExpiredCount)
                                        }
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
        plugin.messageManager.sendComponent(player, message)
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
