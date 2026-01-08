/*
 * Copyright (C) 2026 HitaxasTV
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.network.fabric

import accieo.cobbleworkers.network.payloads.ToggleWorkPayload
import accieo.cobbleworkers.network.payloads.RequestWorkStatePayload
import accieo.cobbleworkers.network.payloads.SyncWorkStatePayload
import accieo.cobbleworkers.utilities.CobbleworkersWorkToggle
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents

object FabricWorkToggleNetworking {

    fun register() {
        ServerPlayNetworking.registerGlobalReceiver(ToggleWorkPayload.ID) { payload, context ->
            context.server().execute {
                val player = context.player()
                val world = player.serverWorld

                val entity = world.iterateEntities()
                    .firstOrNull { it.uuid == payload.pokemonId }

                val pokemonEntity = entity as? PokemonEntity ?: return@execute

                if (pokemonEntity.ownerUuid != player.uuid) return@execute

                val pokemon = pokemonEntity.pokemon
                val newState = !CobbleworkersWorkToggle.canWork(pokemon)

                CobbleworkersWorkToggle.setCanWork(pokemon, newState)

                ServerPlayNetworking.send(
                    player,
                    SyncWorkStatePayload(payload.pokemonId, newState)
                )
            }
        }

        ServerPlayNetworking.registerGlobalReceiver(RequestWorkStatePayload.ID) { payload, context ->
            context.server().execute {
                val player = context.player()
                val world = player.serverWorld

                val entity = world.iterateEntities()
                    .firstOrNull { it.uuid == payload.pokemonId }

                val pokemonEntity = entity as? PokemonEntity ?: return@execute

                if (pokemonEntity.ownerUuid != player.uuid) return@execute

                val pokemon = pokemonEntity.pokemon
                val canWork = CobbleworkersWorkToggle.canWork(pokemon)

                ServerPlayNetworking.send(
                    player,
                    SyncWorkStatePayload(payload.pokemonId, canWork)
                )
            }
        }

        ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            if (!world.isClient && entity is PokemonEntity) {
                val canWork = CobbleworkersWorkToggle.canWork(entity.pokemon)
                world.server?.playerManager?.playerList?.forEach { player ->
                    ServerPlayNetworking.send(
                        player,
                        SyncWorkStatePayload(entity.uuid, canWork)
                    )
                }
            }
        }
    }
}