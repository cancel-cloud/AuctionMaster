package cloud.coffeesystems.auctionmaster.commands

import cloud.coffeesystems.auctionmaster.AuctionMaster
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents

class PaperCommandRegistrar(
        private val plugin: AuctionMaster,
        private val commandExecutor: AuctionCommand,
) {

        @Suppress("UnstableApiUsage")
        fun register() {
                val lifecycleManager = plugin.lifecycleManager
                lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
                        val commands = event.registrar()

                        fun runDelegate(
                                ctx:
                                        com.mojang.brigadier.context.CommandContext<
                                                io.papermc.paper.command.brigadier.CommandSourceStack>,
                                args: Array<String>
                        ): Int {
                                val sender = ctx.source.sender
                                val input = ctx.input.trim()
                                val label = input.substringBefore(" ").ifEmpty { "auction" }

                                val dummyCommand =
                                        object : org.bukkit.command.Command(label) {
                                                override fun execute(
                                                        sender: org.bukkit.command.CommandSender,
                                                        commandLabel: String,
                                                        args: Array<out String>
                                                ): Boolean {
                                                        return true
                                                }
                                        }

                                commandExecutor.onCommand(sender, dummyCommand, label, args)
                                return Command.SINGLE_SUCCESS
                        }

                        val root =
                                Commands.literal("auction")
                                        .requires { source ->
                                                source.sender.hasPermission("auctionmaster.use")
                                        }
                                        .executes { ctx -> runDelegate(ctx, emptyArray()) }

                        root.then(
                                Commands.literal("create")
                                        .executes { ctx -> runDelegate(ctx, arrayOf("create")) }
                                        .then(
                                                Commands.argument(
                                                                "price",
                                                                DoubleArgumentType.doubleArg(0.0)
                                                        )
                                                        .executes { ctx ->
                                                                val price =
                                                                        DoubleArgumentType
                                                                                .getDouble(
                                                                                        ctx,
                                                                                        "price"
                                                                                )
                                                                runDelegate(
                                                                        ctx,
                                                                        arrayOf(
                                                                                "create",
                                                                                price.toString()
                                                                        )
                                                                )
                                                        }
                                                        .then(
                                                                Commands.argument(
                                                                                "duration",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .executes { ctx ->
                                                                                val price =
                                                                                        DoubleArgumentType
                                                                                                .getDouble(
                                                                                                        ctx,
                                                                                                        "price"
                                                                                                )
                                                                                val duration =
                                                                                        StringArgumentType
                                                                                                .getString(
                                                                                                        ctx,
                                                                                                        "duration"
                                                                                                )
                                                                                runDelegate(
                                                                                        ctx,
                                                                                        arrayOf(
                                                                                                "create",
                                                                                                price.toString(),
                                                                                                duration
                                                                                        )
                                                                                )
                                                                        }
                                                        )
                                        )
                        )

                        val listHandler =
                                Command<io.papermc.paper.command.brigadier.CommandSourceStack> { ctx
                                        ->
                                        runDelegate(ctx, arrayOf("list"))
                                }
                        root.then(Commands.literal("list").executes(listHandler))
                        root.then(Commands.literal("browse").executes(listHandler))
                        root.then(Commands.literal("shop").executes(listHandler))

                        root.then(
                                Commands.literal("view")
                                        .then(
                                                Commands.argument(
                                                                "player",
                                                                StringArgumentType.word()
                                                        )
                                                        .suggests { _, builder ->
                                                                val sellers =
                                                                        if (plugin.hasAuctionManager()) {
                                                                                plugin.auctionManager
                                                                                        .getUniqueSellers()
                                                                        } else {
                                                                                emptyList()
                                                                        }
                                                                val input = builder.remaining.lowercase()

                                                                sellers
                                                                        .filter { it.lowercase().startsWith(input) }
                                                                        .forEach { builder.suggest(it) }

                                                                builder.buildFuture()
                                                        }
                                                        .executes { ctx ->
                                                                val player =
                                                                        StringArgumentType
                                                                                .getString(
                                                                                        ctx,
                                                                                        "player"
                                                                                )
                                                                runDelegate(
                                                                        ctx,
                                                                        arrayOf("view", player)
                                                                )
                                                        }
                                        )
                        )

                        val cancelNode =
                                Commands.literal("cancel")
                                        .executes { ctx -> runDelegate(ctx, arrayOf("cancel")) }
                                        .then(
                                                Commands.argument(
                                                                "id",
                                                                IntegerArgumentType.integer()
                                                        )
                                                        .executes { ctx ->
                                                                val id =
                                                                        IntegerArgumentType
                                                                                .getInteger(
                                                                                        ctx,
                                                                                        "id"
                                                                                )
                                                                runDelegate(
                                                                        ctx,
                                                                        arrayOf(
                                                                                "cancel",
                                                                                id.toString()
                                                                        )
                                                                )
                                                        }
                                        )
                                        .build()

                        root.then(cancelNode)
                        root.then(Commands.literal("remove").redirect(cancelNode))

                        root.then(
                                Commands.literal("info")
                                        .then(
                                                Commands.argument(
                                                                "id",
                                                                IntegerArgumentType.integer()
                                                        )
                                                        .executes { ctx ->
                                                                val id =
                                                                        IntegerArgumentType
                                                                                .getInteger(
                                                                                        ctx,
                                                                                        "id"
                                                                                )
                                                                runDelegate(
                                                                        ctx,
                                                                        arrayOf(
                                                                                "info",
                                                                                id.toString()
                                                                        )
                                                                )
                                                        }
                                        )
                        )

                        root.then(
                                Commands.literal("help").executes { ctx ->
                                        runDelegate(ctx, arrayOf("help"))
                                }
                        )

                        root.then(
                                Commands.literal("reload")
                                        .requires { source ->
                                                source.sender.hasPermission("auctionmaster.admin")
                                        }
                                        .executes { ctx -> runDelegate(ctx, arrayOf("reload")) }
                        )

                        root.then(
                                Commands.literal("clear")
                                        .requires { source ->
                                                source.sender.hasPermission("auctionmaster.admin")
                                        }
                                        .executes { ctx -> runDelegate(ctx, arrayOf("clear")) }
                        )

                        root.then(
                                Commands.literal("history")
                                        .executes { ctx -> runDelegate(ctx, arrayOf("history")) }
                                        .then(
                                                Commands.argument("player", StringArgumentType.word())
                                                        .suggests { _, builder ->
                                                                val input = builder.remaining.lowercase()
                                                                plugin.server.onlinePlayers
                                                                        .map { it.name }
                                                                        .filter { it.lowercase().startsWith(input) }
                                                                        .forEach { builder.suggest(it) }

                                                                builder.buildFuture()
                                                        }
                                                        .executes { ctx ->
                                                                val player =
                                                                        StringArgumentType
                                                                                .getString(
                                                                                        ctx,
                                                                                        "player"
                                                                                )
                                                                runDelegate(
                                                                        ctx,
                                                                        arrayOf("history", player)
                                                                )
                                                        }
                                        )
                        )

                        commands.register(
                                root.build(),
                                "Main auction command",
                                listOf("auctionhouse", "auctions", "am", "ah")
                        )
                }
        }
}
