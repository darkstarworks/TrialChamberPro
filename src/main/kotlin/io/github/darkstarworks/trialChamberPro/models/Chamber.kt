package io.github.darkstarworks.trialChamberPro.models

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import java.io.File

/**
 * Represents a Trial Chamber registered in the plugin.
 *
 * @property id Database ID
 * @property name Unique chamber name
 * @property world World name
 * @property minX Minimum X coordinate
 * @property minY Minimum Y coordinate
 * @property minZ Minimum Z coordinate
 * @property maxX Maximum X coordinate
 * @property maxY Maximum Y coordinate
 * @property maxZ Maximum Z coordinate
 * @property exitX Exit location X coordinate (nullable)
 * @property exitY Exit location Y coordinate (nullable)
 * @property exitZ Exit location Z coordinate (nullable)
 * @property exitYaw Exit location yaw (nullable)
 * @property exitPitch Exit location pitch (nullable)
 * @property snapshotFile Path to snapshot file (nullable)
 * @property resetInterval Reset interval in seconds
 * @property lastReset Last reset timestamp in milliseconds (nullable)
 * @property createdAt Creation timestamp in milliseconds
 * @property normalLootTable Per-chamber loot table override for normal vaults (nullable)
 * @property ominousLootTable Per-chamber loot table override for ominous vaults (nullable)
 * @property spawnerCooldownMinutes Per-chamber spawner cooldown override in minutes (nullable = use global config, -1 = vanilla, 0 = no cooldown)
 */
data class Chamber(
    val id: Int,
    val name: String,
    val world: String,
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int,
    val exitX: Double? = null,
    val exitY: Double? = null,
    val exitZ: Double? = null,
    val exitYaw: Float? = null,
    val exitPitch: Float? = null,
    val snapshotFile: String? = null,
    val resetInterval: Long,
    val lastReset: Long? = null,
    val createdAt: Long,
    val normalLootTable: String? = null,
    val ominousLootTable: String? = null,
    val spawnerCooldownMinutes: Int? = null
) {
    /**
     * Gets the loot table override for a specific vault type.
     * Returns null if no override is set (use default).
     */
    fun getLootTable(vaultType: VaultType): String? = when (vaultType) {
        VaultType.NORMAL -> normalLootTable
        VaultType.OMINOUS -> ominousLootTable
    }

    /**
     * Gets the Bukkit world instance.
     */
    fun getWorld(): World? = Bukkit.getWorld(world)

    /**
     * Gets the exit location if configured.
     */
    fun getExitLocation(): Location? {
        val bukkitWorld = getWorld() ?: return null
        return if (exitX != null && exitY != null && exitZ != null) {
            Location(bukkitWorld, exitX, exitY, exitZ, exitYaw ?: 0f, exitPitch ?: 0f)
        } else null
    }

    /**
     * Checks if a location is within this chamber's bounds.
     */
    fun contains(location: Location): Boolean {
        if (location.world?.name != world) return false
        val x = location.blockX
        val y = location.blockY
        val z = location.blockZ
        return x in minX..maxX && y in minY..maxY && z in minZ..maxZ
    }

    /**
     * Gets all players currently inside this chamber.
     */
    fun getPlayersInside(): List<Player> {
        val bukkitWorld = getWorld() ?: return emptyList()
        return bukkitWorld.players.filter { contains(it.location) }
    }

    /**
     * Gets all entities currently inside this chamber.
     */
    fun getEntitiesInside(): List<Entity> {
        val bukkitWorld = getWorld() ?: return emptyList()
        return bukkitWorld.entities.filter { contains(it.location) }
    }

    /**
     * Iterates through all blocks in the chamber bounds.
     */
    fun forEachBlock(action: (Location) -> Unit) {
        val bukkitWorld = getWorld() ?: return
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    action(Location(bukkitWorld, x.toDouble(), y.toDouble(), z.toDouble()))
                }
            }
        }
    }

    /**
     * Gets the volume (number of blocks) in this chamber.
     */
    fun getVolume(): Int {
        return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1)
    }

    /**
     * Gets the center location of this chamber.
     */
    fun getCenter(): Location {
        val bukkitWorld = getWorld() ?: Bukkit.getWorlds().first()
        return Location(
            bukkitWorld,
            (minX + maxX) / 2.0,
            (minY + maxY) / 2.0,
            (minZ + maxZ) / 2.0
        )
    }

    /**
     * Gets all players currently inside this chamber using provided server.
     */
    fun getPlayersInside(server: org.bukkit.Server): List<Player> {
        val bukkitWorld = server.getWorld(world) ?: return emptyList()
        return bukkitWorld.players.filter { contains(it.location) }
    }

    /**
     * Gets the snapshot file if it exists.
     */
    fun getSnapshotFile(): File? {
        return snapshotFile?.let { File(it) }
    }
}
