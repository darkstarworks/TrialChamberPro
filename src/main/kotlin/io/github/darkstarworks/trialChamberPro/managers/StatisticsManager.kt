package io.github.darkstarworks.trialChamberPro.managers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.VaultType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages player statistics tracking for Trial Chambers.
 * Tracks completions, vault opens, mobs killed, deaths, and time spent.
 */
class StatisticsManager(private val plugin: TrialChamberPro) {

    // In-memory cache for frequently accessed stats
    private val statsCache = ConcurrentHashMap<UUID, PlayerStats>()
    private val cacheExpiry = ConcurrentHashMap<UUID, Long>()

    // Per-player mutexes to prevent concurrent database loads for the same player
    private val loadingLocks = ConcurrentHashMap<UUID, Mutex>()

    /**
     * Player statistics data class
     */
    data class PlayerStats(
        val playerUuid: UUID,
        var chambersCompleted: Int = 0,
        var normalVaultsOpened: Int = 0,
        var ominousVaultsOpened: Int = 0,
        var mobsKilled: Int = 0,
        var deaths: Int = 0,
        var timeSpent: Long = 0, // in seconds
        var lastUpdated: Long = System.currentTimeMillis()
    )

    /**
     * Gets statistics for a player.
     * Uses cache with 5-minute expiry.
     * Thread-safe: uses per-player mutex to prevent concurrent database loads.
     */
    suspend fun getStats(playerUuid: UUID): PlayerStats = withContext(Dispatchers.IO) {
        // Quick check without lock (fast path for cached data)
        val expiry = cacheExpiry[playerUuid]
        val cachedStats = statsCache[playerUuid]
        if (expiry != null && cachedStats != null && System.currentTimeMillis() < expiry) {
            return@withContext cachedStats
        }

        // Get or create a mutex for this player to prevent concurrent DB loads
        val mutex = loadingLocks.computeIfAbsent(playerUuid) { Mutex() }

        mutex.withLock {
            // Double-check cache after acquiring lock (another coroutine may have loaded it)
            val expiryAfterLock = cacheExpiry[playerUuid]
            val cachedAfterLock = statsCache[playerUuid]
            if (expiryAfterLock != null && cachedAfterLock != null && System.currentTimeMillis() < expiryAfterLock) {
                return@withContext cachedAfterLock
            }

            // Load from database
            val stats = loadStatsFromDatabase(playerUuid)

            // Update cache
            statsCache[playerUuid] = stats
            cacheExpiry[playerUuid] = System.currentTimeMillis() + 300000 // 5 minutes

            stats
        }
    }

    /**
     * Increments chambers completed for a player.
     */
    suspend fun incrementChambersCompleted(playerUuid: UUID) = withContext(Dispatchers.IO) {
        val stats = getStats(playerUuid)
        stats.chambersCompleted++
        stats.lastUpdated = System.currentTimeMillis()
        saveStats(stats)
        invalidateCache(playerUuid)
    }

    /**
     * Increments vaults opened counter (Normal or Ominous).
     */
    suspend fun incrementVaultsOpened(playerUuid: UUID, vaultType: VaultType) = withContext(Dispatchers.IO) {
        val stats = getStats(playerUuid)
        when (vaultType) {
            VaultType.NORMAL -> stats.normalVaultsOpened++
            VaultType.OMINOUS -> stats.ominousVaultsOpened++
        }
        stats.lastUpdated = System.currentTimeMillis()
        saveStats(stats)
        invalidateCache(playerUuid)
    }

    /**
     * Increments mobs killed counter.
     */
    suspend fun incrementMobsKilled(playerUuid: UUID) = withContext(Dispatchers.IO) {
        val stats = getStats(playerUuid)
        stats.mobsKilled++
        stats.lastUpdated = System.currentTimeMillis()
        saveStats(stats)
        invalidateCache(playerUuid)
    }

    /**
     * Increments deaths counter.
     */
    suspend fun incrementDeaths(playerUuid: UUID) = withContext(Dispatchers.IO) {
        val stats = getStats(playerUuid)
        stats.deaths++
        stats.lastUpdated = System.currentTimeMillis()
        saveStats(stats)
        invalidateCache(playerUuid)
    }

    /**
     * Adds time spent in chambers (in seconds).
     */
    suspend fun addTimeSpent(playerUuid: UUID, seconds: Long) = withContext(Dispatchers.IO) {
        val stats = getStats(playerUuid)
        stats.timeSpent += seconds
        stats.lastUpdated = System.currentTimeMillis()
        saveStats(stats)
        invalidateCache(playerUuid)
    }

