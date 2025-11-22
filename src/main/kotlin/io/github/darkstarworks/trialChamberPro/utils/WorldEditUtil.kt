package io.github.darkstarworks.trialChamberPro.utils

import com.sk89q.worldedit.IncompleteRegionException
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player

/**
 * Utility class for WorldEdit integration.
 * Handles WorldEdit selections for chamber registration.
 */
object WorldEditUtil {

    /**
     * Checks if WorldEdit is available.
     */
    fun isAvailable(): Boolean {
        val pm = Bukkit.getPluginManager()
        return pm.isPluginEnabled("WorldEdit") || pm.isPluginEnabled("FastAsyncWorldEdit")
    }

    /**
     * Gets a player's WorldEdit selection as two corner locations.
     *
     * @param player The player
     * @return Pair of corner locations, or null if no selection or error
     */
    fun getSelection(player: Player): Pair<Location, Location>? {
        if (!isAvailable()) return null

        try {
            val actor = BukkitAdapter.adapt(player)
            val session = WorldEdit.getInstance().sessionManager.get(actor)
            val selectionWorld = session.selectionWorld ?: return null
            val region = session.getSelection(selectionWorld)

            // Get the two corner points
            val minPoint = region.minimumPoint
            val maxPoint = region.maximumPoint

            val world = BukkitAdapter.adapt(selectionWorld)

            // Use x()/y()/z() methods (modern WorldEdit 7.3+)
            val loc1 = Location(world, minPoint.x().toDouble(), minPoint.y().toDouble(), minPoint.z().toDouble())
            val loc2 = Location(world, maxPoint.x().toDouble(), maxPoint.y().toDouble(), maxPoint.z().toDouble())

            return Pair(loc1, loc2)

        } catch (_: IncompleteRegionException) {
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Checks if a player has an active WorldEdit selection.
     */
    fun hasSelection(player: Player): Boolean {
        if (!isAvailable()) return false

        try {
            val actor = BukkitAdapter.adapt(player)
            val session = WorldEdit.getInstance().sessionManager.get(actor)
            val selectionWorld = session.selectionWorld ?: return false
            session.getSelection(selectionWorld)
            return true

        } catch (_: IncompleteRegionException) {
            return false
        } catch (_: Exception) {
            return false
        }
    }
}
