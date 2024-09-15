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

package me.prdis.chasingtails.plugin.managers

import me.prdis.chasingtails.plugin.config.ChasingtailsConfig.resetConfigGameProgress
import me.prdis.chasingtails.plugin.events.GameManageEvent
import me.prdis.chasingtails.plugin.events.HuntingEvent
import me.prdis.chasingtails.plugin.managers.ChasingTailsResumptionManager.joinedGamePlayers
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.checkPlayers
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.lastLocation
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.notifyTeam
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.plugin
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.reinitializeScoreboard
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.scoreboard
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.server
import me.prdis.chasingtails.plugin.objects.GamePlayer
import me.prdis.chasingtails.plugin.tasks.ChasingTailsTasks
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.HandlerList
import java.util.*
import kotlin.random.Random

/**
 * @author aroxu, DytroC, ContentManager
 */

object ChasingTailsGameManager {
    var isRunning = false
        set(value) {
            plugin.config.set("isRunning", value)

            field = value
        }

    var gameHalted = false

    val gamePlayers = LinkedList<GamePlayer>()

    var currentTick = 0

    fun startGame() {
        if (!isRunning) {
            isRunning = true
            val max = plugin.config.getInt("spawnRadius")
            var max2 = plugin.config.getInt("spawnRadius")
            max2*=-1

            scoreboard.reinitializeScoreboard()

            val uuidMap = server.onlinePlayers.filter { it.gameMode != GameMode.SPECTATOR }.map { it.uniqueId }

            gamePlayers.addAll(server.onlinePlayers.filter { it.gameMode != GameMode.SPECTATOR }
                .map { GamePlayer(it.uniqueId) }.shuffled())

            joinedGamePlayers.addAll(uuidMap)

            server.worlds.forEach(ChasingTailsUtils::manageWorld)

            gamePlayers.forEachIndexed { index, gamePlayer ->
                gamePlayer.target = gamePlayers[(index + 1) % gamePlayers.size]
                val player = gamePlayer.player
                player.scoreboard = scoreboard.also {
                    it.getTeam("team$index")?.addPlayer(player)
                }

                player.exp = 0F

                val x = Random.nextInt(max2, max)
                val z = Random.nextInt(max2, max)

                player.teleportAsync(
                    Location(
                        player.world,
                        x + 0.5,
                        player.world.getHighestBlockYAt(x, z) + 1.0,
                        z + 0.5,
                    )
                )

                player.restoreDefaults()
            }

            gamePlayers.forEach { gamePlayer ->
                gamePlayer.notifyTeam()
            }

            ChasingTailsTasks.startTasks()
        } else {
            gameHalted = checkPlayers()
        }

        server.pluginManager.registerEvents(HuntingEvent, plugin)
        server.pluginManager.registerEvents(GameManageEvent, plugin)
    }

    fun stopGame() {
        gamePlayers.clear()

        currentTick = 0

        HandlerList.unregisterAll(HuntingEvent)
        HandlerList.unregisterAll(GameManageEvent)

        ChasingTailsTasks.stopTasks()

        scoreboard.teams.forEach { it.unregister() }

        server.onlinePlayers.forEach { it.restoreDefaults() }
        server.offlinePlayers.forEach { it.lastLocation = null }
        plugin.config.set("last_location", null)

        server.worlds.forEach { world ->
            world.entities.filterIsInstance<TextDisplay>().forEach {
                it.remove()
            }
        }

        resetConfigGameProgress()

        isRunning = false
    }

    private fun Player.restoreDefaults() {
        inventory.clear()
        getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = 20.0

        health = 20.0
        foodLevel = 20
        saturation = 3F
        exhaustion = 0F

        gameMode = GameMode.SURVIVAL
    }
}
