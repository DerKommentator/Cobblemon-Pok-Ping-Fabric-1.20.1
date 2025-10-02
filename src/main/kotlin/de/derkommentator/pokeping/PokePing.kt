package de.derkommentator.pokeping

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.pokemon.Pokemon
import de.derkommentator.pokeping.config.ConfigManager
import net.fabricmc.api.ModInitializer
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object PokePing : ModInitializer {
    const val MOD_ID = "pokeping"
    private val logger = LoggerFactory.getLogger("PokePing")
    private val announcedSpawns = ConcurrentHashMap.newKeySet<Pokemon>()

    override fun onInitialize() {
        logger.info("PokePing geladen")

        ConfigManager.load()
        logger.info("Config geladen: ${ConfigManager.config}")

        CobblemonEvents.POKEMON_ENTITY_SPAWN.subscribe { event ->
            val pokemon = event.entity.pokemon
            val pokemonName = pokemon.species.name.lowercase()
            val pos = event.entity.pos

            //logger.info("Pokemon Spawn: $pokemonName at ${pos.x}, ${pos.y}, ${pos.z}")

            val cfg = ConfigManager.config
            if (!cfg.modEnabled || !cfg.species.contains(pokemonName)) return@subscribe

//            if (cfg.announceOnce && !announcedSpawns.contains(pokemon)) {
//                return@subscribe
//            }

            val message = "**${pokemon.species.translatedName.string}** bei ${pos.x}, ${pos.y}, ${pos.z} gespawnt!"
            // logger.info(message)
            sendMessage("§7[PokéPing]§r §a${pokemon.species.translatedName.string}§r ist bei ${pos.x}, ${pos.y}, ${pos.z} gespawnt!")

            if (cfg.discord.enabled && cfg.discord.webhookUrl.isNotBlank()) {
                sendDiscordWebhook(cfg.discord.webhookUrl, cfg.discord.username, message)
            }
        }

//        CobblemonEvents.POKEMON_CAPTURED.subscribe { evt ->
//            announcedSpawns.remove(evt.pokemon)
//        }
//        CobblemonEvents.POKEMON_FAINTED.subscribe { evt ->
//            announcedSpawns.remove(evt.pokemon)
//        }
//        ServerEntityEvents.ENTITY_UNLOAD.register { entity, _ ->
//            if (entity !is PokemonEntity) return@register
//
//            announcedSpawns.remove(entity.pokemon)
//        }
    }

    private fun sendDiscordWebhook(urlString: String, username: String, message: String) {
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

    fun sendMessage(message: String) {
        val mc = MinecraftClient.getInstance()
        mc.inGameHud?.chatHud?.addMessage(Text.literal(message))
    }
}