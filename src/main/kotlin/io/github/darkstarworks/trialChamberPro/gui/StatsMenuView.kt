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
 * Statistics menu view - access leaderboards and player statistics.
 */
class StatsMenuView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(4, "Statistics")
        val pane = StaticPane(0, 0, 9, 4)

        // Row 0: Header
        pane.addItem(GuiItem(createHeaderItem()) { it.isCancelled = true }, 4, 0)

        // Row 1: Leaderboard categories
        pane.addItem(GuiItem(createLeaderboardItem("vaults", "Vaults Opened", Material.VAULT, NamedTextColor.GOLD)) { event ->
            event.isCancelled = true
            menu.openLeaderboard(player, "vaults")
        }, 1, 1)

        pane.addItem(GuiItem(createLeaderboardItem("chambers", "Chambers Completed", Material.LODESTONE, NamedTextColor.AQUA)) { event ->
            event.isCancelled = true
            menu.openLeaderboard(player, "chambers")
        }, 3, 1)

        pane.addItem(GuiItem(createLeaderboardItem("mobs", "Mobs Killed", Material.IRON_SWORD, NamedTextColor.RED)) { event ->
            event.isCancelled = true
            menu.openLeaderboard(player, "mobs")
        }, 5, 1)

        pane.addItem(GuiItem(createLeaderboardItem("time", "Time Spent", Material.CLOCK, NamedTextColor.YELLOW)) { event ->
            event.isCancelled = true
            menu.openLeaderboard(player, "time")
        }, 7, 1)

        // Row 2: Your stats
        pane.addItem(GuiItem(createYourStatsItem(player)) { event ->
            event.isCancelled = true
            menu.openPlayerStats(player, player.uniqueId)
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

        return gui
    }

    private fun createHeaderItem(): ItemStack {
        val statsEnabled = plugin.config.getBoolean("statistics.enabled", true)

        return ItemStack(Material.WRITABLE_BOOK).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Statistics", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("View player stats & leaderboards", NamedTextColor.GRAY),
                    Component.empty(),
                    if (statsEnabled) {
                        Component.text("Statistics: Enabled", NamedTextColor.GREEN)
                    } else {
                        Component.text("Statistics: Disabled", NamedTextColor.RED)
                    }
                ))
            }
        }
    }

    private fun createLeaderboardItem(type: String, name: String, material: Material, color: NamedTextColor): ItemStack {
        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text(name, color)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("View top players", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click to view leaderboard", NamedTextColor.GREEN)
                ))
            }
        }
    }

    private fun createYourStatsItem(player: Player): ItemStack {
        return ItemStack(Material.PLAYER_HEAD).apply {
            itemMeta = (itemMeta as? org.bukkit.inventory.meta.SkullMeta)?.apply {
                owningPlayer = player
                displayName(Component.text("Your Statistics", NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("View your personal stats", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click to view", NamedTextColor.GREEN)
                ))
            }
        }
    }
}
