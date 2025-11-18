package cloud.coffeesystems.auctionmaster.ui

import cloud.coffeesystems.auctionmaster.AuctionMaster
import cloud.coffeesystems.auctionmaster.model.Auction
import cloud.coffeesystems.auctionmaster.model.AuctionStatus
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

/**
 * GUI for displaying auctions
 */
class AuctionGUI(private val plugin: AuctionMaster) : Listener {

    companion object {
        private val openGUIs = mutableMapOf<Player, AuctionGUI>()
    }

    private var currentPage = 0
    private val itemsPerPage = plugin.config.getInt("gui.items-per-page", 45)
    private lateinit var inventory: Inventory
    private lateinit var viewer: Player

    init {
        // Register event listener
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /**
     * Open the auction GUI for a player
     */
    fun open(player: Player, page: Int = 0) {
        viewer = player
        currentPage = page

        // Get all active auctions
        val auctions = plugin.auctionManager.getActiveAuctions()

        // Create inventory
        val title = plugin.messageManager.getRaw("gui.auction-house.title")
        inventory = Bukkit.createInventory(null, 54, Component.text(title))

        // Calculate pagination
        val totalPages = (auctions.size + itemsPerPage - 1) / itemsPerPage
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, auctions.size)

        // Add auction items
        for (i in startIndex until endIndex) {
            val auction = auctions[i]
            val displayItem = createAuctionItem(auction)
            inventory.setItem(i - startIndex, displayItem)
        }

        // Add navigation buttons
        if (currentPage > 0) {
            inventory.setItem(45, createNavigationItem(Material.ARROW, "gui.auction-house.previous-page", -1))
        }

        if (currentPage < totalPages - 1) {
            inventory.setItem(53, createNavigationItem(Material.ARROW, "gui.auction-house.next-page", 1))
        }

        // Add refresh button
        inventory.setItem(49, createRefreshItem())

        // Add close button
        inventory.setItem(48, createCloseItem())

        // Add page info
        if (totalPages > 0) {
            inventory.setItem(50, createPageInfoItem(currentPage + 1, totalPages))
        }

        // Open inventory
        openGUIs[player] = this
        player.openInventory(inventory)
    }

    /**
     * Create an item representing an auction
     */
    private fun createAuctionItem(auction: Auction): ItemStack {
        val item = auction.item.clone()
        val meta = item.itemMeta

        // Create lore with auction info
        val lore = mutableListOf<Component>()

        // Price
        val priceText = plugin.messageManager.getRaw("gui.auction-house.price", auction.price)
        lore.add(Component.text(priceText).color(NamedTextColor.GOLD))

        // Seller
        val sellerText = plugin.messageManager.getRaw("gui.auction-house.seller", auction.sellerName)
        lore.add(Component.text(sellerText).color(NamedTextColor.GRAY))

        // Time left
        val timeLeft = formatTime(auction.getRemainingTime())
        val timeText = plugin.messageManager.getRaw("gui.auction-house.time-left", timeLeft)
        lore.add(Component.text(timeText).color(NamedTextColor.YELLOW))

        // Add empty line
        lore.add(Component.empty())

        // Click to buy
        if (auction.isSeller(viewer.uniqueId)) {
            val cancelText = plugin.messageManager.getRaw("gui.auction-house.click-to-cancel")
            lore.add(Component.text(cancelText).color(NamedTextColor.RED))
        } else {
            val buyText = plugin.messageManager.getRaw("gui.auction-house.click-to-buy")
            lore.add(Component.text(buyText).color(NamedTextColor.GREEN))
        }

        meta.lore(lore)
        item.itemMeta = meta

        return item
    }

    /**
     * Create a navigation item
     */
    private fun createNavigationItem(material: Material, nameKey: String, pageChange: Int): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta

