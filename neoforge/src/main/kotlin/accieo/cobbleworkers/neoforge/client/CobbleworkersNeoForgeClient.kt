package accieo.cobbleworkers.neoforge.client

import accieo.cobbleworkers.sanity.SanityHudRenderer
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RenderGuiEvent

@EventBusSubscriber(value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.GAME)
object CobbleworkersNeoForgeClient {

    @SubscribeEvent
    fun onRenderOverlay(event: RenderGuiEvent.Post) {
        // Pass the GuiGraphics directly to your renderer
        SanityHudRenderer.render(event.guiGraphics)
    }
}