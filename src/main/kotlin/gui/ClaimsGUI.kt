package gui

import database.AuctionManager
import de.fruxz.sparkle.framework.visual.canvas.Canvas
import de.fruxz.sparkle.framework.visual.canvas.buildCanvas
import kotlinx.coroutines.runBlocking
import model.Claim
import model.ClaimReason
import model.SerializableItemStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import service.AuctionService
import util.FormatUtil

/**
 * GUI for viewing and claiming items/money
 */
@OptIn(Canvas.ExperimentalCanvasApi::class)
class ClaimsGUI(
    private val player: Player,
    private val service: AuctionService = AuctionService()
) {
    fun build(): Canvas {
        val claims = runBlocking {
            AuctionManager.getClaims(player.uniqueId)
        }

        return buildCanvas {
            label("Your Claims")
            base(9 * 5) // 5 rows

            // Display claims
            claims.forEachIndexed { index, claim ->
                if (index < 36) { // First 4 rows
                    this[index] = createClaimItem(claim)
                }
            }

            // Bottom row controls
            this[40] = createClaimAllButton(claims.isNotEmpty())
            this[44] = createBackButton()
        }
    }

    private fun createClaimItem(claim: Claim): ItemStack {
        val hasItems = claim.items.isNotEmpty()
        val hasMoney = claim.money > 0

        val item = if (hasItems) {
            SerializableItemStack.toItemStack(claim.items.first())
        } else {
            ItemStack(Material.GOLD_INGOT)
        }

        val meta = item.itemMeta ?: return item

        // Build display name
        val displayName = when {
            hasItems && hasMoney -> "Items + Money"
            hasItems -> claim.items.first().getDisplayString()
            hasMoney -> "Money"
            else -> "Empty Claim"
        }

        meta.displayName(Component.text(displayName, NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false))

        // Build lore
        val lore = buildList {
            if (hasItems) {
                add(Component.text("Items:", NamedTextColor.GRAY))
                claim.items.forEach { serializedItem ->
                    add(Component.text(
                        "  ${serializedItem.amount}x ${serializedItem.getDisplayString()}",
                        NamedTextColor.WHITE
                    ))
                }
            }

            if (hasMoney) {
                if (hasItems) add(Component.text(""))
                add(Component.text(
                    "Money: ${FormatUtil.formatMoney(claim.money)}",
                    NamedTextColor.GOLD
                ))
            }

            add(Component.text(""))
            add(Component.text("Reason: ${formatReason(claim.reason)}", NamedTextColor.GRAY))
            add(Component.text(""))
            add(Component.text("Click to claim!", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false))
        }

        meta.lore(lore)
        item.itemMeta = meta

        return item
    }

    private fun createClaimAllButton(hasСlaims: Boolean): ItemStack {
        val item = ItemStack(if (hasСlaims) Material.EMERALD_BLOCK else Material.BARRIER)
        val meta = item.itemMeta!!

        if (hasСlaims) {
            meta.displayName(Component.text("Claim All", NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false))
            meta.lore(listOf(
                Component.text("Click to claim all items", NamedTextColor.GRAY),
                Component.text("and money at once", NamedTextColor.GRAY)
            ))
        } else {
            meta.displayName(Component.text("No Claims", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false))
            meta.lore(listOf(
                Component.text("You have nothing to claim", NamedTextColor.GRAY)
            ))
        }

        item.itemMeta = meta
        return item
    }

    private fun createBackButton(): ItemStack {
        val item = ItemStack(Material.ARROW)
        val meta = item.itemMeta!!
        meta.displayName(Component.text("Back", NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false))
        meta.lore(listOf(
            Component.text("Return to auction house", NamedTextColor.GRAY)
        ))
        item.itemMeta = meta
        return item
    }

    private fun formatReason(reason: ClaimReason): String {
        return when (reason) {
            ClaimReason.AUCTION_WON -> "Won Auction"
            ClaimReason.OUTBID_REFUND -> "Outbid Refund"
            ClaimReason.AUCTION_SOLD -> "Auction Sold"
            ClaimReason.AUCTION_UNSOLD -> "Auction Unsold"
            ClaimReason.AUCTION_CANCELLED -> "Auction Cancelled"
        }
    }
}
