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
import accieo.cobbleworkers.utilities.CobbleworkersNavigationUtils
import accieo.cobbleworkers.utilities.CobbleworkersTypeUtils
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.block.Block
import net.minecraft.block.FarmlandBlock
import net.minecraft.particle.ParticleTypes
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

object CropIrrigator : Worker {

    private val config = CobbleworkersConfigHolder.config.irrigation
    override val jobType = JobType.CropIrrigator

    private data class IrrigationState(
        var startTime: Long = 0,
        var isIrrigating: Boolean = false,
        var angle: Float = -40f,
        var sweepDir: Int = 1,
        var activeCenter: BlockPos? = null
    )

    private val states = ConcurrentHashMap<UUID, IrrigationState>()

    override val blockValidator = { world: World, pos: BlockPos ->
        world.getBlockState(pos).contains(FarmlandBlock.MOISTURE)
    }

    override fun shouldRun(pokemon: PokemonEntity): Boolean {
        if (!accieo.cobbleworkers.utilities.CobbleworkersWorkToggle.canWork(pokemon.pokemon)) {
            return false
        }

        if (!config.cropIrrigatorsEnabled) return false
        return CobbleworkersTypeUtils.isAllowedByType(config.typeIrrigatesCrops, pokemon) ||
                config.cropIrrigators.any {
                    it.equals(pokemon.pokemon.species.translatedName.string, true)
                }
    }

    private fun isLegendaryOrMythical(pokemonEntity: PokemonEntity): Boolean {
        val labels = pokemonEntity.pokemon.species.labels
        return labels.contains("legendary") || labels.contains("mythical")
    }

    override fun tick(world: World, origin: BlockPos, pokemon: PokemonEntity) {
        if (world.isClient) return

        val id = pokemon.pokemon.uuid
        val state = states.computeIfAbsent(id) { IrrigationState() }
        val isLegendary = isLegendaryOrMythical(pokemon)
        val target = CobbleworkersNavigationUtils.getTarget(id, world)

        val effectiveRadius =
            if (isLegendary) config.irrigationRadius * 2 else config.irrigationRadius

        if (target == null || areaHydrated(world, target, effectiveRadius)) {
            state.isIrrigating = false
            CobbleworkersNavigationUtils.releaseTarget(id, world)

            findNearestDryFarmland(world, origin)?.let {
                CobbleworkersNavigationUtils.claimTarget(id, it, world)
                state.activeCenter = it
            }
            return
        }

        if (!CobbleworkersNavigationUtils.isPokemonAtPosition(pokemon, target, 1.5)) {
            state.isIrrigating = false
            CobbleworkersNavigationUtils.navigateTo(pokemon, target)
            return
        }

        if (!state.isIrrigating) {
            state.isIrrigating = true
            state.startTime = world.time
            state.angle = -40f
            state.sweepDir = 1
            pokemon.navigation.stop()
            pokemon.setVelocity(0.0, 0.0, 0.0)
        }

        pokemon.velocity = Vec3d.ZERO
        pokemon.navigation.stop()

        val t = (world.time - state.startTime).toFloat()
        val sweepAmplitude = 55f
        val sweepPeriod = 60f
        state.angle = (sin(t / sweepPeriod) * sweepAmplitude)

        val yaw = state.angle + pokemon.bodyYaw
        pokemon.headYaw = yaw
        pokemon.yaw = pokemon.bodyYaw
        pokemon.pitch = -20f

        if (world is ServerWorld) {
            spawnWaterBeam(world, pokemon, isLegendary)
        }

        val hydrateInterval = if (isLegendary) 2L else 4L
        if ((world.time - state.startTime) % hydrateInterval == 0L) {
            hydrateArea(world, target, effectiveRadius, isLegendary)
        }

        val completionTime = if (isLegendary) 50L else 100L
        if (world.time - state.startTime > completionTime ||
            areaHydrated(world, target, effectiveRadius)
        ) {
            irrigateFarmland(world, target, effectiveRadius)
            states.remove(id)
            CobbleworkersNavigationUtils.releaseTarget(id, world)
            pokemon.pitch = 0f
        }
    }

