package de.derkommentator.pokeping.spawning

import com.google.gson.Gson
import com.google.gson.JsonObject
import de.derkommentator.pokeping.config.ConfigManager
import java.util.jar.JarFile
import java.io.InputStreamReader
import org.slf4j.LoggerFactory

object CobblemonSpawnDataLoader {
    private val logger = LoggerFactory.getLogger("PokePing")
    private val gson = Gson()

    val spawnPools: List<SpawnPoolData> by lazy {
        logger.info("Loading Cobblemon Spawn-Pools ...")
        loadAllSpawnPoolsInternal().also {
            logger.info("Loaded Spawn-Pools: ${it.size}")
        }
    }

    fun loadAllSpawnPoolsInternal(): List<SpawnPoolData> {
        val results = mutableListOf<SpawnPoolData>()
        val cobblemonModFile = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getModContainer("cobblemon").orElse(null).origin
            .paths?.firstOrNull()?.toFile() ?: return results

        if (cobblemonModFile.isFile) {
            JarFile(cobblemonModFile).use { jar ->
                jar.entries().asSequence()
                    .filter { entry ->
                        entry.name.startsWith("data/cobblemon/spawn_pool_world/") &&
                        entry.name.endsWith(".json") &&
                        // only pokemon in config
                        ConfigManager.config.species.any { speciesName -> entry.name.contains(speciesName, ignoreCase = true) }
                    }
                    .forEach { entry ->
                        jar.getInputStream(entry).use { stream ->
                            try {
                                val json = gson.fromJson(InputStreamReader(stream), JsonObject::class.java)
                                val spawnsArray = json["spawns"]?.asJsonArray ?: return@use
                                val bucket = json["bucket"]?.asString ?: "common"

                                val spawnEntries = spawnsArray.mapNotNull { element ->
                                    val obj = element.asJsonObject
                                    val pokemonName = obj["pokemon"]?.asString?.substringBefore(" ")?.lowercase() ?: return@mapNotNull null
                                    val bucket = obj["bucket"]?.asString ?: return@mapNotNull null
                                    val weight = obj["weight"]?.asFloat ?: 1f
                                    val biomes = obj["condition"]
                                        ?.asJsonObject
                                        ?.getAsJsonArray("biomes")
                                        ?.mapNotNull { it?.asString } ?: emptyList()

                                    // only pokemon in config
                                    //if (!ConfigManager.config.species.contains(pokemonName)) return@mapNotNull null

                                    SpawnPoolData.SpeciesSpawn(pokemonName, bucket, weight, biomes)
                                }.distinctBy { it.name }.take(ConfigManager.config.biomeSpawn.maxPokemonDisplayed)

                                if (spawnEntries.isNotEmpty())
                                    results.add(SpawnPoolData(bucket, spawnEntries))

                            } catch (e: Exception) {
                                logger.error("Error reading ${entry.name}: ${e.message}")
                            }
                        }
                    }
            }
        }

        return results
    }

    data class SpawnPoolData(
        val bucket: String,
        val entries: List<SpeciesSpawn>
    ) {
        data class SpeciesSpawn(
            val name: String,
            val bucket: String,
            val weight: Float,
            val biomes: List<String>
        )
    }
}
