package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.components.GuiText
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Settings menu view — category hub for plugin configuration. All strings from
 * `messages.yml` under `gui.settings-menu.*` (v1.3.0).
 */
class SettingsMenuView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(4, GuiText.plain(plugin, "gui.settings-menu.title"))
        val pane = StaticPane(0, 0, 9, 4)

        pane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.COMPARATOR,
                "gui.settings-menu.header-name", "gui.settings-menu.header-lore")
        ) { it.isCancelled = true }, 4, 0)

        pane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.COMMAND_BLOCK,
                "gui.settings-menu.global-name", "gui.settings-menu.global-lore")
        ) { e ->
            e.isCancelled = true; menu.openGlobalSettings(player)
        }, 2, 1)

        val protectionEnabled = plugin.config.getBoolean("protection.enabled", true)
        val protectionLoreKey = if (protectionEnabled)
            "gui.settings-menu.protection-lore-enabled" else "gui.settings-menu.protection-lore-disabled"
        pane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.SHIELD,
                "gui.settings-menu.protection-name", protectionLoreKey)
        ) { e ->
            e.isCancelled = true; menu.openProtectionMenu(player)
        }, 4, 1)

        pane.addItem(GuiItem(createPerformanceInfoItem()) { it.isCancelled = true }, 6, 1)

        pane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.REPEATER,
                "gui.settings-menu.reload-name", "gui.settings-menu.reload-lore")
        ) { event ->
            event.isCancelled = true
            if (event.isShiftClick) reloadConfig(player)
        }, 4, 2)

        pane.addItem(GuiComponents.backButton(plugin, "gui.common.dest-main-menu") {
            menu.openMainMenu(player)
        }, 0, 3)
        pane.addItem(GuiComponents.closeButton(plugin, player), 8, 3)

        gui.addPane(pane)
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }
        return gui
    }

    private fun createPerformanceInfoItem(): ItemStack {
        val dbType = plugin.config.getString("database.type", "SQLITE") ?: "SQLITE"
        val cacheEnabled = plugin.config.getBoolean("performance.cache-chamber-lookups", true)
        val cacheDuration = plugin.config.getInt("performance.cache-duration-seconds", 300)
        val chamberCount = plugin.chamberManager.getCachedChambers().size
        return GuiComponents.infoItem(
            plugin, Material.REDSTONE,
            "gui.settings-menu.performance-name", "gui.settings-menu.performance-lore",
            "database" to dbType,
            "cache" to if (cacheEnabled) "Enabled" else "Disabled",
            "duration" to cacheDuration,
            "chambers" to chamberCount
        )
    }

    private fun reloadConfig(player: Player) {
        player.sendMessage(plugin.getMessageComponent("config-reloading"))
        plugin.reloadPluginConfig()
        player.sendMessage(plugin.getMessageComponent("config-reloaded"))
    }
}
