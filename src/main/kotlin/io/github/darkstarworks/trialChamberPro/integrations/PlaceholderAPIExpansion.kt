package io.github.darkstarworks.trialChamberPro.integrations

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import java.util.concurrent.ConcurrentHashMap

/**
 * PlaceholderAPI expansion for TrialChamberPro.
 *
 * Provides the following placeholders:
 *
 * **Player Statistics:**
 * - %tcp_vaults_opened% - Total vaults opened (normal + ominous)
 * - %tcp_vaults_normal% - Normal vaults opened
 * - %tcp_vaults_ominous% - Ominous vaults opened
 * - %tcp_chambers_completed% - Chambers completed
 * - %tcp_mobs_killed% - Mobs killed in chambers
 * - %tcp_deaths% - Deaths in chambers
 * - %tcp_time_spent% - Time spent in chambers (formatted: "1h 30m 45s")
 * - %tcp_time_spent_raw% - Time spent in chambers (raw seconds)
 *
 * **Current State:**
 * - %tcp_current_chamber% - Name of chamber player is in (or "None")
 * - %tcp_in_chamber% - "true" if player is in a chamber, "false" otherwise
 *
 * **Leaderboard Position:**
 * - %tcp_leaderboard_vaults% - Player's rank by total vaults opened
 * - %tcp_leaderboard_chambers% - Player's rank by chambers completed
 * - %tcp_leaderboard_time% - Player's rank by time spent
 *
 * **Top Players (for scoreboards):**
 * - %tcp_top_vaults_1_name% - #1 player name by vaults
 * - %tcp_top_vaults_1_value% - #1 player value by vaults
 * - %tcp_top_vaults_2_name% through %tcp_top_vaults_10_name%
 * - %tcp_top_chambers_1_name% through %tcp_top_chambers_10_name%
 */
class PlaceholderAPIExpansion(private val plugin: TrialChamberPro) : PlaceholderExpansion() {

    // Cache for leaderboard data (refreshed every 60 seconds)
    private var leaderboardCache: MutableMap<String, List<Pair<String, Int>>> = mutableMapOf()
    private var lastLeaderboardRefresh: Long = 0
    private val leaderboardCacheDuration = 60000L // 1 minute
    private var leaderboardRefreshInProgress = false

    // Local stats cache with 30-second TTL (mirrors StatisticsManager but avoids blocking calls)
    private val statsCache = ConcurrentHashMap<java.util.UUID, CachedStats>()
    private data class CachedStats(val stats: io.github.darkstarworks.trialChamberPro.managers.StatisticsManager.PlayerStats, val timestamp: Long)
    private val statsCacheTtl = 30000L // 30 seconds

    override fun getIdentifier(): String = "tcp"

    override fun getAuthor(): String = "darkstarworks"

    override fun getVersion(): String = plugin.pluginMeta.version

    override fun persist(): Boolean = true

    override fun canRegister(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        if (player == null) return null

        val uuid = player.uniqueId

        return when {
            // Player Statistics
            params.equals("vaults_opened", ignoreCase = true) -> {
                val stats = getPlayerStats(uuid)
                (stats.normalVaultsOpened + stats.ominousVaultsOpened).toString()
            }
            params.equals("vaults_normal", ignoreCase = true) -> {
                getPlayerStats(uuid).normalVaultsOpened.toString()
            }
            params.equals("vaults_ominous", ignoreCase = true) -> {
                getPlayerStats(uuid).ominousVaultsOpened.toString()
            }
            params.equals("chambers_completed", ignoreCase = true) -> {
                getPlayerStats(uuid).chambersCompleted.toString()
            }
            params.equals("mobs_killed", ignoreCase = true) -> {
                getPlayerStats(uuid).mobsKilled.toString()
            }
            params.equals("deaths", ignoreCase = true) -> {
                getPlayerStats(uuid).deaths.toString()
            }
            params.equals("time_spent", ignoreCase = true) -> {
                plugin.statisticsManager.formatTime(getPlayerStats(uuid).timeSpent)
            }
            params.equals("time_spent_raw", ignoreCase = true) -> {
                getPlayerStats(uuid).timeSpent.toString()
            }

            // Current State
            params.equals("current_chamber", ignoreCase = true) -> {
                getCurrentChamberName(player)
            }
            params.equals("in_chamber", ignoreCase = true) -> {
                isInChamber(player).toString()
            }

            // Leaderboard Position
            params.equals("leaderboard_vaults", ignoreCase = true) -> {
                getLeaderboardPosition(uuid, "vaults").toString()
            }
            params.equals("leaderboard_chambers", ignoreCase = true) -> {
                getLeaderboardPosition(uuid, "chambers").toString()
            }
            params.equals("leaderboard_time", ignoreCase = true) -> {
                getLeaderboardPosition(uuid, "time").toString()
            }

            // Top Players - vaults
            params.startsWith("top_vaults_", ignoreCase = true) -> {
                handleTopPlaceholder(params, "vaults")
            }

            // Top Players - chambers
            params.startsWith("top_chambers_", ignoreCase = true) -> {
                handleTopPlaceholder(params, "chambers")
            }

            // Top Players - time
            params.startsWith("top_time_", ignoreCase = true) -> {
                handleTopPlaceholder(params, "time")
            }

            else -> null
        }
    }

