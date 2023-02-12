package interchange

import de.fruxz.sparkle.framework.extension.asPlayer
import de.fruxz.sparkle.framework.extension.interchange.InterchangeExecutor
import de.fruxz.sparkle.framework.infrastructure.command.Interchange
import de.fruxz.sparkle.framework.infrastructure.command.InterchangeResult
import de.fruxz.sparkle.framework.infrastructure.command.InterchangeUserRestriction
import de.fruxz.sparkle.framework.infrastructure.command.live.InterchangeAccess
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

class AuctionInterchange : Interchange(
    label = "am",
    commandProperties = CommandProperties.aliases("auctionmaster", "auction", "ah", "auction"),
    requiredClient = InterchangeUserRestriction.ONLY_PLAYERS,
) {
    override val execution: suspend InterchangeAccess<out InterchangeExecutor>.() -> InterchangeResult = {
        executor.sendMessage("Hello World!")
        executor.asPlayer.inventory.addItem(ItemStack(Material.PURPLE_CONCRETE_POWDER, Random.nextInt(64)))
        InterchangeResult.SUCCESS
    }
}