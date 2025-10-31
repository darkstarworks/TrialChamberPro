package io.github.darkstarworks.trialChamberPro.utils

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/**
 * Visualizes bounding boxes using particles, similar to WorldEditSelectionVisualizer.
 * Shows where a schematic will be placed before pasting.
 */
class ParticleVisualizer(private val plugin: org.bukkit.plugin.Plugin) {
    
    private val activeVisualizations = mutableMapOf<UUID, BukkitTask>()
    
    /**
     * Shows a bounding box preview to a player using particles.
     * The visualization auto-refreshes until stopped.
     * 
     * @param player Player to show the visualization to
     * @param min Minimum corner of the bounding box
     * @param max Maximum corner of the bounding box
     * @param particleSpacing Distance between particles (higher = fewer particles)
     */
    fun showBox(player: Player, min: Location, max: Location, particleSpacing: Double = 1.0) {
        stopVisualization(player)
        
        val task = object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline) {
                    cancel()
                    activeVisualizations.remove(player.uniqueId)
                    return
                }
                
                drawBox(player, min, max, particleSpacing)
            }
        }.runTaskTimer(plugin, 0L, 10L) // Update every 10 ticks (0.5s)
        
        activeVisualizations[player.uniqueId] = task
    }
    
    /**
     * Stops the visualization for a player.
     */
    fun stopVisualization(player: Player) {
        activeVisualizations.remove(player.uniqueId)?.cancel()
    }
    
    /**
     * Stops all active visualizations.
     */
    fun stopAll() {
        activeVisualizations.values.forEach { it.cancel() }
        activeVisualizations.clear()
    }
    
    private fun drawBox(player: Player, min: Location, max: Location, spacing: Double) {
        // Draw edges of the bounding box
        val world = min.world ?: return
        
        // Bottom face edges
        drawLine(player, min, Location(world, max.x, min.y, min.z), spacing)
        drawLine(player, Location(world, max.x, min.y, min.z), Location(world, max.x, min.y, max.z), spacing)
        drawLine(player, Location(world, max.x, min.y, max.z), Location(world, min.x, min.y, max.z), spacing)
        drawLine(player, Location(world, min.x, min.y, max.z), min, spacing)
        
        // Top face edges
        drawLine(player, Location(world, min.x, max.y, min.z), Location(world, max.x, max.y, min.z), spacing)
        drawLine(player, Location(world, max.x, max.y, min.z), max, spacing)
        drawLine(player, max, Location(world, min.x, max.y, max.z), spacing)
        drawLine(player, Location(world, min.x, max.y, max.z), Location(world, min.x, max.y, min.z), spacing)
        
        // Vertical edges
        drawLine(player, min, Location(world, min.x, max.y, min.z), spacing)
        drawLine(player, Location(world, max.x, min.y, min.z), Location(world, max.x, max.y, min.z), spacing)
        drawLine(player, Location(world, max.x, min.y, max.z), max, spacing)
        drawLine(player, Location(world, min.x, min.y, max.z), Location(world, min.x, max.y, max.z), spacing)
    }
    
    private fun drawLine(player: Player, start: Location, end: Location, spacing: Double) {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val dz = end.z - start.z
        
        val distance = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        val points = (distance / spacing).toInt().coerceAtLeast(1)
        
        for (i in 0..points) {
            val t = i.toDouble() / points
            val x = start.x + dx * t
            val y = start.y + dy * t
            val z = start.z + dz * t
            
            player.spawnParticle(
                Particle.FLAME,
                x, y, z,
                1, // count
                0.0, 0.0, 0.0, // offset
                0.0 // extra (speed)
            )
        }
    }
}