    /**
     * Batch adds time spent for multiple players in a single transaction.
     * More efficient than calling addTimeSpent multiple times.
     */
    suspend fun batchAddTimeSpent(updates: Map<UUID, Long>) = withContext(Dispatchers.IO) {
        if (updates.isEmpty()) return@withContext

        plugin.databaseManager.connection.use { conn ->
            conn.autoCommit = false
            try {
                // Use database-specific upsert syntax
                val isSQLite = plugin.databaseManager.databaseType ==
                    io.github.darkstarworks.trialChamberPro.database.DatabaseManager.DatabaseType.SQLITE

                val sql = if (isSQLite) {
                    """
                    INSERT INTO player_stats (player_uuid, time_spent, last_updated)
                    VALUES (?, ?, ?)
                    ON CONFLICT(player_uuid) DO UPDATE SET
                        time_spent = time_spent + excluded.time_spent,
                        last_updated = excluded.last_updated
                    """.trimIndent()
                } else {
                    """
                    INSERT INTO player_stats (player_uuid, time_spent, last_updated)
                    VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        time_spent = time_spent + VALUES(time_spent),
                        last_updated = VALUES(last_updated)
                    """.trimIndent()
                }

                val stmt = conn.prepareStatement(sql)

                updates.forEach { (uuid, seconds) ->
                    stmt.setString(1, uuid.toString())
                    stmt.setLong(2, seconds)
                    stmt.setLong(3, System.currentTimeMillis())
                    stmt.addBatch()
                }

                stmt.executeBatch()
                conn.commit()

                // Invalidate cache for all updated players
                updates.keys.forEach { invalidateCache(it) }
            } catch (e: Exception) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = true
            }
        }
    }

    /**
     * Gets top players by a specific stat.
     * @param stat The stat to rank by: "chambers", "normal_vaults", "ominous_vaults", "mobs", "time"
     * @param limit Number of top players to return
     */
    suspend fun getLeaderboard(stat: String, limit: Int = 10): List<Pair<UUID, Int>> = withContext(Dispatchers.IO) {
        val column = when (stat.lowercase()) {
            "chambers" -> "chambers_completed"
            "normal_vaults" -> "normal_vaults_opened"
            "ominous_vaults" -> "ominous_vaults_opened"
            "mobs" -> "mobs_killed"
            "time" -> "time_spent"
            else -> "chambers_completed"
        }

        plugin.databaseManager.connection.use { conn ->
            val stmt = conn.prepareStatement(
                "SELECT player_uuid, $column FROM player_stats ORDER BY $column DESC LIMIT ?"
            )
            stmt.setInt(1, limit)

            val results = mutableListOf<Pair<UUID, Int>>()
            val rs = stmt.executeQuery()

            while (rs.next()) {
                val uuid = UUID.fromString(rs.getString("player_uuid"))
                val value = rs.getInt(column)
                results.add(uuid to value)
            }

            results
        }
    }

    /**
     * Resets all statistics for a player.
     */
    suspend fun resetStats(playerUuid: UUID) = withContext(Dispatchers.IO) {
        plugin.databaseManager.connection.use { conn ->
            val stmt = conn.prepareStatement(
                "DELETE FROM player_stats WHERE player_uuid = ?"
            )
            stmt.setString(1, playerUuid.toString())
            stmt.executeUpdate()
        }
        invalidateCache(playerUuid)
    }

    /**
     * Loads statistics from the database.
     * Creates a new entry if the player doesn't have stats yet.
     */
    private fun loadStatsFromDatabase(playerUuid: UUID): PlayerStats {
        plugin.databaseManager.connection.use { conn ->
            val stmt = conn.prepareStatement(
                "SELECT * FROM player_stats WHERE player_uuid = ?"
            )
            stmt.setString(1, playerUuid.toString())

            val rs = stmt.executeQuery()

            return if (rs.next()) {
                PlayerStats(
                    playerUuid = playerUuid,
                    chambersCompleted = rs.getInt("chambers_completed"),
                    normalVaultsOpened = rs.getInt("normal_vaults_opened"),
                    ominousVaultsOpened = rs.getInt("ominous_vaults_opened"),
                    mobsKilled = rs.getInt("mobs_killed"),
                    deaths = rs.getInt("deaths"),
                    timeSpent = rs.getLong("time_spent"),
                    lastUpdated = rs.getLong("last_updated")
                )
            } else {
                // Create new stats entry
                val newStats = PlayerStats(playerUuid = playerUuid)
                saveStats(newStats)
                newStats
            }
        }
    }

    /**
     * Saves statistics to the database.
     */
    private fun saveStats(stats: PlayerStats) {
        plugin.databaseManager.connection.use { conn ->
            // Use database-specific upsert syntax
            val isSQLite = plugin.databaseManager.databaseType ==
                io.github.darkstarworks.trialChamberPro.database.DatabaseManager.DatabaseType.SQLITE

            val sql = if (isSQLite) {
                """
                INSERT OR REPLACE INTO player_stats
                (player_uuid, chambers_completed, normal_vaults_opened, ominous_vaults_opened,
                 mobs_killed, deaths, time_spent, last_updated)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """
            } else {
                """
                INSERT INTO player_stats
                (player_uuid, chambers_completed, normal_vaults_opened, ominous_vaults_opened,
                 mobs_killed, deaths, time_spent, last_updated)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    chambers_completed = VALUES(chambers_completed),
                    normal_vaults_opened = VALUES(normal_vaults_opened),
                    ominous_vaults_opened = VALUES(ominous_vaults_opened),
                    mobs_killed = VALUES(mobs_killed),
                    deaths = VALUES(deaths),
                    time_spent = VALUES(time_spent),
                    last_updated = VALUES(last_updated)
                """
            }

            val stmt = conn.prepareStatement(sql)
            stmt.setString(1, stats.playerUuid.toString())
            stmt.setInt(2, stats.chambersCompleted)
            stmt.setInt(3, stats.normalVaultsOpened)
            stmt.setInt(4, stats.ominousVaultsOpened)
            stmt.setInt(5, stats.mobsKilled)
            stmt.setInt(6, stats.deaths)
            stmt.setLong(7, stats.timeSpent)
            stmt.setLong(8, stats.lastUpdated)
            stmt.executeUpdate()
        }
    }

    /**
     * Invalidates cache for a player.
     */
    private fun invalidateCache(playerUuid: UUID) {
        statsCache.remove(playerUuid)
        cacheExpiry.remove(playerUuid)
    }

    /**
     * Clears all cached statistics.
     */
    fun clearCache() {
        statsCache.clear()
        cacheExpiry.clear()
    }

    /**
     * Formats time in seconds to a human-readable string.
     */
    fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m ${secs}s"
            minutes > 0 -> "${minutes}m ${secs}s"
            else -> "${secs}s"
        }
    }
}
