/*
 * Copyright (C) 2025 Accieo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.neoforge.client

import accieo.cobbleworkers.sanity.SanityHudRenderer
import accieo.cobbleworkers.sanity.SanityFeatureRegistration
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.ScreenEvent

@EventBusSubscriber(value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.MOD)
object CobbleworkersNeoForgeClientSetup {

    @SubscribeEvent
    fun onClientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork {
            // Register sanity feature renderer
            SanityFeatureRegistration.register()
        }
    }
}

@EventBusSubscriber(value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.GAME)
object CobbleworkersNeoForgeClient {

    @SubscribeEvent
    fun onRenderOverlay(event: RenderGuiEvent.Post) {
        // Render the HUD overlay for workers panel
        SanityHudRenderer.render(event.guiGraphics)
    }

    @SubscribeEvent
    fun onScreenRender(event: ScreenEvent.Render.Post) {
        // Render sanity bar on Summary screens
        SanityFeatureRegistration.renderOnSummary(event.guiGraphics, event.screen)
    }
}