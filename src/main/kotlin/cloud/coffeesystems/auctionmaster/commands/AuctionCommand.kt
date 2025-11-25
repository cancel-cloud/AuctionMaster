package cloud.coffeesystems.auctionmaster.commands

import cloud.coffeesystems.auctionmaster.AuctionMaster
import cloud.coffeesystems.auctionmaster.model.AuctionDuration
import cloud.coffeesystems.auctionmaster.model.AuctionStatus
import cloud.coffeesystems.auctionmaster.ui.AuctionGUI
import cloud.coffeesystems.auctionmaster.ui.CreateAuctionGUI
import cloud.coffeesystems.auctionmaster.ui.HistoryGUI
import cloud.coffeesystems.auctionmaster.ui.MainMenuGUI
import cloud.coffeesystems.auctionmaster.util.TimeUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/** Main command executor for /auction */
class AuctionCommand(private val plugin: AuctionMaster) : CommandExecutor, TabCompleter {

    companion object {
        private val durationPattern = Regex("^(\\d{1,2})([hd])$", RegexOption.IGNORE_CASE)
    }

    override fun onCommand(
            sender: CommandSender,
            command: Command,
            label: String,
            args: Array<out String>
    ): Boolean {

        // Check database connection
        if (plugin.databaseConnectionFailed) {
            plugin.messageManager.send(sender, "database.connection-error")
            return true
        }

        // Check lockdown mode (except for admin commands)
        val isLockdown = plugin.lockdownMode
        val isAdmin = sender.hasPermission("auctionmaster.admin")

        if (isLockdown && !isAdmin) {
            plugin.messageManager.send(sender, "lockdown.active")
            return true
        }

        if (args.isEmpty()) {
            if (label.equals("auctions", ignoreCase = true)) {
                handleList(sender)
            } else {
                openMainMenu(sender)
            }
            return true
        }

        when (args[0].lowercase()) {
            "create" -> handleCreate(sender, args)
            "list", "browse", "shop" -> handleList(sender)
            "view" -> handleView(sender, args)
            "cancel", "remove" -> handleCancel(sender, args)
            "info" -> handleInfo(sender, args)
            "help" -> showHelp(sender)
            "reload" -> handleReload(sender)
            "clear" -> handleClear(sender, args)
            "lockdown" -> handleLockdown(sender, args)
            "migrate" -> handleMigrate(sender, args)
            "history" -> handleHistory(sender, args)
            else -> showHelp(sender)
        }

        return true
    }

    /** Handle /auction create [price] [duration] */
    private fun handleCreate(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            plugin.messageManager.send(sender, "player-only")
            return
        }

