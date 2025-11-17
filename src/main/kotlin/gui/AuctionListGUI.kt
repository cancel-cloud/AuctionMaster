package gui

import config.AuctionConfig
import database.AuctionManager
import de.fruxz.sparkle.framework.visual.canvas.Canvas
import de.fruxz.sparkle.framework.visual.canvas.buildCanvas
import de.fruxz.sparkle.framework.visual.canvas.pagination.PaginationType
import de.fruxz.stacked.extension.KeyExtension.keyOfOrNull
import de.fruxz.stacked.plus
import de.fruxz.stacked.text
import kotlinx.coroutines.runBlocking
import model.Auction
import model.AuctionFilter
import model.SerializableItemStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import util.FormatUtil
import util.TimeUtil

/**
 * GUI for browsing auctions
 */
@OptIn(Canvas.ExperimentalCanvasApi::class)
class AuctionListGUI(
    private val viewer: Player,
    private val filter: AuctionFilter = AuctionFilter.activeOnly(),
    private val config: AuctionConfig = AuctionConfig.default()
) {
    fun build(): Canvas {
        val auctions = runBlocking {
            AuctionManager.getAuctions(filter)
        }

        return buildCanvas {
            label("Auction House")
            base(9 * 6) // 6 rows
            pagination(PaginationType.scroll())

            // Auction items (slots 0-44, 5 rows)
            auctions.forEachIndexed { index, auction ->
                if (index < 45) {
                    this[index] = createAuctionItem(auction)
                }
            }

            // Bottom row controls (slots 45-53)
            this[45] = createInfoButton()
            this[46] = createFilterButton()
            this[48] = createMyAuctionsButton()
            this[49] = createCreateButton()
            this[50] = createMyBidsButton()
            this[52] = createClaimsButton()
            this[53] = createRefreshButton()
        }
    }

    private fun createAuctionItem(auction: Auction): ItemStack {
        val item = SerializableItemStack.toItemStack(auction.itemStack)
        val meta = item.itemMeta ?: return item

        // Set lore with auction info
        val lore = buildList {
            add(Component.text("Seller: ${auction.sellerName}", NamedTextColor.GRAY))
            add(Component.text(""))
            add(Component.text(
                "Current Bid: ${FormatUtil.formatMoney(auction.currentBid)}",
                NamedTextColor.GOLD
            ))

            auction.currentBidderName?.let { bidder ->
                add(Component.text("Leading: $bidder", NamedTextColor.YELLOW))
            } ?: add(Component.text("No bids yet", NamedTextColor.GRAY))

            auction.buyNowPrice?.let { price ->
                add(Component.text(""))
                add(Component.text(
                    "Buy Now: ${FormatUtil.formatMoney(price)}",
                    NamedTextColor.GREEN
                ))
            }

            add(Component.text(""))
            add(Component.text(
                "Time: ${TimeUtil.formatTimeRemaining(auction.getTimeRemaining())}",
                NamedTextColor.AQUA
            ))
            add(Component.text(""))
            add(Component.text("Click to bid!", NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false))
        }

        meta.lore(lore)
        item.itemMeta = meta

        return item
    }

    private fun createInfoButton(): ItemStack {
        val item = ItemStack(Material.BOOK)
        val meta = item.itemMeta!!
        meta.displayName(Component.text("Information", NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false))
        meta.lore(listOf(
            Component.text("Browse and bid on", NamedTextColor.GRAY),
            Component.text("player auctions!", NamedTextColor.GRAY)
        ))
        item.itemMeta = meta
        return item
    }

    private fun createFilterButton(): ItemStack {
        val item = ItemStack(Material.HOPPER)
        val meta = item.itemMeta!!
        meta.displayName(Component.text("Filter", NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false))
        meta.lore(listOf(
            Component.text("Click to filter auctions", NamedTextColor.GRAY)
        ))
        item.itemMeta = meta
        return item
    }

    private fun createMyAuctionsButton(): ItemStack {
        val item = ItemStack(Material.DIAMOND)
        val meta = item.itemMeta!!
        meta.displayName(Component.text("My Auctions", NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false))
        meta.lore(listOf(
            Component.text("View your active auctions", NamedTextColor.GRAY)
        ))
        item.itemMeta = meta
        return item
    }

    private fun createCreateButton(): ItemStack {
        val item = ItemStack(Material.EMERALD)
        val meta = item.itemMeta!!
        meta.displayName(Component.text("Create Auction", NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false))
        meta.lore(listOf(
            Component.text("Click to create a new auction", NamedTextColor.GRAY)
        ))
        item.itemMeta = meta
        return item
    }

    private fun createMyBidsButton(): ItemStack {
        val item = ItemStack(Material.GOLD_INGOT)
        val meta = item.itemMeta!!
        meta.displayName(Component.text("My Bids", NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false))
        meta.lore(listOf(
            Component.text("View auctions you've bid on", NamedTextColor.GRAY)
        ))
        item.itemMeta = meta
        return item
    }

    private fun createClaimsButton(): ItemStack {
        val item = ItemStack(Material.CHEST)
        val meta = item.itemMeta!!
        meta.displayName(Component.text("Claims", NamedTextColor.LIGHT_PURPLE)
            .decoration(TextDecoration.ITALIC, false))
        meta.lore(listOf(
            Component.text("Claim your items and money", NamedTextColor.GRAY)
        ))
        item.itemMeta = meta
        return item
    }

    private fun createRefreshButton(): ItemStack {
        val item = ItemStack(Material.ARROW)
        val meta = item.itemMeta!!
        meta.displayName(Component.text("Refresh", NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false))
        meta.lore(listOf(
            Component.text("Click to refresh listings", NamedTextColor.GRAY)
        ))
        item.itemMeta = meta
        return item
    }
}
