package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.OutlinePane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.components.GuiText
import io.github.darkstarworks.trialChamberPro.models.Chamber
import io.github.darkstarworks.trialChamberPro.models.LootEditorDraft
import io.github.darkstarworks.trialChamberPro.models.LootItem
import io.github.darkstarworks.trialChamberPro.models.LootTable
import io.github.darkstarworks.trialChamberPro.models.VaultType
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack

/**
 * Loot editor — edits a single loot table (or one pool of a multi-pool table).
 * All strings from `messages.yml` under `gui.loot-editor.*` (v1.3.0).
 */
class LootEditorView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val chamber: Chamber?,
    private val kind: MenuService.LootKind,
    private val poolName: String? = null,
    private val existingDraft: LootEditorDraft? = null,
    private val globalTableName: String? = null
) {
    init {
        require(chamber != null || globalTableName != null) {
            "LootEditorView requires either a chamber or a globalTableName"
        }
    }

    private lateinit var gui: ChestGui
    private lateinit var contentPane: OutlinePane
    private lateinit var controlsPane: StaticPane
    private lateinit var draft: LootEditorDraft
    private var discardRequested: Boolean = false

    fun build(player: Player): ChestGui {
        gui = ChestGui(6, title())
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }

        draft = existingDraft?.let { cloneDraft(it) } ?: loadInitialDraft()

        gui.setOnClose {
            if (!discardRequested) {
                if (chamber != null) menu.saveDraft(player, chamber, kind, poolName, draft)
                else menu.saveGlobalDraft(player, globalTableName!!, poolName, draft)
            }
        }

        contentPane = OutlinePane(0, 0, 9, 4).apply { isVisible = true }
        gui.addPane(contentPane)
        controlsPane = StaticPane(0, 4, 9, 2)
        gui.addPane(controlsPane)

        refreshContent(player)
        buildControls(player)
        gui.update()
        return gui
    }

    private fun title(): String {
        val baseTitle = if (chamber != null) {
            val key = when (kind) {
                MenuService.LootKind.NORMAL -> "gui.loot-editor.title-chamber-normal"
                MenuService.LootKind.OMINOUS -> "gui.loot-editor.title-chamber-ominous"
            }
            GuiText.plain(plugin, key, "chamber" to chamber.name)
        } else {
            GuiText.plain(plugin, "gui.loot-editor.title-global", "table" to (globalTableName ?: ""))
        }
        return if (poolName != null) {
            baseTitle + GuiText.plain(plugin, "gui.loot-editor.title-pool-suffix", "pool" to poolName)
        } else baseTitle
    }

    private fun cloneDraft(source: LootEditorDraft): LootEditorDraft = LootEditorDraft(
        tableName = source.tableName,
        guaranteed = source.guaranteed.toMutableList(),
        weighted = source.weighted.toMutableList(),
        minRolls = source.minRolls,
        maxRolls = source.maxRolls,
        dirty = source.dirty
    )

    private fun loadInitialDraft(): LootEditorDraft {
        val baseName: String
        val source: LootTable?
        if (chamber != null) {
            baseName = when (kind) {
                MenuService.LootKind.NORMAL -> "chamber-${chamber.name.lowercase()}"
                MenuService.LootKind.OMINOUS -> "ominous-${chamber.name.lowercase()}"
            }
            val fallback = when (kind) {
                MenuService.LootKind.NORMAL -> "default"
                MenuService.LootKind.OMINOUS -> "ominous-default"
            }
            source = plugin.lootManager.getTable(baseName) ?: plugin.lootManager.getTable(fallback)
        } else {
            baseName = globalTableName!!
            source = plugin.lootManager.getTable(baseName)
        }

        if (poolName != null && source != null && !source.isLegacyFormat()) {
            val pool = source.pools.find { it.name == poolName }
            if (pool != null) {
                return LootEditorDraft(
                    tableName = baseName,
                    guaranteed = pool.guaranteedItems.toMutableList(),
                    weighted = pool.weightedItems.toMutableList(),
                    minRolls = pool.minRolls,
                    maxRolls = pool.maxRolls
                )
            }
        }

        val table = source ?: LootTable(baseName, 1, 3, emptyList(), emptyList(), emptyList())
        return LootEditorDraft(
            tableName = table.name,
            guaranteed = table.guaranteedItems.toMutableList(),
            weighted = table.weightedItems.toMutableList(),
            minRolls = table.minRolls,
            maxRolls = table.maxRolls
        )
    }

    private fun refreshContent(player: Player) {
        contentPane.clear()
        val totalWeight = draft.weighted.filter { it.enabled }.sumOf { it.weight }

        draft.weighted.forEachIndexed { idx, li ->
            contentPane.addItem(GuiItem(renderLootItem(li, weighted = true, totalWeight)) { e ->
                e.isCancelled = true
                handleItemClick(idx, weighted = true, e.click, player)
            })
        }
        draft.guaranteed.forEachIndexed { idx, li ->
            contentPane.addItem(GuiItem(renderLootItem(li, weighted = false, 0.0)) { e ->
                e.isCancelled = true
                handleItemClick(idx, weighted = false, e.click, player)
            })
        }

        if (draft.weighted.isEmpty() && draft.guaranteed.isEmpty()) {
            contentPane.addItem(GuiItem(
                GuiComponents.infoItem(plugin, Material.BARRIER,
                    "gui.loot-editor.empty-name", "gui.loot-editor.empty-lore")
            ) { it.isCancelled = true })
        }
        gui.update()
    }

    private fun renderLootItem(li: LootItem, weighted: Boolean, totalWeight: Double): ItemStack {
        val nameKey = when {
            weighted && li.enabled -> "gui.loot-editor.item-name-weighted-enabled"
            weighted -> "gui.loot-editor.item-name-weighted-disabled"
            li.enabled -> "gui.loot-editor.item-name-guaranteed-enabled"
            else -> "gui.loot-editor.item-name-guaranteed-disabled"
        }

        val lore = mutableListOf<Component>()
        lore += plugin.getGuiText("gui.loot-editor.item-amount",
            "min" to li.amountMin, "max" to li.amountMax)
        if (weighted) {
            if (totalWeight > 0.0 && li.enabled) {
                val pct = String.format("%.1f", (li.weight / totalWeight) * 100.0)
                lore += plugin.getGuiText("gui.loot-editor.item-chance", "percent" to pct)
            } else {
                lore += plugin.getGuiText("gui.loot-editor.item-weight",
                    "weight" to String.format("%.1f", li.weight))
            }
        }
        lore += plugin.getGuiText(if (li.enabled) "gui.loot-editor.item-enabled" else "gui.loot-editor.item-disabled")
        lore += Component.empty()
        lore += plugin.getGuiText("gui.loot-editor.item-controls-header")
        if (weighted) {
            lore += plugin.getGuiText("gui.loot-editor.item-controls-shift-left")
            lore += plugin.getGuiText("gui.loot-editor.item-controls-shift-right")
        }
        lore += plugin.getGuiText("gui.loot-editor.item-controls-right")
        lore += plugin.getGuiText("gui.loot-editor.item-controls-middle")

        val item = ItemStack(li.type)
        item.itemMeta = item.itemMeta?.apply {
            displayName(plugin.getGuiText(nameKey, "item" to li.type.name))
            lore(lore)
        }
        return item
    }

    private fun handleItemClick(index: Int, weighted: Boolean, clickType: ClickType, player: Player) {
        val list = if (weighted) draft.weighted else draft.guaranteed
        if (index !in list.indices) return
        val item = list[index]
        var modified = false

        when (clickType) {
            ClickType.MIDDLE -> {
                list[index] = item.copy(enabled = !item.enabled); modified = true
            }
            ClickType.SHIFT_LEFT -> if (weighted) {
                list[index] = item.copy(weight = (item.weight + 1.0).coerceAtMost(9999.0)); modified = true
            }
            ClickType.SHIFT_RIGHT -> if (weighted) {
                list[index] = item.copy(weight = (item.weight - 1.0).coerceAtLeast(0.1)); modified = true
            }
            ClickType.LEFT, ClickType.WINDOW_BORDER_LEFT -> if (weighted) {
                list[index] = item.copy(weight = (item.weight + 1.0).coerceAtMost(9999.0)); modified = true
            }
            ClickType.RIGHT, ClickType.WINDOW_BORDER_RIGHT -> {
                if (chamber != null) menu.openAmountEditor(player, chamber, kind, index, weighted)
                else menu.openGlobalAmountEditor(player, globalTableName!!, poolName, index, weighted)
            }
            else -> return
        }

        if (modified) {
            draft.dirty = true
            refreshContent(player)
            buildControls(player)
        }
    }

    private fun buildControls(player: Player) {
        controlsPane.clear()

        val saveLoreKey = if (draft.dirty) "gui.loot-editor.save-lore-dirty" else "gui.loot-editor.save-lore-clean"
        val save = GuiComponents.infoItem(plugin, Material.GREEN_CONCRETE,
            "gui.loot-editor.save-name", saveLoreKey)
        controlsPane.addItem(GuiItem(save) {
            it.isCancelled = true
            saveDraft(player)
            draft.dirty = false
            if (chamber != null) {
                menu.saveDraft(player, chamber, kind, poolName, draft)
                if (poolName != null) menu.openPoolSelect(player, chamber, kind)
                else @Suppress("DEPRECATION") menu.openLootKindSelect(player, chamber)
            } else {
                menu.saveGlobalDraft(player, globalTableName!!, poolName, draft)
                if (poolName != null) menu.openGlobalPoolSelect(player, globalTableName)
                else menu.openLootTableList(player)
            }
        }, 0, 1)

        val discard = GuiComponents.infoItem(plugin, Material.RED_CONCRETE,
            "gui.loot-editor.discard-name", "gui.loot-editor.discard-lore")
        controlsPane.addItem(GuiItem(discard) {
            it.isCancelled = true
            discardRequested = true
            if (chamber != null) {
                if (poolName != null) menu.openPoolSelect(player, chamber, kind)
                else @Suppress("DEPRECATION") menu.openLootKindSelect(player, chamber)
            } else {
                if (poolName != null) menu.openGlobalPoolSelect(player, globalTableName!!)
                else menu.openLootTableList(player)
            }
        }, 8, 1)

        val add = GuiComponents.infoItem(plugin, Material.LIME_DYE,
            "gui.loot-editor.add-name", "gui.loot-editor.add-lore")
        controlsPane.addItem(GuiItem(add) {
            it.isCancelled = true
            val hand = player.inventory.itemInMainHand
            if (hand.type == Material.AIR) {
                player.sendMessage(plugin.getMessageComponent("gui-hold-item-to-add"))
                return@GuiItem
            }
            val newItem = LootItem(
                type = hand.type,
                amountMin = 1,
                amountMax = hand.amount.coerceAtLeast(1),
                weight = 1.0,
                name = null, lore = null, enchantments = null, enabled = true
            )
            draft.weighted.add(newItem)
            draft.dirty = true
            player.sendMessage(plugin.getMessageComponent("gui-item-added-to-loot", "item" to hand.type.name))
            refreshContent(player)
            buildControls(player)
        }, 4, 1)

        val rolls = GuiComponents.infoItem(plugin, Material.PAPER,
            "gui.loot-editor.rolls-name", "gui.loot-editor.rolls-lore",
            "min" to draft.minRolls, "max" to draft.maxRolls)
        controlsPane.addItem(GuiItem(rolls) {
            it.isCancelled = true
            val left = it.isLeftClick
            val right = it.isRightClick
            val shift = it.isShiftClick
            var changed = false
            if (!shift && left) { draft.minRolls = (draft.minRolls + 1).coerceAtMost(64); changed = true }
            if (!shift && right) { draft.minRolls = (draft.minRolls - 1).coerceAtLeast(0); changed = true }
            if (shift && left) { draft.maxRolls = (draft.maxRolls + 1).coerceAtMost(64); changed = true }
            if (shift && right) { draft.maxRolls = (draft.maxRolls - 1).coerceAtLeast(draft.minRolls); changed = true }
            if (draft.maxRolls < draft.minRolls) draft.maxRolls = draft.minRolls
            if (changed) {
                draft.dirty = true
                buildControls(player)
            }
        }, 2, 1)

        gui.update()
    }

    private fun saveDraft(player: Player) {
        val existingTable = plugin.lootManager.getTable(draft.tableName)
        val table = if (poolName != null && existingTable != null && !existingTable.isLegacyFormat()) {
            val updatedPools = existingTable.pools.map { pool ->
                if (pool.name == poolName) {
                    pool.copy(
                        minRolls = draft.minRolls, maxRolls = draft.maxRolls,
                        guaranteedItems = draft.guaranteed.toList(),
                        weightedItems = draft.weighted.toList()
                    )
                } else pool
            }
            LootTable(name = draft.tableName, pools = updatedPools)
        } else {
            LootTable(
                name = draft.tableName,
                minRolls = draft.minRolls, maxRolls = draft.maxRolls,
                guaranteedItems = draft.guaranteed.toList(),
                weightedItems = draft.weighted.toList(),
                commandRewards = existingTable?.commandRewards ?: emptyList()
            )
        }
        plugin.lootManager.updateTable(table)
        plugin.lootManager.saveAllToFile()

        if (chamber != null) {
            val type = if (kind == MenuService.LootKind.OMINOUS) VaultType.OMINOUS else VaultType.NORMAL
            plugin.launchAsync {
                try {
                    plugin.vaultManager.setLootTableForChamber(chamber.id, type, draft.tableName)
                } catch (e: Exception) {
                    plugin.logger.warning("Failed to update vault loot table: ${e.message}")
                }
            }
        }

        if (poolName != null) player.sendMessage(plugin.getMessageComponent("gui-loot-pool-saved", "pool" to poolName))
        else player.sendMessage(plugin.getMessageComponent("gui-loot-changes-saved"))
    }
}
