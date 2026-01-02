/*
 * Copyright (C) 2026 HitaxasTV
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.commands

import accieo.cobbleworkers.utilities.CobbleworkersWorkToggle
import com.cobblemon.mod.common.Cobblemon
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text

object CobbleworkersCommands {
    fun register(dispatcher: CommandDispatcher<ServerCommandSource>) {
        dispatcher.register(
            literal("cobbleworkers").then(
                literal("work").then(
                    literal("on").executes { context ->
                        setTeamWork(context.source, true)
                    }
                ).then(
                    literal("off").executes { context ->
                        setTeamWork(context.source, false)
                    }
                )
            )
        )
    }

    private fun setTeamWork(source: ServerCommandSource, canWork: Boolean): Int {
        val player = source.player ?: return 0

        val party = Cobblemon.storage.getParty(player)

        party.forEach { pokemon: com.cobblemon.mod.common.pokemon.Pokemon ->
            CobbleworkersWorkToggle.setCanWork(pokemon, canWork)
        }

        val status = if (canWork) "enabled" else "disabled"
        val color = if (canWork) "§a" else "§c"

        source.sendFeedback({
            Text.literal("${color}Work $status for your entire party!")
        }, false)

        return 1
    }
}