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
 * Main menu view — central hub for the TrialChamberPro admin GUI.
 *
 * v1.4.x: Flattened the previous "Settings → Plugin Settings → Global Settings"
 * navigation. Global Settings, Protection Settings, Performance Info and Reload
 * Configuration are now top-level entries on this hub instead of being hidden
 * one level deeper. The intermediate `SettingsMenuView` was removed.
 *
 * All strings are sourced from `messages.yml` under `gui.main-menu.*`.
 */
class MainMenuView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(6, plainText("gui.main-menu.title"))
        val pane = StaticPane(0, 0, 9, 6)

        // Row 0: header
        pane.addItem(GuiItem(createHeaderItem()) { it.isCancelled = true }, 4, 0)

        // Row 1: primary action tiles
        pane.addItem(GuiItem(createChambersItem()) { event ->
            event.isCancelled = true
            menu.openChamberList(player)
        }, 1, 1)

        pane.addItem(GuiItem(createLootTablesItem()) { event ->
            event.isCancelled = true
            menu.openLootTableList(player)
        }, 3, 1)

        pane.addItem(GuiItem(createGlobalSettingsItem()) { event ->
            event.isCancelled = true
            menu.openGlobalSettings(player)
        }, 5, 1)

        pane.addItem(GuiItem(createProtectionItem()) { event ->
            event.isCancelled = true
            menu.openProtectionMenu(player)
        }, 7, 1)

        // Row 2: secondary tiles + read-only info
        pane.addItem(GuiItem(createHelpItem()) { event ->
            event.isCancelled = true
            menu.openHelpMenu(player)
        }, 2, 2)

        pane.addItem(GuiItem(createPerformanceInfoItem()) { it.isCancelled = true }, 4, 2)

        pane.addItem(GuiItem(createStatisticsItem()) { event ->
            event.isCancelled = true
            menu.openStatsMenu(player)
        }, 6, 2)

        // Row 4: reload (shift-click to fire so accidental clicks don't reload)
        pane.addItem(GuiItem(createReloadItem()) { event ->
            event.isCancelled = true
            if (event.isShiftClick) reloadConfig(player)
        }, 4, 4)

        // Row 5: close
        pane.addItem(GuiComponents.closeButton(plugin, player), 8, 5)

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

    private fun createGlobalSettingsItem(): ItemStack =
        GuiComponents.infoItem(
            plugin, Material.COMMAND_BLOCK,
            "gui.main-menu.global-settings-name", "gui.main-menu.global-settings-lore"
        )

    private fun createProtectionItem(): ItemStack {
        val protectionEnabled = plugin.config.getBoolean("protection.enabled", true)
        val loreKey = if (protectionEnabled) "gui.main-menu.protection-lore-enabled" else "gui.main-menu.protection-lore-disabled"
        return GuiComponents.infoItem(plugin, Material.SHIELD, "gui.main-menu.protection-name", loreKey)
    }

    private fun createHelpItem(): ItemStack =
        GuiComponents.infoItem(plugin, Material.OAK_SIGN, "gui.main-menu.help-name", "gui.main-menu.help-lore")

    private fun createPerformanceInfoItem(): ItemStack {
        val dbType = plugin.config.getString("database.type", "SQLITE") ?: "SQLITE"
        val cacheEnabled = plugin.config.getBoolean("performance.cache-chamber-lookups", true)
        val cacheDuration = plugin.config.getInt("performance.cache-duration-seconds", 300)
        val chamberCount = plugin.chamberManager.getCachedChambers().size
        return GuiComponents.infoItem(
            plugin, Material.REDSTONE,
            "gui.main-menu.performance-name", "gui.main-menu.performance-lore",
            "database" to dbType,
            "cache" to if (cacheEnabled) "Enabled" else "Disabled",
            "duration" to cacheDuration,
            "chambers" to chamberCount
        )
    }

    private fun createReloadItem(): ItemStack =
        GuiComponents.infoItem(
            plugin, Material.REPEATER,
            "gui.main-menu.reload-name", "gui.main-menu.reload-lore"
        )

    private fun reloadConfig(player: Player) {
        player.sendMessage(plugin.getMessageComponent("config-reloading"))
        plugin.reloadPluginConfig()
        player.sendMessage(plugin.getMessageComponent("config-reloaded"))
    }

    private fun plainText(key: String): String =
        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
            .plainText()
            .serialize(plugin.getGuiText(key))
}
