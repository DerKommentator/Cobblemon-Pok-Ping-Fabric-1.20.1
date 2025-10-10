package de.derkommentator.pokeping.spawning

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.spawning.CobblemonWorldSpawnerManager
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.spawning.SpawnCause
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.spawner.SpawningArea
import com.cobblemon.mod.common.pokemon.Species
import de.derkommentator.pokeping.config.BiomeBucketMode
import de.derkommentator.pokeping.config.ConfigManager
import de.derkommentator.pokeping.hud.HudOverlay
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.MathHelper
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object BiomeSpawns {
    private val logger = LoggerFactory.getLogger("PokePing")
    private val lastBiomeForPlayer = ConcurrentHashMap<UUID, String>()
    private var executor = Executors.newSingleThreadExecutor()
    private val ALL_BUCKETS = listOf(BiomeBucketMode.COMMON, BiomeBucketMode.UNCOMMON, BiomeBucketMode.RARE, BiomeBucketMode.ULTRARARE)

    private val speciesCache: Map<String, Species> by lazy {
        PokemonSpecies.implemented.associateBy { it.name.lowercase() }
    }

    private val lastQueryTime = ConcurrentHashMap<UUID, Long>()
    private const val MIN_QUERY_INTERVAL_MS = 5000L // 5 seconds

    fun register() {
        if (executor.isShutdown || executor.isTerminated) {
            executor = Executors.newSingleThreadExecutor()
        }

        ServerPlayerEvents.AFTER_RESPAWN.register(ServerPlayerEvents.AfterRespawn { _, newPlayer, _ ->
            lastBiomeForPlayer.remove(newPlayer.uuid)
            lastQueryTime.remove(newPlayer.uuid)
        })
    }

    fun onPlayerTick(player: ServerPlayerEntity, biomeBucketMode: BiomeBucketMode) {
        val uuid = player.uuid
        val world = player.world as? ServerWorld ?: return

        val biomeKey = world.getBiome(player.blockPos).key.get().value.toString()
        val currentTime = System.currentTimeMillis()

        // prevent spamming (only update if biome changes or 5 seconds have passed)
        if (biomeKey == lastBiomeForPlayer[uuid] &&
            (currentTime - (lastQueryTime[uuid] ?: 0L)) < MIN_QUERY_INTERVAL_MS
        ) return

        lastBiomeForPlayer[uuid] = biomeKey
        lastQueryTime[uuid] = currentTime

        executor.execute {
            try {
                val selectedBuckets = if (biomeBucketMode == BiomeBucketMode.ALL) ALL_BUCKETS else listOf(biomeBucketMode)
                val allProbabilities = mutableMapOf<SpawnDetail, Float>()

                val spawner = CobblemonWorldSpawnerManager.spawnersForPlayers[uuid] ?: return@execute

                for (biomeBucket in selectedBuckets) {
                    val bucket = SpawnBucket(biomeBucket.value, biomeBucket.bucketPercentage)
                    val cause = SpawnCause(spawner, bucket, player)

                    val area = SpawningArea(
                        cause = cause,
                        world = world,
                        baseX = MathHelper.ceil(player.x - Cobblemon.config.worldSliceDiameter / 2F),
                        baseY = MathHelper.ceil(player.y - Cobblemon.config.worldSliceHeight / 2F),
                        baseZ = MathHelper.ceil(player.z - Cobblemon.config.worldSliceDiameter / 2F),
                        length = Cobblemon.config.worldSliceDiameter,
                        height = Cobblemon.config.worldSliceHeight,
                        width = Cobblemon.config.worldSliceDiameter
                    )

                    val slice = spawner.prospector.prospect(spawner, area)
                    val contexts = spawner.resolver.resolve(spawner, spawner.contextCalculators, slice)
                    val spawnProbabilities = spawner.getSpawningSelector().getProbabilities(spawner, contexts)

                    for ((spawnEntry, innerProbability) in spawnProbabilities) {
                        val totalProbability = (innerProbability / 100) * biomeBucket.bucketPercentage

                        allProbabilities[spawnEntry] = (allProbabilities[spawnEntry] ?: 0f) + totalProbability
                    }
                }



                val filtered = allProbabilities.filter { (spawnEntry, _) ->
//                    val speciesName = spawnEntry.getName().string
                    ConfigManager.config.species.contains(spawnEntry.id.replace(Regex("-\\d+$"), "").lowercase())
                }

                if (filtered.isEmpty()) {
                    HudOverlay.updateDisplay(emptyList(), biomeKey)
                    return@execute
                }

                val maxDisplay = ConfigManager.config.biomeSpawn.maxPokemonDisplayed
                val sorted = filtered.entries
                    .sortedByDescending { it.value }
                    .take(maxDisplay)

                val entries = sorted.mapNotNull { (spawnEntry, probability) ->
                    val pokemonId = spawnEntry.id.replace(Regex("-\\d+$"), "").lowercase()
                    val species = speciesCache[pokemonId]
                    if (species == null) {
                        logger.warn("Species '${pokemonId}' not found in cache.")
                        null
                    } else {
                        HudOverlay.PokemonDisplayEntry(
                            resourceIdentifier = species.resourceIdentifier,
                            aspects = species.standardForm.aspects.toMutableSet(),
                            name = species.name,
                            translatedName = species.translatedName.string,
                            spawnChance = probability
                        )
                    }
                }

                HudOverlay.updateDisplay(entries, biomeKey)

            } catch (e: Exception) {
                logger.error("Failed to load biome spawns for ${player.name.string}", e)
            }
        }
    }

    fun shutdown() {
        if (!executor.isShutdown) {
            executor.shutdown()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        }
    }
}