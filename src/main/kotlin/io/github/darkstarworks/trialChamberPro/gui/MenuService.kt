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
 */
class MenuService(private val plugin: TrialChamberPro) {

    data class Session(
        var screen: Screen = Screen.OVERVIEW,
        var chamberId: Int? = null,
        var lootKind: LootKind? = null,
        var itemIndex: Int? = null,
        var isWeighted: Boolean? = null,
        // Draft editors per loot kind per chamber
        val drafts: MutableMap<String, LootEditorView.Draft> = mutableMapOf()
    )

    enum class Screen { OVERVIEW, LOOT_KIND_SELECT, LOOT_EDITOR, AMOUNT_EDITOR }

    enum class LootKind { NORMAL, OMINOUS }

    private val sessions = ConcurrentHashMap<UUID, Session>()

    fun getOrCreateSession(playerId: UUID): Session = sessions.computeIfAbsent(playerId) { Session() }

    fun openFor(player: Player) {
        val s = getOrCreateSession(player.uniqueId)
        when (s.screen) {
            Screen.OVERVIEW -> openOverview(player)
            Screen.LOOT_KIND_SELECT -> {
                val chamber = s.chamberId?.let { plugin.chamberManager.getCachedChamberById(it) }
                if (chamber != null) openLootKindSelect(player, chamber) else openOverview(player)
            }
            Screen.LOOT_EDITOR -> {
                val chamber = s.chamberId?.let { plugin.chamberManager.getCachedChamberById(it) }
                val kind = s.lootKind
                if (chamber != null && kind != null) openLootEditor(player, chamber, kind) else openOverview(player)
            }
            Screen.AMOUNT_EDITOR -> {
                val chamber = s.chamberId?.let { plugin.chamberManager.getCachedChamberById(it) }
                val kind = s.lootKind
                val itemIndex = s.itemIndex
                val isWeighted = s.isWeighted
                if (chamber != null && kind != null && itemIndex != null && isWeighted != null) {
                    openAmountEditor(player, chamber, kind, itemIndex, isWeighted)
                } else {
                    openOverview(player)
                }
            }
        }
    }

    fun openOverview(player: Player) {
        // Warm vault counts cache to keep UI responsive
        try {
            plugin.chamberManager.getCachedChambers().forEach { chamber ->
                plugin.vaultManager.refreshVaultCountsAsync(chamber.id)
            }
        } catch (_: Exception) { /* ignore */ }

        val view = ChambersOverviewView(plugin, this)
        val gui: ChestGui = view.build(player)
        sessions[player.uniqueId]?.apply {
            screen = Screen.OVERVIEW
            lootKind = null
        }
        gui.show(player)
    }

    fun openLootKindSelect(player: Player, chamber: Chamber) {
        val view = LootTypeSelectView(plugin, this, chamber)
        val gui = view.build(player)
        sessions[player.uniqueId]?.apply {
            screen = Screen.LOOT_KIND_SELECT
            chamberId = chamber.id
        }
        gui.show(player)
    }

    fun openLootEditor(player: Player, chamber: Chamber, kind: LootKind) {
        val key = draftKey(chamber.id, kind)
        val draft = sessions[player.uniqueId]?.drafts?.get(key)
        val view = LootEditorView(plugin, this, chamber, kind, existingDraft = draft)
        val gui = view.build(player)
        sessions[player.uniqueId]?.apply {
            screen = Screen.LOOT_EDITOR
            chamberId = chamber.id
            lootKind = kind
        }
        gui.show(player)
    }

    fun openAmountEditor(player: Player, chamber: Chamber, kind: LootKind, itemIndex: Int, isWeighted: Boolean) {
        val key = draftKey(chamber.id, kind)
        val draft = sessions[player.uniqueId]?.drafts?.get(key)
        if (draft == null) {
            // No draft found, return to loot editor
            openLootEditor(player, chamber, kind)
            return
        }
        val view = AmountEditorView(this, chamber, kind, itemIndex, isWeighted, draft)
        val gui = view.build()
        sessions[player.uniqueId]?.apply {
            screen = Screen.AMOUNT_EDITOR
            chamberId = chamber.id
            lootKind = kind
            this.itemIndex = itemIndex
            this.isWeighted = isWeighted
        }
        gui.show(player)
    }

    fun saveDraft(player: Player, chamber: Chamber, kind: LootKind, draft: LootEditorView.Draft) {
        val key = draftKey(chamber.id, kind)
        getOrCreateSession(player.uniqueId).drafts[key] = draft
    }

    private fun draftKey(chamberId: Int, kind: LootKind) = "${chamberId}:${kind.name}"
}