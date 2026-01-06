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
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import accieo.cobbleworkers.network.fabric.FabricSanityNetworking

object CobbleworkersFabricClient : ClientModInitializer {
    override fun onInitializeClient() {
        FabricSanityNetworking.registerClientHandlers()

        // Initialize HUD renderer for the workers panel
        HudRenderCallback.EVENT.register { context, tickDelta ->
            SanityHudRenderer.render(context)
        }

        // Register sanity feature renderer
        SanityFeatureRegistration.register()

        // Hook into screen rendering to draw sanity on Summary screens
        ScreenEvents.AFTER_INIT.register { client, screen, scaledWidth, scaledHeight ->
            ScreenEvents.afterRender(screen).register { _, context, mouseX, mouseY, delta ->
                SanityFeatureRegistration.renderOnSummary(context, screen)
            }
        }
    }
}