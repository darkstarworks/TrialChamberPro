package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Settings menu view - main hub for plugin configuration settings.
 * Provides access to global settings, protection, and other configuration categories.
 */
class SettingsMenuView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(4, "Plugin Settings")
        val pane = StaticPane(0, 0, 9, 4)

        // Row 0: Header
        pane.addItem(GuiItem(createHeaderItem()) { it.isCancelled = true }, 4, 0)

        // Row 1: Settings categories
        pane.addItem(GuiItem(createGlobalSettingsItem()) { event ->
            event.isCancelled = true
            menu.openGlobalSettings(player)
        }, 2, 1)

        pane.addItem(GuiItem(createProtectionItem()) { event ->
            event.isCancelled = true
            menu.openProtectionMenu(player)
        }, 4, 1)

        pane.addItem(GuiItem(createPerformanceInfoItem()) { it.isCancelled = true }, 6, 1)

        // Row 2: Additional settings
        pane.addItem(GuiItem(createReloadConfigItem()) { event ->
            event.isCancelled = true
            if (event.isShiftClick) {
                reloadConfig(player)
            }
        }, 4, 2)

        // Row 3: Navigation
        val backItem = ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Back to Main Menu", NamedTextColor.YELLOW))
            }
        }
        pane.addItem(GuiItem(backItem) { event ->
            event.isCancelled = true
            menu.openMainMenu(player)
        }, 0, 3)

        val closeItem = ItemStack(Material.BARRIER).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Close", NamedTextColor.RED))
            }
        }
        pane.addItem(GuiItem(closeItem) { event ->
            event.isCancelled = true
            player.closeInventory()
        }, 8, 3)

        gui.addPane(pane)
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }

        return gui
    }

    private fun createHeaderItem(): ItemStack {
        return ItemStack(Material.COMPARATOR).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Plugin Settings", NamedTextColor.WHITE)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Configure TrialChamberPro behavior", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Changes are saved to config.yml", NamedTextColor.YELLOW)
                ))
            }
        }
    }

    private fun createGlobalSettingsItem(): ItemStack {
        return ItemStack(Material.COMMAND_BLOCK).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Global Settings", NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Configure global plugin behavior", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Reset behavior, spawner waves,", NamedTextColor.YELLOW),
                    Component.text("spectator mode, statistics", NamedTextColor.YELLOW),
                    Component.empty(),
                    Component.text("Click to configure", NamedTextColor.GREEN)
                ))
            }
        }
    }

    private fun createProtectionItem(): ItemStack {
        val protectionEnabled = plugin.config.getBoolean("protection.enabled", true)

        return ItemStack(Material.SHIELD).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Protection Settings", NamedTextColor.BLUE)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Configure chamber protection", NamedTextColor.GRAY),
                    Component.empty(),
                    if (protectionEnabled) {
                        Component.text("Protection: Enabled", NamedTextColor.GREEN)
                    } else {
                        Component.text("Protection: Disabled", NamedTextColor.RED)
                    },
                    Component.empty(),
                    Component.text("Click to configure", NamedTextColor.GREEN)
                ))
            }
        }
    }

    private fun createPerformanceInfoItem(): ItemStack {
        val dbType = plugin.config.getString("database.type", "SQLITE")
        val cacheEnabled = plugin.config.getBoolean("performance.cache-chamber-lookups", true)
        val cacheDuration = plugin.config.getInt("performance.cache-duration-seconds", 300)
        val chamberCount = plugin.chamberManager.getCachedChambers().size

        return ItemStack(Material.REDSTONE).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Performance Info", NamedTextColor.RED)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Current performance stats", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Database: $dbType", NamedTextColor.YELLOW),
                    Component.text("Cache: ${if (cacheEnabled) "Enabled" else "Disabled"}", NamedTextColor.YELLOW),
                    Component.text("Cache Duration: ${cacheDuration}s", NamedTextColor.YELLOW),
                    Component.text("Cached Chambers: $chamberCount", NamedTextColor.YELLOW),
                    Component.empty(),
                    Component.text("(Read-only - edit config.yml)", NamedTextColor.GRAY)
                ))
            }
        }
    }

    private fun createReloadConfigItem(): ItemStack {
        return ItemStack(Material.REPEATER).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Reload Configuration", NamedTextColor.AQUA)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Reload config.yml, loot.yml,", NamedTextColor.GRAY),
                    Component.text("and messages.yml from disk", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Shift+Click to reload", NamedTextColor.YELLOW)
                ))
            }
        }
    }

    private fun reloadConfig(player: Player) {
        player.sendMessage(plugin.getMessage("config-reloading"))

        plugin.reloadPluginConfig()

        player.sendMessage(plugin.getMessage("config-reloaded"))
    }
}
