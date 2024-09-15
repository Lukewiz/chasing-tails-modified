/*
 * Copyright (C) 2024 Paradise Dev Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package me.prdis.chasingtails.plugin.tasks

import com.github.shynixn.mccoroutine.bukkit.launch
import kotlinx.coroutines.delay
import me.prdis.chasingtails.plugin.managers.ChasingTailsGameManager
import me.prdis.chasingtails.plugin.managers.ChasingTailsGameManager.gamePlayers
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.color
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.plugin
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.server
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.util.NumberConversions
import kotlin.math.roundToInt

/**
 * @author aroxu, DytroC, ContentManager
 */

object ChasingTailsTasks {
    private val masterComponent = text("주인(")

    fun startTasks() {
        distancingTask()
        gameTick()
        heartbeatTask()
        listNameTask()
    }

    fun stopTasks() {
        server.scheduler.cancelTasks(plugin)
    }

    fun listNameTask() {
        server.scheduler.runTaskTimer(plugin, Runnable {
            server.onlinePlayers.forEach {
                it.playerListName(text(it.name, NamedTextColor.WHITE))
            }
        }, 0L, 0L)
    }

    private fun gameTick() {
        server.scheduler.runTaskTimer(plugin, Runnable {
            ++ChasingTailsGameManager.currentTick

            gamePlayers.forEach {
                val master = it.master

                if (it.isDeadTemporarily) {
                    val value = --it.temporaryDeathDuration

                    if (master == null) {
                        it.player.sendActionBar(
                            text("부활까지 ", NamedTextColor.GOLD)
                                .append(text(NumberConversions.ceil(value / 20.0), NamedTextColor.YELLOW))
                                .append(text("초 남았습니다!", NamedTextColor.GOLD))
                        )
                    }
                }

                if (master != null) {
                    it.player.apply {
                        val masterLocation = master.player.location
                        compassTarget = masterLocation

                        if (location.world != master.player.world) {
                            sendActionBar(
                                masterComponent.color(NamedTextColor.RED)
                                    .append(text(master.name, master.player.color))
                                    .append(text(")과 같은 차원에 존재하지 않습니다!", NamedTextColor.RED))
                            )
                        } else if (it.tooFar) {
                            sendActionBar(
                                masterComponent.color(NamedTextColor.RED)
                                    .append(text(master.player.name, master.player.color))
                                    .append(text(")과의 거리가 너무 멉니다!", NamedTextColor.RED))
                            )
                        } else {
                            sendActionBar(
                                masterComponent.color(NamedTextColor.GOLD)
                                    .append(text(master.name, master.player.color))
                                    .append(
                                        text(
                                            ")과의 거리: ",
                                            NamedTextColor.GOLD
                                        )
                                    )
                                    .append(
                                        text(
                                            "${
                                                location.distance(masterLocation).roundToInt()
                                            }m",
                                            NamedTextColor.WHITE
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }, 0, 1)
    }

    private fun heartbeatTask() {
        server.scheduler.runTaskTimer(plugin, Runnable {
            gamePlayers.filter { it.master == null }.forEach {
                val target = it.target.player
                val player = it.player

                if (player.location.world == target.location.world) {
                    val dist = player.location.distance(target.location)
                    val volume = when (dist) {
                        in 0.0..10.0 -> 1.0F
                        in 10.0..20.0 -> 0.8F
                        in 20.0..30.0 -> 0.6F
                        in 30.0..40.0 -> 0.4F
                        in 40.0..50.0 -> 0.2F
                        else -> 0.0F
                    }

                    if (dist <= 50.0) {
                        plugin.launch {
                            target.sendActionBar(
                                text(
                                    "[ ♥ ]",
                                    NamedTextColor.RED
                                ).decorate(TextDecoration.BOLD)
                            )
                            delay(200)
                            target.sendActionBar(text(""))
                        }

                        target.playSound(
                            Sound.sound(
                                Key.key("entity.warden.heartbeat"),
                                Sound.Source.MASTER,
                                volume,
                                1F
                            )
                        )
                    }
                }

            }
        }, 0, 20)
    }

    private fun distancingTask() {
        server.scheduler.runTaskTimer(plugin, Runnable {
            gamePlayers.forEach {
                val master = it.master?.player ?: return@forEach
                if (!it.isDeadTemporarily) {
                    val player = it.player
                    if (player.world != master.world || player.location.distance(master.location) > 50) {
                        player.damage(1.0)
                        it.tooFar = true
                    } else {
                        it.tooFar = false
                    }
                }
            }
        }, 0, 10)
    }
}