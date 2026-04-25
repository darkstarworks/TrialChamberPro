package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.OutlinePane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.components.GuiText
import io.github.darkstarworks.trialChamberPro.models.LootTable
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Loot table list view — browse all loot tables for direct editing.
 * All strings from `messages.yml` under `gui.loot-table-list.*` (v1.3.0).
 */
class LootTableListView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(6, GuiText.plain(plugin, "gui.loot-table-list.title"))
        val pane = StaticPane(0, 0, 9, 6)

        val tables = plugin.lootManager.getLootTableNames().sorted()
        pane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.CHEST,
                "gui.loot-table-list.header-name", "gui.loot-table-list.header-lore",
                "count" to tables.size)
        ) { it.isCancelled = true }, 4, 0)

        val tablePane = OutlinePane(0, 1, 9, 4)
        tables.forEach { tableName ->
            val table = plugin.lootManager.getTable(tableName)
            tablePane.addItem(GuiItem(createTableItem(tableName, table)) { event ->
                event.isCancelled = true
                if (table != null && !table.isLegacyFormat()) menu.openGlobalPoolSelect(player, tableName)
                else menu.openGlobalLootEditor(player, tableName)
            })
        }
        gui.addPane(tablePane)

        if (tables.isEmpty()) {
            val emptyPane = StaticPane(0, 2, 9, 1)
            emptyPane.addItem(GuiItem(
                GuiComponents.infoItem(plugin, Material.BARRIER,
                    "gui.loot-table-list.empty-name", "gui.loot-table-list.empty-lore")
            ) { it.isCancelled = true }, 4, 0)
            gui.addPane(emptyPane)
        }

        pane.addItem(GuiComponents.backButton(plugin, "gui.common.dest-main-menu") {
            menu.openMainMenu(player)
        }, 0, 5)
        pane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.LIME_CONCRETE,
                "gui.loot-table-list.create-name", "gui.loot-table-list.create-lore")
        ) { it.isCancelled = true }, 4, 5)
        pane.addItem(GuiComponents.closeButton(plugin, player), 8, 5)

        gui.addPane(pane)
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }
        return gui
    }

    private fun createTableItem(name: String, table: LootTable?): ItemStack {
        val isOminous = name.startsWith("ominous")
        val material = if (isOminous) Material.PURPLE_WOOL else Material.GREEN_WOOL
        val nameKey = if (isOminous)
            "gui.loot-table-list.table-name-ominous" else "gui.loot-table-list.table-name-normal"

        val poolCount = table?.let { if (it.isLegacyFormat()) 1 else it.pools.size } ?: 0
        val itemCount = table?.let {
            if (it.isLegacyFormat()) it.guaranteedItems.size + it.weightedItems.size
            else it.pools.sumOf { p -> p.guaranteedItems.size + p.weightedItems.size }
        } ?: 0

        val loreKey = if (table?.isLegacyFormat() == true)
            "gui.loot-table-list.table-lore-legacy" else "gui.loot-table-list.table-lore-multi"

        return GuiComponents.infoItem(plugin, material, nameKey, loreKey,
            "name" to name, "pools" to poolCount, "items" to itemCount)
    }
}
