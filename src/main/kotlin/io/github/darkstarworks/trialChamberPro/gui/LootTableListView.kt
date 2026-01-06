package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.OutlinePane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Loot table list view - displays all available loot tables for direct editing.
 */
class LootTableListView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(6, "Loot Tables")
        val pane = StaticPane(0, 0, 9, 6)

        // Row 0: Header
        pane.addItem(GuiItem(createHeaderItem()) { it.isCancelled = true }, 4, 0)

        // Get all loot tables
        val tables = plugin.lootManager.getLootTableNames().sorted()

        // Table grid (rows 1-4)
        val tablePane = OutlinePane(0, 1, 9, 4)
        tables.forEach { tableName ->
            val table = plugin.lootManager.getTable(tableName)
            tablePane.addItem(GuiItem(createTableItem(tableName, table)) { event ->
                event.isCancelled = true
                // Show info about how to edit
                player.sendMessage(plugin.getMessage("gui-loot-table-edit-hint-1", "table" to tableName))
                player.sendMessage(plugin.getMessage("gui-loot-table-edit-hint-2"))
                player.sendMessage(plugin.getMessage("gui-loot-table-edit-hint-3"))
                player.sendMessage(plugin.getMessage("gui-loot-table-edit-hint-4"))
            })
        }
        gui.addPane(tablePane)

        // Row 5: Navigation
        val backItem = ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Back to Main Menu", NamedTextColor.YELLOW))
            }
        }
        pane.addItem(GuiItem(backItem) { event ->
            event.isCancelled = true
            menu.openMainMenu(player)
        }, 0, 5)

        // Create table info
        val createInfo = ItemStack(Material.LIME_CONCRETE).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Create Loot Table", NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("To create a new loot table:", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("1. Edit loot.yml", NamedTextColor.YELLOW),
                    Component.text("2. Add a new table section", NamedTextColor.YELLOW),
                    Component.text("3. Run /tcp reload", NamedTextColor.YELLOW)
                ))
            }
        }
        pane.addItem(GuiItem(createInfo) { it.isCancelled = true }, 4, 5)

        val closeItem = ItemStack(Material.BARRIER).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Close", NamedTextColor.RED))
            }
        }
        pane.addItem(GuiItem(closeItem) { event ->
            event.isCancelled = true
            player.closeInventory()
        }, 8, 5)

        gui.addPane(pane)
        gui.setOnGlobalClick { it.isCancelled = true }

        return gui
    }

    private fun createHeaderItem(): ItemStack {
        val tableCount = plugin.lootManager.getLootTableNames().size

        return ItemStack(Material.CHEST).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Loot Tables", NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("$tableCount tables loaded", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click a table for info", NamedTextColor.YELLOW)
                ))
            }
        }
    }

    private fun createTableItem(name: String, table: io.github.darkstarworks.trialChamberPro.models.LootTable?): ItemStack {
        val isOminous = name.startsWith("ominous")
        val material = if (isOminous) Material.PURPLE_WOOL else Material.GREEN_WOOL
        val color = if (isOminous) NamedTextColor.DARK_PURPLE else NamedTextColor.GREEN

        val poolCount = table?.let {
            if (it.isLegacyFormat()) 1 else it.pools.size
        } ?: 0

        val itemCount = table?.let {
            if (it.isLegacyFormat()) {
                it.guaranteedItems.size + it.weightedItems.size
            } else {
                it.pools.sumOf { pool -> pool.guaranteedItems.size + pool.weightedItems.size }
            }
        } ?: 0

        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text(name, color)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Pools: $poolCount", NamedTextColor.GRAY),
                    Component.text("Items: $itemCount", NamedTextColor.GRAY),
                    Component.empty(),
                    if (table?.isLegacyFormat() == true) {
                        Component.text("Format: Legacy (single pool)", NamedTextColor.YELLOW)
                    } else {
                        Component.text("Format: Multi-pool", NamedTextColor.YELLOW)
                    },
                    Component.empty(),
                    Component.text("Click for edit info", NamedTextColor.GREEN)
                ))
            }
        }
    }
}
