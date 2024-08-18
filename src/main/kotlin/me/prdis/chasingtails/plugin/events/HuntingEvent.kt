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

import com.github.shynixn.mccoroutine.bukkit.launch
import kotlinx.coroutines.delay
import me.prdis.chasingtails.plugin.config.ChasingtailsConfig.saveConfigGameProgress
import me.prdis.chasingtails.plugin.enums.DamageResult
import me.prdis.chasingtails.plugin.managers.ChasingTailsGameManager.currentTick
import me.prdis.chasingtails.plugin.managers.ChasingTailsGameManager.gameHalted
import me.prdis.chasingtails.plugin.managers.ChasingTailsGameManager.gamePlayers
import me.prdis.chasingtails.plugin.managers.ChasingTailsGameManager.stopGame
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.color
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.gamePlayerData
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.initEndSpawn
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.lastLocation
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.plugin
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.server
import me.prdis.chasingtails.plugin.objects.GamePlayer
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.World
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.time.Duration

object HuntingEvent : Listener {

    private val pendingEnslavement = mutableMapOf<GamePlayer, Boolean>()

    @EventHandler
    fun PlayerInteractEvent.onInteract() {
        val gamePlayer = player.gamePlayerData

        when {
            gameHalted -> isCancelled = true
            action.isRightClick && item?.type == Material.DIAMOND && gamePlayer != null && gamePlayer.master == null -> handleDiamondInteraction(
                gamePlayer
            )

            item?.type == Material.END_CRYSTAL && player.world.environment == World.Environment.THE_END -> isCancelled =
                true

            item?.type == Material.COMPASS && gamePlayer?.master != null -> isCancelled = true
            gamePlayer?.isDeadTemporarily == true -> isCancelled = true
        }
    }

    private fun PlayerInteractEvent.handleDiamondInteraction(gamePlayer: GamePlayer) {
        val target = gamePlayer.target.offlinePlayer
        val location = target.location ?: target.lastLocation

        if (location != null) {
            if (player.location.world == location.world) {
                player.launchParticles(location)
                player.playSound(Sound.sound(Key.key("block.note_block.bit"), Sound.Source.MASTER, 1000F, 1F))
                item?.amount = item?.amount?.minus(1) ?: 0
            } else {
                player.sendActionBar(text(" -- 추적 오류: 플레이어와 같은 월드에 있지 않습니다. -- ", NamedTextColor.RED))
            }
        } else {
            player.sendActionBar(text(" -- 추적 오류: 플레이어의 위치를 찾을 수 없습니다. -- ", NamedTextColor.RED))
        }
    }

