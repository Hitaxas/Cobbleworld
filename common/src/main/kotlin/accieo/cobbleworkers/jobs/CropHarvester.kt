/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.jobs

import accieo.cobbleworkers.config.CobbleworkersConfigHolder
import accieo.cobbleworkers.enums.JobType
import accieo.cobbleworkers.interfaces.Worker
import accieo.cobbleworkers.utilities.CobbleworkersCropUtils
import accieo.cobbleworkers.utilities.CobbleworkersInventoryUtils
import accieo.cobbleworkers.utilities.CobbleworkersNavigationUtils
import accieo.cobbleworkers.utilities.CobbleworkersTypeUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID
import kotlin.text.lowercase

/**
 * A worker job for a Pokémon to find, navigate to, and harvest fully grown crops.
 * Harvested items are deposited into the nearest available inventory.
 */
object CropHarvester : Worker {
    private val heldItemsByPokemon = mutableMapOf<UUID, List<ItemStack>>()
    private val failedDepositLocations = mutableMapOf<UUID, MutableSet<BlockPos>>()
    private val pokemonBreakingBlocks = mutableMapOf<UUID, BlockPos>()
    private val config = CobbleworkersConfigHolder.config.cropHarvest

    override val jobType: JobType = JobType.CropHarvester

    override val blockValidator: ((World, BlockPos) -> Boolean) = { world: World, pos: BlockPos ->
        val state = world.getBlockState(pos)

        state.block in CobbleworkersCropUtils.validCropBlocks ||

                Registries.BLOCK.getId(state.block).toString() == "biomeswevegone:blueberry_bush"
    }

    override fun shouldRun(pokemonEntity: PokemonEntity): Boolean {
        if (!accieo.cobbleworkers.utilities.CobbleworkersWorkToggle.canWork(pokemonEntity.pokemon)) {
            return false
        }

        if (!config.cropHarvestersEnabled) return false
        return CobbleworkersTypeUtils.isAllowedByType(config.typeHarvestsCrops, pokemonEntity)
                || isDesignatedHarvester(pokemonEntity)
    }

    private fun isLegendaryOrMythical(pokemonEntity: PokemonEntity): Boolean {
        val labels = pokemonEntity.pokemon.species.labels
        return labels.contains("legendary") || labels.contains("mythical")
    }

    override fun tick(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
        val pokemonId = pokemonEntity.pokemon.uuid
        val heldItems = heldItemsByPokemon[pokemonId]

        if (heldItems.isNullOrEmpty()) {
            failedDepositLocations.remove(pokemonId)
            handleHarvesting(world, origin, pokemonEntity)
        } else {
            val breakingPos = pokemonBreakingBlocks.remove(pokemonId)
            if (breakingPos != null) {
                CobbleworkersCropUtils.cancelBreaking(breakingPos, world)
            }
            CobbleworkersInventoryUtils.handleDepositing(
                world,
                origin,
                pokemonEntity,
                heldItems,
                failedDepositLocations,
                heldItemsByPokemon
            )
        }
    }

