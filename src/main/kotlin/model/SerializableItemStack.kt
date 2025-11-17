package model

import kotlinx.serialization.Serializable
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.util.Base64

@Serializable
data class SerializableItemStack(
    val material: String,
    val amount: Int,
    val displayName: String? = null,
    val lore: List<String>? = null,
    val enchantments: Map<String, Int> = emptyMap(),
    val nbtData: String? = null // Base64 encoded NBT for future compatibility
) {
    companion object {
        /**
         * Convert a Bukkit ItemStack to a serializable format
         */
        fun from(item: ItemStack): SerializableItemStack {
            val meta = item.itemMeta

            // Extract display name using Paper's component API
            val displayName = meta?.displayName()?.let { component ->
                // For now, we'll use a simplified extraction
                // In production, you'd want to serialize the full component
                if (meta.hasDisplayName()) {
                    meta.displayName?.toString() ?: item.type.name
                } else null
            }

            // Extract lore
            val lore = meta?.lore()?.mapNotNull { it.toString() }

            // Extract enchantments
            val enchants = item.enchantments.mapKeys { it.key.key.toString() }
                .mapValues { it.value }

            return SerializableItemStack(
                material = item.type.name,
                amount = item.amount,
                displayName = displayName,
                lore = lore,
                enchantments = enchants,
                nbtData = null // TODO: Implement NBT serialization if needed
            )
        }

        /**
         * Convert serializable data back to a Bukkit ItemStack
         */
        fun toItemStack(data: SerializableItemStack): ItemStack {
            val material = try {
                Material.valueOf(data.material)
            } catch (e: IllegalArgumentException) {
                Material.STONE // Fallback
            }

            val item = ItemStack(material, data.amount)
            val meta = item.itemMeta ?: return item

            // Set display name if present
            data.displayName?.let { name ->
                // For now using legacy text - in production use Component API
                meta.setDisplayName(name)
            }

            // Set lore if present
            data.lore?.let { loreList ->
                meta.lore = loreList
            }

            item.itemMeta = meta

            // Add enchantments
            data.enchantments.forEach { (enchantKey, level) ->
                try {
                    val enchant = org.bukkit.enchantments.Enchantment.getByKey(
                        org.bukkit.NamespacedKey.minecraft(enchantKey.lowercase())
                    )
                    enchant?.let { item.addUnsafeEnchantment(it, level) }
                } catch (e: Exception) {
                    // Skip invalid enchantments
                }
            }

            return item
        }
    }

    /**
     * Get a user-friendly display string for this item
     */
    fun getDisplayString(): String {
        return displayName ?: material.lowercase().replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.capitalize() }
    }
}
