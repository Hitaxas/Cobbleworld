/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.fabric.client

import accieo.cobbleworkers.sanity.SanityFeatureRegistration
import accieo.cobbleworkers.sanity.SanityHudRenderer
import accieo.cobbleworkers.utilities.CobbleworkersWorkToggle
import accieo.cobbleworkers.utilities.ClientWorkStateCache
import accieo.cobbleworkers.network.payloads.ToggleWorkPayload
import accieo.cobbleworkers.network.payloads.RequestWorkStatePayload
import accieo.cobbleworkers.network.payloads.SyncWorkStatePayload
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.client.gui.interact.wheel.InteractWheelOption
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import accieo.cobbleworkers.network.fabric.FabricSanityNetworking
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.util.Identifier

object CobbleworkersFabricClient : ClientModInitializer {

    override fun onInitializeClient() {
        FabricSanityNetworking.registerClientHandlers()

        HudRenderCallback.EVENT.register { context, tickDelta ->
            SanityHudRenderer.render(context)
        }

        SanityFeatureRegistration.register()

        ScreenEvents.AFTER_INIT.register { client, screen, scaledWidth, scaledHeight ->
            ScreenEvents.afterRender(screen).register { _, context, mouseX, mouseY, delta ->
                SanityFeatureRegistration.renderOnSummary(context, screen)
            }
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register {
            ClientWorkStateCache.clear()
        }

        ClientPlayNetworking.registerGlobalReceiver(SyncWorkStatePayload.ID) { payload, context ->
            context.client().execute {
                ClientWorkStateCache.updateState(payload.pokemonId, payload.canWork)

                val world = context.client().world
                val entity = world?.entities?.firstOrNull { it.uuid == payload.pokemonId }
                if (entity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
                    CobbleworkersWorkToggle.setCanWork(entity.pokemon, payload.canWork)
                }
            }
        }

        registerWorkToggleInteraction()
    }

    private fun registerWorkToggleInteraction() {
        CobblemonEvents.POKEMON_INTERACTION_GUI_CREATION.subscribe { event ->
            val client = MinecraftClient.getInstance()
            val world = client.world ?: return@subscribe

            val entity = world.entities
                .firstOrNull { it.uuid == event.pokemonID }

            if (entity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
                val pokemon = entity.pokemon

                ClientPlayNetworking.send(RequestWorkStatePayload(event.pokemonID))

                val canWork = ClientWorkStateCache.getState(event.pokemonID)
                    ?: CobbleworkersWorkToggle.canWork(pokemon)

                val workToggle = InteractWheelOption(
                    iconResource = if (canWork) {
                        Identifier.of("cobbleworkers","textures/gui/interact/interact_wheel_icon_work2.png")
                    } else {
                        Identifier.of("cobbleworkers","textures/gui/interact/interact_wheel_icon_work1.png")
                    },
                    tooltipText = if (canWork) {
                        "cobbleworkers.ui.interact.disable_work"
                    } else {
                        "cobbleworkers.ui.interact.enable_work"
                    },
                    enabled = true,
                    onPress = {
                        ClientPlayNetworking.send(ToggleWorkPayload(entity.uuid))
                        client.setScreen(null)
                    }
                )

                event.addFillingOption(workToggle)
            }
        }
    }
}