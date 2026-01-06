package io.github.darkstarworks.trialChamberPro.managers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.Chamber
import io.github.darkstarworks.trialChamberPro.utils.BlockRestorer
import io.github.darkstarworks.trialChamberPro.utils.MessageUtil
import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Item
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages automatic chamber resets with warnings, player teleportation, and snapshot restoration.
 */
class ResetManager(private val plugin: TrialChamberPro) {

    private val resetScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val scheduledResets = ConcurrentHashMap<Int, Job>()
    private val warningJobs = ConcurrentHashMap<Int, MutableList<Job>>()

    /**
     * Starts monitoring and scheduling resets for all chambers.
     */
    fun startResetScheduler() {
        resetScope.launch {
            while (isActive) {
                try {
                    val chambers = plugin.chamberManager.getAllChambers()
                    chambers.forEach { chamber ->
                        scheduleResetIfNeeded(chamber)
                    }
                } catch (e: Exception) {
                    plugin.logger.severe("Error in reset scheduler: ${e.message}")
                }

                // Check every minute
                delay(60000)
            }
        }

        plugin.logger.info("Reset scheduler started")
    }

    /**
     * Schedules a reset for a chamber if it's due.
     * If resetInterval is <= 0, automatic resets are disabled for this chamber.
     */
    private suspend fun scheduleResetIfNeeded(chamber: Chamber) {
        // Skip if already scheduled
        if (scheduledResets.containsKey(chamber.id)) return

        // Skip if automatic resets are disabled (resetInterval <= 0)
        if (chamber.resetInterval <= 0) {
            plugin.logger.fine("Automatic resets disabled for chamber ${chamber.name} (interval: ${chamber.resetInterval})")
            return
        }

        val lastReset = chamber.lastReset ?: chamber.createdAt
        val resetIntervalMs = chamber.resetInterval * 1000
        val nextResetTime = lastReset + resetIntervalMs
        val now = System.currentTimeMillis()

        if (now >= nextResetTime) {
            // Reset immediately
            resetChamber(chamber)
        } else {
            // Schedule future reset
            val delayMs = nextResetTime - now
            val resetJob = resetScope.launch {
                // Schedule warnings
                scheduleWarnings(chamber, delayMs)

                delay(delayMs)
                resetChamber(chamber)
            }

            scheduledResets[chamber.id] = resetJob
        }
    }

    /**
     * Schedules warning messages before reset.
     */
    private fun scheduleWarnings(chamber: Chamber, delayMs: Long) {
        val warningTimes = plugin.config.getIntegerList("global.reset-warning-times")
            .map { it * 1000L } // Convert to milliseconds

        val warnings = mutableListOf<Job>()

        warningTimes.forEach { warningTimeMs ->
            if (warningTimeMs < delayMs) {
                val warningDelay = delayMs - warningTimeMs
                val warningJob = resetScope.launch {
                    delay(warningDelay)
                    sendResetWarning(chamber, warningTimeMs / 1000)
                }
                warnings.add(warningJob)
            }
        }

        warningJobs[chamber.id] = warnings
    }

    /**
     * Sends a reset warning to all players in the chamber.
     */
    private fun sendResetWarning(chamber: Chamber, secondsRemaining: Long) {
        val timeString = MessageUtil.formatTimeSeconds(secondsRemaining)
        val message = plugin.getMessage("chamber-reset-warning",
            "chamber" to chamber.name,
            "time" to timeString
        )

        chamber.getPlayersInside().forEach { player ->
            player.sendMessage(message)
        }
    }

