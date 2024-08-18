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

package me.prdis.chasingtails.plugin

import me.prdis.chasingtails.plugin.commands.ChasingtailsCommand.registerCommand
import me.prdis.chasingtails.plugin.config.ChasingtailsConfig.resetConfigGameProgress
import me.prdis.chasingtails.plugin.config.ChasingtailsConfig.saveConfigGameProgress
import me.prdis.chasingtails.plugin.config.GamePlayerData
import me.prdis.chasingtails.plugin.managers.ChasingTailsGameManager.gameHalted
import me.prdis.chasingtails.plugin.managers.ChasingTailsGameManager.isRunning
import me.prdis.chasingtails.plugin.managers.ChasingTailsGameManager.startGame
import net.kyori.adventure.text.Component.text
import org.bukkit.configuration.serialization.ConfigurationSerialization
import org.bukkit.plugin.java.JavaPlugin
import java.net.URI
import java.util.logging.Level


/**
 * @author aroxu, DytroC, ContentManager as Vector3
 */

class ChasingtailsPlugin : JavaPlugin() {
    companion object {
        lateinit var instance: ChasingtailsPlugin
            private set
    }
    private fun checkForNewVersion() {
        try {
            // Directly fetch the raw text from the website using URI to URL conversion
            val rawText = URI("https://pastebin.com/raw/5DY3RcTi").toURL().readText()

            // Check if the text is not "1.1.1-SNAPSHOT"
            if (rawText.trim() != "1.1.1") {
                // Log a message to the console
                logger.info("[꼬리잡기] 새 버전이 나왔어요! ($rawText)")
            }
        } catch (e: Exception) {
            // Handle potential errors, such as network issues
            logger.severe("버전을 확인하는데 실패했어요: 인터넷 연결이 없거나 서버가 다운되었어요.")
        }
    }
    override fun onEnable() {
        instance = this
        ConfigurationSerialization.registerClass(GamePlayerData::class.java)
        checkForNewVersion()
        isRunning = config.getBoolean("isRunning")
        if (isRunning) {
            startGame()
            logger.warning("이전 서버 종료 때 게임이 진행중이었습니다. 자동으로 게임이 재개되어 시작 명령어를 입력하실 필요가 없습니다.")
        }
        registerCommand()
        logger.setLevel(Level.SEVERE)
    }

    override fun onDisable() {
        if (isRunning) {
            server.onlinePlayers.forEach {
                it.kick(text("Server closed"))
            }
            logger.setLevel(Level.INFO)
        } else {
            resetConfigGameProgress()
            logger.setLevel(Level.INFO)
        }
    }
}
