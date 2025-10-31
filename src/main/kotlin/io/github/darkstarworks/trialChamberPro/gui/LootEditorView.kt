package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.OutlinePane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.Chamber
import io.github.darkstarworks.trialChamberPro.models.LootItem
import io.github.darkstarworks.trialChamberPro.models.LootTable
import io.github.darkstarworks.trialChamberPro.models.VaultType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.ItemStack

class LootEditorView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val chamber: Chamber,
    private val kind: MenuService.LootKind,
    private val existingDraft: Draft? = null
) {
    data class Draft(
        var tableName: String,
        val guaranteed: MutableList<LootItem>,
        val weighted: MutableList<LootItem>,
        var minRolls: Int,
        var maxRolls: Int,
        var dirty: Boolean = false
    )

    private lateinit var gui: ChestGui
    private lateinit var contentPane: OutlinePane
    private lateinit var controlsPane: StaticPane
    private lateinit var draft: Draft
    private var discardRequested: Boolean = false

    fun build(player: Player): ChestGui {
        gui = ChestGui(6, title())

        // Disable all drag operations
        gui.setOnGlobalClick { event ->
            event.isCancelled = true
        }

        draft = existingDraft?.let { cloneDraft(it) } ?: loadInitialDraft()

        // Persist draft on close (unless user discarded)
        gui.setOnClose { _ ->
            if (!discardRequested) {
                menu.saveDraft(player, chamber, kind, draft)
            }
        }

        // Create panes and add to GUI BEFORE populating them
        contentPane = OutlinePane(0, 0, 9, 4).apply { isVisible = true }
        gui.addPane(contentPane)

        // Controls pane
        controlsPane = StaticPane(0, 4, 9, 2)
        gui.addPane(controlsPane)

        // Now populate panes
        refreshContent(player)
        buildControls(player)

        // Ensure first render shows all panes immediately
        gui.update()

        return gui
    }

    private fun title(): String = when (kind) {
        MenuService.LootKind.NORMAL -> "${chamber.name}: Normal Loot"
        MenuService.LootKind.OMINOUS -> "${chamber.name}: Ominous Loot"
    }

    private fun cloneDraft(source: Draft): Draft = Draft(
        tableName = source.tableName,
        guaranteed = source.guaranteed.toMutableList(),
        weighted = source.weighted.toMutableList(),
        minRolls = source.minRolls,
        maxRolls = source.maxRolls,
        dirty = source.dirty
    )

    private fun loadInitialDraft(): Draft {
        val baseName = when (kind) {
            MenuService.LootKind.NORMAL -> "chamber-${chamber.name.lowercase()}"
            MenuService.LootKind.OMINOUS -> "ominous-${chamber.name.lowercase()}"
        }
        val fallback = when (kind) {
            MenuService.LootKind.NORMAL -> "default"
            MenuService.LootKind.OMINOUS -> "ominous-default"
        }
        val source = plugin.lootManager.getTable(baseName) ?: plugin.lootManager.getTable(fallback)
        val table = source ?: LootTable(baseName, 1, 3, emptyList(), emptyList(), emptyList())
        return Draft(
            tableName = table.name,
            guaranteed = table.guaranteedItems.toMutableList(),
            weighted = table.weightedItems.toMutableList(),
            minRolls = table.minRolls,
            maxRolls = table.maxRolls
        )
    }

    private fun refreshContent(player: Player) {
        contentPane.clear()

        // Calculate total weight for percentage display
        val totalWeight = draft.weighted.filter { it.enabled }.sumOf { it.weight }

        // Show weighted items first
        draft.weighted.forEachIndexed { idx, li ->
            val item = renderLootItem(li, weighted = true, totalWeight = totalWeight)
            contentPane.addItem(GuiItem(item) { e ->
                e.isCancelled = true
                handleItemClick(idx, weighted = true, e.click, player)
            })
        }

        // Show guaranteed items
        draft.guaranteed.forEachIndexed { idx, li ->
            val item = renderLootItem(li, weighted = false, totalWeight = 0.0)
            contentPane.addItem(GuiItem(item) { e ->
                e.isCancelled = true
                handleItemClick(idx, weighted = false, e.click, player)
            })
        }

        // CRITICAL: Update the GUI to reflect changes immediately
        gui.update()
    }

    private fun renderLootItem(li: LootItem, weighted: Boolean, totalWeight: Double): ItemStack {
        val item = ItemStack(li.type)
        item.itemMeta = item.itemMeta?.apply {
            val name = if (weighted) "Chance: ${li.type.name}" else "Guaranteed: ${li.type.name}"
            displayName(Component.text(name, if (li.enabled) NamedTextColor.AQUA else NamedTextColor.DARK_GRAY))

            val lore = mutableListOf<Component>()
            lore += Component.text("Amount: ${li.amountMin}-${li.amountMax}", NamedTextColor.GRAY)

            // Display chance as percentage
            if (weighted) {
                if (totalWeight > 0.0 && li.enabled) {
                    val percentage = (li.weight / totalWeight) * 100.0
                    lore += Component.text("Chance: ${String.format("%.1f", percentage)}%", NamedTextColor.YELLOW)
                } else {
                    lore += Component.text("Weight: ${String.format("%.1f", li.weight)}", NamedTextColor.YELLOW)
                }
            }

            lore += Component.text(
                if (li.enabled) "✓ Enabled" else "✗ Disabled",
                if (li.enabled) NamedTextColor.GREEN else NamedTextColor.RED
            )
            lore += Component.text("", NamedTextColor.GRAY)
            lore += Component.text("━━━ Controls ━━━", NamedTextColor.DARK_GRAY)
            if (weighted) {
                lore += Component.text("Shift+Left: Higher Drop Chance", NamedTextColor.GRAY)
                lore += Component.text("Shift+Right: Lower Drop Chance", NamedTextColor.GRAY)
            }
            lore += Component.text("Right Click: Edit Amounts", NamedTextColor.AQUA)
            lore += Component.text("Middle Click: Toggle", NamedTextColor.GRAY)
            lore(lore)
        }
        return item
    }

    private fun handleItemClick(
        index: Int,
        weighted: Boolean,
        clickType: ClickType,
        player: Player
    ) {
        val list = if (weighted) draft.weighted else draft.guaranteed
        if (index !in list.indices) return

        val item = list[index]
        var modified = false

        when (clickType) {
            ClickType.MIDDLE -> {
                // Toggle enabled/disabled
                list[index] = item.copy(enabled = !item.enabled)
                modified = true
            }
            ClickType.SHIFT_LEFT -> {
                if (weighted) {
                    // Shift+Left: Increase weight by 1
                    list[index] = item.copy(weight = (item.weight + 1.0).coerceAtMost(9999.0))
                    modified = true
                }
            }
            ClickType.SHIFT_RIGHT -> {
                if (weighted) {
                    // Shift+Right: Decrease weight by 1
                    list[index] = item.copy(weight = (item.weight - 1.0).coerceAtLeast(0.1))
                    modified = true
                }
            }
            ClickType.LEFT, ClickType.WINDOW_BORDER_LEFT -> {
                if (weighted) {
                    // Left: Increase weight by 1 for weighted items
                    list[index] = item.copy(weight = (item.weight + 1.0).coerceAtMost(9999.0))
                    modified = true
                }
            }
            ClickType.RIGHT, ClickType.WINDOW_BORDER_RIGHT -> {
                // Right: Open amount editor
                menu.openAmountEditor(player, chamber, kind, index, weighted)
            }
            else -> {
                // Ignore other click types
                return
            }
        }

        if (modified) {
            draft.dirty = true
            // Refresh the content pane to show changes immediately
            refreshContent(player)
            // Also refresh controls so the Save button lore reflects unsaved changes
            buildControls(player)
        }
    }

    private fun buildControls(player: Player) {
        controlsPane.clear()

        // Save button
        val save = ItemStack(Material.GREEN_CONCRETE).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("💾 Save", NamedTextColor.GREEN))
                lore(listOf(
                    Component.text("Apply changes and update loot tables", NamedTextColor.GRAY),
                    if (draft.dirty) Component.text("• Unsaved changes", NamedTextColor.YELLOW)
                    else Component.text("• No changes", NamedTextColor.GRAY)
                ))
            }
        }
        controlsPane.addItem(GuiItem(save) {
            it.isCancelled = true
            saveDraft(player)
            draft.dirty = false
            menu.saveDraft(player, chamber, kind, draft)
            menu.openLootKindSelect(player, chamber)
        }, 0, 1)

        // Discard button (bottom-right)
        val discard = ItemStack(Material.RED_CONCRETE).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("✖ Discard", NamedTextColor.RED))
                lore(listOf(
                    Component.text("Discard changes and go back", NamedTextColor.GRAY)
                ))
            }
        }
        controlsPane.addItem(GuiItem(discard) {
            it.isCancelled = true
            // Mark discard so onClose won't save the cloned draft
            discardRequested = true
            menu.openLootKindSelect(player, chamber)
        }, 8, 1)

        // Add from hand button
        val add = ItemStack(Material.LIME_DYE).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("➕ Add from Hand", NamedTextColor.GREEN))
                lore(listOf(
                    Component.text("Add your held item", NamedTextColor.GRAY),
                    Component.text("Added as weighted (weight: 1.0)", NamedTextColor.GRAY)
                ))
            }
        }
        controlsPane.addItem(GuiItem(add) {
            it.isCancelled = true
            val hand = player.inventory.itemInMainHand
            if (hand.type == Material.AIR) {
                player.sendMessage(Component.text("Hold an item to add!", NamedTextColor.RED))
                return@GuiItem
            }

            val newItem = LootItem(
                type = hand.type,
                amountMin = 1,
                amountMax = hand.amount.coerceAtLeast(1),
                weight = 1.0,
                name = null,
                lore = null,
                enchantments = null,
                enabled = true
            )
            draft.weighted.add(newItem)
            draft.dirty = true

            player.sendMessage(Component.text("Added ${hand.type.name} to loot table", NamedTextColor.GREEN))

            // Refresh content and controls immediately without reopening
            refreshContent(player)
            buildControls(player)
        }, 4, 1)

        // Rolls editor
        val rolls = ItemStack(Material.PAPER).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("🎲 Rolls Configuration", NamedTextColor.YELLOW))
                lore(listOf(
                    Component.text("Min Rolls: ${draft.minRolls}", NamedTextColor.AQUA),
                    Component.text("  Left: +1  |  Right: -1", NamedTextColor.GRAY),
                    Component.text("Max Rolls: ${draft.maxRolls}", NamedTextColor.AQUA),
                    Component.text("  Shift+Left: +1  |  Shift+Right: -1", NamedTextColor.GRAY)
                ))
            }
        }
        controlsPane.addItem(GuiItem(rolls) {
            it.isCancelled = true
            val left = it.isLeftClick
            val right = it.isRightClick
            val shift = it.isShiftClick

            var changed = false

            if (!shift && left) {
                draft.minRolls = (draft.minRolls + 1).coerceAtMost(64)
                changed = true
            }
            if (!shift && right) {
                draft.minRolls = (draft.minRolls - 1).coerceAtLeast(0)
                changed = true
            }
            if (shift && left) {
                draft.maxRolls = (draft.maxRolls + 1).coerceAtMost(64)
                changed = true
            }
            if (shift && right) {
                draft.maxRolls = (draft.maxRolls - 1).coerceAtLeast(draft.minRolls)
                changed = true
            }

            // Ensure max >= min
            if (draft.maxRolls < draft.minRolls) {
                draft.maxRolls = draft.minRolls
            }

            if (changed) {
                draft.dirty = true
                // Refresh controls to update the rolls display immediately
                buildControls(player)
            }
        }, 2, 1)

        // CRITICAL: Update GUI to reflect control changes
        gui.update()
    }

    private fun saveDraft(player: Player) {
        val table = LootTable(
            name = draft.tableName,
            minRolls = draft.minRolls,
            maxRolls = draft.maxRolls,
            guaranteedItems = draft.guaranteed.toList(),
            weightedItems = draft.weighted.toList(),
            commandRewards = plugin.lootManager.getTable(draft.tableName)?.commandRewards ?: emptyList()
        )
        plugin.lootManager.updateTable(table)

        // Persist to loot.yml
        plugin.lootManager.saveAllToFile()

        // Update DB vaults for this chamber/type to use this table name
        val type = if (kind == MenuService.LootKind.OMINOUS) VaultType.OMINOUS else VaultType.NORMAL
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                plugin.vaultManager.setLootTableForChamber(chamber.id, type, draft.tableName)
            } catch (e: Exception) {
                plugin.logger.warning("Failed to update vault loot table: ${e.message}")
            }
        })

        player.sendMessage(Component.text("✓ Saved loot table '${draft.tableName}'", NamedTextColor.GREEN))
    }
}