        if (!sender.hasPermission("auctionmaster.create")) {
            plugin.messageManager.send(sender, "no-permission")
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

        // If no arguments provided, open interactive GUI
        if (args.size < 2) {
            val gui = CreateAuctionGUI(plugin, item.clone())
            gui.open(sender)
            // Remove item from hand
            sender.inventory.setItemInMainHand(null)
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

        if (args.size == 2) {
            val gui = CreateAuctionGUI(plugin, item.clone(), initialPrice = price)
            gui.open(sender)
            sender.inventory.setItemInMainHand(null)
            return
        }

        val durationInput = args[2].lowercase()
        if (!durationPattern.matches(durationInput)) {
            sender.sendMessage(
                    Component.text(
                            "Invalid duration format. Use values like 4h, 6h, 8h, 12h, 1d, 2d, or 7d.",
                            NamedTextColor.RED
                    )
            )
            return
        }

        val durationOption = AuctionDuration.fromConfigKey(durationInput)
        if (durationOption == null) {
            val allowed = AuctionDuration.all().joinToString(", ") { it.configKey }
            sender.sendMessage(
                    Component.text("Unsupported duration. Use one of: $allowed", NamedTextColor.RED)
            )
            return
        }

        var fee = durationOption.calculateFee(plugin, price)
        if (fee > 0) {
            if (!plugin.economyHook.isAvailable()) {
                plugin.messageManager.send(sender, "economy.not-available")
                return
            }

            val balance = plugin.economyHook.getBalance(sender)
            if (balance < fee) {
                sender.sendMessage(
                        Component.text(
                                "Insufficient funds for creation fee! Need: $${"%.2f".format(fee)}, Have: $${"%.2f".format(balance)}",
                                NamedTextColor.RED
                        )
                )
                return
            }

            if (!plugin.economyHook.withdraw(sender, fee)) {
                plugin.messageManager.send(sender, "economy.transaction-failed")
                return
            }
        }

        val durationMillis = durationOption.durationMillis
        val auctionId =
                plugin.auctionManager.createAuction(
                        sender.uniqueId,
                        sender.name,
                        item.clone(),
                        price,
                        durationMillis
                )

        if (auctionId != null) {
            // Remove item from player's hand
            sender.inventory.setItemInMainHand(null)

            // Send success message
            plugin.messageManager.send(sender, "auction.create.success")

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
                    sender,
                    "auction.create.details",
                    itemName,
                    price,
                    durationOption.displayName
            )

            if (fee > 0) {
                sender.sendMessage(
                        Component.text("Creation fee charged: $${"%.2f".format(fee)}", NamedTextColor.GOLD)
                )
            }
        } else {
            if (fee > 0) {
                plugin.economyHook.deposit(sender, fee)
            }
            sender.sendMessage(
                    Component.text(
                            "Failed to create auction. Please try again.",
                            NamedTextColor.RED
                    )
            )
        }
    }

    /** Handle /auction list */
    private fun handleList(sender: CommandSender) {
        if (sender !is Player) {
            plugin.messageManager.send(sender, "player-only")
            return
        }

        if (!sender.hasPermission("auctionmaster.use")) {
            plugin.messageManager.send(sender, "no-permission")
            return
        }

        AuctionGUI(plugin).open(sender)
    }

    private fun openMainMenu(sender: CommandSender) {
        if (sender !is Player) {
            plugin.messageManager.send(sender, "player-only")
            return
        }

        if (!sender.hasPermission("auctionmaster.use")) {
            plugin.messageManager.send(sender, "no-permission")
            return
        }

        MainMenuGUI(plugin).open(sender)
    }

    /** Handle /auction cancel */
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
            sender.sendMessage(
                    Component.text("You have no active auctions to cancel.", NamedTextColor.RED)
            )
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

    /** Handle /auction info <id> */
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

        val itemName =
                if (auction.item.itemMeta.hasDisplayName()) {
                    auction.item.itemMeta.displayName() ?: Component.text("Unknown")
                } else {
                    Component.text(
                            auction.item
                                    .type
                                    .name
                                    .replace("_", " ")
                                    .lowercase()
                                    .split(" ")
                                    .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                    )
                }

