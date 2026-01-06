/*
 * Copyright (C) 2025 HitaxasTV
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.sanity

import io.netty.buffer.ByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.codec.PacketCodecs
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier
import java.util.UUID

data class SanitySyncPayload(val entries: List<SanityEntry>) : CustomPayload {

    override fun getId(): CustomPayload.Id<SanitySyncPayload> = ID

    companion object {
        val ID = CustomPayload.Id<SanitySyncPayload>(
            Identifier.of("cobbleworkers", "sanity_sync")
        )

        private val UUID_CODEC: PacketCodec<RegistryByteBuf, UUID> =
            PacketCodec.of(
                { uuid, buf ->
                    buf.writeLong(uuid.mostSignificantBits)
                    buf.writeLong(uuid.leastSignificantBits)
                },
                { buf ->
                    UUID(buf.readLong(), buf.readLong())
                }
            )

        val CODEC: PacketCodec<RegistryByteBuf, SanitySyncPayload> =
            PacketCodec.of(
                { value, buf ->
                    PacketCodecs.VAR_INT.encode(buf, value.entries.size)
                    value.entries.forEach { entry ->
                        UUID_CODEC.encode(buf, entry.uuid)
                        PacketCodecs.STRING.encode(buf, entry.name)
                        PacketCodecs.VAR_INT.encode(buf, entry.sanity)
                        PacketCodecs.STRING.encode(buf, entry.status)
                    }
                },
                { buf ->
                    val size = PacketCodecs.VAR_INT.decode(buf)
                    val entries = MutableList(size) {
                        SanityEntry(
                            UUID_CODEC.decode(buf),
                            PacketCodecs.STRING.decode(buf),
                            PacketCodecs.VAR_INT.decode(buf),
                            PacketCodecs.STRING.decode(buf)
                        )
                    }
                    SanitySyncPayload(entries)
                }
            )
    }
}