package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.components.GuiText
import org.bukkit.Material
import org.bukkit.entity.Player

/**
 * Help menu view — command reference, permissions, and plugin info.
 * All strings from `messages.yml` under `gui.help-menu.*` (v1.3.0).
 */
class HelpMenuView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(6, GuiText.plain(plugin, "gui.help-menu.title"))
        val pane = StaticPane(0, 0, 9, 6)

        pane.addItem(info(Material.KNOWLEDGE_BOOK, "header"), 4, 0)

        pane.addItem(info(Material.COMMAND_BLOCK, "commands"), 2, 1)
        pane.addItem(info(Material.BOOK, "permissions"), 4, 1)
        pane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.TRIAL_KEY,
                "gui.help-menu.about-name", "gui.help-menu.about-lore",
                "version" to plugin.pluginMeta.version)
        ) { it.isCancelled = true }, 6, 1)

        pane.addItem(info(Material.LODESTONE, "chambers-cmd"), 1, 2)
        pane.addItem(info(Material.CHEST, "loot-cmd"), 3, 2)
        pane.addItem(info(Material.VAULT, "vault-cmd"), 5, 2)
        pane.addItem(info(Material.WRITABLE_BOOK, "stats-cmd"), 7, 2)

        pane.addItem(info(Material.SPYGLASS, "snapshot-cmd"), 2, 3)
        pane.addItem(info(Material.COMPARATOR, "admin-cmd"), 4, 3)
        pane.addItem(info(Material.GOLDEN_PICKAXE, "generate-cmd"), 6, 3)

        pane.addItem(GuiComponents.backButton(plugin, "gui.common.dest-main-menu") {
            menu.openMainMenu(player)
        }, 0, 5)
        pane.addItem(GuiComponents.closeButton(plugin, player), 8, 5)

        gui.addPane(pane)
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }
        return gui
    }

    private fun info(material: Material, id: String): GuiItem {
        return GuiItem(
            GuiComponents.infoItem(plugin, material,
                "gui.help-menu.$id-name", "gui.help-menu.$id-lore")
        ) { it.isCancelled = true }
    }
}
