package io.github.darkstarworks.trialChamberPro.managers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.Chamber
import io.github.darkstarworks.trialChamberPro.models.VaultType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import java.sql.ResultSet
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages chamber CRUD operations and caching.
 * Handles chamber registration, updates, and queries.
 */
class ChamberManager(private val plugin: TrialChamberPro) {

    // Cache for chamber lookups with LRU eviction
    private companion object {
        const val MAX_CACHE_SIZE = 100
    }

    @Suppress("UNCHECKED_CAST")
    private val chamberCache = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, Chamber>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Chamber>?): Boolean {
                return size > MAX_CACHE_SIZE
            }
        }
    )
    private val cacheExpiry = ConcurrentHashMap<String, Long>()

    fun getCachedChambers(): List<Chamber> = chamberCache.values.sortedByDescending { it.createdAt }

    fun getCachedChamberById(id: Int): Chamber? = chamberCache.values.firstOrNull { it.id == id }

    fun getCachedChamberByName(name: String): Chamber? = chamberCache[name]

    /**
     * Creates a new chamber in the database.
     *
     * @param name Unique chamber name
     * @param location1 First corner of the bounding box
     * @param location2 Second corner of the bounding box
     * @param resetInterval Reset interval in seconds
     * @return The created chamber, or null if creation failed
     */
    suspend fun createChamber(
        name: String,
        location1: Location,
        location2: Location,
        resetInterval: Long = plugin.config.getLong("global.default-reset-interval", 172800)
    ): Chamber? = withContext(Dispatchers.IO) {
        if (location1.world != location2.world) {
            plugin.logger.warning("Cannot create chamber with locations in different worlds")
            return@withContext null
        }

        val world = location1.world!!.name

        // Calculate bounding box
        val minX = minOf(location1.blockX, location2.blockX)
        val minY = minOf(location1.blockY, location2.blockY)
        val minZ = minOf(location1.blockZ, location2.blockZ)
        val maxX = maxOf(location1.blockX, location2.blockX)
        val maxY = maxOf(location1.blockY, location2.blockY)
        val maxZ = maxOf(location1.blockZ, location2.blockZ)

        val createdAt = System.currentTimeMillis()

        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    """
                    INSERT INTO chambers (name, world, min_x, min_y, min_z, max_x, max_y, max_z, reset_interval, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    java.sql.Statement.RETURN_GENERATED_KEYS
                ).use { stmt ->
                    stmt.setString(1, name)
                    stmt.setString(2, world)
                    stmt.setInt(3, minX)
                    stmt.setInt(4, minY)
                    stmt.setInt(5, minZ)
                    stmt.setInt(6, maxX)
                    stmt.setInt(7, maxY)
                    stmt.setInt(8, maxZ)
                    stmt.setLong(9, resetInterval)
                    stmt.setLong(10, createdAt)

                    stmt.executeUpdate()

                    val generatedKeys = stmt.generatedKeys
                    if (generatedKeys.next()) {
                        val id = generatedKeys.getInt(1)
                        val chamber = Chamber(
                            id = id,
                            name = name,
                            world = world,
                            minX = minX,
                            minY = minY,
                            minZ = minZ,
                            maxX = maxX,
                            maxY = maxY,
                            maxZ = maxZ,
                            resetInterval = resetInterval,
                            createdAt = createdAt
                        )

                        // Cache the chamber
                        chamberCache[name] = chamber
                        updateCacheExpiry(name)

                        plugin.logger.info("Created chamber: $name (${chamber.getVolume()} blocks)")
                        chamber
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("UNIQUE constraint failed: chambers.name")) {
                plugin.logger.warning("[TCP] Duplicate chamber name '$name' - creation cancelled.")
                return@withContext null
            }
            plugin.logger.severe("Failed to create chamber: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Gets a chamber by name.
     *
     * @param name Chamber name
     * @return The chamber, or null if not found
     */
    suspend fun getChamber(name: String): Chamber? {
        // Check cache first
        if (plugin.config.getBoolean("performance.cache-chamber-lookups", true)) {
            val expiry = cacheExpiry[name]
            if (expiry != null && System.currentTimeMillis() < expiry) {
                return chamberCache[name]
            }
        }

        // Load from database
        return loadChamberFromDb(name)?.also {
            chamberCache[name] = it
            updateCacheExpiry(name)
        }
    }

    /**
     * Gets a chamber by ID.
     */
    suspend fun getChamberById(id: Int): Chamber? = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("SELECT * FROM chambers WHERE id = ?").use { stmt ->
                    stmt.setInt(1, id)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        parseChamber(rs)
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to get chamber by ID: ${e.message}")
            null
        }
    }

    /**
     * Gets all registered chambers.
     */
    suspend fun getAllChambers(): List<Chamber> = withContext(Dispatchers.IO) {
        try {
            val chambers = mutableListOf<Chamber>()
            plugin.databaseManager.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    val rs = stmt.executeQuery("SELECT * FROM chambers ORDER BY created_at DESC")
                    while (rs.next()) {
                        chambers.add(parseChamber(rs))
                    }
                }
            }
            chambers
        } catch (e: Exception) {
            plugin.logger.severe("Failed to get all chambers: ${e.message}")
            emptyList()
        }
    }

    /**
     * Checks if a location is within any chamber.
     */
    fun isInChamber(location: Location): Boolean {
        return chamberCache.values.any { it.contains(location) }
    }

    /**
     * Gets the chamber containing a location.
     */
    suspend fun getChamberAt(location: Location): Chamber? {
        // Check cache first
        chamberCache.values.forEach { chamber ->
            if (chamber.contains(location)) {
                return chamber
            }
        }

        // Load all chambers and check
        val allChambers = getAllChambers()
        return allChambers.firstOrNull { it.contains(location) }
    }

    /**
     * Sets the exit location for a chamber.
     */
    suspend fun setExitLocation(chamberName: String, location: Location): Boolean = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    """
                    UPDATE chambers
                    SET exit_x = ?, exit_y = ?, exit_z = ?, exit_yaw = ?, exit_pitch = ?
                    WHERE name = ?
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setDouble(1, location.x)
                    stmt.setDouble(2, location.y)
                    stmt.setDouble(3, location.z)
                    stmt.setFloat(4, location.yaw)
                    stmt.setFloat(5, location.pitch)
                    stmt.setString(6, chamberName)

                    val updated = stmt.executeUpdate() > 0
                    if (updated) {
                        // Refresh cache with updated data
                        val refreshed = loadChamberFromDb(chamberName)
                        if (refreshed != null) {
                            chamberCache[chamberName] = refreshed
                            updateCacheExpiry(chamberName)
                        } else {
                            chamberCache.remove(chamberName)
                            cacheExpiry.remove(chamberName)
                        }
                        plugin.logger.info("Set exit location for chamber: $chamberName")
                    }
                    updated
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to set exit location: ${e.message}")
            false
        }
    }

    /**
     * Sets the snapshot file path for a chamber.
     */
    suspend fun setSnapshotFile(chamberName: String, filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("UPDATE chambers SET snapshot_file = ? WHERE name = ?").use { stmt ->
                    stmt.setString(1, filePath)
                    stmt.setString(2, chamberName)

                    val updated = stmt.executeUpdate() > 0
                    if (updated) {
                        // Refresh cache with updated data instead of invalidating only
                        val refreshed = loadChamberFromDb(chamberName)
                        if (refreshed != null) {
                            chamberCache[chamberName] = refreshed
                            updateCacheExpiry(chamberName)
                        } else {
                            chamberCache.remove(chamberName)
                            cacheExpiry.remove(chamberName)
                        }
                    }
                    updated
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to set snapshot file: ${e.message}")
            false
        }
    }

    /**
     * Updates the last reset timestamp for a chamber.
     */
    suspend fun updateLastReset(chamberId: Int, timestamp: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("UPDATE chambers SET last_reset = ? WHERE id = ?").use { stmt ->
                    stmt.setLong(1, timestamp)
                    stmt.setInt(2, chamberId)
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to update last reset: ${e.message}")
            false
        }
    }

    /**
     * Deletes a chamber and all associated data.
     */
    suspend fun deleteChamber(name: String): Boolean = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("DELETE FROM chambers WHERE name = ?").use { stmt ->
                    stmt.setString(1, name)
                    val deleted = stmt.executeUpdate() > 0

                    if (deleted) {
                        // Remove from cache
                        chamberCache.remove(name)
                        cacheExpiry.remove(name)

                        // Delete snapshot file
                        plugin.snapshotManager.deleteSnapshot(name)

                        plugin.logger.info("Deleted chamber: $name")
                    }
                    deleted
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to delete chamber: ${e.message}")
            false
        }
    }

    /**
     * Scans a chamber for vaults, spawners, and decorated pots.
     * CRITICAL FIX: Bukkit API must be accessed from main thread only.
     *
     * @return Triple of (vaults, spawners, pots) counts
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun scanChamber(chamber: Chamber): Triple<Int, Int, Int> {
        // Use location-based scheduling for Folia compatibility
        val chamberCenter = Location(
            chamber.getWorld(),
            (chamber.minX + chamber.maxX) / 2.0,
            (chamber.minY + chamber.maxY) / 2.0,
            (chamber.minZ + chamber.maxZ) / 2.0
        )

        return suspendCancellableCoroutine { continuation ->
            plugin.scheduler.runAtLocation(chamberCenter, Runnable {
                try {
                    var vaultCount = 0
                    var spawnerCount = 0
                    var potCount = 0

                    val world = chamber.getWorld()
                    if (world == null) {
                        continuation.resume(Triple(0, 0, 0)) {}
                        return@Runnable
                    }

                    // Now on main thread - safe to access Bukkit API
                    for (x in chamber.minX..chamber.maxX) {
                        for (y in chamber.minY..chamber.maxY) {
                            for (z in chamber.minZ..chamber.maxZ) {
                                val block = world.getBlockAt(x, y, z)

                                when (block.type) {
                                    Material.VAULT -> {
                                        // Save vault with its block state (normal or ominous)
                                        plugin.launchAsync {
                                            saveVault(chamber.id, x, y, z, block)
                                        }
                                        vaultCount++
                                    }
                                    Material.TRIAL_SPAWNER -> {
                                        plugin.launchAsync {
                                            saveSpawner(chamber.id, x, y, z)
                                        }
                                        spawnerCount++
                                    }
                                    Material.DECORATED_POT -> {
                                        potCount++
                                    }
                                    else -> {
                                        // No-op for other blocks
                                    }
                                }
                            }
                        }
                    }

                    plugin.logger.info("Scanned chamber ${chamber.name}: $vaultCount vaults, $spawnerCount spawners, $potCount pots")
                    continuation.resume(Triple(vaultCount, spawnerCount, potCount)) {}
                } catch (e: Exception) {
                    continuation.resumeWith(Result.failure(e))
                }
            })
        }
    }

    /**
     * Saves a vault to the database.
     * Checks block state to determine if it's ominous or normal.
     */
    private suspend fun saveVault(chamberId: Int, x: Int, y: Int, z: Int, block: org.bukkit.block.Block) = withContext(Dispatchers.IO) {
        // Determine current vault type from block state string
        val blockStateString = block.blockData.asString
        val currentType = if (blockStateString.contains("ominous=true", ignoreCase = true)) {
            VaultType.OMINOUS
        } else {
            VaultType.NORMAL
        }

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("Saving vault at ($x,$y,$z): blockData='$blockStateString', currentType=$currentType")
        }

        try {
            plugin.databaseManager.connection.use { conn ->
                // Save BOTH normal and ominous entries for this vault location
                // This allows separate cooldown tracking for each type
                VaultType.entries.forEach { type ->
                    val lootTable = if (type == VaultType.OMINOUS) "ominous-default" else "default"

                    // Use atomic UPSERT to prevent race conditions
                    // SQLite 3.24.0+ supports INSERT ... ON CONFLICT
                    conn.prepareStatement(
                        """
                        INSERT INTO vaults (chamber_id, x, y, z, type, loot_table)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT(chamber_id, x, y, z, type)
                        DO UPDATE SET
                            loot_table = excluded.loot_table
                        """.trimIndent()
                    ).use { stmt ->
                        stmt.setInt(1, chamberId)
                        stmt.setInt(2, x)
                        stmt.setInt(3, y)
                        stmt.setInt(4, z)
                        stmt.setString(5, type.name)
                        stmt.setString(6, lootTable)
                        stmt.executeUpdate()
                    }
                }

                if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                    plugin.logger.info("Saved/updated both vault types at ($x,$y,$z)")
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to save vault: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Saves a spawner to the database.
     */
    private suspend fun saveSpawner(chamberId: Int, x: Int, y: Int, z: Int) = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                // Check if spawner already exists
                conn.prepareStatement(
                    "SELECT id FROM spawners WHERE chamber_id = ? AND x = ? AND y = ? AND z = ?"
                ).use { stmt ->
                    stmt.setInt(1, chamberId)
                    stmt.setInt(2, x)
                    stmt.setInt(3, y)
                    stmt.setInt(4, z)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        return@withContext
                    }
                }

                // Insert new spawner
                conn.prepareStatement(
                    "INSERT INTO spawners (chamber_id, x, y, z, type) VALUES (?, ?, ?, ?, ?)"
                ).use { stmt ->
                    stmt.setInt(1, chamberId)
                    stmt.setInt(2, x)
                    stmt.setInt(3, y)
                    stmt.setInt(4, z)
                    stmt.setString(5, VaultType.NORMAL.name) // Default to normal
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to save spawner: ${e.message}")
        }
    }

    /**
     * Loads a chamber from the database.
     */
    private suspend fun loadChamberFromDb(name: String): Chamber? = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("SELECT * FROM chambers WHERE name = ?").use { stmt ->
                    stmt.setString(1, name)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        parseChamber(rs)
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to load chamber: ${e.message}")
            null
        }
    }

    /**
     * Parses a chamber from a result set.
     */
    private fun parseChamber(rs: ResultSet): Chamber {
        return Chamber(
            id = rs.getInt("id"),
            name = rs.getString("name"),
            world = rs.getString("world"),
            minX = rs.getInt("min_x"),
            minY = rs.getInt("min_y"),
            minZ = rs.getInt("min_z"),
            maxX = rs.getInt("max_x"),
            maxY = rs.getInt("max_y"),
            maxZ = rs.getInt("max_z"),
            exitX = rs.getDouble("exit_x").takeIf { !rs.wasNull() },
            exitY = rs.getDouble("exit_y").takeIf { !rs.wasNull() },
            exitZ = rs.getDouble("exit_z").takeIf { !rs.wasNull() },
            exitYaw = rs.getFloat("exit_yaw").takeIf { !rs.wasNull() },
            exitPitch = rs.getFloat("exit_pitch").takeIf { !rs.wasNull() },
            snapshotFile = rs.getString("snapshot_file"),
            resetInterval = rs.getLong("reset_interval"),
            lastReset = rs.getLong("last_reset").takeIf { !rs.wasNull() },
            createdAt = rs.getLong("created_at"),
            normalLootTable = rs.getString("normal_loot_table"),
            ominousLootTable = rs.getString("ominous_loot_table")
        )
    }

    /**
     * Updates cache expiry for a chamber.
     */
    private fun updateCacheExpiry(name: String) {
        val duration = plugin.config.getInt("performance.cache-duration-seconds", 300)
        cacheExpiry[name] = System.currentTimeMillis() + (duration * 1000)
    }

    /**
     * Clears the chamber cache and reloads all chambers from database.
     */
    fun clearCache() {
        chamberCache.clear()
        cacheExpiry.clear()
        plugin.logger.info("Chamber cache cleared, reloading from database...")

        // Reload all chambers from database asynchronously
        plugin.launchAsync {
            try {
                val chambers = getAllChambers()
                chambers.forEach { chamber ->
                    chamberCache[chamber.name] = chamber
                }
                plugin.logger.info("Reloaded ${chambers.size} chambers into cache")
            } catch (e: Exception) {
                plugin.logger.severe("Failed to reload chambers after cache clear: ${e.message}")
            }
        }
    }

    /**
     * Preloads all chambers into the in-memory cache.
     */
    suspend fun preloadCache() {
        try {
            val all = getAllChambers()
            all.forEach { chamber ->
                chamberCache[chamber.name] = chamber
                updateCacheExpiry(chamber.name)
            }
            plugin.logger.info("Preloaded ${all.size} chambers into cache")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to preload chamber cache: ${e.message}")
        }
    }

    /**
     * Gets the list of cached chamber names without database access.
     */
    fun getCachedChamberNames(): List<String> = chamberCache.keys.toList()

    /**
     * Gets the chamber from cache that contains the given location.
     * Does not access the database.
     */
    fun getCachedChamberAt(location: Location): Chamber? {
        return chamberCache.values.firstOrNull { it.contains(location) }
    }

    /**
     * Sets the loot table override for a chamber.
     *
     * @param chamberName The chamber name
     * @param vaultType The vault type (NORMAL or OMINOUS)
     * @param tableName The loot table name, or null to clear the override
     * @return True if successful
     */
    suspend fun setLootTable(chamberName: String, vaultType: VaultType, tableName: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val column = when (vaultType) {
                VaultType.NORMAL -> "normal_loot_table"
                VaultType.OMINOUS -> "ominous_loot_table"
            }

            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("UPDATE chambers SET $column = ? WHERE name = ?").use { stmt ->
                    if (tableName == null) {
                        stmt.setNull(1, java.sql.Types.VARCHAR)
                    } else {
                        stmt.setString(1, tableName)
                    }
                    stmt.setString(2, chamberName)

                    val updated = stmt.executeUpdate() > 0
                    if (updated) {
                        // Refresh cache with updated data
                        val refreshed = loadChamberFromDb(chamberName)
                        if (refreshed != null) {
                            chamberCache[chamberName] = refreshed
                            updateCacheExpiry(chamberName)
                        } else {
                            chamberCache.remove(chamberName)
                            cacheExpiry.remove(chamberName)
                        }
                        plugin.logger.info("Set ${vaultType.displayName} loot table for chamber $chamberName to: ${tableName ?: "(default)"}")
                    }
                    updated
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to set loot table: ${e.message}")
            false
        }
    }

    /**
     * Gets the effective loot table for a vault, using the priority hierarchy:
     * 1. Chamber override (if set)
     * 2. Vault's stored loot table (fallback)
     *
     * @param chamber The chamber (may be null)
     * @param vaultType The vault type
     * @param vaultStoredTable The vault's stored loot table name
     * @return The effective loot table name to use
     */
    fun getEffectiveLootTable(chamber: Chamber?, vaultType: VaultType, vaultStoredTable: String): String {
        return chamber?.getLootTable(vaultType) ?: vaultStoredTable
    }

    /**
     * Updates the reset interval for a chamber.
     *
     * @param chamberId The chamber ID
     * @param intervalSeconds The new reset interval in seconds
     * @return True if successful
     */
    suspend fun updateResetInterval(chamberId: Int, intervalSeconds: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("UPDATE chambers SET reset_interval = ? WHERE id = ?").use { stmt ->
                    stmt.setLong(1, intervalSeconds)
                    stmt.setInt(2, chamberId)

                    val updated = stmt.executeUpdate() > 0
                    if (updated) {
                        // Refresh cache
                        val chamber = chamberCache.values.find { it.id == chamberId }
                        if (chamber != null) {
                            val refreshed = loadChamberFromDb(chamber.name)
                            if (refreshed != null) {
                                chamberCache[chamber.name] = refreshed
                                updateCacheExpiry(chamber.name)
                            }
                        }
                        plugin.logger.info("Updated reset interval for chamber $chamberId to $intervalSeconds seconds")
                    }
                    updated
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to update reset interval: ${e.message}")
            false
        }
    }

    /**
     * Updates the exit location for a chamber.
     *
     * @param chamberId The chamber ID
     * @param x Exit X coordinate
     * @param y Exit Y coordinate
     * @param z Exit Z coordinate
     * @param yaw Exit yaw rotation
     * @param pitch Exit pitch rotation
     * @return True if successful
     */
    suspend fun updateExitLocation(chamberId: Int, x: Double, y: Double, z: Double, yaw: Float, pitch: Float): Boolean = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "UPDATE chambers SET exit_x = ?, exit_y = ?, exit_z = ?, exit_yaw = ?, exit_pitch = ? WHERE id = ?"
                ).use { stmt ->
                    stmt.setDouble(1, x)
                    stmt.setDouble(2, y)
                    stmt.setDouble(3, z)
                    stmt.setFloat(4, yaw)
                    stmt.setFloat(5, pitch)
                    stmt.setInt(6, chamberId)

                    val updated = stmt.executeUpdate() > 0
                    if (updated) {
                        // Refresh cache
                        val chamber = chamberCache.values.find { it.id == chamberId }
                        if (chamber != null) {
                            val refreshed = loadChamberFromDb(chamber.name)
                            if (refreshed != null) {
                                chamberCache[chamber.name] = refreshed
                                updateCacheExpiry(chamber.name)
                            }
                        }
                        plugin.logger.info("Updated exit location for chamber $chamberId")
                    }
                    updated
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to update exit location: ${e.message}")
            false
        }
    }
}
