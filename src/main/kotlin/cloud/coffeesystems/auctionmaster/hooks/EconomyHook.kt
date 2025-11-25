package cloud.coffeesystems.auctionmaster.hooks

import cloud.coffeesystems.auctionmaster.AuctionMaster
import net.milkbowl.vault.economy.Economy
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.RegisteredServiceProvider

class EconomyHook(private val plugin: AuctionMaster) {

    private var economy: Economy? = null

    init {
        setupEconomy()
    }

    private fun setupEconomy(): Boolean {
        if (plugin.server.pluginManager.getPlugin("Vault") == null) {
            plugin.logger.warning("Vault not found! Economy features will be disabled.")
            return false
        }

        val rsp: RegisteredServiceProvider<Economy>? =
                plugin.server.servicesManager.getRegistration(Economy::class.java)
        if (rsp == null) {
            plugin.logger.warning("No Economy provider found! Economy features will be disabled.")
            return false
        }

        economy = rsp.provider
        plugin.logger.info("Economy hooked: ${economy?.name}")
        return true
    }

    fun isAvailable(): Boolean {
        return economy != null
    }

    fun getBalance(player: Player): Double {
        return economy?.getBalance(player) ?: 0.0
    }

    fun withdraw(player: Player, amount: Double): Boolean {
        if (economy == null) return false
        val response = economy!!.withdrawPlayer(player, amount)
        return response.transactionSuccess()
    }

    fun deposit(player: Player, amount: Double): Boolean {
        if (economy == null) return false
        val response = economy!!.depositPlayer(player, amount)
        return response.transactionSuccess()
    }

    fun depositOffline(player: OfflinePlayer, amount: Double): Boolean {
        if (economy == null) return false
        val response = economy!!.depositPlayer(player, amount)
        return response.transactionSuccess()
    }
}
