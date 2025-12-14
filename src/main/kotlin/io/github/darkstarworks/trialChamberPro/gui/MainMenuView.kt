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
 * Main menu view - the central hub for the TrialChamberPro admin GUI.
 * Provides access to all major features: Chambers, Loot Tables, Statistics,
 * Settings, Protection, and Help.
 */
class MainMenuView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(4, "TrialChamberPro Admin")

        val pane = StaticPane(0, 0, 9, 4)

        // Header info item (center of row 0)
        val headerItem = createHeaderItem()
        pane.addItem(GuiItem(headerItem) { it.isCancelled = true }, 4, 0)

        // Row 1: Chambers, Loot Tables, Statistics
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

        // Row 2: Settings, Protection, Help
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

        // Close button (bottom right)
        val closeItem = ItemStack(Material.BARRIER).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Close", NamedTextColor.RED))
            }
        }
        pane.addItem(GuiItem(closeItem) {
            it.isCancelled = true
            player.closeInventory()
        }, 8, 3)

        gui.addPane(pane)
        gui.setOnGlobalClick { it.isCancelled = true }

        return gui
    }

    private fun createHeaderItem(): ItemStack {
        val chamberCount = plugin.chamberManager.getCachedChambers().size
        val tableCount = plugin.lootManager.getLootTableNames().size

        return ItemStack(Material.TRIAL_KEY).apply {
            itemMeta = itemMeta?.apply {
                displayName(
                    Component.text("TrialChamberPro", NamedTextColor.GOLD)
                        .decoration(TextDecoration.BOLD, true)
                )
                lore(listOf(
                    Component.text("Version ${plugin.description.version}", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Registered Chambers: ", NamedTextColor.YELLOW)
                        .append(Component.text("$chamberCount", NamedTextColor.WHITE)),
                    Component.text("Loot Tables: ", NamedTextColor.YELLOW)
                        .append(Component.text("$tableCount", NamedTextColor.WHITE))
                ))
            }
        }
    }

    private fun createChambersItem(): ItemStack {
        val chamberCount = plugin.chamberManager.getCachedChambers().size

        return ItemStack(Material.LODESTONE).apply {
            itemMeta = itemMeta?.apply {
                displayName(
                    Component.text("Chambers", NamedTextColor.AQUA)
                        .decoration(TextDecoration.BOLD, true)
                )
                lore(listOf(
                    Component.text("Manage trial chambers", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("$chamberCount registered", NamedTextColor.YELLOW),
                    Component.empty(),
                    Component.text("Click to manage chambers", NamedTextColor.GREEN)
                ))
            }
        }
    }

    private fun createLootTablesItem(): ItemStack {
        val tableCount = plugin.lootManager.getLootTableNames().size

        return ItemStack(Material.CHEST).apply {
            itemMeta = itemMeta?.apply {
                displayName(
                    Component.text("Loot Tables", NamedTextColor.GOLD)
                        .decoration(TextDecoration.BOLD, true)
                )
                lore(listOf(
                    Component.text("Edit loot tables", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("$tableCount tables loaded", NamedTextColor.YELLOW),
                    Component.empty(),
                    Component.text("Click to edit loot", NamedTextColor.GREEN)
                ))
            }
        }
    }

    private fun createStatisticsItem(): ItemStack {
        val statsEnabled = plugin.config.getBoolean("statistics.enabled", true)

        return ItemStack(Material.WRITABLE_BOOK).apply {
            itemMeta = itemMeta?.apply {
                displayName(
                    Component.text("Statistics", NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.BOLD, true)
                )
                lore(listOf(
                    Component.text("View player stats & leaderboards", NamedTextColor.GRAY),
                    Component.empty(),
                    if (statsEnabled) {
                        Component.text("Statistics: Enabled", NamedTextColor.GREEN)
                    } else {
                        Component.text("Statistics: Disabled", NamedTextColor.RED)
                    },
                    Component.empty(),
                    Component.text("Click to view stats", NamedTextColor.GREEN)
                ))
            }
        }
    }

    private fun createSettingsItem(): ItemStack {
        return ItemStack(Material.COMPARATOR).apply {
            itemMeta = itemMeta?.apply {
                displayName(
                    Component.text("Settings", NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, true)
                )
                lore(listOf(
                    Component.text("Configure plugin behavior", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Reset behavior, vaults,", NamedTextColor.YELLOW),
                    Component.text("spawner waves, spectator mode", NamedTextColor.YELLOW),
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
                displayName(
                    Component.text("Protection", NamedTextColor.BLUE)
                        .decoration(TextDecoration.BOLD, true)
                )
                lore(listOf(
                    Component.text("Chamber protection settings", NamedTextColor.GRAY),
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

    private fun createHelpItem(): ItemStack {
        return ItemStack(Material.OAK_SIGN).apply {
            itemMeta = itemMeta?.apply {
                displayName(
                    Component.text("Help", NamedTextColor.GREEN)
                        .decoration(TextDecoration.BOLD, true)
                )
                lore(listOf(
                    Component.text("Commands & information", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("View commands list,", NamedTextColor.YELLOW),
                    Component.text("permissions, and plugin info", NamedTextColor.YELLOW),
                    Component.empty(),
                    Component.text("Click for help", NamedTextColor.GREEN)
                ))
            }
        }
    }
}
