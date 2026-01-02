/*
 * Copyright (C) 2025 HitaxasTV
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.utilities

import com.cobblemon.mod.common.pokemon.Pokemon

object CobbleworkersWorkToggle {
    
    private const val WORK_ENABLED_KEY = "cobbleworkers_can_work"

    @JvmStatic
    fun canWork(pokemon: Pokemon): Boolean {
        return pokemon.persistentData.getBoolean(WORK_ENABLED_KEY).takeIf { 
            pokemon.persistentData.contains(WORK_ENABLED_KEY) 
        } ?: true // Default to true (can work)
    }

    @JvmStatic
    fun setCanWork(pokemon: Pokemon, canWork: Boolean) {
        pokemon.persistentData.putBoolean(WORK_ENABLED_KEY, canWork)
    }
}