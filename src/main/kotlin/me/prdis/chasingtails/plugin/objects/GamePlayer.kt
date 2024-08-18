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

import me.prdis.chasingtails.plugin.managers.ChasingTailsGameManager.gamePlayers
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.color
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.scoreboard
import me.prdis.chasingtails.plugin.objects.ChasingTailsUtils.server
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Color
import org.bukkit.EntityEffect
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.attribute.Attribute
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Display
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.NumberConversions
import java.util.*

/**
 * @author aroxu, DytroC, ContentManager
 */

class GamePlayer(val uuid: UUID) {
    val player: Player
        get() = server.getPlayer(uuid)!!

    val offlinePlayer: OfflinePlayer
        get() = server.getOfflinePlayer(uuid)

    val name get() = offlinePlayer.name ?: ""

    var target: GamePlayer = this
        get() = master?.target ?: field

    var master: GamePlayer? = null

    var tooFar: Boolean = false

    val isDeadTemporarily
        get() = temporaryDeathDuration > 0

    var lastlyReceivedDamage: Pair<GamePlayer, Int>? = null
    var temporaryDeathDuration = -1
        set(value) {
            if (value == 0) {
                deathTimer?.remove()
                deathTimer = null

                player.playEffect(EntityEffect.TOTEM_RESURRECT)
                player.addPotionEffects(
                    listOf(
                        PotionEffect(PotionEffectType.RESISTANCE, 20 * 5, 1, false, false, false),
                        PotionEffect(PotionEffectType.REGENERATION, 20 * 45, 1, false, false, false),
                        PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 40, 0, false, false, false)
                    )
                )

                sendMessage(text("부활하셨습니다!"))


                val masterLocation = master?.player?.location
                if (masterLocation != null) {
                    initializeAsSlave()
                    player.teleport(masterLocation)
                }

                field = -1
            } else {
                if (value != -1) {
                    (deathTimer ?: player.world.spawn(
                        player.location.add(0.0, 2.3, 0.0),
                        TextDisplay::class.java,
                    ) {
                        it.isPersistent = true
                        it.alignment = TextDisplay.TextAlignment.CENTER
                        it.billboard = Display.Billboard.CENTER
                        player.addPassenger(it)
                    }.also {
                        deathTimer = it
                    }).text(
                        text("부활까지 ${NumberConversions.ceil(value / 20.0)}초", NamedTextColor.YELLOW)
                    )
                }

                field = value
            }
        }

    var deathTimer: TextDisplay? = null

    fun enslave(slave: GamePlayer, inherited: Boolean = false) {
        target = slave.target
        slave.master = this

        equipSlaveArmor(slave.player)

        if (!inherited) {
            gamePlayers.forEach {
                if (it.master == slave) enslave(it, true)
            }

            slave.initializeAsSlave()

            slave.player.playEffect(EntityEffect.TOTEM_RESURRECT)
            slave.player.sendMessage(
                text("처치당하셨습니다! 앞으로 ").append(text(player.name, player.color))
                    .append(text("님의 꼬리가 됩니다.", NamedTextColor.WHITE))
            )

            slave.player.inventory.addItem(ItemStack(Material.COMPASS))

            sendMessage(
                text("목표를 처치하는데 성공하셨습니다! ").append(text(slave.player.name, slave.player.color))
                    .append(text("님이 꼬리가 됩니다."))
            )

        }

        scoreboard.getPlayerTeam(slave.offlinePlayer)?.unregister()
        scoreboard.getPlayerTeam(offlinePlayer)?.addPlayer(slave.player)

        player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = (20 - (
                (gamePlayers.count { it.master == this }) * 2
                )).toDouble()

        slave.deathTimer?.remove()
    }
    fun lenslave(slave: GamePlayer, inherited: Boolean = false) {
        target = slave.target
        slave.master = this

        equipSlaveArmor(slave.player)

        if (!inherited) {
            gamePlayers.forEach {
                if (it.master == slave) enslave(it, true)
            }

            slave.initializeAsSlave()

            slave.player.inventory.addItem(ItemStack(Material.COMPASS))

            sendMessage(
                text("타겟이 게임을 나갔습니다. 앞으로 ").append(text(slave.player.name, slave.player.color))
                    .append(text("님이 꼬리가 됩니다."))
            )

        }

        scoreboard.getPlayerTeam(slave.offlinePlayer)?.unregister()
        scoreboard.getPlayerTeam(offlinePlayer)?.addPlayer(slave.player)

        player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = (20 - (
                (gamePlayers.count { it.master == this }) * 2
                )).toDouble()

        slave.deathTimer?.remove()
    }
    fun sendMessage(message: ComponentLike) = player.sendMessage(message)
    fun alert(message: String) = sendMessage(text(message, NamedTextColor.RED))

    override fun equals(other: Any?) = other is GamePlayer && other.uuid == uuid
    override fun hashCode() = uuid.hashCode()

    fun temporarilyKillPlayer(duration: Int) {
        player.apply {
            health = getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0
            foodLevel = 20
            saturation = 3F
            exhaustion = 0F
            world.getNearbyLivingEntities(location, 30.0).forEach {
                if (it is Mob) {
                    if (it.target?.uniqueId == uniqueId) it.target = null
                }
            }
        }

        temporaryDeathDuration = duration
        tooFar = false
    }

    private fun initializeAsSlave() = player.apply {
        health = 10.0
        getAttribute(Attribute.GENERIC_MAX_HEALTH)?.baseValue = 10.0

        foodLevel = 20
        saturation = 3F
        exhaustion = 0F
    }

    fun equipSlaveArmor(slave: Player) = slave.inventory.apply {
        helmet = ItemStack(Material.LEATHER_HELMET).applyColorToArmor()
        chestplate = ItemStack(Material.LEATHER_CHESTPLATE).applyColorToArmor()
        leggings = ItemStack(Material.LEATHER_LEGGINGS).applyColorToArmor()
        boots = ItemStack(Material.LEATHER_BOOTS).applyColorToArmor()
    }

    private fun ItemStack.applyColorToArmor() = apply {
        editMeta {
            if (it is LeatherArmorMeta) it.setColor(Color.fromRGB(player.color.value()))
            it.isUnbreakable = true
            it.addItemFlags(*ItemFlag.entries.toTypedArray())
            it.addEnchant(Enchantment.BINDING_CURSE, 1, true)
        }
    }
}