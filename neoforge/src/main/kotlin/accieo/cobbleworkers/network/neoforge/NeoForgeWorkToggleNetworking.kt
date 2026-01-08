/*
 * Copyright (C) 2026 HitaxasTV
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.network.neoforge

import accieo.cobbleworkers.network.payloads.RequestWorkStatePayload
import accieo.cobbleworkers.network.payloads.SyncWorkStatePayload
import accieo.cobbleworkers.network.payloads.ToggleWorkPayload
import accieo.cobbleworkers.utilities.CobbleworkersWorkToggle
import accieo.cobbleworkers.utilities.ClientWorkStateCache
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.registration.PayloadRegistrar
import java.util.*

@EventBusSubscriber(
    modid = "cobbleworkers",
    bus = EventBusSubscriber.Bus.MOD
)
object NeoForgeWorkToggleNetworking {

    @SubscribeEvent
    fun registerPayloads(event: net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent) {
        val registrar: PayloadRegistrar = event.registrar("1.0")

        registrar.playToServer(ToggleWorkPayload.ID, ToggleWorkPayload.CODEC) { payload, context ->
            handleStateRequest(payload.pokemonId, context, toggle = true)
        }

        registrar.playToServer(RequestWorkStatePayload.ID, RequestWorkStatePayload.CODEC) { payload, context ->
            handleStateRequest(payload.pokemonId, context, toggle = false)
        }

        registrar.playToClient(SyncWorkStatePayload.ID, SyncWorkStatePayload.CODEC) { payload, context ->
            context.enqueueWork {
                ClientWorkStateCache.updateState(payload.pokemonId, payload.canWork)

                val client = net.minecraft.client.MinecraftClient.getInstance()
                val world = client.world ?: return@enqueueWork
                val entity = world.entities.firstOrNull { it.uuid == payload.pokemonId }
                if (entity is PokemonEntity) {
                    CobbleworkersWorkToggle.setCanWork(entity.pokemon, payload.canWork)
                }
            }
        }
    }

    private fun handleStateRequest(pokemonId: UUID, context: IPayloadContext, toggle: Boolean) {
        context.enqueueWork {
            val player = context.player() as? ServerPlayerEntity ?: return@enqueueWork
            val serverWorld = player.world as? ServerWorld ?: return@enqueueWork
            val entity = serverWorld.getEntity(pokemonId)

            if (entity is PokemonEntity && entity.ownerUuid == player.uuid) {
                val pokemon = entity.pokemon
                var canWork = CobbleworkersWorkToggle.canWork(pokemon)

                if (toggle) {
                    canWork = !canWork
                    CobbleworkersWorkToggle.setCanWork(pokemon, canWork)
                }

                PacketDistributor.sendToPlayer(player, SyncWorkStatePayload(pokemonId, canWork))
            }
        }
    }

    fun sendToggle(uuid: UUID) = PacketDistributor.sendToServer(ToggleWorkPayload(uuid))

    fun requestState(uuid: UUID) = PacketDistributor.sendToServer(RequestWorkStatePayload(uuid))
}

@EventBusSubscriber(
    modid = "cobbleworkers",
    bus = EventBusSubscriber.Bus.GAME
)
object WorkToggleTrackingHandler {

    @SubscribeEvent
    fun onStartTracking(event: net.neoforged.neoforge.event.entity.player.PlayerEvent.StartTracking) {
        val target = event.target
        val player = event.entity as? ServerPlayerEntity ?: return

        if (target is PokemonEntity) {
            val canWork = CobbleworkersWorkToggle.canWork(target.pokemon)
            PacketDistributor.sendToPlayer(player, SyncWorkStatePayload(target.uuid, canWork))
        }
    }

    @SubscribeEvent
    fun onEntityJoinLevel(event: net.neoforged.neoforge.event.entity.EntityJoinLevelEvent) {
        val entity = event.entity
        if (!event.level.isClient && entity is PokemonEntity) {
            val world = event.level as? ServerWorld ?: return
            world.players.forEach { player ->
                if (player is ServerPlayerEntity) {
                    val canWork = CobbleworkersWorkToggle.canWork(entity.pokemon)
                    PacketDistributor.sendToPlayer(player, SyncWorkStatePayload(entity.uuid, canWork))
                }
            }
        }
    }
}