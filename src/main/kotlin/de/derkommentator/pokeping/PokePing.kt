package de.derkommentator.pokeping

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import de.derkommentator.pokeping.config.ConfigManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.round

object PokePing : ModInitializer {
    //const val MOD_ID = "pokeping"
    private val logger = LoggerFactory.getLogger("PokePing")
    //private val announcedSpawns = ConcurrentHashMap.newKeySet<Pokemon>()

    override fun onInitialize() {
        logger.info("PokePing geladen!")

        ConfigManager.load()
        logger.info("Config geladen: ${ConfigManager.config}")
        val cfg = ConfigManager.config

        ClientEntityEvents.ENTITY_LOAD.register { entity, world ->
            if (!cfg.modEnabled) return@register
            val clientUuid = MinecraftClient.getInstance().player?.uuid

            if (entity is PokemonEntity) {
                val pokemon = entity.pokemon
                val pokemonName = pokemon.species.name.lowercase()
                val pos = entity.pos

                //logger.info("Pokemon Spawn: $pokemonName at ${round(pos.x)}, ${round(pos.y)}, ${round(pos.z)}")

                // Check if pokemon is in team
                if (clientUuid == null) return@register
                val myParty = Cobblemon.storage.getParty(clientUuid)
                if (myParty.any { it.species.name == pokemon.species.name }) return@register

                if (!cfg.species.contains(pokemonName)) return@register

                val message = "**${pokemon.species.translatedName.string}** bei ${round(pos.x)}, ${round(pos.y)}, ${round(pos.z)} gespawnt!"
                // logger.info(message)
                sendMessage("§7[PokéPing]§r §a${pokemon.species.translatedName.string}§r ist bei ${round(pos.x)}, ${round(pos.y)}, ${round(pos.z)} gespawnt!")

                if (cfg.discord.enabled && cfg.discord.webhookUrl.isNotBlank()) {
                    sendDiscordWebhook(cfg.discord.webhookUrl, cfg.discord.username, message)
                }
            }
        }
    }

    private val executor = Executors.newSingleThreadExecutor()

    private fun sendDiscordWebhook(urlString: String, username: String, message: String) {
        executor.submit {
            try {
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val json = """{"username": "$username", "content": "$message"}"""
                conn.outputStream.use { os ->
                    os.write(json.toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                if (responseCode !in listOf(200, 204)) {
                    logger.error("Discord Webhook fehlgeschlagen: HTTP $responseCode")
                }
            } catch (ex: Exception) {
                logger.error("Fehler beim Senden an Discord", ex)
            }
        }
    }

    fun sendMessage(message: String) {
        val mc = MinecraftClient.getInstance()
        mc.inGameHud?.chatHud?.addMessage(Text.literal(message))
    }
}