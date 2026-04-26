package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
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

    // Distance at which a player is removed from an existing boss bar.
    // Must be > detectionRadius to provide hysteresis (avoid flicker at the boundary).
    private val removeDistance = plugin.config.getDouble(
        "spawner-waves.remove-distance",
        (detectionRadius * 1.6).coerceAtLeast(32.0)
    )

    /**
     * Handles mob spawns from trial spawners.
     * Tracks spawns from both registered chambers and wild spawners.
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

        // Determine if ominous from spawner block state
        val world = spawnerLocation.world ?: return
        val block = world.getBlockAt(spawnerLocation)
        val isOminous = isTrialSpawnerOminous(block)

        // Check if spawner is within a registered chamber
        val chamber = plugin.chamberManager.getCachedChamberAt(spawnerLocation)

        if (chamber == null) {
            // Wild spawner - configure cooldown if setting is enabled
            configureWildSpawnerCooldown(block, isOminous)

            // v1.4.0: WildSpawnerResolver seam — let a registered service
            // (typically the planned premium "Wild Custom-Mob Spawners"
            // module) substitute the vanilla spawn with a custom-provider
            // mob. Mirrors the chamber-mode replace-after-spawn flow below.
            if (tryWildSpawnerReplacement(entity, spawnerLocation, isOminous, block)) {
                return
            }
        }

        // v1.3.0: Replace-after-spawn for chambers with a non-vanilla mob provider.
        //
        // The vanilla trial spawner has already produced `entity` and credited it internally
        // (tracked UUID, wave counter, etc.). We remove that entity the same tick and spawn
        // the provider's custom mob at the same location, then record THAT as the tracked
        // wave mob. The vanilla spawner's state machine stays intact — we've just swapped
        // the creature under its feet.
        if (chamber != null && chamber.hasCustomMobProvider(isOminous)) {
            val providerId = chamber.customMobProvider
            val provider = plugin.trialMobProviderRegistry.get(providerId)
            val mobId = chamber.pickMobId(isOminous)

            if (provider != null && provider.id != "vanilla" && mobId != null && provider.isAvailable()) {
                val replaceLoc = entity.location.clone()
                try {
                    entity.remove()
                    val custom = provider.spawnMob(mobId, replaceLoc, isOminous)
                    if (custom != null) {
                        plugin.spawnerWaveManager.recordMobSpawn(spawnerLocation, custom, isOminous)
                        location.getNearbyPlayers(detectionRadius.toDouble()).forEach { player ->
                            plugin.spawnerWaveManager.addPlayerToWave(player, spawnerLocation)
                        }
                        // v1.3.3: notify third-party plugins with chamber + wave context
                        plugin.server.pluginManager.callEvent(
                            io.github.darkstarworks.trialChamberPro.api.events.ChamberMobSpawnedEvent(
                                entity = custom,
                                spawnerLocation = spawnerLocation,
                                chamber = chamber,
                                isOminous = isOminous,
                                providerId = provider.id
                            )
                        )
                        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                            plugin.logger.info("[CustomProvider] Replaced vanilla spawn with ${provider.id}:$mobId at ${spawnerLocation.blockX},${spawnerLocation.blockY},${spawnerLocation.blockZ}")
                        }
                        return
                    } else {
                        plugin.logger.warning("[CustomProvider] ${provider.id} returned null for mobId '$mobId' — wave will undercount this spawn")
                        return
                    }
                } catch (e: Throwable) {
                    plugin.logger.warning("[CustomProvider] Replace-after-spawn failed (${provider.id}:$mobId): ${e.message} — falling back to vanilla")
                    // fall through to vanilla tracking
                }
            }
        }

        // Record the spawn (for both chamber and wild spawners)
        plugin.spawnerWaveManager.recordMobSpawn(spawnerLocation, entity, isOminous)

        // Add nearby players to the wave tracking
        location.getNearbyPlayers(detectionRadius.toDouble()).forEach { player ->
            plugin.spawnerWaveManager.addPlayerToWave(player, spawnerLocation)
        }

        // v1.3.3: notify third-party plugins with chamber + wave context.
        // Fires for both chamber and wild spawners; chamber is null for the latter.
        plugin.server.pluginManager.callEvent(
            io.github.darkstarworks.trialChamberPro.api.events.ChamberMobSpawnedEvent(
                entity = entity,
                spawnerLocation = spawnerLocation,
                chamber = chamber,
                isOminous = isOminous,
                providerId = "vanilla"
            )
        )
    }

    /**
     * v1.4.0 wild-spawner replacement seam. If a [io.github.darkstarworks.trialChamberPro.api.WildSpawnerResolver]
     * is registered as a Bukkit service AND it returns a config for this
     * spawner, the vanilla spawn is removed and replaced with a custom-
     * provider mob (same flow as chamber spawners with custom mob providers).
     *
     * Reads the `tcp:preset_id` PDC tag off the spawner block (written at
     * place-time by `SpawnerPresetPlaceListener`) so the resolver knows
     * which TCP preset the spawner was placed from. Spawners placed by
     * other means (vanilla `/give`, schematic) carry no tag → presetId
     * passes as null.
     *
     * @return `true` if the spawn was replaced and the caller should `return`
     *         from `onCreatureSpawn` without recording the original entity;
     *         `false` to fall through to the vanilla recording path.
     */
    private fun tryWildSpawnerReplacement(
        originalEntity: org.bukkit.entity.Entity,
        spawnerLocation: org.bukkit.Location,
        isOminous: Boolean,
        spawnerBlock: org.bukkit.block.Block
    ): Boolean {
        val resolver = plugin.server.servicesManager
            .load(io.github.darkstarworks.trialChamberPro.api.WildSpawnerResolver::class.java)
            ?: return false

        val presetId = readPresetIdTag(spawnerBlock)
        val config = resolver.resolve(spawnerLocation, presetId) ?: return false
        val mobId = config.pickMobId(isOminous) ?: return false
        val provider = plugin.trialMobProviderRegistry.get(config.providerId) ?: return false
        if (provider.id == "vanilla" || !provider.isAvailable()) return false

        val replaceLoc = originalEntity.location.clone()
        return try {
            originalEntity.remove()
            val custom = provider.spawnMob(mobId, replaceLoc, isOminous)
            if (custom == null) {
                plugin.logger.warning(
                    "[WildSpawnerResolver] ${provider.id} returned null for mobId '$mobId' at " +
                        "${spawnerLocation.blockX},${spawnerLocation.blockY},${spawnerLocation.blockZ} — wave will undercount"
                )
                return true  // we removed the original; nothing to record
            }
            plugin.spawnerWaveManager.recordMobSpawn(spawnerLocation, custom, isOminous)
            originalEntity.location.getNearbyPlayers(detectionRadius.toDouble()).forEach { p ->
                plugin.spawnerWaveManager.addPlayerToWave(p, spawnerLocation)
            }
            plugin.server.pluginManager.callEvent(
                io.github.darkstarworks.trialChamberPro.api.events.ChamberMobSpawnedEvent(
                    entity = custom,
                    spawnerLocation = spawnerLocation,
                    chamber = null,
                    isOminous = isOminous,
                    providerId = provider.id
                )
            )
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.info(
                    "[WildSpawnerResolver] Replaced vanilla spawn with ${provider.id}:$mobId at " +
                        "${spawnerLocation.blockX},${spawnerLocation.blockY},${spawnerLocation.blockZ}" +
                        if (presetId != null) " (preset=$presetId)" else ""
                )
            }
            true
        } catch (e: Throwable) {
            plugin.logger.warning(
                "[WildSpawnerResolver] Replacement failed (${provider.id}:$mobId): ${e.message} — falling back to vanilla"
            )
            false  // let the caller record the original entity (already removed though, so undercount)
        }
    }

    private fun readPresetIdTag(block: org.bukkit.block.Block): String? {
        val state = block.state as? org.bukkit.block.TileState ?: return null
        val key = org.bukkit.NamespacedKey(
            plugin,
            io.github.darkstarworks.trialChamberPro.managers.SpawnerPresetManager.PRESET_ID_KEY_NAME
        )
        return state.persistentDataContainer.get(key, org.bukkit.persistence.PersistentDataType.STRING)
    }

    /**
     * Configures cooldown for wild spawners (outside registered chambers).
     * Only modifies cooldown if reset.wild-spawner-cooldown-minutes is configured (not -1).
     * Called once per wave start (checks if wave already exists).
     */
    private fun configureWildSpawnerCooldown(block: org.bukkit.block.Block, isOminous: Boolean) {
        val wildCooldownMinutes = plugin.config.getInt("reset.wild-spawner-cooldown-minutes", -1)
        if (wildCooldownMinutes == -1) return // Use vanilla default

        // Check if we've already configured this spawner in this wave
        // Only configure once per wave start to avoid repeated state.update() calls
        val existingWave = plugin.spawnerWaveManager.getWaveAt(block.location)
        if (existingWave != null) {
            return // Already tracking this spawner, cooldown already configured
        }

        try {
            val state = block.state
            if (state is org.bukkit.block.TrialSpawner) {
                val cooldownTicks = if (wildCooldownMinutes == 0) {
                    1 // Minimum 1 tick to avoid potential issues
                } else {
                    wildCooldownMinutes * 60 * 20 // Convert minutes to ticks
                }

                state.cooldownLength = cooldownTicks
                state.update()

                if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                    val typeStr = if (isOminous) "ominous" else "normal"
                    plugin.logger.info("[WildSpawner] Configured $typeStr spawner at " +
                        "${block.location.blockX},${block.location.blockY},${block.location.blockZ} " +
                        "with cooldown: ${wildCooldownMinutes}min (${cooldownTicks} ticks)")
                }
            }
        } catch (e: Exception) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.warning("[WildSpawner] Failed to configure cooldown: ${e.message}")
            }
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
     * Works for both registered chambers and wild spawners.
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

        // Find nearby trial spawners and add player to their waves
        // Works for both chamber spawners and wild spawners
        findNearbyTrialSpawners(location, detectionRadius).forEach { spawnerLocation ->
            plugin.spawnerWaveManager.addPlayerToWave(player, spawnerLocation)
        }

        // Drop any boss bars for spawners the player has walked away from.
        plugin.spawnerWaveManager.removePlayerFromDistantWaves(player, removeDistance)
    }

    /**
     * Tears down any active wave when its trial spawner is broken — otherwise the boss bar
     * lingers (its tracked mobs may still be alive elsewhere, so it never satisfies the
     * normal completion conditions).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (!plugin.isReady) return
        if (event.block.type != Material.TRIAL_SPAWNER) return
        plugin.spawnerWaveManager.cancelWaveAt(event.block.location)
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
     * Uses Paper's native TrialSpawner.isOminous property.
     */
    private fun isTrialSpawnerOminous(block: org.bukkit.block.Block): Boolean {
        if (block.type != Material.TRIAL_SPAWNER) return false

        return try {
            val state = block.state
            if (state is org.bukkit.block.TrialSpawner) {
                state.isOminous
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
