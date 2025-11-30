package io.github.darkstarworks.trialChamberPro.managers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.scheduler.ScheduledTask
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages pending schematic paste confirmations with timeout.
 * Players must confirm within 5 minutes or the paste is cancelled.
 * Folia compatible: Uses scheduler adapter for timer tasks.
 */
class PasteConfirmationManager(private val plugin: TrialChamberPro) {

    private val pendingPastes = ConcurrentHashMap<UUID, PendingPaste>()
    private val timeoutTasks = ConcurrentHashMap<UUID, ScheduledTask>()

    data class PendingPaste(
        val schematicName: String,
        val location: Location,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        fun getRemainingSeconds(): Long {
            val elapsed = (System.currentTimeMillis() - createdAt) / 1000
            return (300 - elapsed).coerceAtLeast(0) // 5 minutes = 300 seconds
        }
    }

    /**
     * Creates a pending paste request for a player.
     * Automatically cancels after 5 minutes.
     */
    fun createPending(player: Player, schematicName: String, location: Location) {
        // Cancel any existing pending paste
        cancelPending(player, silent = true)

        val pending = PendingPaste(schematicName, location)
        pendingPastes[player.uniqueId] = pending

        // Schedule timeout (5 minutes) - use entity scheduling for Folia compatibility
        val timeoutTask = plugin.scheduler.runTaskLater(Runnable {
            if (hasPending(player)) {
                // Send message on player's region thread
                plugin.scheduler.runAtEntity(player, Runnable {
                    player.sendMessage(plugin.getMessage("paste-timeout"))
                })
                cancelPending(player, silent = true)
            }
        }, 20L * 60 * 5) // 5 minutes

        timeoutTasks[player.uniqueId] = timeoutTask
    }

    /**
     * Gets the pending paste for a player, if any.
     */
    fun getPending(player: Player): PendingPaste? {
        return pendingPastes[player.uniqueId]
    }

    /**
     * Checks if a player has a pending paste.
     */
    fun hasPending(player: Player): Boolean {
        return pendingPastes.containsKey(player.uniqueId)
    }

    /**
     * Cancels a pending paste for a player.
     */
    fun cancelPending(player: Player, silent: Boolean = false) {
        pendingPastes.remove(player.uniqueId)
        timeoutTasks.remove(player.uniqueId)?.cancel()
        plugin.particleVisualizer.stopVisualization(player)

        if (!silent) {
            player.sendMessage(plugin.getMessage("paste-cancelled"))
        }
    }

    /**
     * Clears all pending pastes (e.g., on plugin disable).
     */
    fun clearAll() {
        timeoutTasks.values.forEach { it.cancel() }
        timeoutTasks.clear()
        pendingPastes.clear()
    }
}
