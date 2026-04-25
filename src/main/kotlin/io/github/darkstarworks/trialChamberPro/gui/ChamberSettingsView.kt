package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.components.GuiText
import io.github.darkstarworks.trialChamberPro.models.Chamber
import io.github.darkstarworks.trialChamberPro.models.VaultType
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Chamber settings view — configure chamber-specific reset interval, exit location,
 * loot table overrides, and spawner cooldown. All strings from `messages.yml` under
 * `gui.chamber-settings.*` (v1.3.0).
 */
class ChamberSettingsView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val chamber: Chamber
) {
    companion object {
        // (intervalSeconds, label-key)
        private val RESET_INTERVALS = listOf(
            0L to "gui.chamber-settings.reset-disabled",
            3600L to "gui.chamber-settings.reset-1h",
            6 * 3600L to "gui.chamber-settings.reset-6h",
            12 * 3600L to "gui.chamber-settings.reset-12h",
            24 * 3600L to "gui.chamber-settings.reset-24h",
            48 * 3600L to "gui.chamber-settings.reset-48h",
            7 * 24 * 3600L to "gui.chamber-settings.reset-1w"
        )

        // (cooldownMinutes-or-null, label-key)
        private val SPAWNER_COOLDOWNS = listOf<Pair<Int?, String>>(
            null to "gui.chamber-settings.spawner-cd-global",
            -1 to "gui.chamber-settings.spawner-cd-vanilla",
            0 to "gui.chamber-settings.spawner-cd-none",
            5 to "gui.chamber-settings.spawner-cd-5m",
            10 to "gui.chamber-settings.spawner-cd-10m",
            15 to "gui.chamber-settings.spawner-cd-15m",
            30 to "gui.chamber-settings.spawner-cd-30m",
            60 to "gui.chamber-settings.spawner-cd-1h"
        )
    }

    fun build(player: Player): ChestGui {
        val gui = ChestGui(5, GuiText.plain(plugin, "gui.chamber-settings.title", "chamber" to chamber.name))
        val pane = StaticPane(0, 0, 9, 5)

        pane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.COMPARATOR,
                "gui.chamber-settings.header-name", "gui.chamber-settings.header-lore",
                "chamber" to chamber.name)
        ) { it.isCancelled = true }, 4, 0)

        pane.addItem(GuiItem(createResetIntervalItem()) { event ->
            event.isCancelled = true
            if (event.isLeftClick) cycleResetInterval(player, 1)
            else if (event.isRightClick) cycleResetInterval(player, -1)
        }, 2, 1)

        pane.addItem(GuiItem(createExitLocationItem()) { event ->
            event.isCancelled = true
            if (event.isLeftClick) setExitLocation(player)
            else if (event.isRightClick) teleportToExit(player)
        }, 6, 1)

        pane.addItem(GuiItem(createNormalLootOverrideItem()) { event ->
            event.isCancelled = true
            when {
                event.isShiftClick && event.isRightClick -> clearLootOverride(player, VaultType.NORMAL)
                event.isLeftClick -> cycleLootTable(player, VaultType.NORMAL, 1)
                event.isRightClick -> cycleLootTable(player, VaultType.NORMAL, -1)
            }
        }, 2, 2)

        pane.addItem(GuiItem(createOminousLootOverrideItem()) { event ->
            event.isCancelled = true
            when {
                event.isShiftClick && event.isRightClick -> clearLootOverride(player, VaultType.OMINOUS)
                event.isLeftClick -> cycleLootTable(player, VaultType.OMINOUS, 1)
                event.isRightClick -> cycleLootTable(player, VaultType.OMINOUS, -1)
            }
        }, 6, 2)

        pane.addItem(GuiItem(createCustomMobEntryItem()) { event ->
            event.isCancelled = true
            menu.openCustomMobProvider(player, chamber)
        }, 4, 2)

        pane.addItem(GuiItem(createSpawnerCooldownItem()) { event ->
            event.isCancelled = true
            when {
                event.isShiftClick && event.isRightClick -> clearSpawnerCooldown(player)
                event.isLeftClick -> cycleSpawnerCooldown(player, 1)
                event.isRightClick -> cycleSpawnerCooldown(player, -1)
            }
        }, 4, 3)

        pane.addItem(GuiComponents.backButton(plugin, "gui.common.dest-chamber") {
            val refreshedChamber = plugin.chamberManager.getCachedChamberById(chamber.id)
            if (refreshedChamber != null) menu.openChamberDetail(player, refreshedChamber)
            else menu.openChamberList(player)
        }, 0, 4)
        pane.addItem(GuiComponents.closeButton(plugin, player), 8, 4)

        gui.addPane(pane)
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }
        return gui
    }

    private fun createResetIntervalItem(): ItemStack {
        val labelKey = RESET_INTERVALS.find { it.first == chamber.resetInterval }?.second
        val value = labelKey?.let { plugin.getMessage(it) } ?: formatDuration(chamber.resetInterval * 1000)
        return GuiComponents.infoItem(plugin, Material.CLOCK,
            "gui.chamber-settings.reset-interval-name", "gui.chamber-settings.reset-interval-lore",
            "value" to value)
    }

    private fun createExitLocationItem(): ItemStack {
        val exitLoc = chamber.getExitLocation()
        val value = exitLoc?.let { "${it.blockX}, ${it.blockY}, ${it.blockZ}" }
            ?: plugin.getMessage("gui.chamber-settings.exit-not-set")
        return GuiComponents.infoItem(plugin, Material.OAK_DOOR,
            "gui.chamber-settings.exit-name", "gui.chamber-settings.exit-lore",
            "value" to value)
    }

    private fun createNormalLootOverrideItem(): ItemStack {
        val value = chamber.normalLootTable ?: plugin.getMessage("gui.chamber-settings.override-default")
        return GuiComponents.infoItem(plugin, Material.GREEN_WOOL,
            "gui.chamber-settings.normal-override-name", "gui.chamber-settings.normal-override-lore",
            "value" to value)
    }

    private fun createOminousLootOverrideItem(): ItemStack {
        val value = chamber.ominousLootTable ?: plugin.getMessage("gui.chamber-settings.override-default")
        return GuiComponents.infoItem(plugin, Material.PURPLE_WOOL,
            "gui.chamber-settings.ominous-override-name", "gui.chamber-settings.ominous-override-lore",
            "value" to value)
    }

    private fun createCustomMobEntryItem(): ItemStack {
        return GuiComponents.infoItem(plugin, Material.ZOMBIE_HEAD,
            "gui.chamber-settings.custom-mob-name", "gui.chamber-settings.custom-mob-lore",
            "provider" to (chamber.customMobProvider ?: "vanilla"),
            "normal" to chamber.customMobIdsNormal.size,
            "ominous" to chamber.customMobIdsOminous.size)
    }

    private fun createSpawnerCooldownItem(): ItemStack {
        val labelKey = SPAWNER_COOLDOWNS.find { it.first == chamber.spawnerCooldownMinutes }?.second
        val value = labelKey?.let { plugin.getMessage(it) }
            ?: chamber.spawnerCooldownMinutes?.let { "${it}m" }
            ?: plugin.getMessage("gui.chamber-settings.spawner-cd-global")
        return GuiComponents.infoItem(plugin, Material.SPAWNER,
            "gui.chamber-settings.spawner-cd-name", "gui.chamber-settings.spawner-cd-lore",
            "value" to value)
    }

    // ==================== Action Handlers (unchanged behavior) ====================

    private fun cycleResetInterval(player: Player, direction: Int) {
        val currentIndex = RESET_INTERVALS.indexOfFirst { it.first == chamber.resetInterval }
        val newIndex = if (currentIndex == -1) {
            if (direction > 0) 0 else RESET_INTERVALS.lastIndex
        } else (currentIndex + direction).mod(RESET_INTERVALS.size)
        val (newInterval, newLabelKey) = RESET_INTERVALS[newIndex]
        val newName = plugin.getMessage(newLabelKey)

        plugin.launchAsync {
            val success = plugin.chamberManager.updateResetInterval(chamber.id, newInterval)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (success) {
                    player.sendMessage(plugin.getMessage("gui-reset-interval-set", "value" to newName))
                    plugin.chamberManager.getCachedChamberById(chamber.id)?.let { menu.openChamberSettings(player, it) }
                } else player.sendMessage(plugin.getMessage("gui-reset-interval-failed"))
            })
        }
    }

    private fun setExitLocation(player: Player) {
        val location = player.location
        plugin.launchAsync {
            val success = plugin.chamberManager.updateExitLocation(
                chamber.id, location.x, location.y, location.z, location.yaw, location.pitch
            )
            plugin.scheduler.runAtEntity(player, Runnable {
                if (success) {
                    player.sendMessage(plugin.getMessage("gui-exit-location-set"))
                    plugin.chamberManager.getCachedChamberById(chamber.id)?.let { menu.openChamberSettings(player, it) }
                } else player.sendMessage(plugin.getMessage("gui-exit-location-failed"))
            })
        }
    }

    private fun teleportToExit(player: Player) {
        val exitLoc = chamber.getExitLocation()
        if (exitLoc == null) {
            player.sendMessage(plugin.getMessage("gui-no-exit-location"))
            return
        }
        player.teleport(exitLoc)
        player.sendMessage(plugin.getMessage("gui-teleport-to-exit"))
        player.closeInventory()
    }

    private fun cycleLootTable(player: Player, vaultType: VaultType, direction: Int) {
        val tables = plugin.lootManager.getLootTableNames().sorted()
        if (tables.isEmpty()) {
            player.sendMessage(plugin.getMessage("gui-no-loot-tables"))
            return
        }
        val currentOverride = when (vaultType) {
            VaultType.NORMAL -> chamber.normalLootTable
            VaultType.OMINOUS -> chamber.ominousLootTable
        }
        val currentIndex = tables.indexOf(currentOverride)
        val newIndex = if (currentIndex == -1) {
            if (direction > 0) 0 else tables.lastIndex
        } else (currentIndex + direction).mod(tables.size)
        val newTable = tables[newIndex]

        plugin.launchAsync {
            val success = plugin.chamberManager.setLootTable(chamber.name, vaultType, newTable)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (success) {
                    player.sendMessage(plugin.getMessage("gui-loot-table-set", "type" to vaultType.displayName, "table" to newTable))
                    plugin.chamberManager.getCachedChamberById(chamber.id)?.let { menu.openChamberSettings(player, it) }
                } else player.sendMessage(plugin.getMessage("gui-loot-table-failed"))
            })
        }
    }

    private fun clearLootOverride(player: Player, vaultType: VaultType) {
        plugin.launchAsync {
            val success = plugin.chamberManager.setLootTable(chamber.name, vaultType, null)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (success) {
                    player.sendMessage(plugin.getMessage("gui-loot-table-cleared", "type" to vaultType.displayName))
                    plugin.chamberManager.getCachedChamberById(chamber.id)?.let { menu.openChamberSettings(player, it) }
                } else player.sendMessage(plugin.getMessage("gui-loot-clear-failed"))
            })
        }
    }

    private fun cycleSpawnerCooldown(player: Player, direction: Int) {
        val currentIndex = SPAWNER_COOLDOWNS.indexOfFirst { it.first == chamber.spawnerCooldownMinutes }
        val newIndex = if (currentIndex == -1) {
            if (direction > 0) 0 else SPAWNER_COOLDOWNS.lastIndex
        } else (currentIndex + direction).mod(SPAWNER_COOLDOWNS.size)
        val (newCooldown, newLabelKey) = SPAWNER_COOLDOWNS[newIndex]
        val newName = plugin.getMessage(newLabelKey)

        plugin.launchAsync {
            val success = plugin.chamberManager.updateSpawnerCooldown(chamber.id, newCooldown)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (success) {
                    player.sendMessage(plugin.getMessage("gui-spawner-cooldown-set", "value" to newName))
                    plugin.chamberManager.getCachedChamberById(chamber.id)?.let { menu.openChamberSettings(player, it) }
                } else player.sendMessage(plugin.getMessage("gui-spawner-cooldown-failed"))
            })
        }
    }

    private fun clearSpawnerCooldown(player: Player) {
        plugin.launchAsync {
            val success = plugin.chamberManager.updateSpawnerCooldown(chamber.id, null)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (success) {
                    player.sendMessage(plugin.getMessage("gui-spawner-cooldown-reset"))
                    plugin.chamberManager.getCachedChamberById(chamber.id)?.let { menu.openChamberSettings(player, it) }
                } else player.sendMessage(plugin.getMessage("gui-spawner-cooldown-reset-failed"))
            })
        }
    }

    private fun formatDuration(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m")
        }.trim().ifEmpty { "${seconds}s" }
    }
}