    private fun handleHarvesting(world: World, origin: BlockPos, pokemonEntity: PokemonEntity) {
        val pokemonId = pokemonEntity.pokemon.uuid
        val breakingPos = pokemonBreakingBlocks[pokemonId]
        val isLegendary = isLegendaryOrMythical(pokemonEntity)

        if (breakingPos != null) {
            val blockState = world.getBlockState(breakingPos)
            if (
                !CobbleworkersCropUtils.isHarvestable(blockState) ||
                !world.getBlockState(breakingPos).isOf(blockState.block)
            ) {
                pokemonBreakingBlocks.remove(pokemonId)
                CobbleworkersCropUtils.cancelBreaking(breakingPos, world)
                CobbleworkersNavigationUtils.releaseTarget(pokemonId, world)
                return
            }

            if (!CobbleworkersNavigationUtils.isPokemonAtPosition(pokemonEntity, breakingPos)) {
                CobbleworkersNavigationUtils.navigateTo(pokemonEntity, breakingPos)
                return
            }

            CobbleworkersCropUtils.harvestCrop(
                world,
                breakingPos,
                pokemonEntity,
                heldItemsByPokemon,
                config
            )

            if (isLegendary && heldItemsByPokemon.containsKey(pokemonId)) {
                val currentItems = heldItemsByPokemon[pokemonId]!!
                val doubledItems = doubleItemStacks(currentItems)
                heldItemsByPokemon[pokemonId] = doubledItems
            }

            if (heldItemsByPokemon.containsKey(pokemonId)) {
                pokemonBreakingBlocks.remove(pokemonId)
                CobbleworkersNavigationUtils.releaseTarget(pokemonId, world)
            }
            return
        }

        val currentTarget = CobbleworkersNavigationUtils.getTarget(pokemonId, world)

        if (currentTarget == null) {
            val availableCrop = CobbleworkersCropUtils.findAvailableCrop(world, origin)
            if (availableCrop != null) {
                CobbleworkersNavigationUtils.claimTarget(pokemonId, availableCrop, world)
            }
            return
        }

        if (CobbleworkersNavigationUtils.isPokemonAtPosition(pokemonEntity, currentTarget)) {
            val blockState = world.getBlockState(currentTarget)

            if (!CobbleworkersCropUtils.isHarvestable(blockState)) {
                CobbleworkersNavigationUtils.releaseTarget(pokemonId, world)
                return
            }

            if (CobbleworkersCropUtils.requiresBreaking(blockState.block)) {
                pokemonBreakingBlocks[pokemonId] = currentTarget
            }

            CobbleworkersCropUtils.harvestCrop(
                world,
                currentTarget,
                pokemonEntity,
                heldItemsByPokemon,
                config
            )

            if (isLegendary && heldItemsByPokemon.containsKey(pokemonId)) {
                val currentItems = heldItemsByPokemon[pokemonId]!!
                val doubledItems = doubleItemStacks(currentItems)
                heldItemsByPokemon[pokemonId] = doubledItems
            }

            if (
                !CobbleworkersCropUtils.requiresBreaking(blockState.block) &&
                heldItemsByPokemon.containsKey(pokemonId)
            ) {
                CobbleworkersNavigationUtils.releaseTarget(pokemonId, world)
            }
        } else {
            CobbleworkersNavigationUtils.navigateTo(pokemonEntity, currentTarget)
        }
    }

    private fun doubleItemStacks(items: List<ItemStack>): List<ItemStack> {
        val result = mutableListOf<ItemStack>()

        for (stack in items) {
            val totalCount = stack.count * 2
            val maxStackSize = stack.maxCount

            if (totalCount <= maxStackSize) {
                val doubled = stack.copy()
                doubled.count = totalCount
                result.add(doubled)
            } else {
                var remaining = totalCount
                while (remaining > 0) {
                    val stackCount = remaining.coerceAtMost(maxStackSize)
                    val newStack = stack.copy()
                    newStack.count = stackCount
                    result.add(newStack)
                    remaining -= stackCount
                }
            }
        }

        return result
    }

    /**
     * Checks if the Pokémon qualifies as a harvester because its species is
     * explicitly listed in the config.
     */
    private fun isDesignatedHarvester(pokemonEntity: PokemonEntity): Boolean {
        val speciesName = pokemonEntity.pokemon.species.translatedName.string.lowercase()
        return config.cropHarvesters.any { it.lowercase() == speciesName }
    }

    override fun isActivelyWorking(pokemonEntity: PokemonEntity): Boolean {
        val uuid = pokemonEntity.pokemon.uuid
        val world = pokemonEntity.world

        if (pokemonBreakingBlocks.containsKey(uuid)) return true

        if (heldItemsByPokemon[uuid]?.isNotEmpty() == true) return true

        val target = CobbleworkersNavigationUtils.getTarget(uuid, world)
        if (target != null) return true

        if (accieo.cobbleworkers.sanity.SanityManager.isRefusingWork(pokemonEntity))
            return false

        if (accieo.cobbleworkers.sanity.SanityManager.isSleepingDuringBreak(pokemonEntity))
            return false

        return true
    }

    override fun interrupt(pokemonEntity: PokemonEntity, world: World) {
        val uuid = pokemonEntity.pokemon.uuid
        heldItemsByPokemon.remove(uuid)
        failedDepositLocations.remove(uuid)
        pokemonBreakingBlocks.remove(uuid)
        CobbleworkersNavigationUtils.releaseTarget(uuid, world)
    }
}