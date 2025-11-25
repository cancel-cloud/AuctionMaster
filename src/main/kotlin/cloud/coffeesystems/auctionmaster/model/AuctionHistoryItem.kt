package cloud.coffeesystems.auctionmaster.model

import java.util.UUID
import org.bukkit.inventory.ItemStack

data class AuctionHistoryItem(
        val id: Int,
        val buyerUuid: UUID,
        val buyerName: String,
        val sellerUuid: UUID,
        val sellerName: String,
        val item: ItemStack,
        val price: Double,
        val timestamp: Long
)
