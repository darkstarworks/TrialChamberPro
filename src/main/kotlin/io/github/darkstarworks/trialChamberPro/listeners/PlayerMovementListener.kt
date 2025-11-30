package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.scheduler.ScheduledTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Optimized time tracking for players in Trial Chambers.
 * Only checks on block boundary crossings to minimize performance impact.
 */
class PlayerMovementListener(private val plugin: TrialChamberPro) : Listener {

    private val movementScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // In-memory tracking of players currently in chambers
    private val playersInChambers = ConcurrentHashMap.newKeySet<UUID>()
    private val playerEntryTimes = ConcurrentHashMap<UUID, Long>()

    init {
        // Start periodic flush task (every 5 minutes)
        if (plugin.config.getBoolean("statistics.enabled", true) &&
            plugin.config.getBoolean("statistics.track-time-spent", true)) {
            startTimeTrackingTask()
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!plugin.config.getBoolean("statistics.enabled", true)) return
        if (!plugin.config.getBoolean("statistics.track-time-spent", true)) return

        val from = event.from
        val to = event.to

        // Only check when crossing block boundaries (major performance optimization)
        if (from.blockX == to.blockX &&
            from.blockY == to.blockY &&
            from.blockZ == to.blockZ) {
            return
        }

        val player = event.player
        val uuid = player.uniqueId

        movementScope.launch {
            val wasInChamber = plugin.chamberManager.getChamberAt(from) != null
            val isInChamber = plugin.chamberManager.getChamberAt(to) != null

            // Player entered a chamber
            if (!wasInChamber && isInChamber) {
                playersInChambers.add(uuid)
                playerEntryTimes[uuid] = System.currentTimeMillis()

                // Optional: Send entry message
                if (plugin.config.getBoolean("messages.chamber-entry-message", false)) {
                    val chamber = plugin.chamberManager.getChamberAt(to)
                    player.sendMessage(
                        plugin.getMessage("chamber-entered", "chamber" to chamber?.name)
                    )
                }
            }

            // Player left a chamber
            else if (wasInChamber && !isInChamber) {
                playersInChambers.remove(uuid)

                // Immediately flush their time
                flushPlayerTime(uuid)

                // Optional: Send exit message
                if (plugin.config.getBoolean("messages.chamber-exit-message", false)) {
                    player.sendMessage(plugin.getMessage("chamber-exited"))
                }
            }
        }
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        if (playersInChambers.remove(uuid)) {
            movementScope.launch {
                flushPlayerTime(uuid)
            }
        }
    }

    // Track the timer task so we can cancel it if needed
    private var timeTrackingTask: ScheduledTask? = null

    /**
     * Starts the periodic flush task that saves time data every 5 minutes.
     * Folia compatible: Uses scheduler adapter for timer tasks.
     */
    private fun startTimeTrackingTask() {
        val interval = plugin.config.getInt("performance.time-tracking-interval", 300) // seconds
        val intervalTicks = interval * 20L

        timeTrackingTask = plugin.scheduler.runTaskTimer(Runnable {
            movementScope.launch {
                flushAllPlayerTimes()
            }
        }, intervalTicks, intervalTicks)
    }

    /**
     * Flushes all currently tracked player times to the database.
     * Uses batch update for efficiency.
     */
    private suspend fun flushAllPlayerTimes() {
        if (playersInChambers.isEmpty()) return

        val currentTime = System.currentTimeMillis()
        val currentPlayers = playersInChambers.toSet()

        // Collect all time updates into a map
        val updates = mutableMapOf<UUID, Long>()
        currentPlayers.forEach { uuid ->
            val entryTime = playerEntryTimes[uuid] ?: return@forEach
            val timeSpent = (currentTime - entryTime) / 1000 // Convert to seconds

            if (timeSpent > 0) {
                updates[uuid] = timeSpent
                playerEntryTimes[uuid] = currentTime // Reset entry time
            }
        }

        // Batch update all players in a single transaction
        if (updates.isNotEmpty()) {
            plugin.statisticsManager.batchAddTimeSpent(updates)
            plugin.logger.info("Flushed time tracking for ${updates.size} players")
        }
    }

    /**
     * Flushes time data for a specific player.
     */
    private suspend fun flushPlayerTime(uuid: UUID) {
        val entryTime = playerEntryTimes.remove(uuid) ?: return
        val timeSpent = (System.currentTimeMillis() - entryTime) / 1000 // Convert to seconds

        if (timeSpent > 0) {
            plugin.statisticsManager.addTimeSpent(uuid, timeSpent)
        }
    }

    /**
     * Gets the set of players currently in chambers.
     */
    fun getPlayersInChambers(): Set<UUID> = playersInChambers.toSet()

    /**
     * Checks if a player is currently in a chamber.
     */
    fun isPlayerInChamber(uuid: UUID): Boolean = playersInChambers.contains(uuid)
}