    private fun Player.launchParticles(location: Location) {
        val eye = eyeLocation
        val offset = location.add(0.0, 1.5, 0.0).toVector().subtract(eye.toVector()).normalize()

        plugin.launch {
            repeat(10) {
                repeat(15) { distance ->
                    world.spawnParticle(
                        Particle.SOUL_FIRE_FLAME,
                        eye.clone().add(offset.clone().multiply(distance * 0.5)),
                        1,
                        0.0,
                        0.0,
                        0.0,
                        0.0
                    )
                }
                delay(200)
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    fun EntityDamageByEntityEvent.onDamageByEntity() {
        val gamePlayer = (entity as? Player)?.gamePlayerData
        val attacker = when (val source = damager) {
            is Player -> source
            is Projectile -> source.shooter as? Player
            is Tameable -> source.owner as? Player
            is TNTPrimed -> source.source as? Player
            else -> null
        }?.gamePlayerData ?: return

        if (attacker == gamePlayer) return

        if (gameHalted) isCancelled = true
        else {
            if (gamePlayer != null) {
                val result = processDamage(gamePlayer, attacker)

                isCancelled = result == DamageResult.DISALLOW
                if (result == DamageResult.ALLOW_ONLY_KNOCKBACK) damage = 0.0
            }
        }
    }

    @EventHandler
    fun EntityDamageEvent.onDamage() {
        val gamePlayer = (entity as? Player)?.gamePlayerData

        if (gamePlayer?.isDeadTemporarily == true && cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            isCancelled = true
        }

        if (gameHalted) isCancelled = true
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    fun PlayerDeathEvent.onDeath() {
        val gamePlayer = entity.gamePlayerData

        isCancelled = true

        player.world.entities.filterIsInstance<Monster>().forEach { monster ->
            if (monster.target == player) monster.target = null
        }

        if (gamePlayer != null) {
            val master = gamePlayer.master

            val killer = (player.killer?.gamePlayerData ?: gamePlayer.lastlyReceivedDamage?.takeIf { (_, time) ->
                currentTick - time < (20 * 60)
            }?.first)

            val killerMaster = (killer?.master ?: killer).takeIf { gamePlayer != it }

            if (gamePlayer == killerMaster?.target && master == null) {
                if (gamePlayer.isDeadTemporarily) gamePlayer.temporaryDeathDuration = -1

                if (gamePlayers.filter { it.master == null }.size == 2) {
                    server.showTitle(
                        Title.title(
                            text(""),
                            text(killerMaster.player.name, killerMaster.player.color).append(
                                text(
                                    "님이 우승하셨습니다!",
                                    NamedTextColor.WHITE
                                )
                            ),
                            Title.Times.times(Duration.ofSeconds(0), Duration.ofSeconds(5), Duration.ofSeconds(0))
                        )
                    )

                    server.broadcast(text("게임을 종료합니다.", NamedTextColor.GREEN))
                    stopGame()

                    return
                }

                killerMaster.enslave(gamePlayer)
            } else {
                if (master != null) {
                    gamePlayer.temporarilyKillPlayer(20 * 30)

                    gamePlayers.forEach {
                        if (it.master == gamePlayer.master || it == gamePlayer.master) {
                            it.sendMessage(text("${gamePlayer.name}님이 사망했습니다. (30초 후 리스폰)", NamedTextColor.RED))
                        }
                    }
                } else {
                    if (player.world.environment == World.Environment.THE_END) {
                        initEndSpawn?.let { spawn -> player.teleportAsync(spawn) }
                    }

                    gamePlayer.temporarilyKillPlayer(120 * 20)
                    gamePlayer.sendMessage(
                        text("죽었습니다! ", NamedTextColor.RED).append(
                            text(
                                "현 시점으로부터 2분 뒤에 부활합니다.",
                                NamedTextColor.WHITE
                            )
                        )
                    )
                }
            }
        }

        player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false))

        saveConfigGameProgress()
    }

    @EventHandler
    fun PlayerQuitEvent.onPlayerQuit() {
        val gamePlayer = player.gamePlayerData
            if (gamePlayer != null && gamePlayer.master == null) {
                // Mark the player for potential enslavement
                pendingEnslavement[gamePlayer] = false

                plugin.launch {
                    delay(60 * 1000L) // Wait for 1 minute (60 seconds)

                    // If the player hasn't rejoined, enslave them
                    if (pendingEnslavement[gamePlayer] == false) {
                        gamePlayers.filter { it.target == gamePlayer }.forEach { potentialMaster ->
                            potentialMaster.lenslave(gamePlayer)
                        }

                        saveConfigGameProgress()
                    }
                }
            }
    }

    @EventHandler
    fun PlayerJoinEvent.onPlayerJoin() {
        val gamePlayer = player.gamePlayerData

        if (gamePlayer != null && pendingEnslavement.containsKey(gamePlayer)) {
            // Player has rejoined within 1 minute, cancel enslavement
            pendingEnslavement[gamePlayer] = true
        }
    }

    private const val DAMAGEABLE_ALERT = "타겟 플레이어와 그 플레이어의 꼬리, 자신의 꼬리 이외의 플레이어는 공격할 수 없습니다!"

    private fun processDamage(damaged: GamePlayer, damager: GamePlayer): DamageResult {
        if (damager.isDeadTemporarily) {
            damager.alert("리스폰 대기 상태에서는 공격할 수 없습니다!")
            return DamageResult.DISALLOW
        } else {
            if (damaged.isDeadTemporarily) {
                return if (damager.target == damaged) {
                    DamageResult.ALLOW
                } else if (damaged.target == damager.master) {
                    DamageResult.ALLOW_ONLY_KNOCKBACK
                } else {
                    damager.alert("해당 플레이어는 리스폰 대기 상태입니다!")
                    return DamageResult.DISALLOW
                }
            } else {
                if (damaged == damager.target) {
                    damaged.lastlyReceivedDamage = damager to currentTick
                    return DamageResult.ALLOW
                } else if (damaged.master != null) {
                    return DamageResult.ALLOW
                } else if (damaged.target == (damager.master ?: damager)) {
                    return DamageResult.ALLOW_ONLY_KNOCKBACK
                } else if (damaged == damager.master) {
                    damager.alert("주인은 공격할 수 없습니다! 혹시 하극상을 시전하려 했나요?")
                    return DamageResult.DISALLOW
                } else {
                    damager.alert(DAMAGEABLE_ALERT)
                    return DamageResult.DISALLOW
                }
            }
        }
    }
}
