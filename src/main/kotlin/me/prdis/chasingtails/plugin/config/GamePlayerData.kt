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

import org.bukkit.configuration.serialization.ConfigurationSerializable

/**
 * @author aroxu, DytroC, ContentManager
 */

@Suppress("UNUSED")
data class GamePlayerData(
    val teamName: String,
    val teamColor: Int,
    val target: String?,
    val master: String?,
    val deathTimer: String?,
    val temporaryDeathDuration: Int
) : ConfigurationSerializable {
    companion object {
        @JvmStatic
        fun deserialize(arg: Map<String, Any?>): GamePlayerData {
            val teamName = arg["teamName"] as String
            val teamColor = arg["teamColor"] as Int
            val target = arg["target"] as? String
            val master = arg["master"] as? String
            val deathTimer = arg["deathTimer"] as? String
            val temporaryDeathDuration = arg["temporaryDeathDuration"] as Int
            return GamePlayerData(teamName, teamColor, target, master, deathTimer, temporaryDeathDuration)
        }
    }
    override fun serialize(): MutableMap<String, Any?> {
        val out = mutableMapOf<String, Any?>()
        out["teamName"] = teamName
        out["teamColor"] = teamColor
        out["target"] = if (master == null) target else null
        out["master"] = master
        out["deathTimer"] = deathTimer
        out["temporaryDeathDuration"] = temporaryDeathDuration

        return out
    }
}