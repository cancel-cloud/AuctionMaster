package interchange

import config.AuctionConfig
import config.Messages
import database.AuctionManager
import de.fruxz.sparkle.framework.extension.asPlayer
import de.fruxz.sparkle.framework.extension.asPlayerOrNull
import de.fruxz.sparkle.framework.extension.visual.notification
import de.fruxz.sparkle.framework.infrastructure.command.completion.buildInterchangeStructure
import de.fruxz.sparkle.framework.infrastructure.command.structured.StructuredPlayerInterchange
import de.fruxz.sparkle.framework.visual.canvas.Canvas
import de.fruxz.sparkle.framework.visual.message.TransmissionAppearance
import de.fruxz.stacked.extension.plus
import de.fruxz.stacked.plus
import de.fruxz.stacked.text
import gui.AuctionListGUI
import gui.ClaimsGUI
import kotlinx.coroutines.runBlocking
import model.*
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import service.AuctionService
import service.ProcessResult
import service.ValidationResult
import util.TimeUtil
import java.util.UUID

@OptIn(Canvas.ExperimentalCanvasApi::class)
class AuctionInterchange : StructuredPlayerInterchange(
    label = "auction",
    commandProperties = CommandProperties.aliases("ah", "auctionhouse", "auctions", "auctionmaster", "am"),
    structure = buildInterchangeStructure {
        val config = AuctionConfig.default()
        val service = AuctionService(config)

        // /auction or /ah - Open auction house
        branch {
            addContent("list")
            concludedExecution {
                executor.asPlayerOrNull?.let { player ->
                    AuctionListGUI(player).build().display(player)
                }
            }
        }

        // /auction create <price> [duration] [buyNow] - Create auction
        branch {
            addContent("create")
            branch {
                addContent("price", true) // Start price
                branch {
                    addContent("duration", false) // Optional duration (e.g., "24h")
                    branch {
                        addContent("buynow", false) // Optional buy now price
                        concludedExecution {
                            val player = executor.asPlayer
                            val item = player.inventory.itemInMainHand

                            if (item.type == Material.AIR) {
                                text {
                                    this + text(Messages.NO_ITEM_IN_HAND).color(NamedTextColor.RED)
                                }.notification(TransmissionAppearance.GENERAL, executor).display()
                                return@concludedExecution
                            }

                            val priceStr = getInput("price") ?: "0"
                            val startPrice = priceStr.toDoubleOrNull() ?: 0.0

                            val durationStr = getInput("duration") ?: "24h"
                            val duration = TimeUtil.parseTimeString(durationStr) ?: TimeUtil.hoursToMillis(24)

                            val buyNowStr = getInput("buynow")
                            val buyNowPrice = buyNowStr?.toDoubleOrNull()

                            runBlocking {
                                // Validate
                                val validation = service.validateAuctionCreation(
                                    player, item, startPrice, duration, buyNowPrice
                                )

                                if (validation !is ValidationResult.Success) {
                                    text {
                                        this + text(validation.getErrorMessage()).color(NamedTextColor.RED)
                                    }.notification(TransmissionAppearance.GENERAL, executor).display()
                                    return@runBlocking
                                }

                                // Create auction
                                val auction = Auction(
                                    sellerId = player.uniqueId,
                                    sellerName = player.name,
                                    itemStack = SerializableItemStack.from(item),
                                    startPrice = startPrice,
                                    currentBid = startPrice,
                                    buyNowPrice = buyNowPrice,
                                    expiresAt = System.currentTimeMillis() + duration,
                                    duration = duration,
                                    category = AuctionCategory.OTHER
                                )

                                val result = AuctionManager.createAuction(auction)

                                if (result.isSuccess) {
                                    // Remove item from player's hand
                                    player.inventory.setItemInMainHand(null)

                                    text {
                                        this + text(
                                            Messages.format(
                                                Messages.AUCTION_CREATED,
                                                "item" to auction.itemStack.getDisplayString()
                                            )
                                        ).color(NamedTextColor.GREEN)
                                    }.notification(TransmissionAppearance.GENERAL, executor).display()
                                } else {
                                    text {
                                        this + text(Messages.ERROR_OCCURRED).color(NamedTextColor.RED)
                                    }.notification(TransmissionAppearance.GENERAL, executor).display()
                                }
                            }
                        }
                    }
                    concludedExecution {
                        val player = executor.asPlayer
                        val item = player.inventory.itemInMainHand

                        if (item.type == Material.AIR) {
                            text {
                                this + text(Messages.NO_ITEM_IN_HAND).color(NamedTextColor.RED)
                            }.notification(TransmissionAppearance.GENERAL, executor).display()
                            return@concludedExecution
                        }

                        val priceStr = getInput("price") ?: "0"
                        val startPrice = priceStr.toDoubleOrNull() ?: 0.0

                        val durationStr = getInput("duration") ?: "24h"
                        val duration = TimeUtil.parseTimeString(durationStr) ?: TimeUtil.hoursToMillis(24)

                        runBlocking {
                            val auction = Auction(
                                sellerId = player.uniqueId,
                                sellerName = player.name,
                                itemStack = SerializableItemStack.from(item),
                                startPrice = startPrice,
                                currentBid = startPrice,
                                expiresAt = System.currentTimeMillis() + duration,
                                duration = duration,
                                category = AuctionCategory.OTHER
                            )

                            val result = AuctionManager.createAuction(auction)

                            if (result.isSuccess) {
                                player.inventory.setItemInMainHand(null)
                                text {
                                    this + text("Auction created!").color(NamedTextColor.GREEN)
                                }.notification(TransmissionAppearance.GENERAL, executor).display()
                            }
                        }
                    }
                }
                concludedExecution {
                    // Default 24h duration
                    val player = executor.asPlayer
                    val item = player.inventory.itemInMainHand

                    if (item.type == Material.AIR) {
                        text {
                            this + text(Messages.NO_ITEM_IN_HAND).color(NamedTextColor.RED)
                        }.notification(TransmissionAppearance.GENERAL, executor).display()
                        return@concludedExecution
                    }

                    val priceStr = getInput("price") ?: "0"
                    val startPrice = priceStr.toDoubleOrNull() ?: 0.0

                    runBlocking {
                        val auction = Auction(
                            sellerId = player.uniqueId,
                            sellerName = player.name,
                            itemStack = SerializableItemStack.from(item),
                            startPrice = startPrice,
                            currentBid = startPrice,
                            expiresAt = System.currentTimeMillis() + TimeUtil.hoursToMillis(24),
                            duration = TimeUtil.hoursToMillis(24),
                            category = AuctionCategory.OTHER
                        )

                        AuctionManager.createAuction(auction)
                        player.inventory.setItemInMainHand(null)

                        text {
                            this + text("Auction created for $startPrice!").color(NamedTextColor.GREEN)
                        }.notification(TransmissionAppearance.GENERAL, executor).display()
                    }
                }
            }
        }

        // /auction bid <auctionId> <amount> - Place bid
        branch {
            addContent("bid")
            branch {
                addContent("id", true)
                branch {
                    addContent("amount", true)
                    concludedExecution {
                        val player = executor.asPlayer
                        val auctionIdStr = getInput("id") ?: return@concludedExecution
                        val amountStr = getInput("amount") ?: return@concludedExecution

                        val auctionId = try {
                            UUID.fromString(auctionIdStr)
                        } catch (e: Exception) {
                            text {
                                this + text("Invalid auction ID!").color(NamedTextColor.RED)
                            }.notification(TransmissionAppearance.GENERAL, executor).display()
                            return@concludedExecution
                        }

                        val amount = amountStr.toDoubleOrNull() ?: run {
                            text {
                                this + text(Messages.INVALID_AMOUNT).color(NamedTextColor.RED)
                            }.notification(TransmissionAppearance.GENERAL, executor).display()
                            return@concludedExecution
                        }

                        runBlocking {
                            val auction = AuctionManager.getAuction(auctionId)

                            if (auction == null) {
                                text {
                                    this + text("Auction not found!").color(NamedTextColor.RED)
                                }.notification(TransmissionAppearance.GENERAL, executor).display()
                                return@runBlocking
                            }

                            val validation = service.validateBid(auction, player, amount)

                            if (validation !is ValidationResult.Success) {
                                text {
                                    this + text(validation.getErrorMessage()).color(NamedTextColor.RED)
                                }.notification(TransmissionAppearance.GENERAL, executor).display()
                                return@runBlocking
                            }

                            val bid = Bid(
                                auctionId = auctionId,
                                bidderId = player.uniqueId,
                                bidderName = player.name,
                                amount = amount
                            )

                            val result = service.processBid(auction, bid)

                            if (result is ProcessResult.Success) {
                                text {
                                    this + text(result.getMessage()).color(NamedTextColor.GREEN)
                                }.notification(TransmissionAppearance.GENERAL, executor).display()
                            } else {
                                text {
                                    this + text(result.getMessage()).color(NamedTextColor.RED)
                                }.notification(TransmissionAppearance.GENERAL, executor).display()
                            }
                        }
                    }
                }
            }
        }

        // /auction claims - View claims
        branch {
            addContent("claims")
            concludedExecution {
                executor.asPlayerOrNull?.let { player ->
                    ClaimsGUI(player, service).build().display(player)
                }
            }
        }

        // /auction myauctions - View player's own auctions
        branch {
            addContent("myauctions")
            concludedExecution {
                executor.asPlayerOrNull?.let { player ->
                    val filter = AuctionFilter.forSeller(player.uniqueId)
                    AuctionListGUI(player, filter).build().display(player)
                }
            }
        }

        // /auction mybids - View auctions player has bid on
        branch {
            addContent("mybids")
            concludedExecution {
                executor.asPlayerOrNull?.let { player ->
                    val filter = AuctionFilter.forBidder(player.uniqueId)
                    AuctionListGUI(player, filter).build().display(player)
                }
            }
        }

        // Default: Show help/info
        concludedExecution {
            text {
                this + text("AuctionMaster") {
                    style(Style.style(NamedTextColor.GOLD, TextDecoration.BOLD))
                }
                this + text(" Commands:\n").color(NamedTextColor.GRAY)
                this + text("/ah").color(NamedTextColor.YELLOW)
                this + text(" - Open auction house\n").color(NamedTextColor.GRAY)
                this + text("/ah create <price> [duration] [buyNow]").color(NamedTextColor.YELLOW)
                this + text(" - Create auction\n").color(NamedTextColor.GRAY)
                this + text("/ah claims").color(NamedTextColor.YELLOW)
                this + text(" - View claims\n").color(NamedTextColor.GRAY)
                this + text("/ah myauctions").color(NamedTextColor.YELLOW)
                this + text(" - View your auctions\n").color(NamedTextColor.GRAY)
                this + text("/ah mybids").color(NamedTextColor.YELLOW)
                this + text(" - View your bids").color(NamedTextColor.GRAY)
            }.notification(TransmissionAppearance.GENERAL, executor).display()
        }
    }
)
