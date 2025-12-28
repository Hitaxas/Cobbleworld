/*
 * Copyright (C) 2025 HitaxasTV
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package accieo.cobbleworkers.sanity

import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.MathHelper
import kotlin.math.roundToInt

object SanityHudRenderer {

    private const val PANEL_WIDTH = 140
    private const val ENTRY_HEIGHT = 36
    private const val BAR_HEIGHT = 3

    private val BG_COLOR = 0xAA003B3B.toInt()
    private val BORDER_COLOR = 0x22FFFFFF.toInt()
    private const val TEXT_PRIMARY = 0xFFFFFF
    private val TEXT_DIM = 0x88FFFFFF.toInt()

    fun render(context: DrawContext) {
        SanityHudClientState.tick()

        val mc = MinecraftClient.getInstance()
        val list = SanityHudClientState.sanityList
        if (list.isEmpty()) return

        val displayList = list.sortedBy { it.sanity }.take(5)

        val panelX = mc.window.scaledWidth - PANEL_WIDTH - 10
        val panelY = 15
        val totalHeight = 16 + (displayList.size * ENTRY_HEIGHT) + 4

        renderBackground(context, panelX, panelY, PANEL_WIDTH, totalHeight)

        context.drawText(mc.textRenderer, Text.literal("WORKERS").formatted(Formatting.BOLD), panelX + 8, panelY + 6, TEXT_DIM, false)

        var currentY = panelY + 18
        for (entry in displayList) {
            renderPalEntry(context, mc, entry, panelX + 5, currentY, PANEL_WIDTH - 10)
            currentY += ENTRY_HEIGHT
        }

        val total = list.size
        val shown = displayList.size
        val extra = total - shown

        if (extra > 0) {
            val text = "+$extra"
            val tr = mc.textRenderer
            val tw = tr.getWidth(text)

            val bx = panelX + PANEL_WIDTH - tw - 4
            val by = panelY + totalHeight + 2

            context.drawText(tr, text, bx, by, 0xFF55FFFF.toInt(), false)
        }
    }

    private fun renderPalEntry(context: DrawContext, mc: MinecraftClient, entry: SanityEntry, x: Int, y: Int, width: Int) {
        val displaySanity = SanityHudClientState.getDisplaySanity(entry.uuid)
        val barColor = getSanityColor(displaySanity)

        // 1. Portrait
        renderPokemonPortrait(context, entry, x, y + 2, 28)

        // 2. Name Line
        val name = if (entry.name.length > 12) entry.name.take(10) + ".." else entry.name
        context.drawText(mc.textRenderer, name, x + 34, y + 2, TEXT_PRIMARY, false)

// 3. Status Line (auto-scroll + clipping)
        val statusClean = entry.status.split(" (")[0]
        val statusColor = getStatusColor(statusClean)

        val textRenderer = mc.textRenderer
        val maxStatusWidth = width - 38        // same width you use for bar
        val statusX = x + 34
        val statusY = y + 12

        val textWidth = textRenderer.getWidth(statusClean)

        context.enableScissor(statusX, statusY - 1, statusX + maxStatusWidth, statusY + 10)

        if (textWidth <= maxStatusWidth) {
            // Fits normally
            context.drawText(textRenderer, statusClean, statusX, statusY, statusColor, false)
        } else {
            // Scroll horizontally
            val time = (mc.world?.time ?: 0L).toDouble()
            val speed = 0.5               // lower = slower scroll
            val gap = 80                  // spacing before repeating

            val totalScroll = textWidth + gap
            val offset = -((time * speed) % totalScroll).toInt()

            // draw twice to seamlessly loop
            context.drawText(textRenderer, statusClean, statusX + offset, statusY, statusColor, false)
            context.drawText(textRenderer, statusClean, statusX + offset + totalScroll, statusY, statusColor, false)
        }

        context.disableScissor()


        // 4. Sanity Bar
        val barX = x + 34
        val barY = y + 24
        val barMaxW = width - 38

        context.fill(barX, barY, barX + barMaxW, barY + BAR_HEIGHT, 0x44000000.toInt())
        val sanityRatio = MathHelper.clamp(displaySanity / 100.0, 0.0, 1.0)
        val fillW = MathHelper.clamp((barMaxW * sanityRatio).roundToInt(), 0, barMaxW)

        var finalBarColor = (0xFF shl 24) or barColor
        if (displaySanity < 30.0 && (mc.world?.time ?: 0) % 20 < 10) {
            finalBarColor = 0xFFFF3333.toInt()
        }

        context.fill(barX, barY, barX + fillW, barY + BAR_HEIGHT, finalBarColor)
    }

    private fun getStatusColor(status: String): Int {
        return when {
            status.contains("Is fast asleep...") -> 0xFFFF5555.toInt() // Red
            status.contains("Is slacking off!") -> 0xFFFF5555.toInt() // Red
            status.contains("Is hard at work.") -> 0xFF55FFFF.toInt() // Cyan
            status.contains("Is in good condition.") -> 0xFFFFAA00.toInt() // Orange
            else -> 0xFFFFAA00.toInt() // Muted Gray
        }
    }

    private fun getSanityColor(sanity: Double): Int {
        return when {
            sanity > 70.0 -> 0x44FF77
            sanity > 30.0 -> 0xFFBB33
            else -> 0xFF4444
        }
    }

    private fun renderBackground(context: DrawContext, x: Int, y: Int, w: Int, h: Int) {
        context.fill(x, y, x + w, y + h, BG_COLOR)
        context.fill(x, y, x + w, y + 1, BORDER_COLOR)
        context.fill(x, y + h - 1, x + w, y + h, BORDER_COLOR)
        context.fill(x, y, x + 1, y + h, BORDER_COLOR)
        context.fill(x + w - 1, y, x + w, y + h, BORDER_COLOR)
    }

    private fun renderPokemonPortrait(context: DrawContext, entry: SanityEntry, x: Int, y: Int, size: Int) {
        try {
            val mc = MinecraftClient.getInstance()
            val pokemonEntity = mc.world?.entities?.filterIsInstance<com.cobblemon.mod.common.entity.pokemon.PokemonEntity>()
                ?.find { it.pokemon.uuid == entry.uuid } ?: return

            val widget = ModelWidget(
                pX = x - 12, pY = y - 6, pWidth = size + 16, pHeight = size + 16,
                pokemon = RenderablePokemon(pokemonEntity.pokemon.species, pokemonEntity.pokemon.aspects),
                baseScale = 1F, rotationY = 340F, offsetY = -4.0
            )

            context.enableScissor(x, y, x + size, y + size)
            widget.render(context, 0, 0, 0f)
            context.disableScissor()
        } catch (_: Exception) {
            context.fill(x, y, x + size, y + size, 0x22FFFFFF.toInt())
        }
    }
}