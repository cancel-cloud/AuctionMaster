package cloud.coffeesystems.auctionmaster.commands

import cloud.coffeesystems.auctionmaster.AuctionMaster
import cloud.coffeesystems.auctionmaster.model.AuctionStatus
import cloud.coffeesystems.auctionmaster.ui.AuctionGUI
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Main command executor for /auction
 */
class AuctionCommand(private val plugin: AuctionMaster) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        // Check database connection
        if (plugin.databaseConnectionFailed) {
            plugin.messageManager.send(sender, "database.connection-error")
            return true
        }

        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "create" -> handleCreate(sender, args)
            "list", "browse", "shop" -> handleList(sender)
            "cancel", "remove" -> handleCancel(sender, args)
            "info" -> handleInfo(sender, args)
            "help" -> showHelp(sender)
            "reload" -> handleReload(sender)
            "clear" -> handleClear(sender, args)
            else -> showHelp(sender)
        }

        return true
    }

    /**
     * Handle /auction create <price>
     */
    private fun handleCreate(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            plugin.messageManager.send(sender, "player-only")
            return
        }

        if (!sender.hasPermission("auctionmaster.create")) {
            plugin.messageManager.send(sender, "no-permission")
            return
        }

        if (args.size < 2) {
            plugin.messageManager.send(sender, "invalid-syntax", "/auction create <price>")
            return
        }

        val item = sender.inventory.itemInMainHand
        if (item.type == Material.AIR) {
            plugin.messageManager.send(sender, "auction.create.no-item")
            return
        }

        // Check if item is blacklisted
        val blacklist = plugin.config.getStringList("auction.blacklisted-items")
        if (blacklist.contains(item.type.name)) {
            plugin.messageManager.send(sender, "auction.create.blacklisted-item")
            return
        }

        // Parse price
        val price = args[1].toDoubleOrNull()
        if (price == null || price <= 0) {
            plugin.messageManager.send(sender, "auction.create.invalid-price")
            return
        }

        // Check price limits
        val minPrice = plugin.config.getDouble("auction.min-price", 1.0)
        val maxPrice = plugin.config.getDouble("auction.max-price", 1000000.0)

        if (price < minPrice) {
            plugin.messageManager.send(sender, "auction.create.price-too-low", minPrice)
            return
        }

        if (price > maxPrice) {
            plugin.messageManager.send(sender, "auction.create.price-too-high", maxPrice)
            return
        }

        // Check max auctions per player
        val maxAuctions = plugin.config.getInt("auction.max-auctions-per-player", 5)
        val currentAuctions = plugin.auctionManager.getActiveAuctionCount(sender.uniqueId)

        if (currentAuctions >= maxAuctions) {
            plugin.messageManager.send(sender, "auction.create.max-reached", maxAuctions)
            return
        }

        // Create auction
        val duration = plugin.config.getLong("auction.duration", 86400) * 1000 // Convert to milliseconds
        val auctionId = plugin.auctionManager.createAuction(
            sender.uniqueId,
            sender.name,
            item.clone(),
            price,
            duration
        )

        if (auctionId != null) {
            // Remove item from player's hand
            sender.inventory.setItemInMainHand(null)

            // Send success message
            plugin.messageManager.send(sender, "auction.create.success")

            val itemName = if (item.itemMeta.hasDisplayName()) {
                item.itemMeta.displayName()
            } else {
                Component.text(item.type.name.replace("_", " ").lowercase()
                    .split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) })
            }

            val durationHours = duration / 1000 / 3600
            plugin.messageManager.sendRaw(sender, "auction.create.details",
                itemName, price, "${durationHours}h")

        } else {
            sender.sendMessage(Component.text("Failed to create auction. Please try again.", NamedTextColor.RED))
        }
    }

    /**
     * Handle /auction list
     */
    private fun handleList(sender: CommandSender) {
        if (sender !is Player) {
            plugin.messageManager.send(sender, "player-only")
            return
        }

        if (!sender.hasPermission("auctionmaster.use")) {
            plugin.messageManager.send(sender, "no-permission")
            return
        }

        // Open auction GUI
        val gui = AuctionGUI(plugin)
        gui.open(sender)
    }

    /**
     * Handle /auction cancel
     */
    private fun handleCancel(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            plugin.messageManager.send(sender, "player-only")
            return
        }

        if (!sender.hasPermission("auctionmaster.create")) {
            plugin.messageManager.send(sender, "no-permission")
            return
        }

        // Get player's active auctions
        val auctions = plugin.auctionManager.getAuctionsBySeller(sender.uniqueId)

        if (auctions.isEmpty()) {
            sender.sendMessage(Component.text("You have no active auctions to cancel.", NamedTextColor.RED))
            return
        }

        // If auction ID specified, cancel that one
        if (args.size > 1) {
            val auctionId = args[1].toIntOrNull()
            if (auctionId == null) {
                sender.sendMessage(Component.text("Invalid auction ID.", NamedTextColor.RED))
                return
            }

            val auction = auctions.find { it.id == auctionId }
            if (auction == null) {
                plugin.messageManager.send(sender, "auction.cancel.not-found")
                return
            }

            // Return item to player
            sender.inventory.addItem(auction.item)

            // Update auction status
            plugin.auctionManager.updateAuctionStatus(auctionId, AuctionStatus.CANCELLED)

            plugin.messageManager.send(sender, "auction.cancel.success")
        } else {
            // Cancel the first active auction
            val auction = auctions.first()

            // Return item to player
            sender.inventory.addItem(auction.item)

            // Update auction status
            plugin.auctionManager.updateAuctionStatus(auction.id, AuctionStatus.CANCELLED)

            plugin.messageManager.send(sender, "auction.cancel.success")
        }
    }

    /**
     * Handle /auction info <id>
     */
    private fun handleInfo(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("auctionmaster.use")) {
            plugin.messageManager.send(sender, "no-permission")
            return
        }

        if (args.size < 2) {
            plugin.messageManager.send(sender, "invalid-syntax", "/auction info <id>")
            return
        }

        val auctionId = args[1].toIntOrNull()
        if (auctionId == null) {
            sender.sendMessage(Component.text("Invalid auction ID.", NamedTextColor.RED))
            return
        }

        val auction = plugin.auctionManager.getAuctionById(auctionId)
        if (auction == null) {
            plugin.messageManager.send(sender, "auction.buy.not-found")
            return
        }

        // Display auction info
        plugin.messageManager.sendRaw(sender, "auction.info.title")

        val itemName = if (auction.item.itemMeta.hasDisplayName()) {
            auction.item.itemMeta.displayName()
        } else {
            Component.text(auction.item.type.name.replace("_", " ").lowercase()
                .split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) })
        }

        plugin.messageManager.sendRaw(sender, "auction.info.seller", auction.sellerName)
        sender.sendMessage(plugin.messageManager.get("auction.info.item").append(itemName))
        plugin.messageManager.sendRaw(sender, "auction.info.amount", auction.item.amount)
        plugin.messageManager.sendRaw(sender, "auction.info.price", auction.price)
        plugin.messageManager.sendRaw(sender, "auction.info.time-left", formatTime(auction.getRemainingTime()))
    }

    /**
     * Handle /auction reload
     */
    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission("auctionmaster.admin")) {
            plugin.messageManager.send(sender, "no-permission")
            return
        }

        plugin.reloadPlugin()
        plugin.messageManager.send(sender, "admin.reload")
    }

    /**
     * Handle /auction clear [player]
     */
    private fun handleClear(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("auctionmaster.admin")) {
            plugin.messageManager.send(sender, "no-permission")
            return
        }

        // Implementation for clearing auctions (admin feature)
        sender.sendMessage(Component.text("Clear auctions feature - to be implemented", NamedTextColor.YELLOW))
    }

    /**
     * Show help message
     */
    private fun showHelp(sender: CommandSender) {
        plugin.messageManager.sendRaw(sender, "help.header")
        plugin.messageManager.sendRaw(sender, "help.create")
        plugin.messageManager.sendRaw(sender, "help.list")
        plugin.messageManager.sendRaw(sender, "help.cancel")
        plugin.messageManager.sendRaw(sender, "help.info")
        plugin.messageManager.sendRaw(sender, "help.help")

        if (sender.hasPermission("auctionmaster.admin")) {
            plugin.messageManager.sendRaw(sender, "help.admin-header")
            plugin.messageManager.sendRaw(sender, "help.reload")
            plugin.messageManager.sendRaw(sender, "help.clear")
        }

        plugin.messageManager.sendRaw(sender, "help.footer")
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

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            val subcommands = mutableListOf("create", "list", "cancel", "info", "help")
            if (sender.hasPermission("auctionmaster.admin")) {
                subcommands.addAll(listOf("reload", "clear"))
            }
            return subcommands.filter { it.startsWith(args[0].lowercase()) }
        }

        if (args.size == 2 && args[0].equals("create", ignoreCase = true)) {
            return listOf("<price>")
        }

        return emptyList()
    }
}
