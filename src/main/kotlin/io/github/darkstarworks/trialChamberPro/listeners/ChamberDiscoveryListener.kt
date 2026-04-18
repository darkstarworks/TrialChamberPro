package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.managers.ChamberDiscoveryManager
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent

/**
 * Detects trial spawner / vault tile entities in newly-loaded chunks
 * and hands the seed location to [ChamberDiscoveryManager] for registration.
 */
class ChamberDiscoveryListener(private val plugin: TrialChamberPro) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onChunkLoad(event: ChunkLoadEvent) {
        if (!plugin.isReady) return
        if (!plugin.config.getBoolean("discovery.enabled", false)) return
        val world = event.world
        if (world.environment != World.Environment.NORMAL) return

        val tileEntities = event.chunk.tileEntities
        if (tileEntities.isEmpty()) return

        for (state in tileEntities) {
            if (!ChamberDiscoveryManager.isSeedBlock(state.type)) continue

            // Skip if this block already lives inside a registered chamber
            val blockX = state.x
            val blockY = state.y
            val blockZ = state.z
            val loc = Location(world, blockX + 0.5, blockY + 0.5, blockZ + 0.5)
            if (plugin.chamberManager.getCachedChamberAt(loc) != null) continue

            plugin.chamberDiscoveryManager.seed(world, blockX, blockY, blockZ)
            return // One seed per chunk is enough to trigger BFS for the whole region
        }
    }
}
