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
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import java.net.URI
import java.util.logging.Level
import org.yaml.snakeyaml.Yaml
import java.io.InputStreamReader
import java.net.HttpURLConnection

class ChasingtailsPlugin : JavaPlugin(), Listener {
    companion object {
        lateinit var instance: ChasingtailsPlugin
            private set
    }

    // Hold the list of banned players
    private val bannedPlayers: MutableSet<String> = mutableSetOf()

    private fun fetchBannedPlayers() {
        try {
            val url = URI("https://raw.githubusercontent.com/Lukewiz/chasing-tails-modified/master/banned-players.yml").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = InputStreamReader(connection.inputStream)
                val yaml = Yaml()
                val data = yaml.load<Map<String, Any>>(inputStream)

                // 안전한 캐스트 사용
                val bannedList = data["banned"] as? List<*> // Unchecked cast가 발생할 수 있는 부분
                if (bannedList != null) {
                    bannedPlayers.clear()
                    bannedPlayers.addAll(bannedList.filterIsInstance<String>().map { it.lowercase() })
                    logger.info("차단된 플레이어들을 업데이트 했습니다: ${bannedPlayers.joinToString(", ")}")
                } else {
                    logger.warning("차단된 플레이어들의 리스트가 올바르지 않습니다!")
                }
            } else {
                logger.warning("서버에 연결하는데 실패했습니다. HTTP 코드: ${connection.responseCode}")
            }
        } catch (e: Exception) {
            logger.severe("어떠한 이유로 연결하는데 실패했습니다: ${e.message}")
            logger.severe("(플러그인에 지장을 주지는 않습니다.)")
        }
    }


    private fun checkForNewVersion() {
        try {
            val rawText = URI("https://pastebin.com/raw/5DY3RcTi").toURL().readText()
            if (rawText.trim() != "1.1.1") {
                logger.info("[꼬리잡기] 새 버전이 나왔어요! ($rawText)")
            }
        } catch (e: Exception) {
            logger.severe("버전을 확인하는데 실패했어요: 인터넷 연결이 없거나 서버가 다운되었어요.")
        }
    }

    override fun onEnable() {
        instance = this
        logger.info("클래스를 불러오는 중....")
        ConfigurationSerialization.registerClass(GamePlayerData::class.java)
        logger.info("이벤트를 등록하는 중....")
        server.pluginManager.registerEvents(this, this)
        logger.info("정보를 얻어오는 중....")
        fetchBannedPlayers()
        logger.info("잔디를 심는 중....")
        checkForNewVersion()
        logger.info("")
        logger.info("chasing-tails-modified V1.1.2 SNAPSHOT-2 로딩 중...")
        isRunning = config.getBoolean("isRunning")
        if (isRunning) {
            startGame()
            logger.warning("이전 서버 종료 때 게임이 진행중이었습니다. 자동으로 게임이 재개되어 시작 명령어를 입력하실 필요가 없습니다.")
        }
        logger.info("명령어를 등록하는 중...")
        registerCommand()
        logger.info("✔ 성공적으로 플러그인이 활성화되었습니다!")
        logger.setLevel(Level.SEVERE)
    }

    override fun onDisable() {
        resetConfigGameProgress()
        logger.setLevel(Level.INFO)
    }

    // Event listener for player join events
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val playerName = event.player.name.lowercase()
        if (bannedPlayers.contains(playerName)) {
            event.player.kick(text("서버에 접속할 수 없습니다: 현재 영구적으로 차단되었습니다."))
            logger.info("$playerName was kicked for being on the banned players list.")
        }
    }
}
