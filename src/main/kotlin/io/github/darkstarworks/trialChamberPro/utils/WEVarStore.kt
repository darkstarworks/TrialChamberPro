package io.github.darkstarworks.trialChamberPro.utils

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * Simple persistent store for named WorldEdit-like regions.
 * Stores normalized min/max corners for a world in we-vars.yml.
 */
object WEVarStore {
    private const val FILE_NAME = "we-vars.yml"

    private fun getFile(dataFolder: File): File {
        return File(dataFolder, FILE_NAME)
    }

    data class SavedRegion(
        val world: String,
        val minX: Int,
        val minY: Int,
        val minZ: Int,
        val maxX: Int,
        val maxY: Int,
        val maxZ: Int
    )

    fun save(dataFolder: File, name: String, loc1: Location, loc2: Location): Boolean {
        if (loc1.world == null || loc2.world == null || loc1.world!!.name != loc2.world!!.name) return false
        val file = getFile(dataFolder)
        val config = YamlConfiguration()
        if (file.exists()) config.load(file)

        val minX = minOf(loc1.blockX, loc2.blockX)
        val minY = minOf(loc1.blockY, loc2.blockY)
        val minZ = minOf(loc1.blockZ, loc2.blockZ)
        val maxX = maxOf(loc1.blockX, loc2.blockX)
        val maxY = maxOf(loc1.blockY, loc2.blockY)
        val maxZ = maxOf(loc1.blockZ, loc2.blockZ)

        val path = "regions.$name"
        config.set("$path.world", loc1.world!!.name)
        config.set("$path.minX", minX)
        config.set("$path.minY", minY)
        config.set("$path.minZ", minZ)
        config.set("$path.maxX", maxX)
        config.set("$path.maxY", maxY)
        config.set("$path.maxZ", maxZ)

        config.save(file)
        return true
    }

    fun get(dataFolder: File, name: String): SavedRegion? {
        val file = getFile(dataFolder)
        if (!file.exists()) return null
        val config = YamlConfiguration()
        config.load(file)
        val path = "regions.$name"
        val world = config.getString("$path.world") ?: return null
        val minX = config.getInt("$path.minX", Int.MIN_VALUE)
        if (minX == Int.MIN_VALUE) return null
        val minY = config.getInt("$path.minY", Int.MIN_VALUE)
        val minZ = config.getInt("$path.minZ", Int.MIN_VALUE)
        val maxX = config.getInt("$path.maxX", Int.MIN_VALUE)
        val maxY = config.getInt("$path.maxY", Int.MIN_VALUE)
        val maxZ = config.getInt("$path.maxZ", Int.MIN_VALUE)
        return SavedRegion(world, minX, minY, minZ, maxX, maxY, maxZ)
    }

    fun delete(dataFolder: File, name: String): Boolean {
        val file = getFile(dataFolder)
        if (!file.exists()) return false
        val config = YamlConfiguration()
        config.load(file)
        val path = "regions.$name"
        if (!config.contains(path)) return false
        config.set(path, null)
        config.save(file)
        return true
    }

    fun list(dataFolder: File): List<Pair<String, SavedRegion>> {
        val file = getFile(dataFolder)
        if (!file.exists()) return emptyList()
        val config = YamlConfiguration()
        config.load(file)
        val section = config.getConfigurationSection("regions") ?: return emptyList()
        return section.getKeys(false).mapNotNull { key ->
            val sr = get(dataFolder, key)
            if (sr != null) key to sr else null
        }.sortedBy { it.first.lowercase() }
    }

    fun toLocations(saved: SavedRegion): Pair<Location, Location>? {
        val world: World = Bukkit.getWorld(saved.world) ?: return null
        val loc1 = Location(world, saved.minX.toDouble(), saved.minY.toDouble(), saved.minZ.toDouble())
        val loc2 = Location(world, saved.maxX.toDouble(), saved.maxY.toDouble(), saved.maxZ.toDouble())
        return Pair(loc1, loc2)
    }
}