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
 * Protection menu view - configure chamber protection settings.
 */
class ProtectionMenuView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(5, "Protection Settings")
        val pane = StaticPane(0, 0, 9, 5)

        // Row 0: Header
        pane.addItem(GuiItem(createHeaderItem()) { it.isCancelled = true }, 4, 0)

        // Row 1: Main protection toggle
        addToggleItem(pane, player,
            "protection.enabled",
            "Protection System",
            "Master toggle for all protection",
            Material.SHIELD, 4, 1
        )

        // Row 2: Individual protection settings
        addToggleItem(pane, player,
            "protection.prevent-block-break",
            "Prevent Block Break",
            "Stop players from breaking blocks",
            Material.IRON_PICKAXE, 1, 2
        )

        addToggleItem(pane, player,
            "protection.prevent-block-place",
            "Prevent Block Place",
            "Stop players from placing blocks",
            Material.COBBLESTONE, 3, 2
        )

        addToggleItem(pane, player,
            "protection.prevent-container-access",
            "Prevent Containers",
            "Stop access to chests, barrels, etc.",
            Material.CHEST, 5, 2
        )

        addToggleItem(pane, player,
            "protection.prevent-mob-griefing",
            "Prevent Mob Griefing",
            "Stop mob block destruction",
            Material.CREEPER_HEAD, 7, 2
        )

        // Row 3: PvP setting (inverted - allow means enabled)
        addToggleItem(pane, player,
            "protection.allow-pvp",
            "Allow PvP",
            "Allow player vs player combat",
            Material.DIAMOND_SWORD, 3, 3
        )

        addToggleItem(pane, player,
            "protection.worldguard-integration",
            "WorldGuard Integration",
            "Use WorldGuard for region protection",
            Material.FILLED_MAP, 5, 3
        )

        // Row 4: Navigation
        val backItem = ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Back to Settings", NamedTextColor.YELLOW))
            }
        }
        pane.addItem(GuiItem(backItem) { event ->
            event.isCancelled = true
            menu.openSettingsMenu(player)
        }, 0, 4)

        val closeItem = ItemStack(Material.BARRIER).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Close", NamedTextColor.RED))
            }
        }
        pane.addItem(GuiItem(closeItem) { event ->
            event.isCancelled = true
            player.closeInventory()
        }, 8, 4)

        gui.addPane(pane)
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }

        return gui
    }

    private fun createHeaderItem(): ItemStack {
        val protectionEnabled = plugin.config.getBoolean("protection.enabled", true)

        return ItemStack(Material.SHIELD).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Protection Settings", NamedTextColor.BLUE)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Configure chamber protection", NamedTextColor.GRAY),
                    Component.empty(),
                    if (protectionEnabled) {
                        Component.text("Protection System: ACTIVE", NamedTextColor.GREEN)
                    } else {
                        Component.text("Protection System: INACTIVE", NamedTextColor.RED)
                    },
                    Component.empty(),
                    Component.text("Click items to toggle", NamedTextColor.YELLOW)
                ))
            }
        }
    }

    private fun addToggleItem(
        pane: StaticPane,
        player: Player,
        configPath: String,
        name: String,
        description: String,
        icon: Material,
        x: Int,
        y: Int
    ) {
        val enabled = plugin.config.getBoolean(configPath, true)
        val material = if (enabled) Material.LIME_WOOL else Material.RED_WOOL

        val item = ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text(name, if (enabled) NamedTextColor.GREEN else NamedTextColor.RED)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text(description, NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Status: ${if (enabled) "Enabled" else "Disabled"}",
                        if (enabled) NamedTextColor.GREEN else NamedTextColor.RED),
                    Component.empty(),
                    Component.text("Click to toggle", NamedTextColor.YELLOW)
                ))
            }
        }

        pane.addItem(GuiItem(item) { event ->
            event.isCancelled = true
            toggleSetting(configPath, event.whoClicked as Player)
        }, x, y)
    }

    private fun toggleSetting(configPath: String, player: Player) {
        val currentValue = plugin.config.getBoolean(configPath, true)
        val newValue = !currentValue

        plugin.config.set(configPath, newValue)
        plugin.saveConfig()

        val settingName = configPath.split(".").last().replace("-", " ").replaceFirstChar { it.uppercase() }
        val valueText = if (newValue) plugin.getMessage("gui-setting-enabled") else plugin.getMessage("gui-setting-disabled")
        player.sendMessage(plugin.getMessage("gui-setting-toggled", "setting" to settingName, "value" to valueText))

        // Refresh the view
        menu.openProtectionMenu(player)
    }
}
