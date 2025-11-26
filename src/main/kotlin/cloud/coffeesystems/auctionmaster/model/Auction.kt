package cloud.coffeesystems.auctionmaster.model

import java.util.UUID
import org.bukkit.inventory.ItemStack

data class Auction(
        val id: Int,
        val sellerUuid: UUID,
        val sellerName: String,
        val item: ItemStack,
        val price: Double,
        val createdAt: Long,
        val expiresAt: Long,
        val status: AuctionStatus,
) {
        fun getRemainingTime(): Long {
                return (expiresAt - System.currentTimeMillis()).coerceAtLeast(0L)
        }

        fun isSeller(uuid: UUID): Boolean = sellerUuid == uuid

        fun isExpired(currentTime: Long = System.currentTimeMillis()): Boolean {
                return status == AuctionStatus.EXPIRED || expiresAt <= currentTime
        }
}