    private fun areaHydrated(world: World, center: BlockPos, radius: Int): Boolean {
        BlockPos.iterate(center.add(-radius, 0, -radius), center.add(radius, 0, radius)).forEach {
            val s = world.getBlockState(it)
            if (s.contains(FarmlandBlock.MOISTURE) &&
                s.get(FarmlandBlock.MOISTURE) < FarmlandBlock.MAX_MOISTURE
            ) return false
        }
        return true
    }

    private fun hydrateArea(world: World, center: BlockPos, radius: Int, isLegendary: Boolean) {
        BlockPos.iterate(center.add(-radius, 0, -radius), center.add(radius, 0, radius)).forEach { pos ->
            val s = world.getBlockState(pos)
            if (s.contains(FarmlandBlock.MOISTURE)) {
                val m = s.get(FarmlandBlock.MOISTURE)
                if (m < FarmlandBlock.MAX_MOISTURE) {
                    val inc = if (isLegendary) 2 else 1
                    world.setBlockState(
                        pos,
                        s.with(FarmlandBlock.MOISTURE, (m + inc).coerceAtMost(7)),
                        Block.NOTIFY_LISTENERS
                    )
                }
            }
        }
    }

    private fun spawnWaterBeam(world: ServerWorld, pokemon: PokemonEntity, isLegendary: Boolean) {
        val head = pokemon.pos.add(0.0, pokemon.height * 0.85, 0.0)
        val yaw = Math.toRadians(pokemon.headYaw.toDouble())
        val pitch = Math.toRadians(10.0)

        val dx = -sin(yaw) * cos(pitch)
        val dy = -sin(pitch)
        val dz = cos(yaw) * cos(pitch)

        val length = if (isLegendary) 14 else 10
        val particles = if (isLegendary) 2 else 1

        for (i in 0..length) {
            val d = i * 0.3
            val x = head.x + dx * d
            val y = head.y + dy * d
            val z = head.z + dz * d

            world.spawnParticles(
                ParticleTypes.FALLING_WATER,
                x, y, z,
                particles,
                0.02, 0.02, 0.02,
                0.01
            )

            if (i % 3 == 0)
                world.spawnParticles(
                    ParticleTypes.BUBBLE,
                    x, y, z,
                    particles,
                    0.01, 0.01, 0.01,
                    0.05
                )

            if (i == length)
                world.spawnParticles(
                    ParticleTypes.SPLASH,
                    x, y, z,
                    if (isLegendary) 20 else 10,
                    0.3, 0.1, 0.3,
                    0.12
                )
        }
    }

    private fun irrigateFarmland(world: World, center: BlockPos, radius: Int) {
        BlockPos.iterate(center.add(-radius, 0, -radius), center.add(radius, 0, radius)).forEach { pos ->
            val s = world.getBlockState(pos)
            if (s.contains(FarmlandBlock.MOISTURE) &&
                s.get(FarmlandBlock.MOISTURE) < FarmlandBlock.MAX_MOISTURE
            ) {
                world.setBlockState(
                    pos,
                    s.with(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE),
                    Block.NOTIFY_LISTENERS
                )
            }
        }
    }

    private fun findNearestDryFarmland(world: World, origin: BlockPos): BlockPos? {
        val r = 16
        for (dist in 0..r) {
            BlockPos.iterate(origin.add(-dist, -2, -dist), origin.add(dist, 2, dist)).forEach { pos ->
                val s = world.getBlockState(pos)
                if (s.contains(FarmlandBlock.MOISTURE)
                    && s.get(FarmlandBlock.MOISTURE) < FarmlandBlock.MAX_MOISTURE
                    && !CobbleworkersNavigationUtils.isTargeted(pos, world)
                ) return pos.toImmutable()
            }
        }
        return null
    }

    override fun isActivelyWorking(pokemon: PokemonEntity): Boolean {
        val state = states[pokemon.pokemon.uuid] ?: return false
        return state.isIrrigating || state.activeCenter != null
    }

    override fun interrupt(pokemon: PokemonEntity, world: World) {
        states.remove(pokemon.pokemon.uuid)
        CobbleworkersNavigationUtils.releaseTarget(pokemon.pokemon.uuid, world)
    }
}
