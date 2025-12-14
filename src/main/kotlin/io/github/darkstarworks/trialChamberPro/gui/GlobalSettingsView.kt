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
 * Global settings view - toggle various plugin settings at runtime.
 * Changes are saved to config.yml immediately.
 */
class GlobalSettingsView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(6, "Global Settings")
        val pane = StaticPane(0, 0, 9, 6)

        // Row 0: Header
        pane.addItem(GuiItem(createHeaderItem()) { it.isCancelled = true }, 4, 0)

        // Row 1: Reset Settings
        pane.addItem(createToggleItem(
            "reset.clear-ground-items",
            "Clear Ground Items",
            "Remove dropped items on reset",
            Material.HOPPER, 1, 1
        ))

        pane.addItem(createToggleItem(
            "reset.remove-spawner-mobs",
            "Remove Spawner Mobs",
            "Remove mobs from spawners on reset",
            Material.ZOMBIE_HEAD, 3, 1
        ))

        pane.addItem(createToggleItem(
            "reset.reset-trial-spawners",
            "Reset Trial Spawners",
            "Clear tracked players from spawners",
            Material.SPAWNER, 5, 1
        ))

        pane.addItem(createToggleItem(
            "reset.reset-vault-cooldowns",
            "Reset Vault Cooldowns",
            "Clear vault locks on chamber reset",
            Material.VAULT, 7, 1
        ))

        // Row 2: Feature Settings
        pane.addItem(createToggleItem(
            "spawner-waves.enabled",
            "Spawner Waves",
            "Track spawner wave progress",
            Material.IRON_SWORD, 1, 2
        ))

        pane.addItem(createToggleItem(
            "spawner-waves.show-boss-bar",
            "Wave Boss Bar",
            "Show boss bar during waves",
            Material.DRAGON_BREATH, 3, 2
        ))

        pane.addItem(createToggleItem(
            "spectator-mode.enabled",
            "Spectator Mode",
            "Allow spectating after death",
            Material.ENDER_EYE, 5, 2
        ))

        pane.addItem(createToggleItem(
            "statistics.enabled",
            "Statistics",
            "Track player statistics",
            Material.WRITABLE_BOOK, 7, 2
        ))

        // Row 3: Loot Settings
        pane.addItem(createToggleItem(
            "loot.apply-luck-effect",
            "Apply Luck Effect",
            "Luck potion affects loot rolls",
            Material.RABBIT_FOOT, 3, 3
        ))

        pane.addItem(createToggleItem(
            "vaults.per-player-loot",
            "Per-Player Loot",
            "Each player gets their own loot",
            Material.CHEST, 5, 3
        ))

        // Row 5: Navigation
        val backItem = ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Back to Settings", NamedTextColor.YELLOW))
            }
        }
        pane.addItem(GuiItem(backItem) { event ->
            event.isCancelled = true
            menu.openSettingsMenu(player)
        }, 0, 5)

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
        return ItemStack(Material.COMMAND_BLOCK).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Global Settings", NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Toggle plugin features", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click items to toggle on/off", NamedTextColor.YELLOW),
                    Component.text("Changes are saved immediately", NamedTextColor.YELLOW)
                ))
            }
        }
    }

    private fun createToggleItem(
        configPath: String,
        name: String,
        description: String,
        baseMaterial: Material,
        x: Int,
        y: Int
    ): Triple<GuiItem, Int, Int> {
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

        return Triple(GuiItem(item) { event ->
            event.isCancelled = true
            toggleSetting(configPath, event.whoClicked as Player)
        }, x, y)
    }

    private fun StaticPane.addItem(triple: Triple<GuiItem, Int, Int>) {
        this.addItem(triple.first, triple.second, triple.third)
    }

    private fun toggleSetting(configPath: String, player: Player) {
        val currentValue = plugin.config.getBoolean(configPath, true)
        val newValue = !currentValue

        plugin.config.set(configPath, newValue)
        plugin.saveConfig()

        val settingName = configPath.split(".").last().replace("-", " ").replaceFirstChar { it.uppercase() }
        player.sendMessage(Component.text("$settingName: ${if (newValue) "Enabled" else "Disabled"}",
            if (newValue) NamedTextColor.GREEN else NamedTextColor.RED))

        // Refresh the view
        menu.openGlobalSettings(player)
    }
}