    /**
     * Gets player stats from local cache, triggering async refresh if stale.
     * NON-BLOCKING: Returns cached data immediately, refreshes in background.
     */
    private fun getPlayerStats(uuid: java.util.UUID): io.github.darkstarworks.trialChamberPro.managers.StatisticsManager.PlayerStats {
        val now = System.currentTimeMillis()
        val cached = statsCache[uuid]

        // Return cached data if fresh
        if (cached != null && now - cached.timestamp < statsCacheTtl) {
            return cached.stats
        }

        // Trigger async refresh (non-blocking)
        plugin.launchAsync {
            try {
                val freshStats = plugin.statisticsManager.getStats(uuid)
                statsCache[uuid] = CachedStats(freshStats, System.currentTimeMillis())
            } catch (_: Exception) {
                // Ignore errors during background refresh
            }
        }

        // Return stale cached data or defaults
        return cached?.stats ?: io.github.darkstarworks.trialChamberPro.managers.StatisticsManager.PlayerStats(uuid)
    }

    /**
     * Gets the name of the chamber the player is currently in.
     * NON-BLOCKING: Uses cache-only lookup (no database call).
     */
    private fun getCurrentChamberName(player: OfflinePlayer): String {
        val onlinePlayer = player.player ?: return "None"
        val location = onlinePlayer.location

        // Use cache-only method (non-blocking)
        val chamber = plugin.chamberManager.getCachedChamberAt(location)

        return chamber?.name ?: "None"
    }

    /**
     * Checks if the player is currently in a chamber.
     * NON-BLOCKING: Uses cache-only lookup.
     */
    private fun isInChamber(player: OfflinePlayer): Boolean {
        val onlinePlayer = player.player ?: return false
        val location = onlinePlayer.location

        // Use cache-only method (non-blocking)
        return plugin.chamberManager.getCachedChamberAt(location) != null
    }

    /**
     * Gets the player's leaderboard position for a specific stat.
     * Returns 0 if not ranked.
     */
    private fun getLeaderboardPosition(uuid: java.util.UUID, stat: String): Int {
        refreshLeaderboardCache()

        val leaderboard = leaderboardCache[stat] ?: return 0
        val playerName = plugin.server.getOfflinePlayer(uuid).name ?: return 0

        val position = leaderboard.indexOfFirst { it.first.equals(playerName, ignoreCase = true) }
        return if (position >= 0) position + 1 else 0
    }

    /**
     * Handles top player placeholders (e.g., top_vaults_1_name, top_vaults_1_value).
     */
    private fun handleTopPlaceholder(params: String, stat: String): String? {
        refreshLeaderboardCache()

        val prefix = "top_${stat}_"
        val remaining = params.removePrefix(prefix)

        // Parse position and type (e.g., "1_name" or "1_value")
        val parts = remaining.split("_")
        if (parts.size != 2) return null

        val position = parts[0].toIntOrNull() ?: return null
        val type = parts[1].lowercase()

        if (position < 1 || position > 10) return null

        val leaderboard = leaderboardCache[stat] ?: return null
        if (position > leaderboard.size) return "---"

        val entry = leaderboard[position - 1]

        return when (type) {
            "name" -> entry.first
            "value" -> {
                if (stat == "time") {
                    plugin.statisticsManager.formatTime(entry.second.toLong())
                } else {
                    entry.second.toString()
                }
            }
            else -> null
        }
    }

    /**
     * Refreshes the leaderboard cache if it's stale.
     * NON-BLOCKING: Triggers async refresh if stale, returns immediately.
     */
    private fun refreshLeaderboardCache() {
        val now = System.currentTimeMillis()
        if (now - lastLeaderboardRefresh < leaderboardCacheDuration) return

        // Prevent multiple concurrent refreshes
        if (leaderboardRefreshInProgress) return
        leaderboardRefreshInProgress = true

        // Trigger async refresh (non-blocking)
        plugin.launchAsync {
            try {
                // Fetch top 100 for each stat to calculate positions
                val vaultsLeaderboard = plugin.statisticsManager.getLeaderboard("normal_vaults", 100)
                val chambersLeaderboard = plugin.statisticsManager.getLeaderboard("chambers", 100)
                val timeLeaderboard = plugin.statisticsManager.getLeaderboard("time", 100)

                // Convert UUIDs to names
                leaderboardCache["vaults"] = vaultsLeaderboard.map { (uuid, value) ->
                    val name = plugin.server.getOfflinePlayer(uuid).name ?: "Unknown"
                    name to value
                }
                leaderboardCache["chambers"] = chambersLeaderboard.map { (uuid, value) ->
                    val name = plugin.server.getOfflinePlayer(uuid).name ?: "Unknown"
                    name to value
                }
                leaderboardCache["time"] = timeLeaderboard.map { (uuid, value) ->
                    val name = plugin.server.getOfflinePlayer(uuid).name ?: "Unknown"
                    name to value
                }

                lastLeaderboardRefresh = System.currentTimeMillis()
            } catch (_: Exception) {
                // Ignore errors during background refresh
            } finally {
                leaderboardRefreshInProgress = false
            }
        }
    }
}
