package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.components.GuiText
import io.github.darkstarworks.trialChamberPro.models.Chamber
import io.github.darkstarworks.trialChamberPro.models.LootEditorDraft
import io.github.darkstarworks.trialChamberPro.models.LootItem
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Amount editor — adjusts min/max stack range for a single LootItem in a draft.
 * All strings from `messages.yml` under `gui.amount-editor.*` (v1.3.0).
 */
class AmountEditorView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val chamber: Chamber?,
    private val kind: MenuService.LootKind,
    private val poolName: String? = null,
    private val itemIndex: Int,
    private val isWeighted: Boolean,
    private val draft: LootEditorDraft,
    private val globalTableName: String? = null
) {
    init {
        require(chamber != null || globalTableName != null) {
            "AmountEditorView requires either a chamber or a globalTableName"
        }
    }

    private lateinit var gui: ChestGui
    private lateinit var mainPane: StaticPane
    private var currentItem: LootItem = (if (isWeighted) draft.weighted else draft.guaranteed)[itemIndex].copy()

    fun build(): ChestGui {
        gui = ChestGui(3, GuiText.plain(plugin, "gui.amount-editor.title", "item" to currentItem.type.name))
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }
        mainPane = StaticPane(0, 0, 9, 3)
        gui.addPane(mainPane)
        buildContent()
        return gui
    }

    private fun buildContent() {
        mainPane.clear()

        mainPane.addItem(GuiItem(createDisplayItem()) { it.isCancelled = true }, 4, 0)

        listOf(1, 5, 10).forEachIndexed { i, amount ->
            mainPane.addItem(GuiItem(createAdjustButton(amount)) { event ->
                event.isCancelled = true
                handleAdjustClick(amount, event.isLeftClick, event.isRightClick, event.isShiftClick)
            }, 2 + i, 1)
        }

        mainPane.addItem(GuiItem(createResetButton()) { event ->
            event.isCancelled = true
            handleResetClick(event.isLeftClick, event.isRightClick)
        }, 6, 1)

        mainPane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.GREEN_CONCRETE,
                "gui.amount-editor.save-name", "gui.amount-editor.save-lore")
        ) { event ->
            event.isCancelled = true
            saveAndReturn(event.whoClicked as Player)
        }, 0, 2)

        mainPane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.RED_CONCRETE,
                "gui.amount-editor.discard-name", "gui.amount-editor.discard-lore")
        ) { event ->
            event.isCancelled = true
            discardAndReturn(event.whoClicked as Player)
        }, 8, 2)

        gui.update()
    }

    private fun createDisplayItem(): ItemStack {
        val item = ItemStack(currentItem.type)
        val totalWeight = if (isWeighted) draft.weighted.filter { it.enabled }.sumOf { it.weight } else 0.0

        val lore = mutableListOf<Component>()
        lore += plugin.getGuiText("gui.amount-editor.display-amount",
            "min" to currentItem.amountMin, "max" to currentItem.amountMax)
        if (isWeighted && totalWeight > 0.0 && currentItem.enabled) {
            val percentage = (currentItem.weight / totalWeight) * 100.0
            lore += plugin.getGuiText("gui.amount-editor.display-chance",
                "percent" to String.format("%.1f", percentage))
        }
        lore += plugin.getGuiText(
            if (currentItem.enabled) "gui.amount-editor.display-enabled" else "gui.amount-editor.display-disabled"
        )

        item.itemMeta = item.itemMeta?.apply {
            displayName(plugin.getGuiText("gui.amount-editor.display-name", "item" to currentItem.type.name))
            lore(lore)
        }
        return item
    }

    private fun createAdjustButton(amount: Int): ItemStack {
        return GuiComponents.infoItem(plugin, Material.YELLOW_CONCRETE,
            "gui.amount-editor.adjust-name", "gui.amount-editor.adjust-lore",
            "amount" to amount)
    }

    private fun createResetButton(): ItemStack {
        return GuiComponents.infoItem(plugin, Material.CYAN_CONCRETE,
            "gui.amount-editor.reset-name", "gui.amount-editor.reset-lore")
    }

    private fun handleAdjustClick(amount: Int, left: Boolean, right: Boolean, shift: Boolean) {
        when {
            shift && left -> currentItem = currentItem.copy(
                amountMax = (currentItem.amountMax + amount).coerceIn(1, 64))
            shift && right -> currentItem = currentItem.copy(
                amountMax = (currentItem.amountMax - amount).coerceIn(currentItem.amountMin, 64))
            left -> currentItem = currentItem.copy(
                amountMin = (currentItem.amountMin + amount).coerceIn(1, currentItem.amountMax))
            right -> currentItem = currentItem.copy(
                amountMin = (currentItem.amountMin - amount).coerceIn(1, 64))
        }
        buildContent()
    }

    private fun handleResetClick(left: Boolean, right: Boolean) {
        when {
            left -> currentItem = currentItem.copy(amountMin = 1)
            right -> currentItem = currentItem.copy(
                amountMax = 1, amountMin = currentItem.amountMin.coerceAtMost(1))
        }
        buildContent()
    }

    private fun saveAndReturn(player: Player) {
        val list = if (isWeighted) draft.weighted else draft.guaranteed
        list[itemIndex] = currentItem
        draft.dirty = true
        if (chamber != null) {
            menu.saveDraft(player, chamber, kind, poolName, draft)
            menu.openLootEditor(player, chamber, kind, poolName)
        } else {
            menu.saveGlobalDraft(player, globalTableName!!, poolName, draft)
            menu.openGlobalLootEditor(player, globalTableName, poolName)
        }
    }

    private fun discardAndReturn(player: Player) {
        if (chamber != null) menu.openLootEditor(player, chamber, kind, poolName)
        else menu.openGlobalLootEditor(player, globalTableName!!, poolName)
    }
}
