package cloud.coffeesystems.auctionmaster.ui

import cloud.coffeesystems.auctionmaster.AuctionMaster
import cloud.coffeesystems.auctionmaster.model.Auction
import cloud.coffeesystems.auctionmaster.model.AuctionStatus
import java.util.Locale
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.jetbrains.exposed.sql.insert

class ConfirmationGUI(
        private val plugin: AuctionMaster,
        private val auction: Auction,
        private val mode: Mode = Mode.BUY,
        private val previousGUI: AuctionGUI? = null
) : Listener {

    enum class Mode {
        BUY,
        CANCEL
    }

    companion object {
        private val openGUIs = mutableMapOf<Player, ConfirmationGUI>()
    }

    private lateinit var inventory: Inventory
    private lateinit var viewer: Player
    private var isOpening = false

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

    fun open(player: Player) {
        plugin.logger.info(
                "Opening ConfirmationGUI for ${player.name}, mode: $mode, GUI instance: ${System.identityHashCode(this)}"
        )
        viewer = player

        val title =
                when (mode) {
                    Mode.BUY -> msg("gui.confirmation.title")
                    Mode.CANCEL -> msg("gui.cancel-confirmation.title")
                }
        inventory = Bukkit.createInventory(null, 27, title)

        // Item to buy/cancel in the middle with lore
        val displayItem = auction.item.clone()
        val meta = displayItem.itemMeta

        // Add lore with auction details
        val lore = mutableListOf<Component>()
        lore.add(Component.empty())
        lore.add(msg("gui.auction-house.price", formatCurrency(auction.price)))
        lore.add(msg("gui.auction-house.seller", auction.sellerName))

        val timeLeft =
                cloud.coffeesystems.auctionmaster.util.TimeUtil.formatTime(
                        plugin,
                        auction.getRemainingTime()
                )
        lore.add(msg("gui.auction-house.time-left", timeLeft))
        meta.lore(lore)
        displayItem.itemMeta = meta

        inventory.setItem(13, displayItem)

        // Confirm button (Green for buy, Red for cancel)
        val confirmItem =
                when (mode) {
                    Mode.BUY -> ItemStack(Material.LIME_TERRACOTTA)
                    Mode.CANCEL -> ItemStack(Material.RED_TERRACOTTA)
                }
        val confirmMeta = confirmItem.itemMeta

        val confirmKey =
                when (mode) {
                    Mode.BUY -> "gui.confirmation.confirm"
                    Mode.CANCEL -> "gui.cancel-confirmation.confirm"
                }
        val confirmLoreKey = "$confirmKey-lore"
        confirmMeta.displayName(msg(confirmKey))
        confirmMeta.lore(msgList(confirmLoreKey))
        confirmItem.itemMeta = confirmMeta
        inventory.setItem(11, confirmItem)

        // Cancel/Back button (Red for buy, Green for cancel)
        val cancelItem =
                when (mode) {
                    Mode.BUY -> ItemStack(Material.RED_TERRACOTTA)
                    Mode.CANCEL -> ItemStack(Material.LIME_TERRACOTTA)
                }
        val cancelMeta = cancelItem.itemMeta

        val cancelKey =
                when (mode) {
                    Mode.BUY -> "gui.confirmation.cancel"
                    Mode.CANCEL -> "gui.cancel-confirmation.back"
                }
        val cancelLoreKey = "$cancelKey-lore"
        cancelMeta.displayName(msg(cancelKey))
        cancelMeta.lore(msgList(cancelLoreKey))
        cancelItem.itemMeta = cancelMeta
        inventory.setItem(15, cancelItem)

        // Fill background
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
        plugin.logger.info(
                "Registered ConfirmationGUI ${System.identityHashCode(this)} for ${player.name}"
        )

        HandlerList.unregisterAll(this)
        plugin.server.pluginManager.registerEvents(this, plugin)
        plugin.logger.info("Registered event listeners for ConfirmationGUI")

        isOpening = true
        plugin.logger.info("Set isOpening flag to true")
        player.openInventory(inventory)
        isOpening = false
        plugin.logger.info(
                "Set isOpening flag to false - Opened confirmation inventory for ${player.name}"
        )
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.whoClicked !is Player) return
        val player = event.whoClicked as Player

        // Debug: check if player is in map
        val registeredGUI = openGUIs[player]
        plugin.logger.info(
                "ConfirmationGUI - Click detected - Player: ${player.name}, This GUI: ${System.identityHashCode(this)}, Registered GUI: ${if (registeredGUI != null) System.identityHashCode(registeredGUI) else "null"}"
        )

        if (openGUIs[player] != this) {
            plugin.logger.warning(
                    "ConfirmationGUI - Click ignored - GUI mismatch for ${player.name}"
            )
            return
        }

        // Cancel ALL clicks in ANY inventory when this GUI is open (prevents item manipulation)
        event.isCancelled = true

        // Only process clicks in the top inventory (our confirmation GUI)
        if (event.clickedInventory != inventory) {
            plugin.logger.info("ConfirmationGUI - Click in wrong inventory, ignoring")
            return
        }

        // Verify inventory is initialized and player matches viewer
        if (!::inventory.isInitialized || !::viewer.isInitialized || player != viewer) {
            plugin.logger.warning("ConfirmationGUI - Inventory or viewer not initialized")
            return
        }

        plugin.logger.info("ConfirmationGUI click detected: slot ${event.slot}, mode $mode")

        when (event.slot) {
            11 -> {
                plugin.logger.info("Confirm button clicked")
                when (mode) {
                    Mode.BUY -> handleConfirm(player)
                    Mode.CANCEL -> handleCancelAuction(player)
                }
            }
            15 -> {
                plugin.logger.info("Cancel/Back button clicked")
                handleCancel(player)
            }
        }
    }

    private fun handleConfirm(player: Player) {
        // Re-check auction status
        val currentAuction = plugin.auctionManager.getAuctionById(auction.id)
        if (currentAuction == null || currentAuction.status != AuctionStatus.ACTIVE) {
            plugin.messageManager.send(player, "auction.buy.not-found")
            player.closeInventory()
            return
        }

        // Check economy
        if (!plugin.economyHook.isAvailable()) {
            plugin.messageManager.send(player, "economy.not-available")
            player.closeInventory()
            return
        }

        val balance = plugin.economyHook.getBalance(player)
        if (balance < auction.price) {
            plugin.messageManager.send(
                    player,
                    "auction.buy.insufficient-funds",
                    auction.price - balance
            )
            player.closeInventory()
            return
        }

        // Check inventory space
        if (player.inventory.firstEmpty() == -1) {
            plugin.messageManager.send(player, "auction.buy.inventory-full")
            player.closeInventory()
            return
        }

        // Process transaction
        if (plugin.economyHook.withdraw(player, auction.price)) {
            // Give item
            player.inventory.addItem(auction.item)

            // Update status
            plugin.auctionManager.updateAuctionStatus(auction.id, AuctionStatus.SOLD)

            // Record transaction
            plugin.auctionManager.recordTransaction(auction, player.uniqueId, player.name)
            
            // Play positive confirmation sound for buyer
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)

            // Pay seller
            val seller = Bukkit.getPlayer(auction.sellerUuid)
            if (seller != null && seller.isOnline) {
                // Seller is online - pay immediately
                plugin.economyHook.deposit(seller, auction.price)
                plugin.messageManager.send(
                        seller,
                        "auction-sold.notification",
                        auction.item.type.name,
                        auction.price
                )
                
                // Play notification sound for seller
                seller.playSound(seller.location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)

                // Send action bar notification if enabled
                if (plugin.config.getBoolean("notifications.sale-notification", true)) {
                    val itemName =
                            if (auction.item.itemMeta?.hasDisplayName() == true) {
                                auction.item.itemMeta?.displayName()
                                        ?: Component.text(auction.item.type.name)
                            } else {
                                Component.text(auction.item.type.name)
                            }
                    seller.sendActionBar(
                            Component.text("✓ Your ", NamedTextColor.GREEN)
                                    .append(itemName)
                                    .append(
                                            Component.text(
                                                    " sold for $${auction.price}!",
                                                    NamedTextColor.GOLD
                                            )
                                    )
                    )
                }
            } else {
                // Seller is offline - store pending payment
                val offlineSeller = Bukkit.getOfflinePlayer(auction.sellerUuid)

                // Try to deposit to offline player
                if (plugin.economyHook.depositOffline(offlineSeller, auction.price)) {
                    // Payment successful, store notification for when they login
                    org.jetbrains.exposed.sql.transactions.transaction(
                            plugin.databaseManager.getDatabase()
                    ) {
                        cloud.coffeesystems.auctionmaster.database.PendingPayments.insert {
                            it[sellerUuid] = auction.sellerUuid.toString()
                            it[sellerName] = auction.sellerName
                            it[itemName] = auction.item.type.name
                            it[amount] = auction.price
                            it[timestamp] = System.currentTimeMillis()
                            it[paid] = true
                        }
                    }
                } else {
                    // Economy doesn't support offline deposits, store for later
                    org.jetbrains.exposed.sql.transactions.transaction(
                            plugin.databaseManager.getDatabase()
                    ) {
                        cloud.coffeesystems.auctionmaster.database.PendingPayments.insert {
                            it[sellerUuid] = auction.sellerUuid.toString()
                            it[sellerName] = auction.sellerName
                            it[itemName] = auction.item.type.name
                            it[amount] = auction.price
                            it[timestamp] = System.currentTimeMillis()
                            it[paid] = false
                        }
                    }
                }
            }

            plugin.messageManager.send(
                    player,
                    "auction.buy.success",
                    auction.item.type.name,
                    auction.price
            )
            player.closeInventory()
        } else {
            plugin.messageManager.send(player, "economy.transaction-failed")
        }
    }

    private fun handleCancelAuction(player: Player) {
        // Verify player has space in inventory
        if (player.inventory.firstEmpty() == -1) {
            plugin.messageManager.send(player, "auction.cancel.inventory-full")
            player.closeInventory()
            return
        }

        // Return item to player
        player.inventory.addItem(auction.item)

        // Update auction status
        plugin.auctionManager.updateAuctionStatus(auction.id, AuctionStatus.CANCELLED)

        // Send success message
        plugin.messageManager.send(player, "auction.cancel.success")

        player.closeInventory()
    }

    private fun handleCancel(player: Player) {
        if (previousGUI != null) {
            previousGUI.open(player)
        } else {
            player.closeInventory()
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        if (event.player is Player) {
            val player = event.player as Player
            plugin.logger.info(
                    "ConfirmationGUI - Inventory closed for ${player.name}, isOpening: $isOpening, GUI: ${System.identityHashCode(this)}"
            )

            // Don't remove if we're in the process of opening (prevents premature unregistration)
            if (isOpening) {
                plugin.logger.info("ConfirmationGUI - Ignoring close event (opening in progress)")
                return
            }

            // Only remove if this GUI is registered for this player
            if (openGUIs[player] == this) {
                plugin.logger.info(
                        "ConfirmationGUI - Removing ${player.name} from openGUIs and unregistering listeners"
                )
                openGUIs.remove(player)
                HandlerList.unregisterAll(this)
            } else {
                plugin.logger.info("ConfirmationGUI - Not removing (different GUI registered)")
            }
        }
    }
}
