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
        @Volatile var completed: Boolean = false
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

        val durationMs = System.currentTimeMillis() - wave.startTime
        val durationSeconds = durationMs / 1000

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[SpawnerWave] Wave complete at ${wave.spawnerId}: " +
                "killed ${wave.mobsKilled.get()}, participants=${wave.participatingPlayers.size}, " +
                "duration=${durationSeconds}s")
        }

        // Configure cooldown for wild spawners (must be done BEFORE spawner enters cooldown state)
        configureWildSpawnerCooldownAtCompletion(wave)

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
        if (wildCooldownMinutes == -1) return // Use vanilla default

        // Check if this spawner is in a registered chamber
        val chamber = plugin.chamberManager.getCachedChamberAt(wave.location)
        if (chamber != null) {
            // Registered chamber - cooldown handled by ResetManager
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
