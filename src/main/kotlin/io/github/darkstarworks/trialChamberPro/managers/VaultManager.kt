package io.github.darkstarworks.trialChamberPro.managers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.VaultData
import io.github.darkstarworks.trialChamberPro.models.VaultType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Location
import java.sql.ResultSet
import java.util.UUID

/**
 * Manages vault tracking, cooldowns, and player interactions.
 * Handles per-player vault loot with separate cooldowns for Normal and Ominous vaults.
 */
class VaultManager(private val plugin: TrialChamberPro) {

    data class VaultCounts(val normal: Int, val ominous: Int, val updatedAt: Long)

    private val countsCache = java.util.concurrent.ConcurrentHashMap<Int, VaultCounts>()

    fun getVaultCounts(chamberId: Int, ttlMs: Long = 30_000L): Pair<Int, Int> {
        val now = System.currentTimeMillis()
        val cached = countsCache[chamberId]
        if (cached != null && now - cached.updatedAt < ttlMs) {
            return cached.normal to cached.ominous
        }
        // Trigger async refresh and return last known or defaults
        refreshVaultCountsAsync(chamberId)
        return cached?.let { it.normal to it.ominous } ?: (0 to 0)
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

    fun setLootTableForChamber(chamberId: Int, type: VaultType, tableName: String) {
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
     * Gets a vault by its location.
     *
     * @param location The vault location
     * @return The vault data, or null if not found
     */
    suspend fun getVault(location: Location): VaultData? = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT * FROM vaults WHERE x = ? AND y = ? AND z = ?"
                ).use { stmt ->
                    stmt.setInt(1, location.blockX)
                    stmt.setInt(2, location.blockY)
                    stmt.setInt(3, location.blockZ)

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
                        rs.getLong("last_opened")
                    } else {
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
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to record vault opening: ${e.message}")
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
        val lastOpened = getLastOpened(playerUuid, vault.id)

        if (lastOpened == 0L) {
            // Never opened before
            return Pair(true, 0L)
        }

        // Get cooldown based on vault type
        val cooldownHours = when (vault.type) {
            VaultType.NORMAL -> plugin.config.getLong("vaults.normal-cooldown-hours", -1)
            VaultType.OMINOUS -> plugin.config.getLong("vaults.ominous-cooldown-hours", -1)
        }

        // Check for permanent lock (vanilla behavior)
        if (cooldownHours < 0) {
            // -1 or any negative value means permanent lock until chamber reset
            return Pair(false, Long.MAX_VALUE)
        }

        // Time-based cooldown
        val cooldownMs = cooldownHours * 3600000 // Convert hours to milliseconds
        val timeSince = System.currentTimeMillis() - lastOpened
        val remaining = cooldownMs - timeSince

        return if (remaining <= 0) {
            Pair(true, 0L)
        } else {
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
     *
     * @param playerUuid Player UUID
     * @param vaultId Vault ID
     * @return True if reset successfully
     */
    suspend fun resetCooldown(playerUuid: UUID, vaultId: Int): Boolean = withContext(Dispatchers.IO) {
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
            plugin.logger.severe("Failed to reset cooldown: ${e.message}")
            false
        }
    }

    /**
     * Resets all player cooldowns for a specific vault.
     *
     * @param vaultId Vault ID
     * @return Number of records deleted
     */
    suspend fun resetAllCooldowns(vaultId: Int): Int = withContext(Dispatchers.IO) {
        try {
            plugin.databaseManager.connection.use { conn ->
                conn.prepareStatement("DELETE FROM player_vaults WHERE vault_id = ?").use { stmt ->
                    stmt.setInt(1, vaultId)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to reset all cooldowns: ${e.message}")
            0
        }
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
}
