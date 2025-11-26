package cloud.coffeesystems.auctionmaster.bootstrap

import cloud.coffeesystems.auctionmaster.AuctionMaster
import cloud.coffeesystems.auctionmaster.listeners.DatabaseWarningListener
import cloud.coffeesystems.auctionmaster.listeners.PendingPaymentListener
import cloud.coffeesystems.auctionmaster.listeners.PlayerJoinExpiryListener

class ListenerRegistrar(private val plugin: AuctionMaster) {

    fun registerCoreListeners() {
        val pluginManager = plugin.server.pluginManager

        pluginManager.registerEvents(DatabaseWarningListener(plugin), plugin)
        pluginManager.registerEvents(PendingPaymentListener(plugin), plugin)
        pluginManager.registerEvents(PlayerJoinExpiryListener(plugin), plugin)
    }
}