        plugin.messageManager.sendRaw(sender, "auction.info.seller", auction.sellerName)
        sender.sendMessage(plugin.messageManager.get("auction.info.item").append(itemName))
        plugin.messageManager.sendRaw(sender, "auction.info.amount", auction.item.amount)
        plugin.messageManager.sendRaw(sender, "auction.info.price", auction.price)
        plugin.messageManager.sendRaw(
                sender,
                "auction.info.time-left",
                TimeUtil.formatTime(plugin, auction.getRemainingTime())
        )
    }

    /** Handle /auction reload */
    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission("auctionmaster.admin")) {
            plugin.messageManager.send(sender, "no-permission")
            return
        }

        plugin.reloadPlugin()
        plugin.messageManager.send(sender, "admin.reload")
    }

    /** Handle /auction clear [player] */
    private fun handleClear(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("auctionmaster.admin")) {
            plugin.messageManager.send(sender, "no-permission")
            return
        }

        // Implementation for clearing auctions (admin feature)
        sender.sendMessage(
                Component.text("Clear auctions feature - to be implemented", NamedTextColor.YELLOW)
        )
    }

    /** Handle /auction lockdown <on|off> */
    private fun handleLockdown(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("auctionmaster.admin")) {
            plugin.messageManager.send(sender, "no-permission")
            return
        }

        if (args.size < 2) {
            plugin.messageManager.send(sender, "invalid-syntax", "/auction lockdown <on|off>")
            return
        }

        when (args[1].lowercase()) {
            "on", "true", "enable" -> plugin.setLockdown(true)
            "off", "false", "disable" -> plugin.setLockdown(false)
            else ->
                    plugin.messageManager.send(
                            sender,
                            "invalid-syntax",
                            "/auction lockdown <on|off>"
                    )
        }
    }

    /** Handle /auction migrate */
    private fun handleMigrate(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("auctionmaster.admin")) {
            plugin.messageManager.send(sender, "no-permission")
            return
        }

        val confirm = args.size > 1 && args[1].equals("confirm", ignoreCase = true)
        plugin.migrateDatabase(sender, confirm)
    }

    /** Handle /auction history [player] */
    private fun handleHistory(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            plugin.messageManager.send(sender, "player-only")
            return
        }

        var targetPlayer = sender

        if (args.size > 1) {
            if (!sender.hasPermission("auctionmaster.history.others")) {
                plugin.messageManager.send(sender, "no-permission")
                return
            }
            val targetName = args[1]
            val offlinePlayer = plugin.server.getOfflinePlayer(targetName)
            if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline) {
                // Try UUID
                try {
                    val uuid = java.util.UUID.fromString(targetName)
                    val uuidPlayer = plugin.server.getOfflinePlayer(uuid)
                    if (uuidPlayer.hasPlayedBefore() || uuidPlayer.isOnline) {
                        // Found by UUID
                        // We need to pass OfflinePlayer to GUI
                        HistoryGUI(plugin, uuidPlayer).open(sender)
                        return
                    }
                } catch (e: IllegalArgumentException) {
                    // Not a UUID
                }
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED))
                return
            }
            // Found by name
            HistoryGUI(plugin, offlinePlayer).open(sender)
        } else {
            // View own history
            HistoryGUI(plugin, sender).open(sender)
        }
    }

    /** Handle /auction view <player> */
    private fun handleView(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            plugin.messageManager.send(sender, "player-only")
            return
        }

        if (args.size < 2) {
            sender.sendMessage(
                    Component.text("Usage: /auction view <playername>", NamedTextColor.RED)
            )
            return
        }

        val sellerName = args[1]

        // Check if player has auctions
        val auctions = plugin.auctionManager.getAuctionsBySeller(sellerName)
        if (auctions.isEmpty()) {
            plugin.messageManager.send(sender, "auction.view.no-auctions", sellerName)
            return
        }

        // Open auction GUI filtered by seller
        AuctionGUI(plugin).open(sender, 0, sellerName)
    }

    /** Show help message */
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

    override fun onTabComplete(
            sender: CommandSender,
            command: Command,
            alias: String,
            args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            val subcommands =
                    mutableListOf("create", "list", "view", "cancel", "info", "help", "history")
            if (sender.hasPermission("auctionmaster.admin")) {
                subcommands.addAll(listOf("reload", "clear"))
            }
            return subcommands.filter { it.startsWith(args[0].lowercase()) }
        }

        if (args.size == 2 && args[0].equals("create", ignoreCase = true)) {
            return listOf("<price>")
        }

        if (args.size == 3 && args[0].equals("create", ignoreCase = true)) {
            return listOf("1h", "2h", "6h", "12h", "1d", "2d")
        }

        // Tab completion for /auction view <player>
        if (args.size == 2 && args[0].equals("view", ignoreCase = true)) {
            val sellers = plugin.auctionManager.getUniqueSellers()
            return sellers.filter { it.lowercase().startsWith(args[1].lowercase()) }
        }

        return emptyList()
    }
}
