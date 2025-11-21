package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.models.Chamber
import io.github.darkstarworks.trialChamberPro.models.LootItem
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class AmountEditorView(
    private val menu: MenuService,
    private val chamber: Chamber,
    private val kind: MenuService.LootKind,
    private val poolName: String? = null,
    private val itemIndex: Int,
    private val isWeighted: Boolean,
    private val draft: LootEditorView.Draft
) {
    private lateinit var gui: ChestGui
    private lateinit var mainPane: StaticPane
    private var currentItem: LootItem

    init {
        val list = if (isWeighted) draft.weighted else draft.guaranteed
        currentItem = list[itemIndex].copy()
    }

    fun build(): ChestGui {
        gui = ChestGui(3, "Edit Amount: ${currentItem.type.name}")

        // Disable all drag operations
        gui.setOnGlobalClick { event ->
            event.isCancelled = true
        }

        // IMPORTANT: add the pane to the GUI before populating it
        mainPane = StaticPane(0, 0, 9, 3)
        gui.addPane(mainPane)
        buildContent()

        return gui
    }

    private fun buildContent() {
        mainPane.clear()

        // Display the item top-center with info
        val displayItem = createDisplayItem()
        mainPane.addItem(GuiItem(displayItem) { it.isCancelled = true }, 4, 0)

        // Adjustment buttons on row 2
        // +-1 button at position 2
        val adjust1 = createAdjustButton(1)
        mainPane.addItem(GuiItem(adjust1) { event ->
            event.isCancelled = true
            handleAdjustClick(1, event.isLeftClick, event.isRightClick, event.isShiftClick)
        }, 2, 1)

        // +-5 button at position 3
        val adjust5 = createAdjustButton(5)
        mainPane.addItem(GuiItem(adjust5) { event ->
            event.isCancelled = true
            handleAdjustClick(5, event.isLeftClick, event.isRightClick, event.isShiftClick)
        }, 3, 1)

        // +-10 button at position 4
        val adjust10 = createAdjustButton(10)
        mainPane.addItem(GuiItem(adjust10) { event ->
            event.isCancelled = true
            handleAdjustClick(10, event.isLeftClick, event.isRightClick, event.isShiftClick)
        }, 4, 1)

        // Reset to 1-1 button at position 6
        val reset = createResetButton()
        mainPane.addItem(GuiItem(reset) { event ->
            event.isCancelled = true
            handleResetClick(event.isLeftClick, event.isRightClick)
        }, 6, 1)

        // Bottom row: Save and Discard
        val save = ItemStack(Material.GREEN_CONCRETE).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("💾 Save", NamedTextColor.GREEN))
                lore(listOf(
                    Component.text("Apply changes and return", NamedTextColor.GRAY)
                ))
            }
        }
        mainPane.addItem(GuiItem(save) {
            it.isCancelled = true
            saveAndReturn(it.whoClicked as Player)
        }, 0, 2)

        val discard = ItemStack(Material.RED_CONCRETE).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("✖ Discard", NamedTextColor.RED))
                lore(listOf(
                    Component.text("Cancel changes and return", NamedTextColor.GRAY)
                ))
            }
        }
        mainPane.addItem(GuiItem(discard) {
            it.isCancelled = true
            discardAndReturn(it.whoClicked as Player)
        }, 8, 2)

        gui.update()
    }

    private fun createDisplayItem(): ItemStack {
        val item = ItemStack(currentItem.type)

        // Calculate chance percentage if weighted
        val totalWeight = if (isWeighted) {
            draft.weighted.filter { it.enabled }.sumOf { it.weight }
        } else {
            0.0
        }

        item.itemMeta = item.itemMeta?.apply {
            displayName(Component.text(currentItem.type.name, NamedTextColor.AQUA))

            val lore = mutableListOf<Component>()
            lore += Component.text("Amount: ${currentItem.amountMin}-${currentItem.amountMax}", NamedTextColor.YELLOW)

            if (isWeighted && totalWeight > 0.0 && currentItem.enabled) {
                val percentage = (currentItem.weight / totalWeight) * 100.0
                lore += Component.text("Chance: ${String.format("%.1f", percentage)}%", NamedTextColor.YELLOW)
            }

            lore += Component.text(
                if (currentItem.enabled) "✓ Enabled" else "✗ Disabled",
                if (currentItem.enabled) NamedTextColor.GREEN else NamedTextColor.RED
            )

            lore(lore)
        }
        return item
    }

    private fun createAdjustButton(amount: Int): ItemStack {
        val item = ItemStack(Material.YELLOW_CONCRETE)
        item.itemMeta = item.itemMeta?.apply {
            displayName(Component.text("Adjust Amount", NamedTextColor.GOLD))
            lore(listOf(
                Component.text("", NamedTextColor.GRAY),
                Component.text("Left: Minimum +$amount", NamedTextColor.AQUA),
                Component.text("Right: Minimum -$amount", NamedTextColor.AQUA),
                Component.text("", NamedTextColor.GRAY),
                Component.text("Shift + Left: Maximum +$amount", NamedTextColor.LIGHT_PURPLE),
                Component.text("Shift + Right: Maximum -$amount", NamedTextColor.LIGHT_PURPLE)
            ))
        }
        return item
    }

    private fun createResetButton(): ItemStack {
        val item = ItemStack(Material.CYAN_CONCRETE)
        item.itemMeta = item.itemMeta?.apply {
            displayName(Component.text("Reset to drop only 1", NamedTextColor.DARK_AQUA))
            lore(listOf(
                Component.text("", NamedTextColor.GRAY),
                Component.text("Left: Set Minimum to 1", NamedTextColor.AQUA),
                Component.text("Right: Set Maximum to 1", NamedTextColor.AQUA)
            ))
        }
        return item
    }

    private fun handleAdjustClick(amount: Int, left: Boolean, right: Boolean, shift: Boolean) {
        when {
            shift && left -> {
                // Increase maximum
                currentItem = currentItem.copy(
                    amountMax = (currentItem.amountMax + amount).coerceIn(1, 64)
                )
            }
            shift && right -> {
                // Decrease maximum (but not below minimum)
                currentItem = currentItem.copy(
                    amountMax = (currentItem.amountMax - amount).coerceIn(currentItem.amountMin, 64)
                )
            }
            left -> {
                // Increase minimum (but not above maximum)
                currentItem = currentItem.copy(
                    amountMin = (currentItem.amountMin + amount).coerceIn(1, currentItem.amountMax)
                )
            }
            right -> {
                // Decrease minimum
                currentItem = currentItem.copy(
                    amountMin = (currentItem.amountMin - amount).coerceIn(1, 64)
                )
            }
        }

        buildContent()
    }

    private fun handleResetClick(left: Boolean, right: Boolean) {
        when {
            left -> {
                // Set minimum to 1
                currentItem = currentItem.copy(amountMin = 1)
            }
            right -> {
                // Set maximum to 1
                currentItem = currentItem.copy(
                    amountMax = 1,
                    amountMin = currentItem.amountMin.coerceAtMost(1)
                )
            }
        }

        buildContent()
    }

    private fun saveAndReturn(player: Player) {
        // Update the item in the draft
        val list = if (isWeighted) draft.weighted else draft.guaranteed
        list[itemIndex] = currentItem
        draft.dirty = true

        // Save the draft and return to loot editor
        menu.saveDraft(player, chamber, kind, poolName, draft)
        menu.openLootEditor(player, chamber, kind, poolName)
    }

    private fun discardAndReturn(player: Player) {
        // Just return to loot editor without saving
        menu.openLootEditor(player, chamber, kind, poolName)
    }
}