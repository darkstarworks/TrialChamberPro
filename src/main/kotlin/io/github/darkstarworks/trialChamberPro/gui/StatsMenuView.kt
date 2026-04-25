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

class StatsMenuView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(4, GuiText.plain(plugin, "gui.stats-menu.title"))
        val pane = StaticPane(0, 0, 9, 4)

        pane.addItem(GuiItem(createHeaderItem()) { it.isCancelled = true }, 4, 0)

        pane.addItem(GuiItem(categoryItem(Material.VAULT, "gui.stats-menu.category-vaults-name")) { e ->
            e.isCancelled = true; menu.openLeaderboard(player, "vaults")
        }, 1, 1)
        pane.addItem(GuiItem(categoryItem(Material.LODESTONE, "gui.stats-menu.category-chambers-name")) { e ->
            e.isCancelled = true; menu.openLeaderboard(player, "chambers")
        }, 3, 1)
        pane.addItem(GuiItem(categoryItem(Material.IRON_SWORD, "gui.stats-menu.category-mobs-name")) { e ->
            e.isCancelled = true; menu.openLeaderboard(player, "mobs")
        }, 5, 1)
        pane.addItem(GuiItem(categoryItem(Material.CLOCK, "gui.stats-menu.category-time-name")) { e ->
            e.isCancelled = true; menu.openLeaderboard(player, "time")
        }, 7, 1)

        pane.addItem(GuiItem(
            GuiComponents.playerHead(plugin, player.uniqueId,
                "gui.stats-menu.your-stats-name", "gui.stats-menu.your-stats-lore")
        ) { e ->
            e.isCancelled = true; menu.openPlayerStats(player, player.uniqueId)
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

    private fun createHeaderItem(): ItemStack {
        val statsEnabled = plugin.config.getBoolean("statistics.enabled", true)
        val loreKey = if (statsEnabled) "gui.stats-menu.header-lore-enabled" else "gui.stats-menu.header-lore-disabled"
        return GuiComponents.infoItem(plugin, Material.WRITABLE_BOOK, "gui.stats-menu.header-name", loreKey)
    }

    private fun categoryItem(material: Material, nameKey: String): ItemStack =
        GuiComponents.infoItem(plugin, material, nameKey, "gui.stats-menu.category-lore")
}
