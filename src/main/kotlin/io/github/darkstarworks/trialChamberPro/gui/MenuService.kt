package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.Chamber
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Central service to open and navigate the TCP admin GUI and to preserve
 * per-player session state so reopening /tcp menu restores the last window.
 *
 * Overhauled in v1.2.8 to support comprehensive admin interface with:
 * - Main menu hub
 * - Paginated chamber list
 * - Chamber settings and vault management
 * - Global settings and protection toggles
 * - Statistics and leaderboards
 */
class MenuService(private val plugin: TrialChamberPro) {

    /**
     * Session state for a player's GUI navigation.
     */
    data class Session(
        var screen: Screen = Screen.MAIN_MENU,
        var chamberId: Int? = null,
        var lootKind: LootKind? = null,
        var poolName: String? = null,
        var itemIndex: Int? = null,
        var isWeighted: Boolean? = null,

        // Pagination
        var currentPage: Int = 0,

        // Statistics
        var leaderboardType: String? = null,
        var targetPlayerUuid: UUID? = null,

        // Loot table direct editing (without chamber context)
        var lootTableName: String? = null,

        // Draft editors per loot kind per chamber per pool
        val drafts: MutableMap<String, LootEditorView.Draft> = mutableMapOf()
    )

    /**
     * All available screens in the GUI system.
     */
    enum class Screen {
        // Main
        MAIN_MENU,

        // Chambers
        CHAMBER_LIST,
        CHAMBER_DETAIL,
        CHAMBER_SETTINGS,
        VAULT_MANAGEMENT,

        // Loot (existing + new)
        LOOT_TABLE_LIST,
        POOL_SELECT,
        LOOT_EDITOR,
        AMOUNT_EDITOR,

        // Stats
        STATS_MENU,
        LEADERBOARD,
        PLAYER_STATS,

        // Settings
        SETTINGS_MENU,
        GLOBAL_SETTINGS,
        PROTECTION_MENU,

        // Help
        HELP_MENU
    }

    enum class LootKind { NORMAL, OMINOUS }

    private val sessions = ConcurrentHashMap<UUID, Session>()

    fun getOrCreateSession(playerId: UUID): Session = sessions.computeIfAbsent(playerId) { Session() }

    /**
     * Opens the GUI for a player, restoring their last viewed screen.
     */
    fun openFor(player: Player) {
        val s = getOrCreateSession(player.uniqueId)
        when (s.screen) {
            Screen.MAIN_MENU -> openMainMenu(player)
            Screen.CHAMBER_LIST -> openChamberList(player, s.currentPage)
            Screen.CHAMBER_DETAIL -> {
                val chamber = s.chamberId?.let { plugin.chamberManager.getCachedChamberById(it) }
                if (chamber != null) openChamberDetail(player, chamber) else openMainMenu(player)
            }
            Screen.CHAMBER_SETTINGS -> {
                val chamber = s.chamberId?.let { plugin.chamberManager.getCachedChamberById(it) }
                if (chamber != null) openChamberSettings(player, chamber) else openMainMenu(player)
            }
            Screen.VAULT_MANAGEMENT -> {
                val chamber = s.chamberId?.let { plugin.chamberManager.getCachedChamberById(it) }
                if (chamber != null) openVaultManagement(player, chamber) else openMainMenu(player)
            }
            Screen.LOOT_TABLE_LIST -> openLootTableList(player)
            Screen.POOL_SELECT -> {
                val chamber = s.chamberId?.let { plugin.chamberManager.getCachedChamberById(it) }
                val kind = s.lootKind
                if (chamber != null && kind != null) openPoolSelect(player, chamber, kind) else openMainMenu(player)
            }
            Screen.LOOT_EDITOR -> {
                val chamber = s.chamberId?.let { plugin.chamberManager.getCachedChamberById(it) }
                val kind = s.lootKind
                val poolName = s.poolName
                if (chamber != null && kind != null) {
                    openLootEditor(player, chamber, kind, poolName)
                } else {
                    openMainMenu(player)
                }
            }
            Screen.AMOUNT_EDITOR -> {
                val chamber = s.chamberId?.let { plugin.chamberManager.getCachedChamberById(it) }
                val kind = s.lootKind
                val itemIndex = s.itemIndex
                val isWeighted = s.isWeighted
                if (chamber != null && kind != null && itemIndex != null && isWeighted != null) {
                    openAmountEditor(player, chamber, kind, itemIndex, isWeighted)
                } else {
                    openMainMenu(player)
                }
            }
            Screen.STATS_MENU -> openStatsMenu(player)
            Screen.LEADERBOARD -> openLeaderboard(player, s.leaderboardType ?: "vaults")
            Screen.PLAYER_STATS -> {
                val targetUuid = s.targetPlayerUuid
                if (targetUuid != null) openPlayerStats(player, targetUuid) else openStatsMenu(player)
            }
            Screen.SETTINGS_MENU -> openSettingsMenu(player)
            Screen.GLOBAL_SETTINGS -> openGlobalSettings(player)
            Screen.PROTECTION_MENU -> openProtectionMenu(player)
            Screen.HELP_MENU -> openHelpMenu(player)
        }
    }

