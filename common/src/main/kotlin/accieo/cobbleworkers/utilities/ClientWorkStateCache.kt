/*
 * Copyright (C) 2026 HitaxasTV
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.utilities

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ClientWorkStateCache {
    private val cache = ConcurrentHashMap<UUID, Boolean>()

    fun updateState(pokemonId: UUID, canWork: Boolean) {
        cache[pokemonId] = canWork
    }

    fun getState(pokemonId: UUID): Boolean? {
        return cache[pokemonId]
    }

    fun clear() {
        cache.clear()
    }
}