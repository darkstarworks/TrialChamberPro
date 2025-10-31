package io.github.darkstarworks.trialChamberPro.managers

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages pending schematic paste confirmations with timeout.
 * Players must confirm within 5 minutes or the paste is cancelled.
 */
class PasteConfirmationManager(private val plugin: io.github.darkstarworks.trialChamberPro.TrialChamberPro) {
    
    private val pendingPastes = ConcurrentHashMap<UUID, PendingPaste>()
    private val timeoutTasks = ConcurrentHashMap<UUID, BukkitTask>()
    
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
        
        // Schedule timeout (5 minutes)
        val timeoutTask = object : BukkitRunnable() {
            override fun run() {
                if (hasPending(player)) {
                    player.sendMessage(plugin.getMessage("paste-timeout"))
                    cancelPending(player, silent = true)
                }
            }
        }.runTaskLater(plugin, 20L * 60 * 5) // 5 minutes
        
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
