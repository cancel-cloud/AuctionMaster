package model

import kotlinx.serialization.Serializable

@Serializable
enum class AuctionCategory {
    WEAPONS,
    ARMOR,
    TOOLS,
    BLOCKS,
    FOOD,
    POTIONS,
    ENCHANTED,
    RARE,
    OTHER
}
