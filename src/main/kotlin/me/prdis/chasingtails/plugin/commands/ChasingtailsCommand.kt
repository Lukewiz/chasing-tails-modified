package me.prdis.chasingtails.plugin.commands

import com.github.shynixn.mccoroutine.bukkit.launch
import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import me.prdis.chasingtails.plugin.managers.ChasingTailsGameManager.isRunning
import me.prdis.chasingtails.plugin.managers.ChasingTailsGameManager.startGame
import me.prdis.chasingtails.plugin.managers.ChasingTailsGameManager.stopGame
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.plugin
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.server
import kotlinx.coroutines.delay
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import me.prdis.chasingtails.plugin.config.ChasingtailsConfig.revisespawnRadius
import me.prdis.chasingtails.plugin.config.ChasingtailsConfig.revisedeathPenalty
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit

@Suppress("UnstableApiUsage")
object ChasingtailsCommand {
    fun registerCommand() {
        plugin.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()
            val ct = Commands.literal("chasingtails").requires { it.sender.isOp }

            ct.executes {
                val ctx = it.source.sender
                ctx.sendMessage(
                    text(
                        """
                        꼬리잡기 (Chasing-tails) 플러그인
                        by Paradise Dev Team
                        -----------------------------
                        도움말
                        - 게임 시작: /chasingtails start
                        - 게임 종료: /chasingtails stop
                        
                        ※ /ct 로 축약 사용 가능합니다.
                    """.trimIndent()
                    )
                )

                Command.SINGLE_SUCCESS
            }

            ct.then(Commands.literal("start").executes {
                val ctx = it.source.sender

                if (server.onlinePlayers.filter { player -> player.gameMode != GameMode.SPECTATOR }.size !in 2..10) {
                    ctx.sendMessage(text("플레이어의 수가 2 ~ 10명 이어야합니다.", NamedTextColor.RED))
                } else {
                    if (!isRunning) {
                        plugin.launch {
                            server.onlinePlayers.forEach { player ->
                                player.sendMessage(text("게임 도중 나가는 경우 버그가 발생 할 수 있습니다.", NamedTextColor.RED))
                                player.sendMessage(text("3초 후 게임이 시작됩니다.", NamedTextColor.GREEN))
                                delay(1000)
                                player.sendMessage(text("2초 후 게임이 시작됩니다.", NamedTextColor.GOLD))
                                delay(1000)
                                player.sendMessage(text("1초 후 게임이 시작됩니다.", NamedTextColor.RED))
                                delay(1000)
                            }
                            delay(100)
                            server.onlinePlayers.forEach { player ->
                                repeat(100) {
                                    player.sendMessage(text("\n"))
                                }
                            }

                            startGame()
                            server.onlinePlayers.forEach { player ->
                                player.sendMessage(text("게임을 시작합니다.", NamedTextColor.GREEN))
                            }
                        }
                    } else {
                        ctx.sendMessage(text("이미 게임이 진행중입니다.", NamedTextColor.RED))
                    }
                }

                Command.SINGLE_SUCCESS
            })

            ct.then(Commands.literal("stop").executes {
                val ctx = it.source.sender

                if (isRunning) {
                    stopGame()
                    ctx.sendMessage(text("게임을 종료합니다.", NamedTextColor.GREEN))
                } else {
                    ctx.sendMessage(text("게임이 진행중이지 않습니다.", NamedTextColor.RED))
                }

                Command.SINGLE_SUCCESS
            })

            // Add the spawnRadius command with an integer argument
            ct.then(Commands.literal("spawnRadius")
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 10000)) // Argument from 1 to 100
                    .executes { context: CommandContext<*> ->
                        val radius = IntegerArgumentType.getInteger(context, "radius")

                        // Here you can set the spawn radius or call the appropriate method
                        revisespawnRadius(radius)
                        for (i in Bukkit.getOnlinePlayers()){
                            i.sendMessage(text("게임 변수가 변경되었습니다.", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                        }
                        Command.SINGLE_SUCCESS
                    }
                )
            )
            ct.then(Commands.literal("deathPenalty")
                .then(Commands.argument("penalty", IntegerArgumentType.integer(1, 10000)) // Argument from 1 to 100
                    .executes { context: CommandContext<*> ->
                        val penalty = IntegerArgumentType.getInteger(context, "penalty")

                        // Here you can set the spawn radius or call the appropriate method
                        revisedeathPenalty(penalty)
                        for (i in Bukkit.getOnlinePlayers()){
                            i.sendMessage(text("게임 변수가 변경되었습니다.", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
                        }
                        Command.SINGLE_SUCCESS
                    }
                )
            )

            registrar.register(ct.build(), "A chasing tails command.", listOf("ct"))
        }
    }
}
