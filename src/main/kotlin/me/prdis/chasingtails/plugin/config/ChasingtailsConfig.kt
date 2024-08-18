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

package me.prdis.chasingtails.plugin.config

import me.prdis.chasingtails.plugin.managers.ChasingTailsGameManager.gamePlayers
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.gamePlayerData
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.plugin
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.scoreboard
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.server

/**
 * @author aroxu, DytroC, ContentManager
 */

object ChasingtailsConfig {
    fun saveConfigGameProgress() {
        server.onlinePlayers.forEach { player ->
            player.gamePlayerData?.let { gamePlayer ->
                val team = scoreboard.getPlayerTeam(player)

                if (team != null) {
                    plugin.config.set(
                        "chasingtails.${player.uniqueId}",
                        GamePlayerData(
                            team.name,
                            team.color().value(),
                            gamePlayer.target.takeIf { gamePlayer.master == null }?.run { uuid.toString() },
                            gamePlayer.master?.run { uuid.toString() },
                            gamePlayer.deathTimer?.run { uniqueId.toString() },
                            gamePlayer.temporaryDeathDuration
                        )
                    )
                }
            }
        }

        plugin.config.set("gamePlayers", gamePlayers.map { it.offlinePlayer.uniqueId.toString() })

        plugin.saveConfig()
    }

    fun resetConfigGameProgress() {
        server.offlinePlayers.forEach { player ->
            plugin.config.set("chasingtails.${player.uniqueId}", null)
        }

        plugin.config.set("chasingtails", null)
        plugin.config.set("gamePlayers", null)

        plugin.saveConfig()
    }
}