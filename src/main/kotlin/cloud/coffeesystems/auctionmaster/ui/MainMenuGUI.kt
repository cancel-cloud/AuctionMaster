package cloud.coffeesystems.auctionmaster.ui

import cloud.coffeesystems.auctionmaster.AuctionMaster
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/** Main menu GUI for the auction house */
class MainMenuGUI(private val plugin: AuctionMaster) : Listener {

    companion object {
        private val openGUIs = mutableMapOf<Player, MainMenuGUI>()
    }

    private lateinit var inventory: Inventory
    private lateinit var viewer: Player

    private fun msg(key: String, vararg args: Any?) =
            plugin.messageManager.get(key, *args).decoration(TextDecoration.ITALIC, false)

    private fun msgList(key: String, vararg args: Any?): List<Component> =
            plugin.messageManager.getList(key, *args).map {
                it.decoration(TextDecoration.ITALIC, false)
            }

    fun open(player: Player) {
        viewer = player

        val title = msg("gui.main-menu.title")
        inventory = Bukkit.createInventory(null, 27, title)

        // Create menu items
        inventory.setItem(10, createBrowseAllItem(player))
        inventory.setItem(12, createYourListingsItem(player))
        inventory.setItem(14, createPastItemsItem(player))
        inventory.setItem(16, createCreateAuctionItem(player))

        // Fill background with glass panes
        val filler = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val fillerMeta = filler.itemMeta
        fillerMeta.displayName(Component.empty())
        filler.itemMeta = fillerMeta

        for (i in 0 until 27) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler)
            }
        }

        openGUIs[player] = this

        HandlerList.unregisterAll(this)
        plugin.server.pluginManager.registerEvents(this, plugin)

        player.openInventory(inventory)
    }

    private fun createBrowseAllItem(player: Player): ItemStack {
        val item = ItemStack(Material.COMPASS)
        val meta = item.itemMeta

        meta.displayName(msg("gui.main-menu.browse-all.name"))

        val activeCount = plugin.auctionManager.getActiveAuctions().size
        val lore = msgList("gui.main-menu.browse-all.lore", activeCount)

        meta.lore(lore)
        item.itemMeta = meta
        return item
    }

    private fun createYourListingsItem(player: Player): ItemStack {
        val item = ItemStack(Material.CHEST)
        val meta = item.itemMeta

        val count = plugin.auctionManager.getActiveAuctionCountNotExpired(player.uniqueId)
        meta.displayName(msg("gui.main-menu.your-listings.name"))

        val lore = msgList("gui.main-menu.your-listings.lore", count).toMutableList()

        val expiringSoon =
                plugin.auctionManager
                        .getAuctionsBySeller(player.uniqueId)
                        .count { it.getRemainingTime() < 3600000 }

        if (expiringSoon > 0) {
            lore.add(msg("gui.main-menu.your-listings.expiring", expiringSoon))
        }

        meta.lore(lore)
        item.itemMeta = meta
        return item
    }

    private fun createPastItemsItem(player: Player): ItemStack {
        val item = ItemStack(Material.BOOK)
        val meta = item.itemMeta

        val name = msg("gui.main-menu.past-items.name")
        meta.displayName(name)

        val soldCount = plugin.auctionManager.getHistory(player.uniqueId).size

        val expiredCount = plugin.auctionManager.getPendingExpiredItems(player.uniqueId).size

        val lore = msgList("gui.main-menu.past-items.lore", soldCount, expiredCount).toMutableList()

        if (expiredCount > 0) {
            lore.add(msg("gui.main-menu.past-items.pending", expiredCount))
        }

        meta.lore(lore)
        item.itemMeta = meta
        return item
    }

    private fun createCreateAuctionItem(player: Player): ItemStack {
        val item = ItemStack(Material.DIAMOND)
        val meta = item.itemMeta

        val name = msg("gui.main-menu.create-auction.name")
        meta.displayName(name)

        val lore = msgList("gui.main-menu.create-auction.lore").toMutableList()

        meta.lore(lore)
        item.itemMeta = meta
        return item
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.whoClicked !is Player) return
        val player = event.whoClicked as Player

        if (openGUIs[player] != this) return

        // Cancel ALL clicks
        event.isCancelled = true

        // Only handle clicks in the top inventory
        if (event.clickedInventory != inventory) return

        // Verify inventory and player
        if (!::inventory.isInitialized || !::viewer.isInitialized || player != viewer) return

        when (event.slot) {
            10 -> handleBrowseAll(player)
            12 -> handleYourListings(player)
            14 -> handlePastItems(player)
            16 -> handleCreateAuction(player)
        }
    }

    private fun handleBrowseAll(player: Player) {
        player.closeInventory()
        AuctionGUI(plugin).open(player)
    }

    private fun handleYourListings(player: Player) {
        player.closeInventory()
        MyListingsGUI(plugin).open(player)
    }

    private fun handlePastItems(player: Player) {
        player.closeInventory()
        PastItemsGUI(plugin).open(player)
    }

    private fun handleCreateAuction(player: Player) {
        val item = player.inventory.itemInMainHand

        if (item.type == Material.AIR) {
            plugin.messageManager.send(player, "auction.create.no-item")
            return
        }

        // Check if item is blacklisted
        val blacklist = plugin.config.getStringList("auction.blacklisted-items")
        if (blacklist.contains(item.type.name)) {
            plugin.messageManager.send(player, "auction.create.blacklisted-item")
            return
        }

        player.closeInventory()

        // Remove item from hand and open creation GUI
        player.inventory.setItemInMainHand(null)
        CreateAuctionGUI(plugin, item.clone()).open(player)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.player is Player) {
            val player = event.player as Player
            if (openGUIs[player] == this) {
                openGUIs.remove(player)
                HandlerList.unregisterAll(this)
            }
        }
    }
}
