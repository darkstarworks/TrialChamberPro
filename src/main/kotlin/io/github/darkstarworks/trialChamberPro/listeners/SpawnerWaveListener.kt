package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Listens for trial spawner events and updates wave tracking.
 *
 * Tracks:
 * - Mob spawns from trial spawners
 * - Mob deaths (for wave progress)
 * - Player proximity for boss bar display
 */
class SpawnerWaveListener(private val plugin: TrialChamberPro) : Listener {

    // Detection radius for nearby trial spawners (to add players to boss bar)
    private val detectionRadius = plugin.config.getInt("spawner-waves.detection-radius", 20)

    /**
     * Handles mob spawns from trial spawners.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        // Only track spawner spawns
        if (event.spawnReason != CreatureSpawnEvent.SpawnReason.TRIAL_SPAWNER) {
            return
        }

        if (!plugin.isReady) return
        if (!plugin.config.getBoolean("spawner-waves.enabled", true)) return

        val entity = event.entity
        val location = event.location

        // Find the trial spawner that spawned this mob (search nearby)
        val spawnerLocation = findNearbyTrialSpawner(location) ?: return

        // Check if spawner is within a registered chamber
        val chamber = plugin.chamberManager.getCachedChamberAt(spawnerLocation)
        if (chamber == null) {
            // Not in a tracked chamber, ignore
            return
        }

        // Determine if ominous from spawner block state
        val world = spawnerLocation.world ?: return
        val block = world.getBlockAt(spawnerLocation)
        val isOminous = isTrialSpawnerOminous(block)

        // Record the spawn
        plugin.spawnerWaveManager.recordMobSpawn(spawnerLocation, entity, isOminous)

        // Add nearby players to the wave tracking
        location.getNearbyPlayers(detectionRadius.toDouble()).forEach { player ->
            plugin.spawnerWaveManager.addPlayerToWave(player, spawnerLocation)
        }
    }

    /**
     * Handles mob deaths for wave progress tracking.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (!plugin.isReady) return
        if (!plugin.config.getBoolean("spawner-waves.enabled", true)) return

        val entity = event.entity

        // Check if this mob was tracked by a wave
        val killer = entity.killer
        plugin.spawnerWaveManager.recordMobDeath(entity, killer)
    }

    /**
     * Adds players to boss bar when they approach trial spawners.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!plugin.isReady) return
        if (!plugin.config.getBoolean("spawner-waves.enabled", true)) return
        if (!plugin.config.getBoolean("spawner-waves.show-boss-bar", true)) return

        // Only check on block changes to reduce overhead
        val from = event.from
        val to = event.to
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) {
            return
        }

        val player = event.player
        val location = to

        // Check if player is in a chamber
        val chamber = plugin.chamberManager.getCachedChamberAt(location)
        if (chamber == null) return

        // Find nearby trial spawners and add player to their waves
        findNearbyTrialSpawners(location, detectionRadius).forEach { spawnerLocation ->
            plugin.spawnerWaveManager.addPlayerToWave(player, spawnerLocation)
        }
    }

    /**
     * Cleans up player data on disconnect.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (!plugin.isReady) return

        plugin.spawnerWaveManager.removePlayer(event.player)
    }

    /**
     * Finds a trial spawner near the given location.
     */
    private fun findNearbyTrialSpawner(location: org.bukkit.Location): org.bukkit.Location? {
        val world = location.world ?: return null
        val searchRadius = 5 // Trial spawner spawn range

        for (x in -searchRadius..searchRadius) {
            for (y in -searchRadius..searchRadius) {
                for (z in -searchRadius..searchRadius) {
                    val block = world.getBlockAt(
                        location.blockX + x,
                        location.blockY + y,
                        location.blockZ + z
                    )
                    if (block.type == Material.TRIAL_SPAWNER) {
                        return block.location
                    }
                }
            }
        }

        return null
    }

    /**
     * Finds all trial spawners within a radius.
     */
    private fun findNearbyTrialSpawners(location: org.bukkit.Location, radius: Int): List<org.bukkit.Location> {
        val world = location.world ?: return emptyList()
        val spawners = mutableListOf<org.bukkit.Location>()

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    // Optimize: only check blocks within sphere
                    if (x * x + y * y + z * z > radius * radius) continue

                    val block = world.getBlockAt(
                        location.blockX + x,
                        location.blockY + y,
                        location.blockZ + z
                    )
                    if (block.type == Material.TRIAL_SPAWNER) {
                        spawners.add(block.location)
                    }
                }
            }
        }

        return spawners
    }

    /**
     * Checks if a trial spawner block is in ominous mode.
     * Uses Paper API with fallback to block data string parsing.
     */
    private fun isTrialSpawnerOminous(block: org.bukkit.block.Block): Boolean {
        if (block.type != Material.TRIAL_SPAWNER) return false

        return try {
            // Try to use the TrialSpawner API (Paper 1.21+)
            val state = block.state
            if (state is org.bukkit.block.TrialSpawner) {
                // Use reflection to check for isOminous() method (may not exist in all versions)
                val isOminousMethod = state.javaClass.getMethod("isOminous")
                isOminousMethod.invoke(state) as? Boolean ?: false
            } else {
                // Fallback: parse block data string
                block.blockData.asString.contains("ominous=true", ignoreCase = true)
            }
        } catch (_: NoSuchMethodException) {
            // Method doesn't exist, fallback to string parsing
            block.blockData.asString.contains("ominous=true", ignoreCase = true)
        } catch (_: Exception) {
            // Any other error, fallback to string parsing
            block.blockData.asString.contains("ominous=true", ignoreCase = true)
        }
    }
}