    /**
     * Resets a chamber completely.
     *
     * @param chamber The chamber to reset
     * @param initiatingPlayer Optional player who initiated the reset (for WorldEdit undo support)
     */
    suspend fun resetChamber(chamber: Chamber, initiatingPlayer: Player? = null): Boolean = withContext(Dispatchers.IO) {
        plugin.logger.info("Resetting chamber: ${chamber.name}")

        try {
            // Cancel any scheduled resets/warnings
            scheduledResets.remove(chamber.id)?.cancel()
            warningJobs.remove(chamber.id)?.forEach { it.cancel() }

            // Step 1: Teleport players out
            if (plugin.config.getBoolean("global.teleport-players-on-reset", true)) {
                teleportPlayersOut(chamber)
            }

            // Step 2: Clear entities
            clearEntities(chamber)

            // Step 3: Restore from snapshot
            val snapshotFile = chamber.getSnapshotFile()
            if (snapshotFile != null && snapshotFile.exists()) {
                restoreFromSnapshot(chamber, snapshotFile, initiatingPlayer)

                // Small delay to ensure all scheduled block restoration tasks complete
                // before we modify spawner state. This is important because restoreBlocks()
                // schedules tasks asynchronously and returns before they complete.
                delay(200)
            } else {
                plugin.logger.warning("No snapshot found for chamber ${chamber.name}, skipping block restoration")
            }

            // Step 4: Reset trial spawners (clear tracked players so they drop keys again)
            // This runs AFTER block restoration completes to apply config-based cooldown
            if (plugin.config.getBoolean("reset.reset-trial-spawners", true)) {
                resetTrialSpawners(chamber)
            }

            // Step 5: Update last reset time
            val now = System.currentTimeMillis()
            plugin.chamberManager.updateLastReset(chamber.id, now)

            // Step 6: Optionally reset vault cooldowns (defaults to true for vanilla behavior)
            if (plugin.config.getBoolean("reset.reset-vault-cooldowns", true)) {
                val vaults = plugin.vaultManager.getVaultsForChamber(chamber.id)
                vaults.forEach { vault ->
                    plugin.vaultManager.resetAllCooldowns(vault.id)
                }
            }

            // Send completion message
            plugin.scheduler.runTask(Runnable {
                val message = plugin.getMessage("chamber-reset-complete")
                Bukkit.getOnlinePlayers().forEach { it.sendMessage(message) }
            })

            plugin.logger.info("Chamber ${chamber.name} reset successfully")
            true

        } catch (e: Exception) {
            plugin.logger.severe("Failed to reset chamber ${chamber.name}: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Teleports all players out of the chamber.
     * Folia compatible: Uses entity-based scheduling for player teleportation.
     * CRITICAL FIX: Added timeout to prevent hanging coroutines.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun teleportPlayersOut(chamber: Chamber) {
        withTimeout(5000) {  // 5 second timeout
            // Get players list on global/main thread first
            suspendCancellableCoroutine<Unit> { continuation ->
                plugin.scheduler.runTask(Runnable {
                    try {
                        val players = chamber.getPlayersInside()
                        if (players.isEmpty()) {
                            continuation.resume(Unit) {}
                            return@Runnable
                        }

                        val teleportMode = plugin.config.getString("global.teleport-location", "EXIT_POINT")
                        var remaining = players.size

                        // Teleport each player on their own region thread (Folia compatible)
                        players.forEach { player ->
                            plugin.scheduler.runAtEntity(player, Runnable {
                                val destination = when (teleportMode) {
                                    "EXIT_POINT" -> chamber.getExitLocation() ?: getOutsideLocation(chamber, player.location)
                                    "OUTSIDE_BOUNDS" -> getOutsideLocation(chamber, player.location)
                                    "WORLD_SPAWN" -> player.world.spawnLocation
                                    else -> chamber.getExitLocation() ?: player.world.spawnLocation
                                }

                                player.teleport(destination)
                                player.sendMessage(plugin.getMessage("teleported-to-exit", "chamber" to chamber.name))

                                // Track completion (thread-safe decrement)
                                synchronized(this) {
                                    remaining--
                                    if (remaining == 0) {
                                        continuation.resume(Unit) {}
                                    }
                                }
                            }, Runnable {
                                // Player retired/removed - still count as done
                                synchronized(this) {
                                    remaining--
                                    if (remaining == 0) {
                                        continuation.resume(Unit) {}
                                    }
                                }
                            })
                        }
                    } catch (e: Exception) {
                        continuation.resumeWith(Result.failure(e))
                    }
                })
            }
        }
    }

    /**
     * Gets a safe location outside the chamber bounds.
     * Scans for a solid block and places player 2 blocks above it.
     */
    private fun getOutsideLocation(chamber: Chamber, currentLocation: Location): Location {
        val world = chamber.getWorld() ?: return currentLocation

        val centerX = (chamber.minX + chamber.maxX) / 2.0
        val centerZ = (chamber.minZ + chamber.maxZ) / 2.0

        // Start scanning from 5 blocks above chamber max
        val startY = chamber.maxY + 5

        // Scan downward from start position to find safe ground
        for (y in startY downTo world.minHeight) {
            val checkLoc = Location(world, centerX, y.toDouble(), centerZ)
            val block = checkLoc.block
            val blockType = block.type

            // Skip unsafe blocks
            val isUnsafe = blockType == Material.AIR ||
                    blockType == Material.WATER ||
                    blockType == Material.LAVA ||
                    blockType == Material.POINTED_DRIPSTONE ||
                    blockType == Material.MAGMA_BLOCK ||
                    blockType == Material.SAND ||
                    blockType == Material.GRAVEL ||
                    blockType == Material.CAVE_AIR ||
                    blockType == Material.VOID_AIR

            // Check if this is a safe block to stand on
            if (!isUnsafe && blockType.isSolid) {
                // Found safe ground! Return position 2 blocks above it
                return Location(world, centerX, y + 2.0, centerZ)
            }
        }

        // Fallback: if no safe block found, use world spawn
        plugin.logger.warning("No safe ground found outside chamber ${chamber.name}, using world spawn")
        return world.spawnLocation
    }

    /**
     * Clears entities from the chamber.
     * Folia compatible: Uses location-based scheduling for entity removal.
     * CRITICAL FIX: Added timeout to prevent hanging coroutines.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun clearEntities(chamber: Chamber) {
        withTimeout(5000) {  // 5 second timeout
            // Use a representative location in the chamber for region scheduling
            val chamberCenter = Location(
                chamber.getWorld(),
                (chamber.minX + chamber.maxX) / 2.0,
                (chamber.minY + chamber.maxY) / 2.0,
                (chamber.minZ + chamber.maxZ) / 2.0
            )

            suspendCancellableCoroutine<Unit> { continuation ->
                plugin.scheduler.runAtLocation(chamberCenter, Runnable {
                    try {
                        val entities = chamber.getEntitiesInside()

                        entities.forEach { entity ->
                            when {
                                entity is Item && plugin.config.getBoolean("reset.clear-ground-items", true) -> {
                                    entity.remove()
                                }
                                entity is LivingEntity && entity !is Player -> {
                                    val shouldRemove = when {
                                        plugin.config.getBoolean("reset.remove-spawner-mobs", true) -> {
                                            // Check if mob is from a spawner (simplified check)
                                            true
                                        }
                                        plugin.config.getBoolean("reset.remove-non-chamber-mobs", false) -> true
                                        else -> false
                                    }

                                    if (shouldRemove) {
                                        entity.remove()
                                    }
                                }
                            }
                        }
                        continuation.resume(Unit) {}
                    } catch (e: Exception) {
                        continuation.resumeWith(Result.failure(e))
                    }
                })
            }
        }
    }

    /**
     * Resets all trial spawners in a chamber.
     * CRITICAL: This clears tracked players so spawners can be reactivated
     * and will drop trial keys again (50% chance per player per vanilla behavior).
     *
     * Folia compatible: Uses location-based scheduling.
     *
     * @param chamber The chamber to reset spawners in
     * @param cooldownMinutesOverride Optional per-chamber cooldown override (null = use global config)
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun resetTrialSpawners(chamber: Chamber, cooldownMinutesOverride: Int? = null) {
        withTimeout(10000) {  // 10 second timeout for larger chambers
            val chamberCenter = Location(
                chamber.getWorld(),
                (chamber.minX + chamber.maxX) / 2.0,
                (chamber.minY + chamber.maxY) / 2.0,
                (chamber.minZ + chamber.maxZ) / 2.0
            )

            suspendCancellableCoroutine<Unit> { continuation ->
                plugin.scheduler.runAtLocation(chamberCenter, Runnable {
                    try {
                        val world = chamber.getWorld() ?: run {
                            continuation.resume(Unit) {}
                            return@Runnable
                        }

                        var resetCount = 0
                        val resetOminous = plugin.config.getBoolean("reset.reset-ominous-spawners", true)

                        // Get cooldown setting: per-chamber override > global config > vanilla default (-1)
                        val cooldownMinutes = cooldownMinutesOverride
                            ?: chamber.spawnerCooldownMinutes
                            ?: plugin.config.getInt("reset.spawner-cooldown-minutes", -1)

                        val verboseLogging = plugin.config.getBoolean("debug.verbose-logging", false)
                        if (verboseLogging) {
                            plugin.logger.info("[SpawnerReset] Chamber: ${chamber.name}, cooldownMinutes: $cooldownMinutes " +
                                "(override: $cooldownMinutesOverride, perChamber: ${chamber.spawnerCooldownMinutes}, " +
                                "config: ${plugin.config.getInt("reset.spawner-cooldown-minutes", -1)})")
                        }

                        // Scan chamber for trial spawners
                        for (x in chamber.minX..chamber.maxX) {
                            for (y in chamber.minY..chamber.maxY) {
                                for (z in chamber.minZ..chamber.maxZ) {
                                    val block = world.getBlockAt(x, y, z)
                                    if (block.type == Material.TRIAL_SPAWNER) {
                                        val state = block.state
                                        if (state is org.bukkit.block.TrialSpawner) {
                                            val oldCooldown = state.cooldownLength

                                            // Clear tracked players - KEY fix for trial key drops!
                                            state.trackedPlayers.forEach { player ->
                                                state.stopTrackingPlayer(player)
                                            }

                                            // Clear tracked entities (spawned mobs)
                                            state.trackedEntities.forEach { entity ->
                                                state.stopTrackingEntity(entity)
                                            }

                                            // Optionally reset ominous spawners back to normal
                                            if (resetOminous && state.isOminous) {
                                                state.isOminous = false
                                            }

                                            // Apply custom cooldown if configured
                                            // -1 = vanilla default (don't change)
                                            // 0 = no cooldown (immediate reactivation)
                                            // >0 = custom cooldown in minutes
                                            if (cooldownMinutes >= 0) {
                                                val cooldownTicks = cooldownMinutes * 60 * 20 // minutes to ticks
                                                state.cooldownLength = cooldownTicks

                                                if (verboseLogging) {
                                                    plugin.logger.info("[SpawnerReset] Spawner at ${block.x},${block.y},${block.z}: " +
                                                        "cooldown $oldCooldown -> $cooldownTicks ticks (${cooldownMinutes}m)")
                                                }
                                            }

                                            // Commit changes
                                            state.update(true, false)

                                            // Verify the change was applied (debug)
                                            if (verboseLogging && cooldownMinutes >= 0) {
                                                val newState = block.state as? org.bukkit.block.TrialSpawner
                                                val actualCooldown = newState?.cooldownLength ?: -1
                                                val cooldownTicks = cooldownMinutes * 60 * 20
                                                if (actualCooldown != cooldownTicks) {
                                                    plugin.logger.warning("[SpawnerReset] MISMATCH! Expected $cooldownTicks but got $actualCooldown")
                                                }
                                            }

                                            resetCount++
                                        }
                                    }
                                }
                            }
                        }

                        if (resetCount > 0) {
                            val cooldownInfo = when {
                                cooldownMinutes < 0 -> "vanilla default"
                                cooldownMinutes == 0 -> "no cooldown (0 ticks)"
                                else -> "${cooldownMinutes}m cooldown (${cooldownMinutes * 60 * 20} ticks)"
                            }
                            plugin.logger.info("Reset $resetCount trial spawners in chamber ${chamber.name} ($cooldownInfo)")
                        }

                        continuation.resume(Unit) {}
                    } catch (e: Exception) {
                        plugin.logger.warning("Error resetting trial spawners: ${e.message}")
                        continuation.resume(Unit) {}  // Don't fail the whole reset
                    }
                })
            }
        }
    }

    /**
     * Restores the chamber from a snapshot.
     *
     * @param chamber The chamber being restored
     * @param snapshotFile The snapshot file to restore from
     * @param initiatingPlayer Optional player who initiated the restoration (for WorldEdit undo support)
     */
    private suspend fun restoreFromSnapshot(chamber: Chamber, snapshotFile: File, initiatingPlayer: Player? = null) {
        plugin.logger.info("Restoring chamber ${chamber.name} from snapshot")

        val snapshot = plugin.snapshotManager.loadSnapshot(snapshotFile)
        if (snapshot == null) {
            plugin.logger.severe("Failed to load snapshot for chamber ${chamber.name}")
            return
        }

        val blockRestorer = BlockRestorer(plugin)
        blockRestorer.restoreBlocks(
            snapshot,
            onProgress = { processed, total ->
                if (processed % 1000 == 0) {
                    plugin.logger.info("Restoring ${chamber.name}: $processed/$total blocks")
                }
            },
            onComplete = {
                plugin.logger.info("Restored ${snapshot.size} blocks for chamber ${chamber.name}")
            },
            initiatingPlayer = initiatingPlayer
        )
    }

    /**
     * Forces an immediate reset of a chamber.
     *
     * @param chamberName The name of the chamber to reset
     * @param initiatingPlayer Optional player who initiated the reset (for WorldEdit undo support)
     */
    suspend fun forceReset(chamberName: String, initiatingPlayer: Player? = null): Boolean {
        val chamber = plugin.chamberManager.getChamber(chamberName) ?: return false
        return resetChamber(chamber, initiatingPlayer)
    }

    /**
     * Cancels a scheduled reset.
     */
    fun cancelScheduledReset(chamberId: Int) {
        scheduledResets.remove(chamberId)?.cancel()
        warningJobs.remove(chamberId)?.forEach { it.cancel() }
    }

    /**
     * Gets the time until the next reset for a chamber.
     */
    fun getTimeUntilReset(chamber: Chamber): Long {
        val lastReset = chamber.lastReset ?: chamber.createdAt
        val resetIntervalMs = chamber.resetInterval * 1000
        val nextResetTime = lastReset + resetIntervalMs
        val now = System.currentTimeMillis()

        return maxOf(0, nextResetTime - now)
    }

    /**
     * Stops the reset scheduler.
     */
    fun shutdown() {
        resetScope.cancel()
        plugin.logger.info("Reset scheduler stopped")
    }
}
