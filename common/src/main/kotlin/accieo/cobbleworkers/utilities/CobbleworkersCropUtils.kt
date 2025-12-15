/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.utilities

import accieo.cobbleworkers.cache.CobbleworkersCacheManager
import accieo.cobbleworkers.config.CobbleworkersConfig
import accieo.cobbleworkers.enums.JobType
import accieo.cobbleworkers.integration.FarmersDelightBlocks
import accieo.cobbleworkers.integration.CroptopiaBlocks
import accieo.cobbleworkers.jobs.CropHarvester
import accieo.cobbleworkers.jobs.CropIrrigator
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.block.HeartyGrainsBlock
import com.cobblemon.mod.common.block.MedicinalLeekBlock
import com.cobblemon.mod.common.block.NutBushBlock
import com.cobblemon.mod.common.block.RevivalHerbBlock
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.*
import net.minecraft.item.ItemStack
import net.minecraft.loot.context.LootContextParameterSet
import net.minecraft.loot.context.LootContextParameters
import net.minecraft.registry.Registries
import net.minecraft.server.world.ServerWorld
import net.minecraft.state.property.IntProperty
import net.minecraft.state.property.Properties.AGE_3
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.UUID

/**
 * Utility functions for crop related stuff.
 * Integrated with Vanilla, Cobblemon, Farmer's Delight, and Croptopia.
 */
object CobbleworkersCropUtils {

    val validCropBlocks: MutableSet<Block> = mutableSetOf(
        Blocks.POTATOES,
        Blocks.BEETROOTS,
        Blocks.CARROTS,
        Blocks.WHEAT,
        Blocks.SWEET_BERRY_BUSH,
        Blocks.CAVE_VINES,
        Blocks.CAVE_VINES_PLANT,
        CobblemonBlocks.REVIVAL_HERB,
        CobblemonBlocks.MEDICINAL_LEEK,
        CobblemonBlocks.VIVICHOKE_SEEDS,
        CobblemonBlocks.HEARTY_GRAINS,
        CobblemonBlocks.GALARICA_NUT_BUSH
    )

    /**
     * Adds compat for Farmers Delight and Croptopia crops
     */
    fun addCompatibility(externalBlocks: Set<Block>) {
        validCropBlocks.addAll(externalBlocks)
    }

    /**
     * Detects if a block belongs to the Croptopia namespace.
     */
    fun isCroptopia(block: Block): Boolean {
        return Registries.BLOCK.getId(block).namespace == "croptopia"
    }

    /**
     * Determines if a block is a valid harvest target for the worker.
     */
    fun isHarvestable(state: BlockState): Boolean {
        val block = state.block
        return block in validCropBlocks || isCroptopia(block) || block is CropBlock
    }

    fun findClosestCrop(world: World, origin: BlockPos): BlockPos? {
        val possibleTargets = CobbleworkersCacheManager.getTargets(origin, JobType.CropHarvester)
        if (possibleTargets.isEmpty()) return null

        return possibleTargets
            .filter { pos ->
                val state = world.getBlockState(pos)
                isHarvestable(state) &&
                        isMatureCrop(world, pos) &&
                        !CobbleworkersNavigationUtils.isRecentlyExpired(pos, world)
            }
            .minByOrNull { it.getSquaredDistance(origin) }
    }

    fun harvestCrop(
        world: World,
        blockPos: BlockPos,
        pokemonEntity: PokemonEntity,
        pokemonHeldItems: MutableMap<UUID, List<ItemStack>>,
        config: CobbleworkersConfig.CropHarvestGroup
    ) {
        val blockState = world.getBlockState(blockPos)
        val block = blockState.block

        if (!isHarvestable(blockState)) return

        val lootParams = LootContextParameterSet.Builder(world as ServerWorld)
            .add(LootContextParameters.ORIGIN, blockPos.toCenterPos())
            .add(LootContextParameters.BLOCK_STATE, blockState)
            .add(LootContextParameters.TOOL, ItemStack.EMPTY)
            .addOptional(LootContextParameters.THIS_ENTITY, pokemonEntity)

        val drops = blockState.getDroppedStacks(lootParams)
        if (drops.isNotEmpty()) {
            pokemonHeldItems[pokemonEntity.pokemon.uuid] = drops
        }

        val id = Registries.BLOCK.getId(block)
        val path = id.path

        // Handle multi-block Hearty Grains first
        if (block == CobblemonBlocks.HEARTY_GRAINS) {
            val belowPos = blockPos.down()
            val belowState = world.getBlockState(belowPos)
            world.setBlockState(belowPos, belowState.with(HeartyGrainsBlock.AGE, HeartyGrainsBlock.AGE_AFTER_HARVEST), Block.NOTIFY_LISTENERS)
            world.setBlockState(blockPos, Blocks.AIR.defaultState, Block.NOTIFY_LISTENERS)
            return
        }

        val newState = if (config.shouldReplantCrops) {
            val ageProp = getAgeProperty(blockState)
            when {
                /** Farmer's Delight Special Logic **/
                path == FarmersDelightBlocks.RICE_PANICLES -> Blocks.AIR.defaultState
                (path == FarmersDelightBlocks.TOMATOES || path in FarmersDelightBlocks.MUSHROOMS) && blockState.contains(AGE_3) -> blockState.with(AGE_3, 0)

                /** Croptopia & Modded age property reset **/
                ageProp != null -> {
                    val resetAge = when {
                        // Some Croptopia/Vanilla bushes reset to stage 1, not 0
                        block is SweetBerryBushBlock ||
                                block == CobblemonBlocks.GALARICA_NUT_BUSH ||
                                (isCroptopia(block) && path.contains("berry")) -> 1

                        block == CobblemonBlocks.REVIVAL_HERB -> RevivalHerbBlock.MIN_AGE
                        else -> 0
                    }
                    blockState.with(ageProp, resetAge)
                }

                /** Cave vines **/
                block is CaveVines -> blockState.with(CaveVinesBodyBlock.BERRIES, false)

                else -> Blocks.AIR.defaultState
            }
        } else {
            // Replant disabled: Bushes still reset to stage 1 (to avoid broken textures), others become Air
            if (block is SweetBerryBushBlock || block == CobblemonBlocks.GALARICA_NUT_BUSH) {
                blockState.with(AGE_3, 1)
            } else {
                Blocks.AIR.defaultState
            }
        }

        world.setBlockState(blockPos, newState, Block.NOTIFY_LISTENERS)
    }

    private fun isMatureCrop(world: World, pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        val block = state.block
        val path = Registries.BLOCK.getId(block).path

        // Check for integer "age" property first (Generic mod support)
        val ageProp = getAgeProperty(state)
        if (ageProp != null) {
            val maxAge = ageProp.values.maxOrNull() ?: 0
            return state.get(ageProp) >= maxAge
        }

        // Fallback for non-standard maturity indicators
        return when {
            block is HeartyGrainsBlock -> block.getAge(state) == HeartyGrainsBlock.MATURE_AGE
            block is CaveVines -> state.get(CaveVinesBodyBlock.BERRIES)
            path in FarmersDelightBlocks.MUSHROOMS && state.contains(AGE_3) -> state.get(AGE_3) == 3
            else -> false
        }
    }

    private fun getAgeProperty(state: BlockState): IntProperty? {
        return state.properties.filterIsInstance<IntProperty>().firstOrNull { it.name == "age" }
    }
}