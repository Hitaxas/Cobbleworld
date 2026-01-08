/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.neoforge.client

import accieo.cobbleworkers.network.neoforge.NeoForgeWorkToggleNetworking
import accieo.cobbleworkers.sanity.SanityHudRenderer
import accieo.cobbleworkers.sanity.SanityFeatureRegistration
import accieo.cobbleworkers.utilities.CobbleworkersWorkToggle
import accieo.cobbleworkers.utilities.ClientWorkStateCache
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.client.gui.interact.wheel.InteractWheelOption
import net.minecraft.client.MinecraftClient
import net.minecraft.util.Identifier
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.event.level.LevelEvent

@EventBusSubscriber(value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.MOD)
object CobbleworkersNeoForgeClientSetup {

    @SubscribeEvent
    fun onClientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            SanityFeatureRegistration.register()

            registerWorkToggleInteraction()
        }
    }

    private fun registerWorkToggleInteraction() {
        CobblemonEvents.POKEMON_INTERACTION_GUI_CREATION.subscribe { event ->
            val client = MinecraftClient.getInstance()
            val world = client.world ?: return@subscribe

            val entity = world.entities
                .firstOrNull { it.uuid == event.pokemonID }

            if (entity is com.cobblemon.mod.common.entity.pokemon.PokemonEntity) {
                val pokemon = entity.pokemon

                NeoForgeWorkToggleNetworking.requestState(entity.uuid)

                val canWork = ClientWorkStateCache.getState(entity.uuid)
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
                        NeoForgeWorkToggleNetworking.sendToggle(entity.uuid)
                        client.setScreen(null)
                    }
                )

                event.addFillingOption(workToggle)
            }
        }
    }
}

@EventBusSubscriber(value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.GAME)
object CobbleworkersNeoForgeClient {

    @SubscribeEvent
    fun onRenderOverlay(event: RenderGuiEvent.Post) {
        SanityHudRenderer.render(event.guiGraphics)
    }

    @SubscribeEvent
    fun onScreenRender(event: ScreenEvent.Render.Post) {
        SanityFeatureRegistration.renderOnSummary(event.guiGraphics, event.screen)
    }

    @SubscribeEvent
    fun onWorldUnload(event: LevelEvent.Unload) {
        if (event.level.isClient) {
            ClientWorkStateCache.clear()
        }
    }
}