/*
 * Copyright (C) 2025 HitaxasTV
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.party

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.world.ServerWorld
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

@EventBusSubscriber(modid = "cobbleworkers")
object PartyWorkerNeoForge {

    @SubscribeEvent
    fun onJoin(event: EntityJoinLevelEvent) {
        val level = event.level

        if (event.entity is PokemonEntity) {
            val pokemon = event.entity as PokemonEntity
        }

        if (level.isClient) return

        (event.entity as? PokemonEntity)?.let { pokemon ->
            if (pokemon.owner != null) {
                PartyWorkerCore.markActive(pokemon)
            } else {
            }
        }
    }

    @SubscribeEvent
    fun onLeave(event: EntityLeaveLevelEvent) {
        val level = event.level
        if (level.isClient) return

        (event.entity as? PokemonEntity)?.let {
            PartyWorkerCore.markInactive(it)
        }
    }

    @SubscribeEvent
    fun tick(event: ServerTickEvent.Post) {
        val server = event.server

        server.worlds.forEach { level ->
            if (level is ServerWorld) {
                for (entity in level.iterateEntities()) {
                    if (entity is PokemonEntity && entity.owner != null) {
                        PartyWorkerCore.tickPokemon(entity)
                    }
                }
            }
        }
    }
}