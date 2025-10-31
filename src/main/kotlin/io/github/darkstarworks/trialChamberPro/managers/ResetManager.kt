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
     */
    private suspend fun scheduleResetIfNeeded(chamber: Chamber) {
        // Skip if already scheduled
        if (scheduledResets.containsKey(chamber.id)) return

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
     */
    suspend fun resetChamber(chamber: Chamber): Boolean = withContext(Dispatchers.IO) {
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
                restoreFromSnapshot(chamber, snapshotFile)
            } else {
                plugin.logger.warning("No snapshot found for chamber ${chamber.name}, skipping block restoration")
            }

            // Step 4: Reset ominous spawners
            if (plugin.config.getBoolean("reset.reset-ominous-spawners", true)) {
                // TODO: Convert ominous spawners back to normal
            }

            // Step 5: Update last reset time
            val now = System.currentTimeMillis()
            plugin.chamberManager.updateLastReset(chamber.id, now)

            // Step 6: Optionally reset vault cooldowns
            if (plugin.config.getBoolean("reset.reset-vault-cooldowns", false)) {
                val vaults = plugin.vaultManager.getVaultsForChamber(chamber.id)
                vaults.forEach { vault ->
                    plugin.vaultManager.resetAllCooldowns(vault.id)
                }
            }

            // Send completion message
            Bukkit.getScheduler().runTask(plugin, Runnable {
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
     * MUST be called from main thread or will wrap in sync call.
     * CRITICAL FIX: Added timeout to prevent hanging coroutines.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun teleportPlayersOut(chamber: Chamber) {
        withTimeout(5000) {  // 5 second timeout
            // Player access MUST happen on main thread
            suspendCancellableCoroutine<Unit> { continuation ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                try {
                    val players = chamber.getPlayersInside()
                    if (players.isEmpty()) {
                        continuation.resume(Unit) {}
                        return@Runnable
                    }

                    val teleportMode = plugin.config.getString("global.teleport-location", "EXIT_POINT")

                    players.forEach { player ->
                        val destination = when (teleportMode) {
                            "EXIT_POINT" -> chamber.getExitLocation() ?: getOutsideLocation(chamber, player.location)
                            "OUTSIDE_BOUNDS" -> getOutsideLocation(chamber, player.location)
                            "WORLD_SPAWN" -> player.world.spawnLocation
                            else -> chamber.getExitLocation() ?: player.world.spawnLocation
                        }

                        player.teleport(destination)
                        player.sendMessage(plugin.getMessage("teleported-to-exit", "chamber" to chamber.name))
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
     * MUST be called from main thread or will wrap in sync call.
     * CRITICAL FIX: Added timeout to prevent hanging coroutines.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun clearEntities(chamber: Chamber) {
        withTimeout(5000) {  // 5 second timeout
            // Entity access MUST happen on main thread
            suspendCancellableCoroutine<Unit> { continuation ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
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
     * Restores the chamber from a snapshot.
     */
    private suspend fun restoreFromSnapshot(chamber: Chamber, snapshotFile: File) {
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
            }
        )
    }

    /**
     * Forces an immediate reset of a chamber.
     */
    suspend fun forceReset(chamberName: String): Boolean {
        val chamber = plugin.chamberManager.getChamber(chamberName) ?: return false
        return resetChamber(chamber)
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
