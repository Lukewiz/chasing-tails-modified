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

package me.prdis.chasingtails.plugin.objects

import me.prdis.chasingtails.plugin.ChasingtailsPlugin
import me.prdis.chasingtails.plugin.config.GamePlayerData
import me.prdis.chasingtails.plugin.managers.ChasingTailsGameManager.gamePlayers
import me.prdis.chasingtails.plugin.managers.ChasingTailsResumptionManager.joinedGamePlayers
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.scoreboard.Scoreboard

/**
 * @author aroxu, DytroC, ContentManager
 */

object ChasingTailsUtils {
    val plugin = ChasingtailsPlugin.instance
    val server = plugin.server

    val scoreboard = server.scoreboardManager.mainScoreboard.apply {
        reinitializeScoreboard()
    }

    private val mappedColors = hashMapOf<NamedTextColor, String>(
        Pair(NamedTextColor.LIGHT_PURPLE, "핑크색"),
        Pair(NamedTextColor.RED, "빨간색"),
        Pair(NamedTextColor.GOLD, "주황색"),
        Pair(NamedTextColor.YELLOW, "노란색"),
        Pair(NamedTextColor.GREEN, "초록색"),
        Pair(NamedTextColor.BLUE, "파란색"),
        Pair(NamedTextColor.DARK_BLUE, "남색"),
        Pair(NamedTextColor.DARK_PURPLE, "보라색"),
        Pair(NamedTextColor.GRAY, "회색"),
        Pair(NamedTextColor.AQUA, "아쿠아색")
    )

    fun Scoreboard.reinitializeScoreboard() {
        val colorList = listOf(
            NamedTextColor.LIGHT_PURPLE,
            NamedTextColor.RED,
            NamedTextColor.GOLD,
            NamedTextColor.YELLOW,
            NamedTextColor.GREEN,
            NamedTextColor.BLUE,
            NamedTextColor.DARK_BLUE,
            NamedTextColor.DARK_PURPLE,
            NamedTextColor.GRAY,
            NamedTextColor.AQUA
        ).reversed()

        colorList.forEachIndexed { index, color ->
            getTeam("team$index")?.unregister()

            registerNewTeam("team$index").apply {
                color(color)
            }
        }
    }

    fun manageWorld(world: World) = world.apply {
        setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
        setGameRule(GameRule.KEEP_INVENTORY, true)
        setGameRule(GameRule.SHOW_DEATH_MESSAGES, false)
        setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
        difficulty = Difficulty.EASY
    }

    val Player.gamePlayerData
        get() = gamePlayers.find { it.uuid == uniqueId }

    var OfflinePlayer.lastLocation: Location?
        get() = plugin.config.getLocation("last_location.${uniqueId}")
        set(value) {
            plugin.config.set("last_location.${uniqueId}", value)
        }

    var initEndSpawn: Location? = null

    val Player.color: TextColor
        get() = ChasingTailsUtils.scoreboard.getPlayerTeam(this)?.color()
            ?: NamedTextColor.WHITE

    private val Player.koreanColor
        get() = mappedColors[color] ?: "하얀색"

    fun GamePlayer.notifyTeam() {
        sendMessage(
            text("당신의 팀: ", NamedTextColor.YELLOW)
                .append(text(player.koreanColor, player.color))
        )

        sendMessage(
            text("당신의 타겟은 ", NamedTextColor.YELLOW).append(
                text(
                    target
                        .player.koreanColor, target.player.color
                )
            ).append(text("입니다.", NamedTextColor.YELLOW))
        )
    }

    fun checkPlayers(): Boolean {
        val configGamePlayers =
            plugin.config.getStringList("gamePlayers")

        return configGamePlayers.size != joinedGamePlayers.size
    }

    fun Player.restoreGamePlayer() {
        val data = plugin.config.get("chasingtails.${uniqueId}") as GamePlayerData

        gamePlayerData?.let { gamePlayer ->
            val target = gamePlayers.find { it.uuid.toString() == data.target }
            if (target != null) gamePlayer.target = target

            gamePlayer.master = gamePlayers.find { it.uuid.toString() == data.master }
            gamePlayer.deathTimer = server.worlds
                .flatMap { world -> world.entities }
                .firstOrNull { it.uniqueId.toString() == data.deathTimer } as? TextDisplay
            gamePlayer.temporaryDeathDuration = data.temporaryDeathDuration

            gamePlayer.deathTimer?.let { deathTimer ->
                gamePlayer.player.addPassenger(deathTimer)
            }

            gamePlayer.master?.let {
                getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = 10.0
            }
        }

        val team = ChasingTailsUtils.scoreboard.getTeam(data.teamName)
            ?: ChasingTailsUtils.scoreboard.registerNewTeam(data.teamName)

        try {
            team.color()
        } catch (e: IllegalStateException) {
            team.color(NamedTextColor.namedColor(data.teamColor))
        }

        team.addPlayer(this)

        if (gamePlayerData?.master != null) gamePlayerData?.equipSlaveArmor(this)
    }
}