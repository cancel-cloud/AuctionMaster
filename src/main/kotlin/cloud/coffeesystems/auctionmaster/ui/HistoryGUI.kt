package cloud.coffeesystems.auctionmaster.ui

import cloud.coffeesystems.auctionmaster.AuctionMaster
import cloud.coffeesystems.auctionmaster.model.AuctionHistoryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class HistoryGUI(private val plugin: AuctionMaster, private val targetPlayer: OfflinePlayer) :
        Listener {

    companion object {
        private val openGUIs = mutableMapOf<Player, HistoryGUI>()
    }

    private var currentPage = 0
    private val itemsPerPage = 45
    private lateinit var inventory: Inventory
    private lateinit var viewer: Player
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")
    private var isSwitchingPage = false
    private fun msg(key: String, vararg args: Any?) =
            plugin.messageManager.get(key, *args).decoration(TextDecoration.ITALIC, false)

    private fun msgList(key: String, vararg args: Any?): List<Component> =
            plugin.messageManager.getList(key, *args).map {
                it.decoration(TextDecoration.ITALIC, false)
            }

    private fun formatCurrency(value: Double): String = String.format(Locale.US, "%.2f", value)

    init {
        // Listener registration moved to open()
    }

    fun open(player: Player, page: Int = 0) {
        viewer = player
        currentPage = page

        val history = plugin.auctionManager.getHistory(targetPlayer.uniqueId)

        val title = msg("gui.history.title", targetPlayer.name ?: "Unknown")
        inventory = Bukkit.createInventory(null, 54, title)

        val totalPages = (history.size + itemsPerPage - 1) / itemsPerPage
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, history.size)

        for (i in startIndex until endIndex) {
            val item = history[i]
            val displayItem = createHistoryItem(item)
            inventory.setItem(i - startIndex, displayItem)
        }

        // Navigation
        if (currentPage > 0) {
            inventory.setItem(
                    45,
                    createNavigationItem(Material.ARROW, "gui.auction-house.previous-page")
            )
        }

        if (currentPage < totalPages - 1) {
            inventory.setItem(
                    53,
                    createNavigationItem(Material.ARROW, "gui.auction-house.next-page")
            )
        }

        // Close button
        inventory.setItem(49, createCloseItem())

        // Page info
        if (totalPages > 0) {
            inventory.setItem(50, createPageInfoItem(currentPage + 1, totalPages))
        }

        openGUIs[player] = this

        HandlerList.unregisterAll(this)
        plugin.server.pluginManager.registerEvents(this, plugin)

        isSwitchingPage = true
        player.openInventory(inventory)
        isSwitchingPage = false
    }

    private fun createHistoryItem(historyItem: AuctionHistoryItem): ItemStack {
        val item = historyItem.item.clone()
        val meta = item.itemMeta
        val lore =
                msgList(
                                "gui.history.item-lore",
                                formatCurrency(historyItem.price),
                                historyItem.sellerName,
                                dateFormat.format(Date(historyItem.timestamp))
                        )
                        .toMutableList()

        meta.lore(lore)
        item.itemMeta = meta
        return item
    }

    private fun createNavigationItem(material: Material, nameKey: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(msg(nameKey))
        meta.lore(msgList("$nameKey-lore"))
        item.itemMeta = meta
        return item
    }

    private fun createCloseItem(): ItemStack {
        val item = ItemStack(Material.BARRIER)
        val meta = item.itemMeta
        meta.displayName(msg("gui.auction-house.close"))
        meta.lore(msgList("gui.auction-house.close-lore"))
        item.itemMeta = meta
        return item
    }

    private fun createPageInfoItem(currentPage: Int, totalPages: Int): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta
        val pageInfo = msg("auction.list.page-info", currentPage, totalPages)
        meta.displayName(pageInfo)
        meta.lore(msgList("gui.controls.page-info-lore"))
        item.itemMeta = meta
        return item
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.whoClicked !is Player) return
        val player = event.whoClicked as Player

        if (openGUIs[player] != this) return

        // Cancel ALL clicks when this GUI is open (prevents dragging items out)
        event.isCancelled = true

        // Only handle clicks in the top inventory (our GUI)
        if (event.clickedInventory != inventory) return

        when (event.slot) {
            45 -> open(player, currentPage - 1)
            53 -> open(player, currentPage + 1)
            49 -> player.closeInventory()
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.player is Player) {
            openGUIs.remove(event.player as Player)

            if (!isSwitchingPage) {
                HandlerList.unregisterAll(this)
            }
        }
    }
}
