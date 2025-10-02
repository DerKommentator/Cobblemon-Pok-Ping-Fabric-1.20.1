package de.derkommentator.pokeping.config

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text

class PokePingMenu : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent: Screen ->
            val localConfig = ConfigManager.config

            val builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("PokePing Config"))

            val entryBuilder = builder.entryBuilder()
            val general = builder.getOrCreateCategory(Text.literal("Allgemein"))

            general.addEntry(
                entryBuilder.startBooleanToggle(Text.literal("Mod aktivieren"), ConfigManager.config.modEnabled)
                    .setDefaultValue(true)
                    .setSaveConsumer { newIsModEnabled -> localConfig.modEnabled = newIsModEnabled}
                    .build()
            )

            general.addEntry(
                entryBuilder.startStrList(
                    Text.literal("Pokémon Liste"),
                    localConfig.species.toMutableList()
                )
                    .setDefaultValue(listOf())
                    //.setTooltip(Text.literal("Cobblemon Species Name (https://gitlab.com/cable-mc/cobblemon/-/tree/main/common/src/main/resources/data/cobblemon/species)"))
                    .setSaveConsumer { list -> localConfig.species = list.map { it.lowercase() }}
                    .build()
            )

            val discord = builder.getOrCreateCategory(Text.literal("Discord"))

            discord.addEntry(
                entryBuilder.startBooleanToggle(Text.literal("Discord aktivieren"), ConfigManager.config.discord.enabled)
                    .setDefaultValue(true)
                    .setSaveConsumer { newIsEnabled -> localConfig.discord.enabled = newIsEnabled}
                    .build()
            )

            discord.addEntry(
                entryBuilder.startStrField(Text.literal("Webhook URL"), ConfigManager.config.discord.webhookUrl)
                    .setDefaultValue("")
                    .setSaveConsumer { newWebhookUrl -> localConfig.discord.webhookUrl = newWebhookUrl}
                    .build()
            )

            discord.addEntry(
                entryBuilder.startStrField(Text.literal("Username"), ConfigManager.config.discord.username)
                    .setDefaultValue("")
                    .setSaveConsumer { newUsername -> localConfig.discord.username = newUsername}
                    .build()
            )

            builder.setSavingRunnable {
                ConfigManager.saveLocalConfig(localConfig)
                ConfigManager.load()
            }

            builder.build()
        }
    }
}