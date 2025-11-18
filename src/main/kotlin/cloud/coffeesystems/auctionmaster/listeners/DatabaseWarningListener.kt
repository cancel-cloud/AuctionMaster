package cloud.coffeesystems.auctionmaster.listeners

import cloud.coffeesystems.auctionmaster.AuctionMaster
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

/**
 * Listens for player joins to notify operators about database issues
 */
class DatabaseWarningListener(private val plugin: AuctionMaster) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // Check if notifications are enabled and player is an operator
        if (plugin.config.getBoolean("notifications.notify-ops-on-join", true) &&
            player.hasPermission("auctionmaster.notify")) {

            // Check if database connection failed
            if (plugin.databaseConnectionFailed) {
                // Delay the message slightly so it appears after join messages
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    if (player.isOnline) {
                        plugin.messageManager.send(player, "database.operator-warning")
                    }
                }, 20L) // 1 second delay
            }
        }
    }
}