        val name = plugin.messageManager.getRaw(nameKey)
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false))

        item.itemMeta = meta
        return item
    }

    /**
     * Create refresh button
     */
    private fun createRefreshItem(): ItemStack {
        val item = ItemStack(Material.EMERALD)
        val meta = item.itemMeta

        val name = plugin.messageManager.getRaw("gui.auction-house.refresh")
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false))

        item.itemMeta = meta
        return item
    }

    /**
     * Create close button
     */
    private fun createCloseItem(): ItemStack {
        val item = ItemStack(Material.BARRIER)
        val meta = item.itemMeta

        val name = plugin.messageManager.getRaw("gui.auction-house.close")
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false))

        item.itemMeta = meta
        return item
    }

    /**
     * Create page info item
     */
    private fun createPageInfoItem(currentPage: Int, totalPages: Int): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta

        val pageInfo = plugin.messageManager.getRaw("auction.list.page-info", currentPage, totalPages)
        meta.displayName(Component.text(pageInfo).decoration(TextDecoration.ITALIC, false))

        item.itemMeta = meta
        return item
    }

    /**
     * Handle inventory clicks
     */
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.whoClicked !is Player) return
        val player = event.whoClicked as Player

        if (openGUIs[player] != this) return
        if (event.clickedInventory != inventory) return

        event.isCancelled = true

        val slot = event.slot

        // Handle navigation
        when (slot) {
            45 -> open(player, currentPage - 1) // Previous page
            53 -> open(player, currentPage + 1) // Next page
            49 -> open(player, currentPage) // Refresh
            48 -> player.closeInventory() // Close
            50 -> return // Page info (do nothing)
            else -> handleAuctionClick(player, slot)
        }
    }

    /**
     * Handle clicking on an auction item
     */
    private fun handleAuctionClick(player: Player, slot: Int) {
        val auctions = plugin.auctionManager.getActiveAuctions()
        val auctionIndex = currentPage * itemsPerPage + slot

        if (auctionIndex >= auctions.size) return

        val auction = auctions[auctionIndex]

        // Check if player is clicking their own auction
        if (auction.isSeller(player.uniqueId)) {
            // Cancel auction
            player.inventory.addItem(auction.item)
            plugin.auctionManager.updateAuctionStatus(auction.id, AuctionStatus.CANCELLED)
            plugin.messageManager.send(player, "auction.cancel.success")
            player.closeInventory()
            return
        }

        // Try to buy auction
        if (!player.hasPermission("auctionmaster.buy")) {
            plugin.messageManager.send(player, "no-permission")
            return
        }

        // Check if player has enough money (placeholder - requires economy plugin)
        // For now, just complete the purchase

        // Check inventory space
        if (player.inventory.firstEmpty() == -1) {
            plugin.messageManager.send(player, "auction.buy.insufficient-funds", 0) // Placeholder
            return
        }

        // Give item to buyer
        player.inventory.addItem(auction.item)

        // Update auction status
        plugin.auctionManager.updateAuctionStatus(auction.id, AuctionStatus.SOLD)

        // Record transaction
        plugin.auctionManager.recordTransaction(auction.id, player.uniqueId, player.name, auction.price)

        // Send success message
        val itemName = if (auction.item.itemMeta.hasDisplayName()) {
            auction.item.itemMeta.displayName().toString()
        } else {
            auction.item.type.name.replace("_", " ").lowercase()
                .split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        }

        plugin.messageManager.send(player, "auction.buy.success", itemName, auction.price)

        // Notify seller if online
        if (plugin.config.getBoolean("notifications.notify-seller-on-sale", true)) {
            val seller = Bukkit.getPlayer(auction.sellerUuid)
            seller?.let {
                plugin.messageManager.send(it, "auction.buy.seller-notification", itemName, auction.price)
            }
        }

        player.closeInventory()
    }

    /**
     * Handle inventory close
     */
    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.player is Player) {
            val player = event.player as Player
            openGUIs.remove(player)
        }
    }

    /**
     * Format time remaining
     */
    private fun formatTime(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> plugin.messageManager.getRaw("time.days", days)
            hours > 0 -> plugin.messageManager.getRaw("time.hours", hours)
            minutes > 0 -> plugin.messageManager.getRaw("time.minutes", minutes)
            seconds > 0 -> plugin.messageManager.getRaw("time.seconds", seconds)
            else -> plugin.messageManager.getRaw("time.expired")
        }
    }
}
