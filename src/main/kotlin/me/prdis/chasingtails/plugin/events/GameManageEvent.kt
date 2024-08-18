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

package me.prdis.chasingtails.plugin.events

import com.destroystokyo.paper.event.player.PlayerTeleportEndGatewayEvent
import me.prdis.chasingtails.plugin.config.ChasingtailsConfig.saveConfigGameProgress
import me.prdis.chasingtails.plugin.managers.ChasingTailsGameManager.gameHalted
import me.prdis.chasingtails.plugin.managers.ChasingTailsGameManager.gamePlayers
import me.prdis.chasingtails.plugin.managers.ChasingTailsResumptionManager.joinedGamePlayers
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.checkPlayers
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.gamePlayerData
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.initEndSpawn
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.lastLocation
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.notifyTeam
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.plugin
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.restoreGamePlayer
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.scoreboard
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.server
import me.prdis.chasingtails.plugin.objects.GamePlayer
import me.prdis.chasingtails.plugin.tasks.ChasingTailsTasks.listNameTask
import me.prdis.chasingtails.plugin.tasks.ChasingTailsTasks.startTasks
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.player.*
import java.util.*

/**
 * @author aroxu, DytroC, ContentManager
 */

object GameManageEvent : Listener {

    @EventHandler
    fun PlayerJoinEvent.onJoin() {
        val uuid = player.uniqueId

        val configGamePlayers = plugin.config.getStringList("gamePlayers")

        if (configGamePlayers.isEmpty()) {
            player.sendMessage(text("설정 파일의 참여 플레이어 정보가 비어있습니다. 버그일 가능성이 매우 높습니다.", NamedTextColor.RED))
            return
        }

        player.noDamageTicks = 0

        joinMessage(
            translatable("multiplayer.player.joined", player.displayName().color(null)).color(NamedTextColor.YELLOW)
        )

        if (gameHalted) {
            if (server.scheduler.pendingTasks.isEmpty()) listNameTask()

            if (uuid.toString() in configGamePlayers) joinedGamePlayers.add(uuid)

            val check = checkPlayers()

            if (check) {
                player.sendMessage(text("기존 게임 플레이 인원이 전부 접속되지 않아 현재 게임이 정지되어있습니다.", NamedTextColor.GRAY))
            } else {
                val players = configGamePlayers.map {
                    GamePlayer(UUID.fromString(it))
                }

                gamePlayers.clear()
                gamePlayers.addAll(players)

                gameHalted = false

                server.scoreboardManager.mainScoreboard.teams.forEach { it.unregister() }

                server.onlinePlayers.filter { it.gameMode != GameMode.SPECTATOR }.forEach {
                    it.restoreGamePlayer()
                    it.sendMessage(text("게임을 재개합니다.", NamedTextColor.GREEN))
                }

                server.onlinePlayers.filter { it.gameMode != GameMode.SPECTATOR }.forEach {
                    it.gamePlayerData?.notifyTeam()
                }

                server.scheduler.cancelTasks(plugin)

                startTasks()
            }
        }

        if (player.uniqueId.toString() !in configGamePlayers) {
            player.gameMode = GameMode.SPECTATOR
            player.sendMessage(text("NOW SPECTATING >").decorate(TextDecoration.BOLD))
            player.sendMessage(text("현재 게임이 진행 중이여서 관전 모드로 전환되었습니다!"))
        }
    }

    @EventHandler
    fun PlayerQuitEvent.onQuit() {
        val uuid = player.uniqueId
        player.lastLocation = player.location

        quitMessage(
            translatable("multiplayer.player.left", player.displayName().color(null)).color(NamedTextColor.YELLOW)
        )

        if (uuid in joinedGamePlayers) {
            if (!gameHalted) {
                gameHalted = false

                server.onlinePlayers.forEach {
                    it.sendMessage(
                        text("! ", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)
                            .append(
                                text(
                                    "게임에 참여한 플레이어가 나갔습니다. 1분 안에 들어오지 않으면 탈락 처리됩니다.(노예 처리)",
                                    NamedTextColor.YELLOW
                                ).decoration(
                                    TextDecoration.BOLD,
                                    false
                                )
                            )
                    )
                }
            }

            saveConfigGameProgress()
        }

        joinedGamePlayers.remove(uuid)
    }

    @EventHandler
    fun PlayerMoveEvent.onMove() {
        val gamePlayer = player.gamePlayerData
        if (gamePlayer?.isDeadTemporarily == true && hasChangedBlock()) {
            isCancelled = true
        }

        if (gameHalted) isCancelled = true
    }

    @EventHandler
    fun PlayerTeleportEndGatewayEvent.onPlayerTeleportEndGateway() {
        isCancelled = true
    }

    @EventHandler
    fun PlayerChangedWorldEvent.onChangedWorld() {
        server.onlinePlayers.filter { scoreboard.getTeam(player.name)?.entries?.contains(it.name) == true }
            .forEach {
                it.teleportAsync(player.location)
            }
    }

    @EventHandler
    fun PlayerPortalEvent.onPlayerPortal() {
        if (from.world.environment == World.Environment.NORMAL && to.world.environment == World.Environment.THE_END && initEndSpawn == null) {
            initEndSpawn = to

            return
        }

        if (from.world.environment == World.Environment.NORMAL && to.world.environment == World.Environment.NETHER) {
            to = Location(to.world, from.blockX.toDouble(), to.blockY.toDouble(), from.blockZ.toDouble())
        } else if (from.world.environment == World.Environment.NETHER && to.world.environment == World.Environment.NORMAL) {
            to = Location(to.world, from.blockX.toDouble(), to.blockY.toDouble(), from.blockZ.toDouble())
        }
    }

    @EventHandler
    fun PlayerKickEvent.onKick() {
        if (cause == PlayerKickEvent.Cause.FLYING_PLAYER) isCancelled = true
    }

    @EventHandler
    fun EntityTargetLivingEntityEvent.onTarget() {
        if (target is Player) {
            val gamePlayer = (target as Player).gamePlayerData
            if (gamePlayer?.isDeadTemporarily == true) {
                isCancelled = true
            }

            if (gameHalted) isCancelled = true
        }
    }

    @EventHandler
    fun BlockPlaceEvent.onPlace() {
        val gamePlayer = player.gamePlayerData
        if (gamePlayer?.isDeadTemporarily == true) isCancelled = true
        if (gameHalted) isCancelled = true
    }

    @EventHandler
    fun BlockBreakEvent.onBreak() {
        val gamePlayer = player.gamePlayerData
        if (gamePlayer?.isDeadTemporarily == true) isCancelled = true
        if (gameHalted) isCancelled = true
    }
}