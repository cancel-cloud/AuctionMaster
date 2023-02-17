package interchange

import de.fruxz.sparkle.framework.extension.asPlayer
import de.fruxz.sparkle.framework.extension.asPlayerOrNull
import de.fruxz.sparkle.framework.extension.interchange.InterchangeExecutor
import de.fruxz.sparkle.framework.extension.structureManager
import de.fruxz.sparkle.framework.extension.visual.notification
import de.fruxz.sparkle.framework.extension.visual.ui.item
import de.fruxz.sparkle.framework.infrastructure.command.Interchange
import de.fruxz.sparkle.framework.infrastructure.command.InterchangeResult
import de.fruxz.sparkle.framework.infrastructure.command.InterchangeUserRestriction
import de.fruxz.sparkle.framework.infrastructure.command.completion.buildInterchangeStructure
import de.fruxz.sparkle.framework.infrastructure.command.live.InterchangeAccess
import de.fruxz.sparkle.framework.infrastructure.command.structured.StructuredInterchange
import de.fruxz.sparkle.framework.infrastructure.command.structured.StructuredPlayerInterchange
import de.fruxz.sparkle.framework.visual.canvas.Canvas
import de.fruxz.sparkle.framework.visual.canvas.buildCanvas
import de.fruxz.sparkle.framework.visual.canvas.pagination.PaginationType
import de.fruxz.sparkle.framework.visual.color.ColorType
import de.fruxz.sparkle.framework.visual.color.DyeableMaterial
import de.fruxz.sparkle.framework.visual.message.TransmissionAppearance
import de.fruxz.stacked.extension.plus
import de.fruxz.stacked.hover
import de.fruxz.stacked.plus
import de.fruxz.stacked.text
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import kotlin.random.Random

@OptIn(Canvas.ExperimentalCanvasApi::class)
class AuctionInterchange : StructuredPlayerInterchange(
    label = "auction",
    commandProperties = CommandProperties.aliases("ah", "auctionhouse", "auctions", "auctionmaster", "am"),
    structure = buildInterchangeStructure {
        branch {
            addContent("create")
            concludedExecution {
                val player = executor.asPlayer
                val item = player.inventory.itemInMainHand
                if (item.type == Material.AIR) {
                    text {
                        this + text("You need to hold an item in your hand to create an auction!").color(NamedTextColor.RED)
                    }.notification(TransmissionAppearance.Companion.GENERAL, executor).display()
                    return@concludedExecution
                }
                //TODO: add auction here
            }
        }

        branch {
            addContent("list")
            concludedExecution {
                //TODO: list auctions here
                executor.asPlayerOrNull?.let {
                    buildCanvas {
                        label("<i>Hello!")
                        pagination(PaginationType.scroll())
                        base(9 * 6)

                        repeat(217) {
                            this[it] = DyeableMaterial.BANNER.withColor(ColorType.values().random()).item {
                                label("$it")
                            }
                        }
                    }.display(executor.asPlayer)
                }
            }
        }


















        concludedExecution {
            text {
                this + text("AuctionMaster (am)") {
                    style(Style.style(NamedTextColor.GOLD, TextDecoration.BOLD))
                    //hover { text("Click to visit the plugin page!").color(NamedTextColor.GRAY) }
                    //clickEvent(ClickEvent.openUrl("https://modrinth.com/plugin/mtpah"))
                }
                this + text(" Is developed by ").color(NamedTextColor.GRAY)
                this + text("@cancel-cloud").style(Style.style(NamedTextColor.YELLOW, TextDecoration.BOLD))
            }
                .notification(TransmissionAppearance.Companion.GENERAL, executor).display()
        }
    })