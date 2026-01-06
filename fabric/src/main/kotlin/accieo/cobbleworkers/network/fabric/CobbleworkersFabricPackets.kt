package accieo.cobbleworkers.network.fabric

import accieo.cobbleworkers.sanity.SanitySyncPayload
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry

object CobbleworkersFabricPackets {

    fun registerCommon() {
        // Client → Server
        PayloadTypeRegistry.playC2S().register(
            SanitySyncPayload.ID,
            SanitySyncPayload.CODEC
        )

        // Server → Client
        PayloadTypeRegistry.playS2C().register(
            SanitySyncPayload.ID,
            SanitySyncPayload.CODEC
        )
    }
}