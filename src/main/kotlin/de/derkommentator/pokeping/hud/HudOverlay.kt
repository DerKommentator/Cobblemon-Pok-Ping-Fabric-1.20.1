package de.derkommentator.pokeping.hud

import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import com.mojang.blaze3d.systems.RenderSystem
import de.derkommentator.pokeping.PokePing
import de.derkommentator.pokeping.config.ConfigManager
import de.derkommentator.pokeping.config.OverlayPosition
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.joml.Quaternionf
import org.joml.Vector3f
import org.slf4j.LoggerFactory
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.concurrent.CopyOnWriteArrayList

object HudOverlay : HudRenderCallback {
    private val df = DecimalFormat().apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
        roundingMode = RoundingMode.CEILING
        isGroupingUsed = false
    }
    private val displayEntries = CopyOnWriteArrayList<PokemonDisplayEntry>()
    private val logger = LoggerFactory.getLogger("PokePing")
    @Volatile private var lastBiome: String = ""
    val cfg = ConfigManager.config
    private const val ENTRY_DISTANCE: Int = 20

    data class PokemonDisplayEntry(
        val resourceIdentifier: Identifier,
        val aspects: Set<String>,
        val name: String,
        val translatedName: String,
        val spawnChance: Float
    )

    fun updateDisplay(newEntries: List<PokemonDisplayEntry>, biome: String) {
        val maxEntries = ConfigManager.config.biomeSpawn.maxPokemonDisplayed
        val limited = newEntries.take(maxEntries)

        displayEntries.clear()
        displayEntries.addAll(limited)

        lastBiome = biome
    }

    override fun onHudRender(context: DrawContext, tickDelta: Float) {
        val mc = MinecraftClient.getInstance() ?: return

        if (!cfg.biomeSpawn.enabled || mc.player == null) return

        val startX: Int
        val startY: Int
        val width = mc.window.scaledWidth
        val height = mc.window.scaledHeight

        when (cfg.biomeSpawn.overlayPosition) {
            OverlayPosition.TOP_LEFT -> {
                startX = 20; startY = 20
            }
            OverlayPosition.TOP_RIGHT -> {
                startX = width - 150; startY = 20
            }
            OverlayPosition.BOTTOM_LEFT -> {
                startX = 20; startY = height - (displayEntries.size * ENTRY_DISTANCE) - 20
            }
            OverlayPosition.BOTTOM_RIGHT -> {
                startX = width - 150; startY = height - (displayEntries.size * ENTRY_DISTANCE) - 20
            }
        }

        context.drawText(
            mc.textRenderer,
            Text.translatable("${PokePing.MOD_ID}.hud.biomeTitle", lastBiome),
            startX,
            startY,
            0xFFFFFF,
            false
        )

        var y = startY
        for (entry in displayEntries) {
            try {
                drawPokemonEntry(context, entry, tickDelta, startX, y)
            } catch (e: Exception) {
                logger.error("Failed to render ${entry.name}", e)
            }
            y += ENTRY_DISTANCE
        }
    }

    private fun drawPokemonEntry(context: DrawContext, entry: PokemonDisplayEntry, tickDelta: Float, x: Int, y: Int) {
        val client = MinecraftClient.getInstance() ?: return

        if (cfg.biomeSpawn.modelsEnabled) {
            val rotation = Quaternionf().fromEulerXYZDegrees(Vector3f(10f, 340f, 0F))
            val matrixStack = context.matrices

            RenderSystem.enableBlend()

            matrixStack.push()
            matrixStack.translate(x.toDouble() + 18.0, y.toDouble() + 18.0, 0.0)

            try {
                drawProfilePokemon(
                    entry.resourceIdentifier,
                    entry.aspects,
                    matrixStack,
                    rotation,
                    null,
                    tickDelta,
                    12f
                )
            } catch (e: Exception) {
                logger.warn("Pokemon ${entry.name} could not be rendered: ${e.message}")
            }

            matrixStack.pop()
        }

        context.drawText(
            client.textRenderer,
            Text.translatable(
                "${PokePing.MOD_ID}.hud.pokemonSpawnProbability",
                entry.translatedName,
                df.format((entry.spawnChance))
            ),
            if (cfg.biomeSpawn.modelsEnabled) (x + 40) else x,
            y + 30,
            0xFFFFFF,
            true
        )

        RenderSystem.disableBlend()
    }
}