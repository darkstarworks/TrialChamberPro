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
 * Protection menu view — configure chamber protection settings.
 * All strings from `messages.yml` under `gui.protection-menu.*` (v1.3.0).
 */
class ProtectionMenuView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService
) {
    private data class ToggleDef(
        val configPath: String,
        val labelKey: String,
        val descKey: String,
        val x: Int,
        val y: Int
    )

    private val toggles = listOf(
        ToggleDef("protection.enabled",
            "gui.protection-menu.system-label",
            "gui.protection-menu.system-desc", 4, 1),
        ToggleDef("protection.prevent-block-break",
            "gui.protection-menu.block-break-label",
            "gui.protection-menu.block-break-desc", 1, 2),
        ToggleDef("protection.prevent-block-place",
            "gui.protection-menu.block-place-label",
            "gui.protection-menu.block-place-desc", 3, 2),
        ToggleDef("protection.prevent-container-access",
            "gui.protection-menu.containers-label",
            "gui.protection-menu.containers-desc", 5, 2),
        ToggleDef("protection.prevent-mob-griefing",
            "gui.protection-menu.mob-griefing-label",
            "gui.protection-menu.mob-griefing-desc", 7, 2),
        ToggleDef("protection.allow-pvp",
            "gui.protection-menu.pvp-label",
            "gui.protection-menu.pvp-desc", 3, 3),
        ToggleDef("protection.worldguard-integration",
            "gui.protection-menu.worldguard-label",
            "gui.protection-menu.worldguard-desc", 5, 3),
    )

    fun build(player: Player): ChestGui {
        val gui = ChestGui(5, GuiText.plain(plugin, "gui.protection-menu.title"))
        val pane = StaticPane(0, 0, 9, 5)

        val protectionEnabled = plugin.config.getBoolean("protection.enabled", true)
        val headerNameKey = if (protectionEnabled)
            "gui.protection-menu.header-name-enabled" else "gui.protection-menu.header-name-disabled"
        val headerLoreKey = if (protectionEnabled)
            "gui.protection-menu.header-lore-enabled" else "gui.protection-menu.header-lore-disabled"
        pane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.SHIELD, headerNameKey, headerLoreKey)
        ) { it.isCancelled = true }, 4, 0)

        for (def in toggles) {
            val enabled = plugin.config.getBoolean(def.configPath, true)
            pane.addItem(GuiItem(
                GuiComponents.toggleItem(plugin, enabled, def.labelKey, def.descKey)
            ) { event ->
                event.isCancelled = true
                toggleSetting(def, event.whoClicked as Player)
            }, def.x, def.y)
        }

        pane.addItem(GuiComponents.backButton(plugin, "gui.common.dest-settings") {
            menu.openSettingsMenu(player)
        }, 0, 4)
        pane.addItem(GuiComponents.closeButton(plugin, player), 8, 4)

        gui.addPane(pane)
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }
        return gui
    }

    private fun toggleSetting(def: ToggleDef, player: Player) {
        val newValue = !plugin.config.getBoolean(def.configPath, true)
        plugin.config.set(def.configPath, newValue)
        plugin.saveConfig()

        val settingLabel = plugin.getMessage(def.labelKey)
        val valueText = plugin.getMessage(
            if (newValue) "gui.common.setting-enabled" else "gui.common.setting-disabled"
        )
        player.sendMessage(plugin.getMessageComponent("gui.common.setting-toggled",
            "setting" to settingLabel, "value" to valueText))

        menu.openProtectionMenu(player)
    }
}
