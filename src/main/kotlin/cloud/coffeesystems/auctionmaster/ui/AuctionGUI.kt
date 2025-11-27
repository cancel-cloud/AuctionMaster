package cloud.coffeesystems.auctionmaster.ui

import cloud.coffeesystems.auctionmaster.AuctionMaster
import cloud.coffeesystems.auctionmaster.model.Auction
import cloud.coffeesystems.auctionmaster.util.TimeUtil
import java.util.Locale
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

/** GUI for displaying auctions */
class AuctionGUI(private val plugin: AuctionMaster) : Listener {

    companion object {
        private val openGUIs = mutableMapOf<Player, AuctionGUI>()
    }

    private var currentPage = 0
    private val itemsPerPage = plugin.config.getInt("gui.items-per-page", 45)
    private lateinit var inventory: Inventory
    private lateinit var viewer: Player
    private var sellerFilter: String? = null
    private var currentAuctions: List<Auction> = emptyList()

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

    /** Open the auction GUI for a player */
    fun open(player: Player, page: Int = 0, sellerFilter: String? = null) {
        plugin.logger.info("Opening AuctionGUI for ${player.name}, page $page, GUI instance: ${System.identityHashCode(this)}")

        viewer = player
        currentPage = page
        this.sellerFilter = sellerFilter

        // Get auctions (filtered or all) and store for pagination
        currentAuctions =
                if (sellerFilter != null) {
                    plugin.auctionManager.getAuctionsBySeller(sellerFilter)
                } else {
                    plugin.auctionManager.getActiveAuctions()
                }
        val auctions = currentAuctions

        plugin.logger.info("Found ${auctions.size} auctions")

        // Create inventory with dynamic title
        val baseTitle = msg("gui.auction-house.title")
        val title =
                if (sellerFilter != null) {
                    baseTitle.append(
                            msg("gui.auction-house.filtered-suffix", sellerFilter)
                    )
                } else {
                    baseTitle
                }
        inventory = Bukkit.createInventory(null, 54, title)

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

        // Add close button
        inventory.setItem(48, createCloseItem())

        // Add page info
        if (totalPages > 0) {
            inventory.setItem(50, createPageInfoItem(currentPage + 1, totalPages))
        }

        // Open inventory
        openGUIs[player] = this
        plugin.logger.info("Registered GUI ${System.identityHashCode(this)} for ${player.name}")

        // Manage listener
        HandlerList.unregisterAll(this)
        plugin.server.pluginManager.registerEvents(this, plugin)

        isSwitchingPage = true
        plugin.logger.info("Switching page flag set to true, opening inventory")
        player.openInventory(inventory)
        isSwitchingPage = false
        plugin.logger.info("Switching page flag set to false, inventory opened")
    }

    /** Create an item representing an auction */
    private fun createAuctionItem(auction: Auction): ItemStack {
        val item = auction.item.clone()
        val meta = item.itemMeta

        // Create lore with auction info
        val lore = mutableListOf<Component>()

        // Price
        lore.add(msg("gui.auction-house.price", formatCurrency(auction.price)))

        // Seller
        lore.add(msg("gui.auction-house.seller", auction.sellerName))

        // Time left
        val timeLeft = TimeUtil.formatTime(plugin, auction.getRemainingTime())
        lore.add(msg("gui.auction-house.time-left", timeLeft))

        lore.add(Component.empty())

        // Click to buy
        if (auction.isSeller(viewer.uniqueId)) {
            lore.add(msg("gui.auction-house.click-to-cancel"))
        } else {
            lore.add(msg("gui.auction-house.click-to-buy"))
        }

        meta.lore(lore)
        item.itemMeta = meta

        return item
    }

    /** Create a navigation item */
    private fun createNavigationItem(material: Material, nameKey: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta

        meta.displayName(msg(nameKey))
        val lore = msgList("$nameKey-lore")
        meta.lore(lore)

        item.itemMeta = meta
        return item
    }

    /** Create refresh button */
    private fun createRefreshItem(): ItemStack {
        val item = ItemStack(Material.EMERALD)
        val meta = item.itemMeta

        meta.displayName(msg("gui.auction-house.refresh"))
        meta.lore(msgList("gui.auction-house.refresh-lore"))

        item.itemMeta = meta
        return item
    }

    /** Create close button */
    private fun createCloseItem(): ItemStack {
        val item = ItemStack(Material.BARRIER)
        val meta = item.itemMeta

        meta.displayName(msg("gui.auction-house.close"))
        meta.lore(msgList("gui.auction-house.close-lore"))

        item.itemMeta = meta
        return item
    }

    /** Create page info item */
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

        // Debug: check if player is in map
        val registeredGUI = openGUIs[player]
        plugin.logger.info("Click detected - Player: ${player.name}, This GUI: ${System.identityHashCode(this)}, Registered GUI: ${if (registeredGUI != null) System.identityHashCode(registeredGUI) else "null"}")

        if (openGUIs[player] != this) {
            plugin.logger.warning("Click ignored - GUI mismatch for ${player.name}")
            return
        }

        // Cancel ALL clicks when this GUI is open (prevents dragging items out)
        event.isCancelled = true

        // Only handle clicks in the top inventory (our GUI)
        if (event.clickedInventory != inventory) return

        val slot = event.slot
        plugin.logger.info("Processing click in slot $slot on page $currentPage")

        // Handle navigation
        when (slot) {
            45 -> {
                plugin.logger.info("Previous page clicked")
                open(player, currentPage - 1, sellerFilter)
            } // Previous page
            53 -> {
                plugin.logger.info("Next page clicked")
                open(player, currentPage + 1, sellerFilter)
            } // Next page
            49 -> {
                plugin.logger.info("Refresh clicked")
                open(player, currentPage, sellerFilter)
            } // Refresh
            48 -> player.closeInventory() // Close
            50 -> return // Page info (do nothing)
            else -> handleAuctionClick(player, slot)
        }
    }

    /** Handle clicking on an auction item */
    private fun handleAuctionClick(player: Player, slot: Int) {
        // Use stored auctions list to respect filters and pagination
        val auctionIndex = currentPage * itemsPerPage + slot

        if (auctionIndex >= currentAuctions.size) return

        val auction = currentAuctions[auctionIndex]

        // Check if player is clicking their own auction
        if (auction.isSeller(player.uniqueId)) {
            // Open cancel confirmation
            ConfirmationGUI(plugin, auction, ConfirmationGUI.Mode.CANCEL, this).open(player)
            return
        }

        // Try to buy auction
        if (!player.hasPermission("auctionmaster.buy")) {
            plugin.messageManager.send(player, "no-permission")
            return
        }

        // Open buy confirmation GUI
        ConfirmationGUI(plugin, auction, ConfirmationGUI.Mode.BUY, this).open(player)
    }

    /** Handle inventory close */
    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.player is Player) {
            val player = event.player as Player
            plugin.logger.info("Inventory closed for ${player.name}, switching page: $isSwitchingPage, GUI: ${System.identityHashCode(this)}")

            // Only remove from map and unregister if not switching pages
            if (!isSwitchingPage) {
                plugin.logger.info("Removing ${player.name} from openGUIs and unregistering listeners")
                openGUIs.remove(player)
                HandlerList.unregisterAll(this)
            } else {
                plugin.logger.info("Keeping ${player.name} in openGUIs (page switch in progress)")
            }
        }
    }
}
