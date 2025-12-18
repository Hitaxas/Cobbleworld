/*
 * Copyright (C) 2025 HitaxasTV
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0.
 */

package accieo.cobbleworkers.sanity

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.world.World
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

object SanityManager {

    /* ----------------------------- CONFIG ----------------------------- */

    const val MAX_SANITY = 100.0
    const val COMPLAINING_THRESHOLD = 50.0
    const val REFUSE_THRESHOLD = 30.0
    const val RESUME_THRESHOLD = 60.0

    private const val WORK_DRAIN_PER_TICK = 0.000625 // 0.05 // testing values
    private const val REST_RECOVERY_PER_TICK = 0.00146 // 0.05 // testing values
    private const val MIN_BREAK_DURATION_TICKS = 20L * 60L

    /* ------------------------------ STATE ------------------------------ */

    private val sanity: MutableMap<UUID, Double> = ConcurrentHashMap()
    private val breakStartTime: MutableMap<UUID, Long> = ConcurrentHashMap()
    private val isRefusing: MutableMap<UUID, Boolean> = ConcurrentHashMap()
    private val lastComplaintTime: MutableMap<UUID, Long> = ConcurrentHashMap()
    private val hasComplainedDuringThisStretch: MutableMap<UUID, Boolean> = ConcurrentHashMap()

    private const val COMPLAINT_INTERVAL = 20L * 30L


    fun getSanity(pokemon: PokemonEntity): Double {
        return sanity.computeIfAbsent(pokemon.pokemon.uuid) { MAX_SANITY }
    }

    fun isComplaining(pokemon: PokemonEntity): Boolean {
        val currentSanity = getSanity(pokemon)
        return currentSanity < COMPLAINING_THRESHOLD && currentSanity >= REFUSE_THRESHOLD
    }

    fun canWork(pokemon: PokemonEntity, world: World): Boolean {
        val uuid = pokemon.pokemon.uuid
        val currentSanity = getSanity(pokemon)

        if (isRefusing[uuid] == true) {
            val breakStart = breakStartTime[uuid] ?: return false

            // Check if break is over
            if (world.time - breakStart >= MIN_BREAK_DURATION_TICKS && currentSanity >= RESUME_THRESHOLD) {
                isRefusing[uuid] = false
                breakStartTime.remove(uuid)
                sendActionBar(pokemon, "${pokemon.pokemon.species.translatedName.string} has finished their break.", Formatting.GREEN)
                return true
            }
            return false // Still on break
        }

        if (currentSanity < REFUSE_THRESHOLD) {
            beginRefusal(pokemon, world)
            return false
        }

        return true
    }

    fun drainWhileWorking(pokemon: PokemonEntity) {
        val uuid = pokemon.pokemon.uuid
        val currentSanity = getSanity(pokemon)
        sanity[uuid] = max(0.0, currentSanity - WORK_DRAIN_PER_TICK)
    }

    fun recoverWhileIdle(pokemon: PokemonEntity) {
        val uuid = pokemon.pokemon.uuid
        sanity[uuid] = min(MAX_SANITY, getSanity(pokemon) + REST_RECOVERY_PER_TICK)
    }

    fun recoverWhileSleeping(pokemon: PokemonEntity) {
        val uuid = pokemon.pokemon.uuid
        sanity[uuid] = min(MAX_SANITY, getSanity(pokemon) + (REST_RECOVERY_PER_TICK * 3.5))
    }

    fun shouldComplain(pokemon: PokemonEntity, world: World): Boolean {
        val uuid = pokemon.pokemon.uuid
        val isCurrentlyComplaining = isComplaining(pokemon)

        // If sanity recovered above 50%, reset the flag so they can complain again if it drops later
        if (getSanity(pokemon) >= COMPLAINING_THRESHOLD) {
            hasComplainedDuringThisStretch[uuid] = false
            return false
        }

        // If they are in the complaint range and haven't sent the message yet
        if (isCurrentlyComplaining && hasComplainedDuringThisStretch[uuid] != true) {
            hasComplainedDuringThisStretch[uuid] = true // Mark as sent

            val name = pokemon.pokemon.species.translatedName.string
            sendActionBar(pokemon, "$name is unhappy with the working conditions...", Formatting.YELLOW)
            return true
        }

        return false
    }

    fun beginRefusal(pokemon: PokemonEntity, world: World) {
        val uuid = pokemon.pokemon.uuid
        if (isRefusing[uuid] != true) {
            isRefusing[uuid] = true
            breakStartTime[uuid] = world.time

            val name = pokemon.pokemon.species.translatedName.string
            sendActionBar(pokemon, "$name is slacking off!", Formatting.RED)
        }
    }

    fun needsForcedBreak(pokemon: PokemonEntity): Boolean {
        val currentSanity = getSanity(pokemon)
        return currentSanity < REFUSE_THRESHOLD
    }

    fun isRefusingWork(pokemon: PokemonEntity): Boolean {
        return isRefusing[pokemon.pokemon.uuid] == true
    }

    fun clear(pokemon: PokemonEntity) {
        val uuid = pokemon.pokemon.uuid
        sanity.remove(uuid)
        breakStartTime.remove(uuid)
        isRefusing.remove(uuid)
        lastComplaintTime.remove(uuid)
        hasComplainedDuringThisStretch.remove(uuid) // Added this
    }

    /* ----------------------------- HELPERS ----------------------------- */

    private fun sendActionBar(pokemon: PokemonEntity, message: String, color: Formatting) {
        val text = Text.literal(message).formatted(color)
        val owner = pokemon.owner
        if (owner is ServerPlayerEntity) {
            owner.sendMessage(text, true)
        }
    }
}