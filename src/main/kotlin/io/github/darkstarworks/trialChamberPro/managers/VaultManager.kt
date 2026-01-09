package io.github.darkstarworks.trialChamberPro.managers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.VaultData
import io.github.darkstarworks.trialChamberPro.models.VaultType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Vault
import java.sql.ResultSet
import java.util.UUID

/**
 * Manages vault tracking, cooldowns, and player interactions.
 * Handles per-player vault loot with separate cooldowns for Normal and Ominous vaults.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VaultManager(private val plugin: TrialChamberPro) {

    data class VaultCounts(val normal: Int, val ominous: Int, val updatedAt: Long)

    private val countsCache = java.util.concurrent.ConcurrentHashMap<Int, VaultCounts>()

    fun getVaultCounts(chamberId: Int, ttlMs: Long = 30_000L): Pair<Int, Int> {
        val now = System.currentTimeMillis()
        val cached = countsCache[chamberId]
        if (cached != null && now - cached.updatedAt < ttlMs) {
            return cached.normal to cached.ominous
        }

        // If we have stale cached data, trigger async refresh and return stale data
        if (cached != null) {
            refreshVaultCountsAsync(chamberId)
            return cached.normal to cached.ominous
        }

        // No cached data at all - do synchronous fetch to avoid returning (0, 0)
        // This only happens once per chamber until cache is populated
        return getVaultCountsSync(chamberId)
    }

    /**
     * Synchronously fetches vault counts from the database.
     * Used when cache is completely empty to avoid returning (0, 0).
     */
    private fun getVaultCountsSync(chamberId: Int): Pair<Int, Int> {
        return try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    """
                    SELECT type, COUNT(*) as count
                    FROM vaults
                    WHERE chamber_id = ?
                    GROUP BY type
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setInt(1, chamberId)
                    val rs = stmt.executeQuery()

                    var normal = 0
                    var ominous = 0

                    while (rs.next()) {
                        when (rs.getString("type")) {
                            "NORMAL" -> normal = rs.getInt("count")
                            "OMINOUS" -> ominous = rs.getInt("count")
                        }
                    }

                    // Cache the result
                    countsCache[chamberId] = VaultCounts(normal, ominous, System.currentTimeMillis())

                    normal to ominous
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to get vault counts for chamber $chamberId: ${e.message}")
            0 to 0
        }
    }

    fun refreshVaultCountsAsync(chamberId: Int) {
        plugin.launchAsync {
            try {
                val vaults = getVaultsForChamber(chamberId)
                val normal = vaults.count { it.type == VaultType.NORMAL }
                val ominous = vaults.count { it.type == VaultType.OMINOUS }
                countsCache[chamberId] = VaultCounts(normal, ominous, System.currentTimeMillis())
            } catch (e: Exception) {
                plugin.logger.warning("Failed to refresh vault counts for chamber $chamberId: ${e.message}")
            }
        }
    }

    /**
     * Clears the vault counts cache.
     * Should be called during plugin reload to ensure fresh counts are fetched.
     */
    fun clearCache() {
        countsCache.clear()
        plugin.logger.info("Vault counts cache cleared")
    }

    /**
     * Sets the loot table for all vaults of a specific type in a chamber.
     * Must be called from a coroutine context.
     */
    suspend fun setLootTableForChamber(chamberId: Int, type: VaultType, tableName: String) = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "UPDATE vaults SET loot_table = ? WHERE chamber_id = ? AND type = ?"
                ).use { stmt ->
                    stmt.setString(1, tableName)
                    stmt.setInt(2, chamberId)
                    stmt.setString(3, type.name)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to set loot table for chamber $chamberId: ${e.message}")
        }
    }

    /**
     * Gets a vault by its location and type.
     *
     * @param location The vault location
     * @param type The vault type (NORMAL or OMINOUS)
     * @return The vault data, or null if not found
     */
    suspend fun getVault(location: Location, type: VaultType): VaultData? = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT * FROM vaults WHERE x = ? AND y = ? AND z = ? AND type = ?"
                ).use { stmt ->
                    stmt.setInt(1, location.blockX)
                    stmt.setInt(2, location.blockY)
                    stmt.setInt(3, location.blockZ)
                    stmt.setString(4, type.name)

                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        parseVault(rs)
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to get vault: ${e.message}")
            null
        }
    }

    /**
     * Gets all vaults for a chamber.
     *
     * @param chamberId The chamber ID
     * @return List of vaults in the chamber
     */
    suspend fun getVaultsForChamber(chamberId: Int): List<VaultData> = withContext(Dispatchers.IO) {
        try {
            val vaults = mutableListOf<VaultData>()
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("SELECT * FROM vaults WHERE chamber_id = ?").use { stmt ->
                    stmt.setInt(1, chamberId)
                    val rs = stmt.executeQuery()
                    while (rs.next()) {
                        vaults.add(parseVault(rs))
                    }
                }
            }
            vaults
        } catch (e: Exception) {
            plugin.logger.severe("Failed to get vaults for chamber: ${e.message}")
            emptyList()
        }
    }

    /**
     * Gets the last time a player opened a specific vault.
     *
     * @param playerUuid Player UUID
     * @param vaultId Vault ID
     * @return Timestamp in milliseconds, or 0 if never opened
     */
    suspend fun getLastOpened(playerUuid: UUID, vaultId: Int): Long = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT last_opened FROM player_vaults WHERE player_uuid = ? AND vault_id = ?"
                ).use { stmt ->
                    stmt.setString(1, playerUuid.toString())
                    stmt.setInt(2, vaultId)

                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        val lastOpened = rs.getLong("last_opened")
                        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                            plugin.logger.info("[Cooldown] getLastOpened: player=$playerUuid, vault=$vaultId, lastOpened=$lastOpened")
                        }
                        lastOpened
                    } else {
                        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                            plugin.logger.info("[Cooldown] getLastOpened: player=$playerUuid, vault=$vaultId, NOT FOUND (returning 0)")
                        }
                        0L
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to get last opened time: ${e.message}")
            0L
        }
    }

    /**
     * Records a vault opening by a player.
     *
     * @param playerUuid Player UUID
     * @param vaultId Vault ID
     */
    suspend fun recordOpen(playerUuid: UUID, vaultId: Int) = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()

            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.info("[Cooldown] recordOpen STARTING: player=$playerUuid, vault=$vaultId, timestamp=$now")
            }

            plugin.databaseManager.connection.use { conn ->
                // Use UPSERT (atomic INSERT or UPDATE) to avoid race conditions
                // This is SQLite 3.24.0+ syntax
                conn.prepareStatement(
                    """
                    INSERT INTO player_vaults (player_uuid, vault_id, last_opened, times_opened)
                    VALUES (?, ?, ?, 1)
                    ON CONFLICT(player_uuid, vault_id)
                    DO UPDATE SET
                        last_opened = excluded.last_opened,
                        times_opened = times_opened + 1
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setString(1, playerUuid.toString())
                    stmt.setInt(2, vaultId)
                    stmt.setLong(3, now)
                    val rowsAffected = stmt.executeUpdate()

                    if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                        plugin.logger.info("[Cooldown] recordOpen COMPLETED: player=$playerUuid, vault=$vaultId, rowsAffected=$rowsAffected")
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to record vault opening: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Checks if a player can open a vault (cooldown check).
     *
     * @param playerUuid Player UUID
     * @param vault The vault data
     * @return Pair of (canOpen, remainingTime in milliseconds)
     */
    suspend fun canOpenVault(playerUuid: UUID, vault: VaultData): Pair<Boolean, Long> {
        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[Cooldown] canOpenVault CHECKING: player=$playerUuid, vaultId=${vault.id}, vaultType=${vault.type}")
        }

        val lastOpened = getLastOpened(playerUuid, vault.id)

        if (lastOpened == 0L) {
            // Never opened before
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.info("[Cooldown] canOpenVault RESULT: ALLOWED (never opened before)")
            }
            return Pair(true, 0L)
        }

        // Get cooldown based on vault type
        val cooldownHours = when (vault.type) {
            VaultType.NORMAL -> plugin.config.getLong("vaults.normal-cooldown-hours", -1)
            VaultType.OMINOUS -> plugin.config.getLong("vaults.ominous-cooldown-hours", -1)
        }

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[Cooldown] canOpenVault: lastOpened=$lastOpened, cooldownHours=$cooldownHours")
        }

        // Check for permanent lock (vanilla behavior)
        if (cooldownHours < 0) {
            // -1 or any negative value means permanent lock until chamber reset
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.info("[Cooldown] canOpenVault RESULT: BLOCKED (permanent lock, cooldownHours=$cooldownHours)")
            }
            return Pair(false, Long.MAX_VALUE)
        }

        // Time-based cooldown
        val cooldownMs = cooldownHours * 3600000 // Convert hours to milliseconds
        val timeSince = System.currentTimeMillis() - lastOpened
        val remaining = cooldownMs - timeSince

        return if (remaining <= 0) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.info("[Cooldown] canOpenVault RESULT: ALLOWED (cooldown expired)")
            }
            Pair(true, 0L)
        } else {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.info("[Cooldown] canOpenVault RESULT: BLOCKED (remaining=${remaining}ms)")
            }
            Pair(false, remaining)
        }
    }

    /**
     * Gets the count of locked vaults for a player in a chamber (split by type).
     *
     * @param playerUuid Player UUID
     * @param chamberId Chamber ID
     * @return Pair of (locked normal count, locked ominous count)
     */
    suspend fun getLockedVaultCounts(playerUuid: UUID, chamberId: Int): Pair<Int, Int> = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    """
                    SELECT v.type, COUNT(*) as count
                    FROM player_vaults pv
                    JOIN vaults v ON pv.vault_id = v.id
                    WHERE pv.player_uuid = ? AND v.chamber_id = ?
                    GROUP BY v.type
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setString(1, playerUuid.toString())
                    stmt.setInt(2, chamberId)

                    var normalLocked = 0
                    var ominousLocked = 0

                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val type = rs.getString("type")
                            val count = rs.getInt("count")
                            when (type) {
                                "NORMAL" -> normalLocked = count
                                "OMINOUS" -> ominousLocked = count
                            }
                        }
                    }

                    Pair(normalLocked, ominousLocked)
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to get locked vault counts: ${e.message}")
            Pair(0, 0)
        }
    }

    /**
     * Resets the cooldown for a player on a specific vault.
     * Clears both database tracking AND native Vault.rewarded_players.
     *
     * @param playerUuid Player UUID
     * @param vaultId Vault ID
     * @return True if reset successfully
     */
    suspend fun resetCooldown(playerUuid: UUID, vaultId: Int): Boolean {
        // Clear database tracking
        val dbCleared = withContext(Dispatchers.IO) {
            try {
                plugin.databaseManager.connection.use { conn ->
                    conn.prepareStatement(
                        "DELETE FROM player_vaults WHERE player_uuid = ? AND vault_id = ?"
                    ).use { stmt ->
                        stmt.setString(1, playerUuid.toString())
                        stmt.setInt(2, vaultId)
                        stmt.executeUpdate() > 0
                    }
                }
            } catch (e: Exception) {
                plugin.logger.severe("Failed to reset cooldown in database: ${e.message}")
                false
            }
        }

        // Also clear from native Vault block state
        clearVaultRewardedPlayer(vaultId, playerUuid)

        return dbCleared
    }

    /**
     * Resets all player cooldowns for a specific vault.
     * Clears both database tracking AND native Vault.rewarded_players.
     *
     * @param vaultId Vault ID
     * @return Number of records deleted from database
     */
    suspend fun resetAllCooldowns(vaultId: Int): Int {
        // Clear database tracking
        val dbCount = withContext(Dispatchers.IO) {
            try {
                plugin.databaseManager.connection.use { conn ->
                    conn.prepareStatement("DELETE FROM player_vaults WHERE vault_id = ?").use { stmt ->
                        stmt.setInt(1, vaultId)
                        stmt.executeUpdate()
                    }
                }
            } catch (e: Exception) {
                plugin.logger.severe("Failed to reset all cooldowns in database: ${e.message}")
                0
            }
        }

        // Also clear all rewarded players from native Vault block state
        clearAllVaultRewardedPlayers(vaultId)

        return dbCount
    }

    /**
     * Gets the number of times a player has opened a vault.
     *
     * @param playerUuid Player UUID
     * @param vaultId Vault ID
     * @return Number of times opened
     */
    suspend fun getTimesOpened(playerUuid: UUID, vaultId: Int): Int = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT times_opened FROM player_vaults WHERE player_uuid = ? AND vault_id = ?"
                ).use { stmt ->
                    stmt.setString(1, playerUuid.toString())
                    stmt.setInt(2, vaultId)

                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        rs.getInt("times_opened")
                    } else {
                        0
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to get times opened: ${e.message}")
            0
        }
    }

    /**
     * Checks if a player has ever opened a vault.
     *
     * @param playerUuid Player UUID
     * @param vaultId Vault ID
     * @return True if the player has opened this vault before
     */
    suspend fun hasOpened(playerUuid: UUID, vaultId: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT COUNT(*) as count FROM player_vaults WHERE player_uuid = ? AND vault_id = ?"
                ).use { stmt ->
                    stmt.setString(1, playerUuid.toString())
                    stmt.setInt(2, vaultId)

                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        rs.getInt("count") > 0
                    } else {
                        false
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to check if vault was opened: ${e.message}")
            false
        }
    }

    /**
     * Gets the count of players who have a lock on a specific vault.
     *
     * @param vaultId Vault ID
     * @return Number of players with a lock on this vault
     */
    suspend fun getVaultLockCount(vaultId: Int): Int = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT COUNT(*) as count FROM player_vaults WHERE vault_id = ?"
                ).use { stmt ->
                    stmt.setInt(1, vaultId)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        rs.getInt("count")
                    } else {
                        0
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to get vault lock count: ${e.message}")
            0
        }
    }

    /**
     * Parses vault data from a result set.
     */
    private fun parseVault(rs: ResultSet): VaultData {
        return VaultData(
            id = rs.getInt("id"),
            chamberId = rs.getInt("chamber_id"),
            x = rs.getInt("x"),
            y = rs.getInt("y"),
            z = rs.getInt("z"),
            type = VaultType.valueOf(rs.getString("type")),
            lootTable = rs.getString("loot_table")
        )
    }

    /**
     * Clears a specific player from a vault's native rewarded_players list.
     * Uses Paper's Vault.removeRewardedPlayer() API.
     * Must be called on the region thread (Folia compatible).
     *
     * @param vaultId Vault ID (to look up coordinates)
     * @param playerUuid Player UUID to remove
     */
    private suspend fun clearVaultRewardedPlayer(vaultId: Int, playerUuid: UUID) {
        // Get vault data to find coordinates
        val vaultData = getVaultById(vaultId) ?: return
        val chamber = plugin.chamberManager.getChamberById(vaultData.chamberId) ?: return
        val world = Bukkit.getWorld(chamber.world) ?: return
        val location = Location(world, vaultData.x.toDouble(), vaultData.y.toDouble(), vaultData.z.toDouble())

        // Clear from native Vault state on the region thread (Folia compatible)
        suspendCancellableCoroutine<Unit> { continuation ->
            plugin.scheduler.runAtLocation(location, Runnable {
                try {
                    val block = location.block
                    if (block.type != Material.VAULT) {
                        continuation.resume(Unit) {}
                        return@Runnable
                    }

                    val vaultState = block.state as? Vault
                    if (vaultState == null) {
                        continuation.resume(Unit) {}
                        return@Runnable
                    }

                    vaultState.removeRewardedPlayer(playerUuid)
                    vaultState.update()

                    if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                        plugin.logger.info("[Vault API] Removed rewarded player $playerUuid from vault at ${location.blockX},${location.blockY},${location.blockZ}")
                    }

                    continuation.resume(Unit) {}
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to clear vault rewarded player: ${e.message}")
                    continuation.resume(Unit) {}
                }
            })
        }
    }

    /**
     * Clears all players from a vault's native rewarded_players list.
     * Uses Paper's Vault.removeRewardedPlayer() API.
     * Must be called on the region thread (Folia compatible).
     *
     * @param vaultId Vault ID (to look up coordinates)
     */
    private suspend fun clearAllVaultRewardedPlayers(vaultId: Int) {
        // Get vault data to find coordinates
        val vaultData = getVaultById(vaultId) ?: return
        val chamber = plugin.chamberManager.getChamberById(vaultData.chamberId) ?: return
        val world = Bukkit.getWorld(chamber.world) ?: return
        val location = Location(world, vaultData.x.toDouble(), vaultData.y.toDouble(), vaultData.z.toDouble())

        // Clear all from native Vault state on the region thread (Folia compatible)
        suspendCancellableCoroutine<Unit> { continuation ->
            plugin.scheduler.runAtLocation(location, Runnable {
                try {
                    val block = location.block
                    if (block.type != Material.VAULT) {
                        continuation.resume(Unit) {}
                        return@Runnable
                    }

                    val vaultState = block.state as? Vault
                    if (vaultState == null) {
                        continuation.resume(Unit) {}
                        return@Runnable
                    }

                    // Get all rewarded players and remove them
                    val rewardedPlayers = vaultState.rewardedPlayers.toList()
                    rewardedPlayers.forEach { uuid ->
                        vaultState.removeRewardedPlayer(uuid)
                    }
                    vaultState.update()

                    if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                        plugin.logger.info("[Vault API] Cleared ${rewardedPlayers.size} rewarded players from vault at ${location.blockX},${location.blockY},${location.blockZ}")
                    }

                    continuation.resume(Unit) {}
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to clear all vault rewarded players: ${e.message}")
                    continuation.resume(Unit) {}
                }
            })
        }
    }

    /**
     * Gets vault data by ID.
     */
    private suspend fun getVaultById(vaultId: Int): VaultData? = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("SELECT * FROM vaults WHERE id = ?").use { stmt ->
                    stmt.setInt(1, vaultId)
                    val rs = stmt.executeQuery()
                    if (rs.next()) {
                        parseVault(rs)
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to get vault by ID: ${e.message}")
            null
        }
    }
}
