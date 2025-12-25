/*
 * Copyright (C) 2025 HitaxasTV
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.sanity

import java.util.UUID

object SanityHudClientState {

    private data class SanityData(
        val entry: SanityEntry,
        val lastUpdateTime: Long,
        var displaySanity: Double
    )

    private val sanityMap: MutableMap<UUID, SanityData> = mutableMapOf()

    var sanityList: List<SanityEntry> = emptyList()
        private set

    fun update(entries: List<SanityEntry>) {
        val currentTime = System.currentTimeMillis()

        entries.forEach { entry ->
            val existing = sanityMap[entry.uuid]
            if (existing != null) {

                sanityMap[entry.uuid] = SanityData(
                    entry = entry,
                    lastUpdateTime = currentTime,
                    displaySanity = existing.displaySanity
                )
            } else {
                sanityMap[entry.uuid] = SanityData(
                    entry = entry,
                    lastUpdateTime = currentTime,
                    displaySanity = entry.sanity.toDouble() // This must be a Double
                )
            }
        }

        val currentUUIDs = entries.map { it.uuid }.toSet()
        sanityMap.keys.retainAll(currentUUIDs)

        sanityList = entries
    }

    fun tick() {
        sanityMap.forEach { (_, data) ->
            val target = data.entry.sanity.toDouble()
            val current = data.displaySanity

            val lerpSpeed = 0.1
            data.displaySanity = current + (target - current) * lerpSpeed

            if (data.displaySanity < 0.0) data.displaySanity = 0.0
            if (data.displaySanity > 100.0) data.displaySanity = 100.0
        }
    }

    fun getDisplaySanity(uuid: UUID): Double {
        return sanityMap[uuid]?.displaySanity ?: 0.0
    }
}