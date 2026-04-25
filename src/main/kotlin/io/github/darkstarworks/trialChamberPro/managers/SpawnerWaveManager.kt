package io.github.darkstarworks.trialChamberPro.managers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.Material
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages Trial Spawner wave tracking with boss bar display.
 *
 * Tracks:
 * - Active waves per spawner location
 * - Mob spawn/death counts
 * - Participating players
 * - Boss bar progress display
 *
 * Features:
 * - Boss bar shows wave progress (mobs killed / total mobs)
 * - Ominous spawners show purple boss bar, normal shows yellow
 * - Wave completion triggers bonus statistics
 */
class SpawnerWaveManager(private val plugin: TrialChamberPro) {

    /**
     * Represents the state of an active trial spawner wave.
     * Uses AtomicInteger for thread-safe counter access from multiple event handlers.
     */
    data class WaveState(
        val spawnerId: String,
        val location: Location,
        val isOminous: Boolean,
        val startTime: Long = System.currentTimeMillis(),
        val totalMobsExpected: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(0),
        val mobsSpawned: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(0),
        val mobsKilled: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(0),
        val trackedMobs: MutableSet<UUID> = ConcurrentHashMap.newKeySet(),
        val participatingPlayers: MutableSet<UUID> = ConcurrentHashMap.newKeySet(),
        var bossBar: BossBar? = null,
        var waveNumber: Int = 1,
        @Volatile var completed: Boolean = false,
        @Volatile var glowEntityId: UUID? = null
    ) {
        fun getProgress(): Float {
            val expected = totalMobsExpected.get()
            if (expected <= 0) return 0f
            return (mobsKilled.get().toFloat() / expected.toFloat()).coerceIn(0f, 1f)
        }

        fun isAllMobsKilled(): Boolean = mobsKilled.get() >= totalMobsExpected.get() && totalMobsExpected.get() > 0
    }

    // Active wave states keyed by spawner location string
    private val activeWaves = ConcurrentHashMap<String, WaveState>()

    // Map mob UUIDs to their spawner location for death tracking
    private val mobToSpawner = ConcurrentHashMap<UUID, String>()

    // Player to spawner mapping for boss bar cleanup
    private val playerBossBars = ConcurrentHashMap<UUID, MutableSet<String>>()

    /**
     * Gets spawner key from location.
     */
    private fun getSpawnerKey(location: Location): String {
        return "${location.world?.name}:${location.blockX}:${location.blockY}:${location.blockZ}"
    }

    /**
     * Starts or updates a wave for a trial spawner.
     */
    fun startWave(spawnerLocation: Location, isOminous: Boolean, expectedMobs: Int): WaveState {
        val key = getSpawnerKey(spawnerLocation)

        val existingWave = activeWaves[key]
        if (existingWave != null) {
            if (!existingWave.completed) {
                // Update existing active wave with new expected mob count (atomic max)
                existingWave.totalMobsExpected.updateAndGet { current -> maxOf(current, expectedMobs) }
                return existingWave
            } else {
                // Wave is completed but still in cleanup delay - remove old boss bar immediately
                // to prevent duplicate bars when starting a new wave
                removeBossBar(existingWave)
                activeWaves.remove(key)
            }
        }

        // Create new wave state
        val wave = WaveState(
            spawnerId = key,
            location = spawnerLocation.clone(),
            isOminous = isOminous
        ).apply {
            totalMobsExpected.set(expectedMobs)
        }

        // Create boss bar
        if (plugin.config.getBoolean("spawner-waves.show-boss-bar", true)) {
            wave.bossBar = createBossBar(wave)
        }

        activeWaves[key] = wave

        // Spawn glow outline on active spawner (v1.2.27)
        spawnGlowDisplay(wave)

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[SpawnerWave] Started wave at $key: expected $expectedMobs mobs, ominous=$isOminous")
        }

