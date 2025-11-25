package cloud.coffeesystems.auctionmaster.ui

import cloud.coffeesystems.auctionmaster.AuctionMaster
import cloud.coffeesystems.auctionmaster.model.AuctionHistoryItem
import cloud.coffeesystems.auctionmaster.model.AuctionStatus
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
import java.text.SimpleDateFormat

/** GUI for viewing past auction items (sold + expired) with filters */
class PastItemsGUI(private val plugin: AuctionMaster) : Listener {

    companion object {
        private val openGUIs = mutableMapOf<Player, PastItemsGUI>()
    }

    enum class FilterMode {
        ALL,
        SOLD,
        EXPIRED
    }

    private var currentPage = 0
    private val itemsPerPage = 36 // Leave room for filter buttons
    private lateinit var inventory: Inventory
    private lateinit var viewer: Player
    private var currentFilter = FilterMode.ALL
    private var isSwitchingPage = false

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm")

    // Store combined data
    private data class PastItem(
            val item: ItemStack,
            val price: Double,
            val timestamp: Long,
            val otherPlayer: String, // Buyer or seller depending on context
            val type: FilterMode, // SOLD or EXPIRED
            val pendingId: Int? = null // For claimable expired items
    )

    private var currentItems: List<PastItem> = emptyList()

    fun open(player: Player, page: Int = 0, filter: FilterMode = FilterMode.ALL) {
        viewer = player
        currentPage = page
        currentFilter = filter

        val title = plugin.messageManager.get("gui.past-items.title")
        inventory = Bukkit.createInventory(null, 54, title)

        // Gather all past items
        currentItems = gatherPastItems(player)

        // Calculate pagination
        val totalPages = (currentItems.size + itemsPerPage - 1) / itemsPerPage
        val startIndex = currentPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, currentItems.size)

        // Add filter buttons at top (slots 0, 1, 2)
        inventory.setItem(0, createFilterButton(FilterMode.ALL))
        inventory.setItem(1, createFilterButton(FilterMode.SOLD))
        inventory.setItem(2, createFilterButton(FilterMode.EXPIRED))

