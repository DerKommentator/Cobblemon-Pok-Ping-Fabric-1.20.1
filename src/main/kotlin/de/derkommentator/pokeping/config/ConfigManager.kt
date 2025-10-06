package de.derkommentator.pokeping.config

import com.google.gson.GsonBuilder
import java.io.File

data class DiscordConfig(
    var enabled: Boolean = false,
    var webhookUrl: String = "",
    var username: String = "PokePing",
    var messageMode: DiscordMessageMode = DiscordMessageMode.Embed
)

data class PokePingConfig(
    var modEnabled: Boolean = true,
    var species: List<String> = listOf(),
    val announceOnce: Boolean = true,
    var discord: DiscordConfig = DiscordConfig()
)

object ConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configFile = File("config/pokeping.json")

    var config: PokePingConfig = PokePingConfig()

    fun load() {
        if (configFile.exists()) {
            config = gson.fromJson(configFile.readText(), PokePingConfig::class.java)
        } else {
            save()
        }
    }

    fun save() {
        configFile.parentFile.mkdirs()
        configFile.writeText(gson.toJson(config))
        println(config.toString())
    }

    fun saveLocalConfig(config: PokePingConfig) {
        configFile.parentFile.mkdirs()
        configFile.writeText(gson.toJson(config))
        println(config.toString())
    }
}