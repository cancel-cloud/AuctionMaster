package cloud.coffeesystems.auctionmaster.ui

import cloud.coffeesystems.auctionmaster.AuctionMaster
import cloud.coffeesystems.auctionmaster.model.AuctionDuration
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
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/** GUI for creating auctions interactively */
class CreateAuctionGUI(
        private val plugin: AuctionMaster,
        private val item: ItemStack,
        private val initialPrice: Double? = null,
        private val initialDuration: AuctionDuration = AuctionDuration.FOUR_HOURS
) :
        Listener {

    companion object {
        private val openGUIs = mutableMapOf<Player, CreateAuctionGUI>()
        private val awaitingPriceInput = mutableSetOf<Player>()
    }

    private lateinit var inventory: Inventory
    private lateinit var viewer: Player

    private var price: Double? = initialPrice
    private var selectedDuration: AuctionDuration = initialDuration

    fun open(player: Player) {
        viewer = player
        inventory = Bukkit.createInventory(null, 27, Component.text("Create Auction"))

        updateInventory()

        openGUIs[player] = this

        HandlerList.unregisterAll(this)
        plugin.server.pluginManager.registerEvents(this, plugin)

        player.openInventory(inventory)
    }

    private fun updateInventory() {
        inventory.clear()

        // Item being auctioned (center)
        val displayItem = item.clone()
        val meta = displayItem.itemMeta
        val lore = mutableListOf<Component>()
        lore.add(Component.empty())
        lore.add(
                Component.text("Amount: ${item.amount}", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        )
        meta.lore(lore)
        displayItem.itemMeta = meta
        inventory.setItem(13, displayItem)

        // Price button
        inventory.setItem(10, createPriceButton())

        // Duration selector (clock)
        inventory.setItem(12, createDurationButton())

        // Create auction button
        inventory.setItem(14, createConfirmButton())

        // Cancel button
        inventory.setItem(16, createCancelButton())

        // Info item (fee display)
        if (price != null) {
            inventory.setItem(22, createFeeInfoItem())
        }

        // Fill borders with glass panes
        val filler = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val fillerMeta = filler.itemMeta
        fillerMeta.displayName(Component.empty())
        filler.itemMeta = fillerMeta

        for (i in listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 15, 17, 18, 19, 20, 21, 23, 24, 25, 26)) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler)
            }
        }
    }

    private fun createPriceButton(): ItemStack {
        val item =
                ItemStack(
                        if (price != null) Material.EMERALD else Material.GOLD_INGOT
                )
        val meta = item.itemMeta

        val name =
                if (price != null) {
                    Component.text("Price: $$price", NamedTextColor.GREEN)
                } else {
                    Component.text("Set Price", NamedTextColor.YELLOW)
                }
        meta.displayName(name.decoration(TextDecoration.ITALIC, false))

        val lore = mutableListOf<Component>()
        lore.add(Component.empty())
        if (price == null) {
            lore.add(
                    Component.text("Click to set auction price", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            )
        } else {
            lore.add(
                    Component.text("Click to change price", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            )
        }

        meta.lore(lore)
        item.itemMeta = meta
        return item
    }

    private fun createDurationButton(): ItemStack {
        val item = ItemStack(Material.CLOCK)
        val meta = item.itemMeta

        meta.displayName(
                Component.text("Duration: ${selectedDuration.displayName}", NamedTextColor.AQUA)
                        .decoration(TextDecoration.ITALIC, false)
        )

        val lore = mutableListOf<Component>()
        lore.add(Component.empty())

        val baseFee = selectedDuration.getBaseFee(plugin)
        val feePercentage = selectedDuration.getFeePercentage(plugin)

        // Debug logging
        plugin.logger.info("Duration: ${selectedDuration.displayName} (${selectedDuration.configKey})")
        plugin.logger.info("Base Fee: $baseFee, Fee Percentage: $feePercentage")
        plugin.logger.info("Config enabled: ${plugin.config.getBoolean("auction.creation-fees.enabled", false)}")

        if (selectedDuration.isFree(plugin)) {
            lore.add(
                    Component.text("FREE", NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false)
                            .decoration(TextDecoration.BOLD, true)
            )
        } else {
            lore.add(
                    Component.text("Base Fee: $${"%.2f".format(baseFee)}", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false)
            )
            lore.add(
                    Component.text("+ ${feePercentage}% of price", NamedTextColor.YELLOW)
                            .decoration(TextDecoration.ITALIC, false)
            )
        }

        lore.add(Component.empty())
        lore.add(
                Component.text("Click to change duration", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        )

        meta.lore(lore)
        item.itemMeta = meta
        return item
    }

    private fun createConfirmButton(): ItemStack {
        val canCreate = price != null && price!! > 0
        val item =
                ItemStack(
                        if (canCreate) Material.LIME_TERRACOTTA
                        else Material.GRAY_TERRACOTTA
                )
        val meta = item.itemMeta

        meta.displayName(
                Component.text(
                                "Create Auction",
                                if (canCreate) NamedTextColor.GREEN else NamedTextColor.DARK_GRAY
                        )
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true)
        )

        val lore = mutableListOf<Component>()
        lore.add(Component.empty())

        if (!canCreate) {
            lore.add(
                    Component.text("Set a price first!", NamedTextColor.RED)
                            .decoration(TextDecoration.ITALIC, false)
            )
        } else {
            val fee = selectedDuration.calculateFee(plugin, price!!)
            if (fee > 0) {
                lore.add(
                        Component.text("Creation Fee: $${"%.2f".format(fee)}", NamedTextColor.GOLD)
                                .decoration(TextDecoration.ITALIC, false)
                )
            }
            lore.add(
                    Component.text("Click to create!", NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false)
            )
        }

        meta.lore(lore)
        item.itemMeta = meta
        return item
    }

    private fun createCancelButton(): ItemStack {
        val item = ItemStack(Material.RED_TERRACOTTA)
        val meta = item.itemMeta

        meta.displayName(
                Component.text("Cancel", NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true)
        )

        val lore = mutableListOf<Component>()
        lore.add(Component.empty())
        lore.add(
                Component.text("Click to cancel", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        )

        meta.lore(lore)
        item.itemMeta = meta
        return item
    }

    private fun createFeeInfoItem(): ItemStack {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta

        meta.displayName(
                Component.text("Fee Breakdown", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false)
        )

        val lore = mutableListOf<Component>()
        lore.add(Component.empty())

        val baseFee = selectedDuration.getBaseFee(plugin)
        val feePercentage = selectedDuration.getFeePercentage(plugin)
        val percentageFee = price!! * (feePercentage / 100.0)
        val totalFee = baseFee + percentageFee

        lore.add(
                Component.text("Base Fee: $${"%.2f".format(baseFee)}", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false)
        )
        lore.add(
                Component.text(
                                "Percentage Fee: $${"%.2f".format(percentageFee)} ($feePercentage%)",
                                NamedTextColor.YELLOW
                        )
                        .decoration(TextDecoration.ITALIC, false)
        )
        lore.add(Component.empty())
        lore.add(
                Component.text("Total Fee: $${"%.2f".format(totalFee)}", NamedTextColor.GOLD)
                        .decoration(TextDecoration.ITALIC, false)
                        .decoration(TextDecoration.BOLD, true)
        )

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
            10 -> handlePriceClick(player)
            12 -> handleDurationClick(player)
            14 -> handleConfirmClick(player)
            16 -> handleCancelClick(player)
        }
    }

    private fun handlePriceClick(player: Player) {
        awaitingPriceInput.add(player)
        player.closeInventory()
        plugin.messageManager.send(player, "auction.create.chat-prompt")
    }

    private fun handleDurationClick(player: Player) {
        // Cycle through durations
        val durations = AuctionDuration.all()
        val currentIndex = durations.indexOf(selectedDuration)
        val nextIndex = (currentIndex + 1) % durations.size
        selectedDuration = durations[nextIndex]

        updateInventory()
    }

    private fun handleConfirmClick(player: Player) {
        if (price == null || price!! <= 0) {
            plugin.messageManager.send(player, "auction.create.price-not-set")
            return
        }

        // Check price limits
        val minPrice = plugin.config.getDouble("auction.min-price", 1.0)
        val maxPrice = plugin.config.getDouble("auction.max-price", 1000000.0)

        if (price!! < minPrice) {
            plugin.messageManager.send(player, "auction.create.price-too-low", minPrice)
            return
        }

        if (price!! > maxPrice) {
            plugin.messageManager.send(player, "auction.create.price-too-high", maxPrice)
            return
        }

        // Check max auctions
        val maxAuctions = plugin.config.getInt("auction.max-auctions-per-player", 5)
        val currentAuctions = plugin.auctionManager.getActiveAuctionCount(player.uniqueId)

        if (currentAuctions >= maxAuctions) {
            plugin.messageManager.send(player, "auction.create.max-reached", maxAuctions)
            return
        }

        // Calculate and check fee
        val fee = selectedDuration.calculateFee(plugin, price!!)
        if (fee > 0) {
            if (!plugin.economyHook.isAvailable()) {
                plugin.messageManager.send(player, "economy.not-available")
                return
            }

            val balance = plugin.economyHook.getBalance(player)
            if (balance < fee) {
                val needed = "%.2f".format(fee)
                val available = "%.2f".format(balance)
                plugin.messageManager.send(
                        player,
                        "auction.create.fee-insufficient",
                        needed,
                        available
                )
                return
            }

            // Charge the fee
            if (!plugin.economyHook.withdraw(player, fee)) {
                plugin.messageManager.send(player, "economy.transaction-failed")
                return
            }
        }

        // Create the auction
        val auctionId =
                plugin.auctionManager.createAuction(
                        player.uniqueId,
                        player.name,
                        item,
                        price!!,
                        selectedDuration.durationMillis
                )

        if (auctionId != null) {
            plugin.messageManager.send(player, "auction.create.success")

            val itemName =
                    if (item.itemMeta.hasDisplayName()) {
                        item.itemMeta.displayName() ?: Component.text("Unknown")
                    } else {
                        Component.text(
                                item.type
                                        .name
                                        .replace("_", " ")
                                        .lowercase()
                                        .split(" ")
                                        .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                        )
                    }

            plugin.messageManager.sendRaw(
                    player,
                    "auction.create.details",
                    itemName,
                    price!!,
                    selectedDuration.displayName
            )

            if (fee > 0) {
                plugin.messageManager.send(
                        player,
                        "auction.create.fee-charged",
                        "%.2f".format(fee)
                )
            }

            player.closeInventory()
        } else {
            // Refund fee if auction creation failed
            if (fee > 0) {
                plugin.economyHook.deposit(player, fee)
            }
            plugin.messageManager.send(player, "auction.create.failed-refund")
        }
    }

    private fun handleCancelClick(player: Player) {
        // Return item to player
        player.inventory.addItem(item)
        plugin.messageManager.send(player, "auction.create.cancelled")
        player.closeInventory()
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.player is Player) {
            val player = event.player as Player
            if (openGUIs[player] == this) {
                // Don't remove if awaiting price input
                if (!awaitingPriceInput.contains(player)) {
                    openGUIs.remove(player)
                    HandlerList.unregisterAll(this)
                }
            }
        }
    }

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        if (!awaitingPriceInput.contains(player)) return

        event.isCancelled = true
        val input = event.message.trim()

        if (input.equals("cancel", ignoreCase = true)) {
            awaitingPriceInput.remove(player)
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            Runnable {
                                plugin.messageManager.send(
                                        player,
                                        "auction.create.price-input-cancelled"
                                )
                                open(player)
                            }
                    )
            return
        }

        val parsedPrice = input.toDoubleOrNull()
        if (parsedPrice == null || parsedPrice <= 0) {
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            Runnable {
                                plugin.messageManager.send(
                                        player,
                                        "auction.create.price-invalid"
                                )
                                plugin.messageManager.send(
                                        player,
                                        "auction.create.price-retry"
                                )
                            }
                    )
            return
        }

        val minPrice = plugin.config.getDouble("auction.min-price", 1.0)
        if (parsedPrice < minPrice) {
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            Runnable {
                                plugin.messageManager.send(
                                        player,
                                        "auction.create.price-too-low-chat",
                                        minPrice
                                )
                                plugin.messageManager.send(
                                        player,
                                        "auction.create.price-enter-new"
                                )
                            }
                    )
            return
        }

        awaitingPriceInput.remove(player)
        price = parsedPrice

        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        Runnable {
                            plugin.messageManager.send(
                                    player,
                                    "auction.create.price-set",
                                    "%.2f".format(price)
                            )
                            open(player)
                        }
                )
    }
}
