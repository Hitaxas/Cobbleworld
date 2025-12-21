/*
 * Copyright (C) 2025 HitaxasTV
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.party

import accieo.cobbleworkers.jobs.WorkerDispatcher
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.util.math.BlockPos
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PartyWorkerCore {

    private val activePartyPokemon = ConcurrentHashMap.newKeySet<UUID>()
    private val pokemonWorkOrigin = ConcurrentHashMap<UUID, BlockPos>()

    fun markActive(pokemon: PokemonEntity) {
        val uuid = pokemon.pokemon.uuid
        val name = pokemon.pokemon.getDisplayName().string
        activePartyPokemon.add(uuid)

        val origin = BlockPos.ofFloored(pokemon.x, pokemon.y, pokemon.z)
        pokemonWorkOrigin.putIfAbsent(uuid, origin)

    }

    fun markInactive(pokemon: PokemonEntity) {
        val uuid = pokemon.pokemon.uuid
        val name = pokemon.pokemon.getDisplayName().string
        activePartyPokemon.remove(uuid)
        pokemonWorkOrigin.remove(uuid)

    }

    fun tickPokemon(pokemon: PokemonEntity) {
        val uuid = pokemon.pokemon.uuid

        if (!activePartyPokemon.contains(uuid)) {
            return
        }


        val world = pokemon.world
        if (world.isClient) {
            return
        }

        val workOrigin = pokemonWorkOrigin[uuid]
            ?: BlockPos.ofFloored(pokemon.x, pokemon.y, pokemon.z)


        // Party workers scan their own area
        WorkerDispatcher.tickAreaScan(world, workOrigin)

        // Then tick the pokemon's work logic
        WorkerDispatcher.tickPokemon(world, workOrigin, pokemon)
    }

    fun updateWorkOrigin(pokemon: PokemonEntity, newOrigin: BlockPos) {
        pokemonWorkOrigin[pokemon.pokemon.uuid] = newOrigin
    }

    fun isActive(pokemon: PokemonEntity): Boolean {
        return activePartyPokemon.contains(pokemon.pokemon.uuid)
    }

    fun getActivePokemon(): Set<UUID> {
        return activePartyPokemon.toSet()
    }

    fun clearAll() {
        activePartyPokemon.clear()
        pokemonWorkOrigin.clear()
    }
}