    // ==================== Main Menu ====================

    fun openMainMenu(player: Player) {
        val view = MainMenuView(plugin, this)
        val gui = view.build(player)
        getOrCreateSession(player.uniqueId).apply {
            screen = Screen.MAIN_MENU
        }
        gui.show(player)
    }

    // ==================== Chamber Screens ====================

    fun openChamberList(player: Player, page: Int = 0) {
        // Warm vault counts cache
        warmVaultCountsCache()

        val view = ChamberListView(plugin, this)
        val gui = view.build(player, page)
        getOrCreateSession(player.uniqueId).apply {
            screen = Screen.CHAMBER_LIST
            currentPage = page
        }
        gui.show(player)
    }

    fun openChamberDetail(player: Player, chamber: Chamber) {
        val view = ChamberDetailView(plugin, this, chamber)
        val gui = view.build(player)
        getOrCreateSession(player.uniqueId).apply {
            screen = Screen.CHAMBER_DETAIL
            chamberId = chamber.id
            lootKind = null
            poolName = null
        }
        gui.show(player)
    }

    fun openChamberSettings(player: Player, chamber: Chamber) {
        val view = ChamberSettingsView(plugin, this, chamber)
        val gui = view.build(player)
        getOrCreateSession(player.uniqueId).apply {
            screen = Screen.CHAMBER_SETTINGS
            chamberId = chamber.id
        }
        gui.show(player)
    }

    fun openVaultManagement(player: Player, chamber: Chamber) {
        val view = VaultManagementView(plugin, this, chamber)
        val gui = view.build(player)
        getOrCreateSession(player.uniqueId).apply {
            screen = Screen.VAULT_MANAGEMENT
            chamberId = chamber.id
        }
        gui.show(player)
    }

    // ==================== Loot Screens ====================

    fun openLootTableList(player: Player) {
        val view = LootTableListView(plugin, this)
        val gui = view.build(player)
        getOrCreateSession(player.uniqueId).apply {
            screen = Screen.LOOT_TABLE_LIST
        }
        gui.show(player)
    }

    fun openPoolSelect(player: Player, chamber: Chamber, kind: LootKind) {
        val view = PoolSelectorView(plugin, this, chamber, kind)
        val gui = view.build(player)
        getOrCreateSession(player.uniqueId).apply {
            screen = Screen.POOL_SELECT
            chamberId = chamber.id
            lootKind = kind
            poolName = null
        }
        gui.show(player)
    }

    fun openLootEditor(player: Player, chamber: Chamber, kind: LootKind, poolName: String? = null) {
        val key = draftKey(chamber.id, kind, poolName)
        val draft = sessions[player.uniqueId]?.drafts?.get(key)
        val view = LootEditorView(plugin, this, chamber, kind, poolName, existingDraft = draft)
        val gui = view.build(player)
        getOrCreateSession(player.uniqueId).apply {
            screen = Screen.LOOT_EDITOR
            chamberId = chamber.id
            lootKind = kind
            this.poolName = poolName
        }
        gui.show(player)
    }

