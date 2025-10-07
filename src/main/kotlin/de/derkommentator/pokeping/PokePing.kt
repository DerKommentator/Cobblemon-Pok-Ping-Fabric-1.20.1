package de.derkommentator.pokeping

import net.minecraft.text.Text
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import de.derkommentator.pokeping.config.ConfigManager
import de.derkommentator.pokeping.config.DiscordMessageMode
import de.derkommentator.pokeping.config.PokePingConfig
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents
import net.minecraft.client.MinecraftClient
import org.slf4j.LoggerFactory
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.round

object PokePing : ModInitializer {
    const val MOD_ID = "pokeping"
    private val logger = LoggerFactory.getLogger("PokePing")
    private val executor = Executors.newSingleThreadExecutor()
    private var cfg = PokePingConfig()

    override fun onInitialize() {
        logger.info("Loaded PokePing!")

        ConfigManager.load()
        cfg = ConfigManager.config
        logger.info("Loaded Config: $cfg")

        ClientEntityEvents.ENTITY_LOAD.register { entity, world ->
            if (!cfg.modEnabled) return@register
//            val clientUuid = MinecraftClient.getInstance().player?.uuid

            if (entity is PokemonEntity) {
                val pokemon = entity.pokemon
                val pokemonName = pokemon.species.name.lowercase()
                val pos = entity.pos

                //logger.info("Pokemon Spawn: $pokemonName at ${round(pos.x)}, ${round(pos.y)}, ${round(pos.z)}")

                // Check if pokemon is in team
//                if (clientUuid == null) return@register
//                val myParty = Cobblemon.storage.getParty(clientUuid)
//                if (myParty.any { it.species.name == pokemon.species.name }) return@register

                if (!cfg.species.contains(pokemonName)) return@register

                val translatedName = pokemon.species.translatedName.string
                //val message = "**${pokemon.species.translatedName.string}** bei ${round(pos.x)}, ${round(pos.y)}, ${round(pos.z)} gespawnt!"
                // logger.info(message)
                sendMessage(Text.translatable("$MOD_ID.notification.spawnMessageChat", translatedName, round(pos.x), round(pos.y), round(pos.z)))

                if (cfg.discord.enabled && cfg.discord.webhookUrl.isNotBlank()) {
                    when (cfg.discord.messageMode) {
                        DiscordMessageMode.Text -> {
                            val message = Text.translatable("$MOD_ID.notification.spawnMessageDiscordText", translatedName, round(pos.x), round(pos.y), round(pos.z)).asString()
                            sendDiscordWebhook(cfg.discord.webhookUrl, cfg.discord.username, message)
                        }
                        DiscordMessageMode.Embed -> {
                            val title = Text.translatable("$MOD_ID.notification.spawnMessageDiscordEmbedTitle").asString()
                            val desc = Text.translatable("$MOD_ID.notification.spawnMessageDiscordEmbedDesc", translatedName, round(pos.x), round(pos.y), round(pos.z)).asString()).asString()
                            val imageUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${pokemon.species.nationalPokedexNumber}.png"
                            sendDiscordEmbedWebhook(
                                cfg.discord.webhookUrl,
                                cfg.discord.username,
                                title,
                                desc,
                                imageUrl
                            )
                        }
                    }
                }
            }
        }
    }

    fun reloadConfig() {
        ConfigManager.load()
        logger.info("Reloaded Config!")
        cfg = ConfigManager.config
        sendMessage(Text.translatable("$MOD_ID.notification.reloadedConfig"))
    }

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
                    logger.error("Failed discord webhook for text: HTTP $responseCode")
                }
            } catch (ex: Exception) {
                logger.error("Error sending text message to discord", ex)
            }
        }
    }

    private fun sendDiscordEmbedWebhook(urlString: String, username: String, title: String, description: String, imageUrl: String?) {
        executor.submit {
            try {
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val embed = buildString {
                    append("{")
                    append("\"title\": \"$title\",")
                    append("\"description\": \"$description\"")
                    if (imageUrl != null && imageUrl.isNotBlank()) {
                        append(",\"thumbnail\": { \"url\": \"$imageUrl\" }")
                    }
                    append("}")
                }

                val json = """
                {
                  "username": "$username",
                  "embeds": [ $embed ]
                }
            """.trimIndent()

                conn.outputStream.use { os ->
                    os.write(json.toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                if (responseCode !in listOf(200, 204)) {
                    logger.error("Failed discord webhook for embed: HTTP $responseCode")
                }
            } catch (ex: Exception) {
                logger.error("Error sending embed message to discord", ex)
            }
        }
    }


    fun sendMessage(text: Text) {
        val mc = MinecraftClient.getInstance()
        mc.inGameHud?.chatHud?.addMessage(text)
    }
}