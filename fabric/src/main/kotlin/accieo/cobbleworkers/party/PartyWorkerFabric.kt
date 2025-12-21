/*
 * Copyright (C) 2025 HitaxasTV
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.party

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents

object PartyWorkerFabric {

    fun init() {

        ServerEntityEvents.ENTITY_LOAD.register { entity, world ->
            if (world.isClient) return@register
            if (entity is PokemonEntity && entity.owner != null)
                PartyWorkerCore.markActive(entity)
        }

        ServerEntityEvents.ENTITY_UNLOAD.register { entity, world ->
            if (world.isClient) return@register
            if (entity is PokemonEntity)
                PartyWorkerCore.markInactive(entity)
        }

        ServerTickEvents.END_WORLD_TICK.register { world ->
            world.iterateEntities().forEach { entity ->
                if (entity is PokemonEntity && entity.owner != null) {
                    PartyWorkerCore.tickPokemon(entity)
                }
            }
        }
    }
}
