/*
 * Copyright (C) 2025 HitaxasTV
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.sanity

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.network.ServerPlayerEntity

object FabricSanityNetworking : SanityPlatformNetworking {
    override fun sendSanityUpdate(player: ServerPlayerEntity, list: List<SanityEntry>) {
        ServerPlayNetworking.send(player, SanitySyncPayload(list))
    }
}
