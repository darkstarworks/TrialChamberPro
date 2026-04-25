package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Main menu view — the central hub for the TrialChamberPro admin GUI.
 * All strings are sourced from `messages.yml` under `gui.main-menu.*` (v1.3.0).
 */
class MainMenuView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(4, plainText("gui.main-menu.title"))
        val pane = StaticPane(0, 0, 9, 4)

        pane.addItem(GuiItem(createHeaderItem()) { it.isCancelled = true }, 4, 0)

        // Row 1
        pane.addItem(GuiItem(createChambersItem()) { event ->
            event.isCancelled = true
            menu.openChamberList(player)
        }, 2, 1)

        pane.addItem(GuiItem(createLootTablesItem()) { event ->
            event.isCancelled = true
            menu.openLootTableList(player)
        }, 4, 1)

        pane.addItem(GuiItem(createStatisticsItem()) { event ->
            event.isCancelled = true
            menu.openStatsMenu(player)
        }, 6, 1)

        // Row 2
        pane.addItem(GuiItem(createSettingsItem()) { event ->
            event.isCancelled = true
            menu.openSettingsMenu(player)
        }, 2, 2)

        pane.addItem(GuiItem(createProtectionItem()) { event ->
            event.isCancelled = true
            menu.openProtectionMenu(player)
        }, 4, 2)

        pane.addItem(GuiItem(createHelpItem()) { event ->
            event.isCancelled = true
            menu.openHelpMenu(player)
        }, 6, 2)

        // Close (bottom right)
        pane.addItem(GuiComponents.closeButton(plugin, player), 8, 3)

        gui.addPane(pane)
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }
        return gui
    }

    private fun createHeaderItem(): ItemStack {
        val chamberCount = plugin.chamberManager.getCachedChambers().size
        val tableCount = plugin.lootManager.getLootTableNames().size
        return GuiComponents.infoItem(
            plugin, Material.TRIAL_KEY,
            "gui.main-menu.header-name", "gui.main-menu.header-lore",
            "version" to plugin.pluginMeta.version,
            "chambers" to chamberCount,
            "tables" to tableCount
        )
    }

    private fun createChambersItem(): ItemStack {
        val chamberCount = plugin.chamberManager.getCachedChambers().size
        return GuiComponents.infoItem(
            plugin, Material.LODESTONE,
            "gui.main-menu.chambers-name", "gui.main-menu.chambers-lore",
            "count" to chamberCount
        )
    }

    private fun createLootTablesItem(): ItemStack {
        val tableCount = plugin.lootManager.getLootTableNames().size
        return GuiComponents.infoItem(
            plugin, Material.CHEST,
            "gui.main-menu.loot-name", "gui.main-menu.loot-lore",
            "count" to tableCount
        )
    }

    private fun createStatisticsItem(): ItemStack {
        val statsEnabled = plugin.config.getBoolean("statistics.enabled", true)
        val loreKey = if (statsEnabled) "gui.main-menu.stats-lore-enabled" else "gui.main-menu.stats-lore-disabled"
        return GuiComponents.infoItem(plugin, Material.WRITABLE_BOOK, "gui.main-menu.stats-name", loreKey)
    }

    private fun createSettingsItem(): ItemStack =
        GuiComponents.infoItem(plugin, Material.COMPARATOR, "gui.main-menu.settings-name", "gui.main-menu.settings-lore")

    private fun createProtectionItem(): ItemStack {
        val protectionEnabled = plugin.config.getBoolean("protection.enabled", true)
        val loreKey = if (protectionEnabled) "gui.main-menu.protection-lore-enabled" else "gui.main-menu.protection-lore-disabled"
        return GuiComponents.infoItem(plugin, Material.SHIELD, "gui.main-menu.protection-name", loreKey)
    }

    private fun createHelpItem(): ItemStack =
        GuiComponents.infoItem(plugin, Material.OAK_SIGN, "gui.main-menu.help-name", "gui.main-menu.help-lore")

    private fun plainText(key: String): String =
        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText()
            .serialize(plugin.getGuiText(key))
}