        return wave
    }

    /**
     * Records a mob spawn from a trial spawner.
     */
    fun recordMobSpawn(spawnerLocation: Location, mob: Entity, isOminous: Boolean) {
        val key = getSpawnerKey(spawnerLocation)
        val mobUUID = mob.uniqueId

        // Get or create wave
        var wave = activeWaves[key]
        if (wave == null || wave.completed) {
            // Start a new wave with estimated mob count (will be updated)
            wave = startWave(spawnerLocation, isOminous, 6) // Default estimate
        }

        // Track the mob
        wave.trackedMobs.add(mobUUID)
        val spawned = wave.mobsSpawned.incrementAndGet()
        mobToSpawner[mobUUID] = key

        // Update expected if we're spawning more than expected (atomic update)
        wave.totalMobsExpected.updateAndGet { current -> maxOf(current, spawned) }

        // Update boss bar
        updateBossBar(wave)

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[SpawnerWave] Mob spawned at $key: ${mob.type}, total=${wave.mobsSpawned.get()}/${wave.totalMobsExpected.get()}")
        }
    }

    /**
     * Records a mob death and updates wave progress.
     */
    fun recordMobDeath(mob: Entity, killer: Player?): Boolean {
        val mobUUID = mob.uniqueId
        val key = mobToSpawner.remove(mobUUID) ?: return false

        val wave = activeWaves[key] ?: return false
        if (!wave.trackedMobs.remove(mobUUID)) return false

        wave.mobsKilled.incrementAndGet()

        // Track killer as participant
        if (killer != null) {
            wave.participatingPlayers.add(killer.uniqueId)
            addPlayerToBossBar(killer, wave)
        }

        // Update boss bar
        updateBossBar(wave)

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[SpawnerWave] Mob killed at $key: ${wave.mobsKilled.get()}/${wave.totalMobsExpected.get()}")
        }

        // Check if wave is complete
        if (wave.isAllMobsKilled() && wave.trackedMobs.isEmpty()) {
            completeWave(wave)
            return true
        }

        return true
    }

    /**
     * Adds a player to the wave tracking (e.g., when they enter spawner range).
     */
    fun addPlayerToWave(player: Player, spawnerLocation: Location) {
        val key = getSpawnerKey(spawnerLocation)
        val wave = activeWaves[key] ?: return

        wave.participatingPlayers.add(player.uniqueId)
        addPlayerToBossBar(player, wave)
    }

    /**
     * Removes a player from all wave tracking (e.g., on disconnect).
     */
    fun removePlayer(player: Player) {
        val playerUUID = player.uniqueId
        val spawnerKeys = playerBossBars.remove(playerUUID) ?: return

        spawnerKeys.forEach { key ->
            val wave = activeWaves[key] ?: return@forEach
            wave.participatingPlayers.remove(playerUUID)
            wave.bossBar?.let { bar ->
                bar.removeViewer(player)
            }
        }
    }

    /**
     * Removes the player from any wave whose spawner is farther than [removeDistance] blocks.
     * Prevents the boss bar from lingering after a player leaves the chamber area.
     */
    fun removePlayerFromDistantWaves(player: Player, removeDistance: Double) {
        val playerUUID = player.uniqueId
        val tracked = playerBossBars[playerUUID] ?: return
        if (tracked.isEmpty()) return

        val playerLoc = player.location
        val removeDistSq = removeDistance * removeDistance

        // Iterate a snapshot to avoid CME
        tracked.toList().forEach { key ->
            val wave = activeWaves[key] ?: run {
                tracked.remove(key)
                return@forEach
            }
            val waveLoc = wave.location
            // Ignore cross-world cases (also stale)
            if (waveLoc.world != playerLoc.world ||
                playerLoc.distanceSquared(waveLoc) > removeDistSq
            ) {
                wave.bossBar?.removeViewer(player)
                wave.participatingPlayers.remove(playerUUID)
                tracked.remove(key)
            }
        }

        if (tracked.isEmpty()) {
            playerBossBars.remove(playerUUID)
        }
    }

    /**
     * Force-clears all active waves inside a chamber. Called on chamber reset so boss bars
     * don't linger after blocks/entities have been wiped.
     */
    fun clearWavesInChamber(chamber: io.github.darkstarworks.trialChamberPro.models.Chamber) {
        val toRemove = activeWaves.entries.filter { chamber.contains(it.value.location) }
        toRemove.forEach { (key, wave) ->
            removeBossBar(wave)
            // Drop any tracked mob UUIDs pointing at this wave
            mobToSpawner.entries.removeIf { it.value == key }
            activeWaves.remove(key)
        }
    }

    /**
     * Gets the active wave at a spawner location.
     */
    fun getWaveAt(spawnerLocation: Location): WaveState? {
        val key = getSpawnerKey(spawnerLocation)
        return activeWaves[key]?.takeIf { !it.completed }
    }

    /**
     * Marks a spawner wave as complete (e.g., when spawner enters cooldown).
     */
    fun completeWave(spawnerLocation: Location) {
        val key = getSpawnerKey(spawnerLocation)
        val wave = activeWaves[key] ?: return
        completeWave(wave)
    }

    /**
     * Completes a wave and gives rewards.
     */
    private fun completeWave(wave: WaveState) {
        if (wave.completed) return
        wave.completed = true

        // Remove glow immediately on completion (don't wait for boss bar's 3s delay)
        removeGlowDisplay(wave)

        val durationMs = System.currentTimeMillis() - wave.startTime
        val durationSeconds = durationMs / 1000

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[SpawnerWave] Wave complete at ${wave.spawnerId}: " +
                "killed ${wave.mobsKilled.get()}, participants=${wave.participatingPlayers.size}, " +
                "duration=${durationSeconds}s")
        }

        // Configure cooldown for wild spawners (must be done BEFORE spawner enters cooldown state)
        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[SpawnerWave] Calling configureWildSpawnerCooldownAtCompletion for ${wave.spawnerId}")
        }
        configureWildSpawnerCooldownAtCompletion(wave)

        // v1.3.0: Plugin-driven key drops for non-vanilla providers.
        // Vanilla trial spawners drop their own keys via the spawner state machine; when we've
        // replaced the spawns with provider mobs the spawner still enters cooldown but will not
        // eject keys (it's tracking UUIDs that no longer exist). We compensate here.
        //
        // wave.isOminous is captured at wave creation (WaveState ctor) — it cannot flip mid-wave,
        // so it is safe to use here as the "ominous-at-start" snapshot.
        maybeDropProviderKeys(wave)

        // Award bonus stats to participants
        if (plugin.config.getBoolean("spawner-waves.award-stats", true)) {
            wave.participatingPlayers.forEach { playerUUID ->
                plugin.launchAsync {
                    // Track mobs killed stat (already tracked per kill in StatisticsManager)
                    // Could add wave completion bonus here if desired
                }
            }
        }

        // Send completion message to participants
        if (plugin.config.getBoolean("spawner-waves.completion-message", true)) {
            val typeStr = if (wave.isOminous) "Ominous" else "Trial"
            val message = plugin.getMessage("spawner-wave-complete",
                "type" to typeStr,
                "killed" to wave.mobsKilled.get(),
                "duration" to formatDuration(durationSeconds)
            )

            wave.participatingPlayers.forEach { playerUUID ->
                plugin.server.getPlayer(playerUUID)?.sendMessage(message)
            }
        }

        // Update boss bar to show completion then remove
        wave.bossBar?.let { bar ->
            bar.name(getMessageComponent("spawner-wave-boss-bar-complete"))
            bar.progress(1.0f)
            bar.color(BossBar.Color.GREEN)

            // Remove after a short delay (must be sync - removeBossBar accesses Bukkit API)
            plugin.scheduler.runTaskLater(Runnable {
                removeBossBar(wave)
                // Clean up wave state after delay
                activeWaves.remove(wave.spawnerId)
            }, 60L) // 3 seconds
        } ?: run {
            // No boss bar, clean up immediately
            activeWaves.remove(wave.spawnerId)
        }

        // Fire post-event for downstream consumers (stat plugins, custom rewards, etc).
        // Resolved chamber may be null for wild spawners — listeners must tolerate that.
        plugin.server.pluginManager.callEvent(
            io.github.darkstarworks.trialChamberPro.api.events.SpawnerWaveCompleteEvent(
                spawnerLocation = wave.location,
                chamber = plugin.chamberManager.getCachedChamberAt(wave.location),
                ominous = wave.isOminous,
                participants = wave.participatingPlayers.toSet(),
                durationMs = durationMs
            )
        )
    }

    /**
     * Drops Trial Keys / Ominous Trial Keys for participants when a wave was driven by a
     * non-vanilla [io.github.darkstarworks.trialChamberPro.providers.TrialMobProvider].
     *
     * Vanilla trial spawners eject keys through their own state machine; when we substituted
     * custom mobs, the spawner enters cooldown but can't find its tracked entities and won't
     * drop anything. This method mirrors vanilla behavior: one key per unique participating
     * player, dropped above the spawner block with a small upward velocity, tagged with the
     * participant's UUID + timestamp so [io.github.darkstarworks.trialChamberPro.listeners.SpawnerKeyDropOwnerListener]
     * can enforce owner-only pickup during the grace window.
     *
     * No-op for vanilla-driven waves. No-op for wild spawners not in a registered chamber —
     * we can't determine the configured provider there and vanilla still handles them correctly.
     */
    private fun maybeDropProviderKeys(wave: WaveState) {
        val chamber = plugin.chamberManager.getCachedChamberAt(wave.location) ?: return
        if (!chamber.hasCustomMobProvider(wave.isOminous)) return
        if (wave.participatingPlayers.isEmpty()) return

        val keyMaterial = if (wave.isOminous) Material.OMINOUS_TRIAL_KEY else Material.TRIAL_KEY
        val dropLoc = wave.location.clone().add(0.5, 1.2, 0.5)
        val participants = wave.participatingPlayers.toList() // snapshot
        val now = System.currentTimeMillis()

        plugin.scheduler.runAtLocation(wave.location, Runnable {
            val world = dropLoc.world ?: return@Runnable
            try {
                participants.forEach { uuid ->
                    // Fire pre-drop event; listeners may suppress an individual key.
                    val dropEvent = io.github.darkstarworks.trialChamberPro.api.events.TrialKeyDropEvent(
                        location = dropLoc,
                        keyType = keyMaterial,
                        ownerUuid = uuid
                    )
                    plugin.server.pluginManager.callEvent(dropEvent)
                    if (dropEvent.isCancelled) return@forEach

                    val stack = org.bukkit.inventory.ItemStack(keyMaterial, 1)
                    val itemEntity = world.dropItem(dropLoc, stack)
                    // Small upward pop to approximate vanilla vault/spawner ejection
                    itemEntity.velocity = org.bukkit.util.Vector(
                        (Math.random() - 0.5) * 0.15,
                        0.3,
                        (Math.random() - 0.5) * 0.15
                    )
                    // Tag for owner-only pickup enforcement
                    itemEntity.persistentDataContainer.set(
                        io.github.darkstarworks.trialChamberPro.listeners.SpawnerKeyDropOwnerListener.OWNER_KEY,
                        org.bukkit.persistence.PersistentDataType.STRING,
                        uuid.toString()
                    )
                    itemEntity.persistentDataContainer.set(
                        io.github.darkstarworks.trialChamberPro.listeners.SpawnerKeyDropOwnerListener.DROPPED_AT_KEY,
                        org.bukkit.persistence.PersistentDataType.LONG,
                        now
                    )
                    // Pickup-hint visual
                    try { itemEntity.owner = uuid } catch (_: Throwable) { /* owner setter unavailable on some forks */ }
                }

                if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                    plugin.logger.info("[SpawnerWave] Dropped ${participants.size} ${keyMaterial.name} for provider-driven wave at ${wave.spawnerId}")
                }
            } catch (e: Exception) {
                plugin.logger.warning("[SpawnerWave] Failed to drop provider keys: ${e.message}")
            }
        })
    }

    /**
     * Configures cooldown for wild spawners at wave completion.
     * This is the critical timing - must be set BEFORE the spawner transitions to cooldown state.
     * For registered chambers, cooldown is handled by ResetManager during chamber reset.
     *
     * When cooldown is 0, also clears tracked players so the spawner can reactivate for them.
     */
    private fun configureWildSpawnerCooldownAtCompletion(wave: WaveState) {
        val wildCooldownMinutes = plugin.config.getInt("reset.wild-spawner-cooldown-minutes", -1)

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[WildSpawner] Wave complete - checking cooldown config: wild-spawner-cooldown-minutes=$wildCooldownMinutes")
        }

        if (wildCooldownMinutes == -1) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.info("[WildSpawner] Skipping - using vanilla default (config is -1)")
            }
            return
        }

        // Check if this spawner is in a registered chamber
        val chamber = plugin.chamberManager.getCachedChamberAt(wave.location)
        if (chamber != null) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.info("[WildSpawner] Skipping - spawner is inside registered chamber '${chamber.name}'")
            }
            return
        }

        try {
            val world = wave.location.world ?: return
            val block = world.getBlockAt(wave.location)

            if (block.type != Material.TRIAL_SPAWNER) {
                if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                    plugin.logger.warning("[WildSpawner] Block at ${wave.location} is not a trial spawner")
                }
                return
            }

            val state = block.state
            if (state is org.bukkit.block.TrialSpawner) {
                val cooldownTicks = if (wildCooldownMinutes == 0) {
                    1 // Minimum 1 tick to avoid potential issues
                } else {
                    wildCooldownMinutes * 60 * 20 // Convert minutes to ticks
                }

                state.cooldownLength = cooldownTicks

                // For instant reactivation (cooldown 0), also clear tracked players
                // so the spawner will activate again for the same players
                if (wildCooldownMinutes == 0) {
                    val trackedPlayers = state.trackedPlayers.toList() // Copy to avoid CME
                    trackedPlayers.forEach { player ->
                        state.stopTrackingPlayer(player)
                    }
                    // Also clear tracked entities (spawned mobs)
                    val trackedEntities = state.trackedEntities.toList()
                    trackedEntities.forEach { entity ->
                        state.stopTrackingEntity(entity)
                    }

                    if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                        plugin.logger.info("[WildSpawner] Cleared ${trackedPlayers.size} tracked players " +
                            "and ${trackedEntities.size} tracked entities for instant reactivation")
                    }

                    // Schedule a delayed task to force-reset spawner state after key ejection
                    // This handles copied spawners that have old cooldown values baked in
                    val spawnerLocation = wave.location.clone()
                    plugin.scheduler.runAtLocationLater(spawnerLocation, Runnable {
                        forceResetSpawnerState(spawnerLocation)
                    }, 40L) // 2 seconds - enough time for key ejection
                }

                state.update()

                if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                    val typeStr = if (wave.isOminous) "ominous" else "normal"
                    plugin.logger.info("[WildSpawner] Set $typeStr spawner cooldown at wave completion: " +
                        "${wave.location.blockX},${wave.location.blockY},${wave.location.blockZ} " +
                        "cooldown=${wildCooldownMinutes}min (${cooldownTicks} ticks)")
                }
            }
        } catch (e: Exception) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.warning("[WildSpawner] Failed to configure cooldown at completion: ${e.message}")
            }
        }
    }

    /**
     * Forces a trial spawner to reset from cooldown state to waiting_for_players.
     * Used for instant reactivation when wild-spawner-cooldown-minutes is 0.
     * This handles copied spawners that have old cooldown values baked into their NBT.
     */
    private fun forceResetSpawnerState(location: Location) {
        try {
            val world = location.world ?: return
            val block = world.getBlockAt(location)

            if (block.type != Material.TRIAL_SPAWNER) return

            val blockData = block.blockData
            val blockDataString = blockData.asString

            // Check if spawner is in cooldown state
            if (!blockDataString.contains("trial_spawner_state=cooldown")) {
                if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                    plugin.logger.info("[WildSpawner] Spawner not in cooldown state, no force-reset needed")
                }
                return
            }

            // Replace cooldown state with waiting_for_players
            val newBlockDataString = blockDataString.replace(
                "trial_spawner_state=cooldown",
                "trial_spawner_state=waiting_for_players"
            )

            // Create new block data from the modified string
            val newBlockData = plugin.server.createBlockData(newBlockDataString)
            block.setBlockData(newBlockData, false)

            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.info("[WildSpawner] Force-reset spawner state to waiting_for_players at " +
                    "${location.blockX},${location.blockY},${location.blockZ}")
            }
        } catch (e: Exception) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.warning("[WildSpawner] Failed to force-reset spawner state: ${e.message}")
            }
        }
    }

    /**
     * Creates a boss bar for a wave.
     */
    private fun createBossBar(wave: WaveState): BossBar {
        val color = if (wave.isOminous) BossBar.Color.PURPLE else BossBar.Color.YELLOW
        val messageKey = if (wave.isOminous) "spawner-wave-boss-bar-ominous" else "spawner-wave-boss-bar-normal"
        val title = getMessageComponent(messageKey, "wave" to wave.waveNumber)

        return BossBar.bossBar(
            title,
            0f,
            color,
            BossBar.Overlay.PROGRESS
        )
    }

    /**
     * Updates the boss bar display.
     */
    private fun updateBossBar(wave: WaveState) {
        val bar = wave.bossBar ?: return

        val progress = wave.getProgress()
        bar.progress(progress)

        // Update title with kill count
        val typeStr = if (wave.isOminous) "Ominous Trial" else "Trial Spawner"
        bar.name(getMessageComponent("spawner-wave-boss-bar-progress",
            "type" to typeStr,
            "killed" to wave.mobsKilled.get(),
            "total" to wave.totalMobsExpected.get()
        ))
    }

    /**
     * Adds a player as a boss bar viewer.
     */
    private fun addPlayerToBossBar(player: Player, wave: WaveState) {
        val bar = wave.bossBar ?: return

        // Check if already tracked to avoid redundant operations
        val playerSpawners = playerBossBars.computeIfAbsent(player.uniqueId) { ConcurrentHashMap.newKeySet() }
        if (playerSpawners.contains(wave.spawnerId)) {
            return // Already viewing this bar
        }

        bar.addViewer(player)
        playerSpawners.add(wave.spawnerId)
    }

    /**
     * Removes the boss bar from all viewers.
     */
    private fun removeBossBar(wave: WaveState) {
        // Always attempt glow cleanup, even if there's no boss bar to remove
        removeGlowDisplay(wave)

        val bar = wave.bossBar ?: return

        // Remove from all players
        wave.participatingPlayers.forEach { playerUUID ->
            plugin.server.getPlayer(playerUUID)?.let { player ->
                bar.removeViewer(player)
            }
            playerBossBars[playerUUID]?.remove(wave.spawnerId)
        }

        wave.bossBar = null
    }

    /**
     * Spawns an invisible Interaction entity at the spawner with the GLOWING effect applied,
     * producing a colored outline on the spawner block that is visible through walls.
     * Opt-in via `spawner-waves.glow-active-spawners`. Colors configurable per-type.
     */
    private fun spawnGlowDisplay(wave: WaveState) {
        if (!plugin.config.getBoolean("spawner-waves.glow-active-spawners", false)) return
        val world = wave.location.world ?: return
        // Center the interaction box on the spawner block
        val center = wave.location.clone().add(0.5, 0.5, 0.5)

        plugin.scheduler.runAtLocation(wave.location, Runnable {
            try {
                val colorHex = if (wave.isOminous) {
                    plugin.config.getString("spawner-waves.glow-color-ominous", "#A020F0") ?: "#A020F0"
                } else {
                    plugin.config.getString("spawner-waves.glow-color-normal", "#FFFF55") ?: "#FFFF55"
                }
                val color = parseGlowColor(colorHex)

                val entity = world.spawn(center, org.bukkit.entity.Interaction::class.java) { e ->
                    // Interaction entities are invisible; hitbox sized to wrap the 1x1x1 spawner
                    e.interactionWidth = 1.2f
                    e.interactionHeight = 1.2f
                    e.isResponsive = false
                    e.isPersistent = false
                    e.isGlowing = true
                    if (color != null) {
                        try {
                            // Invoke reflectively so we don't hard-bind to a specific Paper API revision
                            e.javaClass.getMethod("setGlowColorOverride", org.bukkit.Color::class.java)
                                .invoke(e, color)
                        } catch (_: Throwable) {
                            // Some server forks or older API revisions may not support this; fall back
                            // to the default team-less glow color (white)
                        }
                    }
                }
                wave.glowEntityId = entity.uniqueId

                if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                    plugin.logger.info("[SpawnerWave] Spawned glow entity ${entity.uniqueId} at ${wave.spawnerId}")
                }
            } catch (e: Exception) {
                plugin.logger.warning("[SpawnerWave] Failed to spawn glow entity: ${e.message}")
            }
        })
    }

    /**
     * Removes the glow Interaction entity previously spawned for this wave, if any.
     * Safe to call multiple times — no-op once the entity id is cleared.
     */
    private fun removeGlowDisplay(wave: WaveState) {
        val id = wave.glowEntityId ?: return
        wave.glowEntityId = null
        val world = wave.location.world ?: return
        plugin.scheduler.runAtLocation(wave.location, Runnable {
            try {
                world.getEntity(id)?.remove()
            } catch (_: Throwable) {
                // Entity may already be unloaded/removed; harmless
            }
        })
    }

    /**
     * Parses a hex color string (e.g., "#FFFF55" or "FFFF55") into a Bukkit Color.
     * Returns null on parse failure so the caller can fall back to the default outline color.
     */
    private fun parseGlowColor(hex: String): org.bukkit.Color? {
        return try {
            val clean = hex.trim().removePrefix("#")
            org.bukkit.Color.fromRGB(clean.toInt(16))
        } catch (_: Exception) {
            plugin.logger.warning("[SpawnerWave] Invalid glow color '$hex' — expected hex like #FFFF55")
            null
        }
    }

    /**
     * Formats duration in seconds to a readable string.
     */
    private fun formatDuration(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        }
    }

    /**
     * Converts a message key with placeholders to a Component for boss bars.
     * Uses the messages.yml translations without the plugin prefix.
     */
    private fun getMessageComponent(key: String, vararg replacements: Pair<String, Any?>): Component {
        val message = plugin.getMessage(key, *replacements)
        return LegacyComponentSerializer.legacySection().deserialize(message)
    }

    /**
     * Cleans up all active waves (called on disable).
     */
    fun shutdown() {
        activeWaves.values.forEach { wave ->
            removeBossBar(wave)
        }
        activeWaves.clear()
        mobToSpawner.clear()
        playerBossBars.clear()
    }

    /**
     * Gets statistics about active waves.
     */
    fun getActiveWaveCount(): Int = activeWaves.count { !it.value.completed }

    /**
     * Gets all active waves for a chamber.
     */
    fun getWavesInChamber(chamber: io.github.darkstarworks.trialChamberPro.models.Chamber): List<WaveState> {
        return activeWaves.values.filter { wave ->
            !wave.completed && chamber.contains(wave.location)
        }
    }
}
