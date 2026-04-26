package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.components.GuiText
import org.bukkit.entity.Player

/**
 * Global settings view — toggle plugin features at runtime.
 * All strings from `messages.yml` under `gui.global-settings.*` (v1.3.0).
 */
class GlobalSettingsView(
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
        ToggleDef("reset.clear-ground-items",
            "gui.global-settings.clear-ground-items-label",
            "gui.global-settings.clear-ground-items-desc", 1, 1),
        ToggleDef("reset.remove-spawner-mobs",
            "gui.global-settings.remove-spawner-mobs-label",
            "gui.global-settings.remove-spawner-mobs-desc", 3, 1),
        ToggleDef("reset.reset-trial-spawners",
            "gui.global-settings.reset-trial-spawners-label",
            "gui.global-settings.reset-trial-spawners-desc", 5, 1),
        ToggleDef("reset.reset-vault-cooldowns",
            "gui.global-settings.reset-vault-cooldowns-label",
            "gui.global-settings.reset-vault-cooldowns-desc", 7, 1),
        ToggleDef("spawner-waves.enabled",
            "gui.global-settings.spawner-waves-label",
            "gui.global-settings.spawner-waves-desc", 1, 2),
        ToggleDef("spawner-waves.show-boss-bar",
            "gui.global-settings.wave-boss-bar-label",
            "gui.global-settings.wave-boss-bar-desc", 3, 2),
        ToggleDef("spectator-mode.enabled",
            "gui.global-settings.spectator-mode-label",
            "gui.global-settings.spectator-mode-desc", 5, 2),
        ToggleDef("statistics.enabled",
            "gui.global-settings.statistics-label",
            "gui.global-settings.statistics-desc", 7, 2),
        ToggleDef("loot.apply-luck-effect",
            "gui.global-settings.luck-effect-label",
            "gui.global-settings.luck-effect-desc", 3, 3),
        ToggleDef("vaults.per-player-loot",
            "gui.global-settings.per-player-loot-label",
            "gui.global-settings.per-player-loot-desc", 5, 3),
    )

    fun build(player: Player): ChestGui {
        val gui = ChestGui(6, GuiText.plain(plugin, "gui.global-settings.title"))
        val pane = StaticPane(0, 0, 9, 6)

        pane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, org.bukkit.Material.COMMAND_BLOCK,
                "gui.global-settings.header-name", "gui.global-settings.header-lore")
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
        }, 0, 5)
        pane.addItem(GuiComponents.closeButton(plugin, player), 8, 5)

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

        menu.openGlobalSettings(player)
    }
}