    fun openAmountEditor(player: Player, chamber: Chamber, kind: LootKind, itemIndex: Int, isWeighted: Boolean) {
        val session = getOrCreateSession(player.uniqueId)
        val poolName = session.poolName
        val key = draftKey(chamber.id, kind, poolName)
        val draft = session.drafts[key]
        if (draft == null) {
            openLootEditor(player, chamber, kind, poolName)
            return
        }
        val view = AmountEditorView(this, chamber, kind, poolName, itemIndex, isWeighted, draft)
        val gui = view.build()
        session.apply {
            screen = Screen.AMOUNT_EDITOR
            chamberId = chamber.id
            lootKind = kind
            this.itemIndex = itemIndex
            this.isWeighted = isWeighted
        }
        gui.show(player)
    }

    // ==================== Stats Screens ====================

    fun openStatsMenu(player: Player) {
        val view = StatsMenuView(plugin, this)
        val gui = view.build(player)
        getOrCreateSession(player.uniqueId).apply {
            screen = Screen.STATS_MENU
        }
        gui.show(player)
    }

    fun openLeaderboard(player: Player, type: String) {
        val view = LeaderboardView(plugin, this, type)
        val gui = view.build(player)
        getOrCreateSession(player.uniqueId).apply {
            screen = Screen.LEADERBOARD
            leaderboardType = type
        }
        gui.show(player)
    }

    fun openPlayerStats(player: Player, targetUuid: UUID) {
        val view = PlayerStatsView(plugin, this, targetUuid)
        val gui = view.build(player)
        getOrCreateSession(player.uniqueId).apply {
            screen = Screen.PLAYER_STATS
            targetPlayerUuid = targetUuid
        }
        gui.show(player)
    }

    // ==================== Settings Screens ====================

    fun openSettingsMenu(player: Player) {
        val view = SettingsMenuView(plugin, this)
        val gui = view.build(player)
        getOrCreateSession(player.uniqueId).apply {
            screen = Screen.SETTINGS_MENU
        }
        gui.show(player)
    }

    fun openGlobalSettings(player: Player) {
        val view = GlobalSettingsView(plugin, this)
        val gui = view.build(player)
        getOrCreateSession(player.uniqueId).apply {
            screen = Screen.GLOBAL_SETTINGS
        }
        gui.show(player)
    }

    fun openProtectionMenu(player: Player) {
        val view = ProtectionMenuView(plugin, this)
        val gui = view.build(player)
        getOrCreateSession(player.uniqueId).apply {
            screen = Screen.PROTECTION_MENU
        }
        gui.show(player)
    }

    // ==================== Help Screen ====================

    fun openHelpMenu(player: Player) {
        val view = HelpMenuView(plugin, this)
        val gui = view.build(player)
        getOrCreateSession(player.uniqueId).apply {
            screen = Screen.HELP_MENU
        }
        gui.show(player)
    }

    // ==================== Utility Methods ====================

    fun saveDraft(player: Player, chamber: Chamber, kind: LootKind, poolName: String?, draft: LootEditorView.Draft) {
        val key = draftKey(chamber.id, kind, poolName)
        getOrCreateSession(player.uniqueId).drafts[key] = draft
    }

    private fun draftKey(chamberId: Int, kind: LootKind, poolName: String? = null): String {
        return if (poolName != null) {
            "${chamberId}:${kind.name}:${poolName}"
        } else {
            "${chamberId}:${kind.name}"
        }
    }

    /**
     * Pre-warms the vault counts cache to prevent DB queries during GUI rendering.
     */
    private fun warmVaultCountsCache() {
        try {
            val chambers = plugin.chamberManager.getCachedChambers()
            chambers.forEachIndexed { index, chamber ->
                plugin.scheduler.runTaskLater(Runnable {
                    plugin.vaultManager.refreshVaultCountsAsync(chamber.id)
                }, (index * 1L))
            }
        } catch (_: Exception) { /* ignore */ }
    }

    // ==================== Legacy Compatibility ====================

    /**
     * Legacy method for backward compatibility.
     * @deprecated Use openChamberList instead
     */
    @Deprecated("Use openChamberList", ReplaceWith("openChamberList(player)"))
    fun openOverview(player: Player) = openChamberList(player)

    /**
     * Legacy method for backward compatibility.
     * @deprecated Use openChamberDetail instead
     */
    @Deprecated("Use openChamberDetail", ReplaceWith("openChamberDetail(player, chamber)"))
    fun openLootKindSelect(player: Player, chamber: Chamber) = openChamberDetail(player, chamber)
}
