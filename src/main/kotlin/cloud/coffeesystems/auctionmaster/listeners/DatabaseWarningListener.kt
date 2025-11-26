package cloud.coffeesystems.auctionmaster.listeners

import cloud.coffeesystems.auctionmaster.AuctionMaster
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class DatabaseWarningListener(private val plugin: AuctionMaster) : Listener {

        @EventHandler
        fun onPlayerJoin(event: PlayerJoinEvent) {
                val player = event.player

                if (!plugin.config.getBoolean("notifications.notify-ops-on-join", true)) {
                        return
                }

                if (!player.hasPermission("auctionmaster.notify")) {
                        return
                }

                if (!plugin.databaseConnectionFailed) {
                        return
                }

                plugin.server.scheduler.runTaskLater(
                        plugin,
                        Runnable {
                                if (player.isOnline) {
                                        plugin.messageManager.send(player, "database.operator-warning")
                                }
                        },
                        20L
                )
        }
}