        // Add items starting from row 2 (slot 9)
        for (i in startIndex until endIndex) {
            val pastItem = currentItems[i]
            val displayItem = createPastItem(pastItem)
            inventory.setItem(9 + (i - startIndex), displayItem)
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

    private fun gatherPastItems(player: Player): List<PastItem> {
        val items = mutableListOf<PastItem>()

        // Add sold items (from purchase history)
        if (currentFilter == FilterMode.ALL || currentFilter == FilterMode.SOLD) {
            val history = plugin.auctionManager.getHistory(player.uniqueId)
            items.addAll(
                    history.map {
                        PastItem(
                                item = it.item,
                                price = it.price,
                                timestamp = it.timestamp,
                                otherPlayer = it.sellerName,
                                type = FilterMode.SOLD
                        )
                    }
            )
        }

        // Add expired items (from pending expired items)
        if (currentFilter == FilterMode.ALL || currentFilter == FilterMode.EXPIRED) {
            val pendingExpired = plugin.auctionManager.getPendingExpiredItems(player.uniqueId)
            items.addAll(
                    pendingExpired.map {(id, item) ->
                        PastItem(
                                item = item,
                                price = 0.0, // No price for expired items
                                timestamp = System.currentTimeMillis(),
                                otherPlayer = "",
                                type = FilterMode.EXPIRED,
                                pendingId = id
                        )
                    }
            )
        }

        // Sort by timestamp (newest first)
        return items.sortedByDescending { it.timestamp }
    }

    private fun createFilterButton(mode: FilterMode): ItemStack {
        val isActive = mode == currentFilter

        val item =
                when (mode) {
                    FilterMode.ALL ->
                            ItemStack(if (isActive) Material.WHITE_STAINED_GLASS else Material.GRAY_STAINED_GLASS)
                    FilterMode.SOLD ->
                            ItemStack(if (isActive) Material.LIME_STAINED_GLASS else Material.GREEN_STAINED_GLASS)
                    FilterMode.EXPIRED ->
                            ItemStack(if (isActive) Material.ORANGE_STAINED_GLASS else Material.RED_STAINED_GLASS)
                }

        val meta = item.itemMeta

        val name =
                when (mode) {
                    FilterMode.ALL -> plugin.messageManager.get("gui.past-items.filter-all")
                    FilterMode.SOLD -> plugin.messageManager.get("gui.past-items.filter-sold")
                    FilterMode.EXPIRED -> plugin.messageManager.get("gui.past-items.filter-expired")
                }

        meta.displayName(name.decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, isActive))

        val lore = mutableListOf<Component>()
        lore.add(Component.empty())

        if (isActive) {
            lore.add(
                    Component.text("✓ Active", NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false)
            )
        } else {
            lore.add(
                    Component.text("Click to filter", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            )
        }

        meta.lore(lore)
        item.itemMeta = meta
        return item
    }

    private fun createPastItem(pastItem: PastItem): ItemStack {
        val item = pastItem.item.clone()
        val meta = item.itemMeta

        val lore = meta.lore()?.toMutableList() ?: mutableListOf()
        lore.add(Component.empty())

        when (pastItem.type) {
            FilterMode.SOLD -> {
                lore.add(
                        Component.text("✓ PURCHASED", NamedTextColor.GREEN)
                                .decoration(TextDecoration.ITALIC, false)
                                .decoration(TextDecoration.BOLD, true)
                )
                lore.add(
                        Component.text("Price: $${pastItem.price}", NamedTextColor.GOLD)
                                .decoration(TextDecoration.ITALIC, false)
                )
                lore.add(
                        Component.text("Seller: ${pastItem.otherPlayer}", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                )
                lore.add(
                        Component.text("Date: ${dateFormat.format(pastItem.timestamp)}", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)
                )
            }
            FilterMode.EXPIRED -> {
                lore.add(
                        Component.text("✗ EXPIRED", NamedTextColor.RED)
                                .decoration(TextDecoration.ITALIC, false)
                                .decoration(TextDecoration.BOLD, true)
                )
                lore.add(Component.empty())
                lore.add(plugin.messageManager.get("gui.past-items.click-to-claim"))
            }
            else -> {}
        }

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

        // Handle filter buttons
        when (slot) {
            0 -> open(player, 0, FilterMode.ALL)
            1 -> open(player, 0, FilterMode.SOLD)
            2 -> open(player, 0, FilterMode.EXPIRED)
            45 -> open(player, currentPage - 1, currentFilter) // Previous page
            53 -> open(player, currentPage + 1, currentFilter) // Next page
            49 -> open(player, currentPage, currentFilter) // Refresh
            48 -> {
                // Back to main menu
                player.closeInventory()
                MainMenuGUI(plugin).open(player)
            }
            50 -> return // Page info (do nothing)
            else -> {
                // Handle item clicks (slots 9-44)
                if (slot >= 9 && slot < 45) {
                    handleItemClick(player, slot - 9)
                }
            }
        }
    }

    private fun handleItemClick(player: Player, relativeSlot: Int) {
        val itemIndex = currentPage * itemsPerPage + relativeSlot

        if (itemIndex >= currentItems.size) return

        val pastItem = currentItems[itemIndex]

        // Only handle expired items (claimable)
        if (pastItem.type == FilterMode.EXPIRED && pastItem.pendingId != null) {
            // Check if inventory has space
            if (player.inventory.firstEmpty() == -1) {
                player.sendMessage(
                        Component.text("Your inventory is full!", NamedTextColor.RED)
                )
                return
            }

            // Give item to player
            player.inventory.addItem(pastItem.item)

            // Remove from pending
            plugin.auctionManager.claimExpiredItem(pastItem.pendingId)

            // Send success message
            player.sendMessage(plugin.messageManager.get("gui.past-items.claimed"))

            // Refresh GUI
            open(player, currentPage, currentFilter)
        }
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
