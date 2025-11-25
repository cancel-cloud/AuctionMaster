package cloud.coffeesystems.auctionmaster.ui

import cloud.coffeesystems.auctionmaster.AuctionMaster
import cloud.coffeesystems.auctionmaster.model.Auction
import cloud.coffeesystems.auctionmaster.model.AuctionStatus
import cloud.coffeesystems.auctionmaster.util.TimeUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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

/** GUI for viewing player's own active auctions */
class MyListingsGUI(private val plugin: AuctionMaster) : Listener {

    companion object {
        private val openGUIs = mutableMapOf<Player, MyListingsGUI>()
    }

    private var currentPage = 0
    private val itemsPerPage = 45
    private lateinit var inventory: Inventory
    private lateinit var viewer: Player
    private var currentAuctions: List<Auction> = emptyList()
    private var isSwitchingPage = false

    fun open(player: Player, page: Int = 0) {
        plugin.logger.info("Opening MyListingsGUI for ${player.name}, page $page")

        viewer = player
        currentPage = page

        // Get player's active auctions (not expired)
        currentAuctions =
                plugin.auctionManager.getAuctionsBySeller(player.uniqueId).filter {
                    !it.isExpired()
                }

        val title = plugin.messageManager.get("gui.my-listings.title")
        inventory = Bukkit.createInventory(null, 54, title)

        // Calculate pagination
        val totalPages = (currentAuctions.size + itemsPerPage - 1) / itemsPerPage
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, currentAuctions.size)

        // Add auction items
        for (i in startIndex until endIndex) {
            val auction = currentAuctions[i]
            val displayItem = createListingItem(auction)
            inventory.setItem(i - startIndex, displayItem)
        }

        // Add navigation buttons
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

        // Add refresh button
        inventory.setItem(49, createRefreshItem())

        // Add back button
        inventory.setItem(48, createBackItem())

        // Add page info
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

    private fun createListingItem(auction: Auction): ItemStack {
        val item = auction.item.clone()
        val meta = item.itemMeta

        // Create lore with auction info
        val lore = mutableListOf<Component>()
        lore.add(Component.empty())

        // Price
        lore.add(plugin.messageManager.get("gui.auction-house.price", auction.price))

        // Time left with color coding
        val timeLeft = auction.getRemainingTime()
        val timeFormatted = TimeUtil.formatTime(plugin, timeLeft)
        val timeColor =
                when {
                    timeLeft < 3600000 -> NamedTextColor.RED // < 1 hour
                    timeLeft < 10800000 -> NamedTextColor.GOLD // < 3 hours
                    else -> NamedTextColor.GREEN
                }

        lore.add(
                Component.text("Time left: ", NamedTextColor.GRAY)
                        .append(Component.text(timeFormatted, timeColor))
                        .decoration(TextDecoration.ITALIC, false)
        )

        if (timeLeft < 3600000) {
            lore.add(
                    Component.text("⚠ EXPIRING SOON!", NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false)
                            .decoration(TextDecoration.BOLD, true)
            )
        }

        // Instructions
        lore.add(Component.empty())
        lore.add(plugin.messageManager.get("gui.my-listings.click-to-cancel"))

        meta.lore(lore)
        item.itemMeta = meta

        return item
    }

    private fun createNavigationItem(material: Material, nameKey: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta

        val name = plugin.messageManager.get(nameKey)
        meta.displayName(name.decoration(TextDecoration.ITALIC, false))

        item.itemMeta = meta
        return item
    }

    private fun createRefreshItem(): ItemStack {
        val item = ItemStack(Material.EMERALD)
        val meta = item.itemMeta

        val name = plugin.messageManager.get("gui.auction-house.refresh")
        meta.displayName(name.decoration(TextDecoration.ITALIC, false))

        item.itemMeta = meta
        return item
    }

    private fun createBackItem(): ItemStack {
        val item = ItemStack(Material.BARRIER)
        val meta = item.itemMeta

        meta.displayName(
                Component.text("Back to Menu", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false)
        )

        item.itemMeta = meta
        return item
    }

    private fun createPageInfoItem(currentPage: Int, totalPages: Int): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta

        val pageInfo = plugin.messageManager.get("auction.list.page-info", currentPage, totalPages)
        meta.displayName(pageInfo.decoration(TextDecoration.ITALIC, false))

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

        val slot = event.slot

        // Handle navigation
        when (slot) {
            45 -> open(player, currentPage - 1) // Previous page
            53 -> open(player, currentPage + 1) // Next page
            49 -> open(player, currentPage) // Refresh
            48 -> {
                // Back to main menu
                player.closeInventory()
                MainMenuGUI(plugin).open(player)
            }
            50 -> return // Page info (do nothing)
            else -> handleAuctionClick(player, slot)
        }
    }

    private fun handleAuctionClick(player: Player, slot: Int) {
        val auctionIndex = currentPage * itemsPerPage + slot

        if (auctionIndex >= currentAuctions.size) return

        val auction = currentAuctions[auctionIndex]

        // Open cancel confirmation
        player.closeInventory()
        ConfirmationGUI(plugin, auction, ConfirmationGUI.Mode.CANCEL, null).open(player)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.player is Player) {
            val player = event.player as Player

            // Only remove from map and unregister if not switching pages
            if (!isSwitchingPage) {
                openGUIs.remove(player)
                HandlerList.unregisterAll(this)
            }
        }
    }
}
