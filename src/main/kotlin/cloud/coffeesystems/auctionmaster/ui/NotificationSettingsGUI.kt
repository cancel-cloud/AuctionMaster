package cloud.coffeesystems.auctionmaster.ui

import cloud.coffeesystems.auctionmaster.AuctionMaster
import cloud.coffeesystems.auctionmaster.notifications.NotificationSettingsService
import cloud.coffeesystems.auctionmaster.notifications.NotificationSoundType
import net.kyori.adventure.text.Component
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

class NotificationSettingsGUI(private val plugin: AuctionMaster) : Listener {

        companion object {
                private val openGUIs = mutableMapOf<Player, NotificationSettingsGUI>()
        }

        private lateinit var inventory: Inventory
        private lateinit var viewer: Player
        private val toggleSlots =
                linkedMapOf(
                        NotificationSoundType.AUCTION_CREATE to 10,
                        NotificationSoundType.AUCTION_BUY to 12,
                        NotificationSoundType.AUCTION_SELL to 14,
                        NotificationSoundType.LOGIN_PAYOUT to 16
                )

        private fun msg(key: String, vararg args: Any?) =
                plugin.messageManager.get(key, *args).decoration(TextDecoration.ITALIC, false)

        private fun msgList(key: String, vararg args: Any?): List<Component> =
                plugin.messageManager.getList(key, *args).map {
                        it.decoration(TextDecoration.ITALIC, false)
                }

        fun open(player: Player) {
                viewer = player
                inventory =
                        Bukkit.createInventory(
                                null,
                                27,
                                msg("gui.notification-settings.title")
                        )

                updateInventory()

                openGUIs[player] = this

                HandlerList.unregisterAll(this)
                plugin.server.pluginManager.registerEvents(this, plugin)

                player.openInventory(inventory)
        }

        private fun updateInventory() {
                val settings =
                        plugin.notificationSettings.getSettings(viewer.uniqueId)
                toggleSlots.forEach { (type, slot) ->
                        inventory.setItem(slot, createToggleItem(type, settings.isEnabled(type)))
                }

                inventory.setItem(
                        inventory.size - 1,
                        createBackItem()
                )
        }

        private fun createToggleItem(type: NotificationSoundType, enabled: Boolean): ItemStack {
                val material = if (enabled) Material.LIME_DYE else Material.GRAY_DYE
                val item = ItemStack(material)
                val meta = item.itemMeta

                val stateKey =
                        if (enabled) "gui.notification-settings.state.enabled"
                        else "gui.notification-settings.state.disabled"

                val typeKey =
                        when (type) {
                                NotificationSoundType.AUCTION_CREATE -> "create"
                                NotificationSoundType.AUCTION_BUY -> "buy"
                                NotificationSoundType.AUCTION_SELL -> "sell"
                                NotificationSoundType.LOGIN_PAYOUT -> "login"
                        }

                meta.displayName(
                        msg("gui.notification-settings.toggle.$typeKey.name")
                )
                meta.lore(
                        msgList(
                                "gui.notification-settings.toggle.$typeKey.lore",
                                msg(stateKey)
                        )
                )

                item.itemMeta = meta
                return item
        }

        private fun createBackItem(): ItemStack {
                val item = ItemStack(Material.BARRIER)
                val meta = item.itemMeta
                meta.displayName(msg("gui.controls.back"))
                meta.lore(msgList("gui.controls.back-lore"))
                item.itemMeta = meta
                return item
        }

        @EventHandler
        fun onInventoryClick(event: InventoryClickEvent) {
                if (event.whoClicked !is Player) return
                val player = event.whoClicked as Player
                if (openGUIs[player] != this) return

                event.isCancelled = true
                if (event.clickedInventory != inventory) return

                val slot = event.slot

                toggleSlots.forEach { (type, toggleSlot) ->
                        if (slot == toggleSlot) {
                                handleToggle(player, type)
                                return
                        }
                }

                if (slot == inventory.size - 1) {
                        player.closeInventory()
                        MainMenuGUI(plugin).open(player)
                }
        }

        private fun handleToggle(player: Player, type: NotificationSoundType) {
                // Use async toggle for better performance, update UI optimistically
                plugin.notificationSettings.toggleAsync(player.uniqueId, type) {
                        // Callback when DB update completes - refresh UI if still open
                        if (player.isOnline && openGUIs[player] == this) {
                                updateInventory()
                        }
                }
                // Update UI immediately (optimistic update)
                updateInventory()
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 0.7f, 1.0f)
        }

        @EventHandler
        fun onInventoryClose(event: InventoryCloseEvent) {
                val player = event.player as? Player ?: return
                if (openGUIs[player] == this) {
                        openGUIs.remove(player)
                        HandlerList.unregisterAll(this)
                }
        }
}
