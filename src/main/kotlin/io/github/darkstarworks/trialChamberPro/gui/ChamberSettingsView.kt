package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.Chamber
import io.github.darkstarworks.trialChamberPro.models.VaultType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Chamber settings view - configure chamber-specific settings like reset interval,
 * exit location, and loot table overrides.
 */
class ChamberSettingsView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val chamber: Chamber
) {
    companion object {
        // Reset interval presets in seconds
        private val RESET_INTERVALS = listOf(
            3600L to "1 hour",
            6 * 3600L to "6 hours",
            12 * 3600L to "12 hours",
            24 * 3600L to "24 hours",
            48 * 3600L to "48 hours",
            7 * 24 * 3600L to "1 week"
        )
    }

    fun build(player: Player): ChestGui {
        val gui = ChestGui(5, "Settings: ${chamber.name}")
        val pane = StaticPane(0, 0, 9, 5)

        // Row 0: Header
        pane.addItem(GuiItem(createHeaderItem()) { it.isCancelled = true }, 4, 0)

        // Row 1: Reset interval configuration
        pane.addItem(GuiItem(createResetIntervalItem()) { event ->
            event.isCancelled = true
            if (event.isLeftClick) {
                cycleResetInterval(player, 1)
            } else if (event.isRightClick) {
                cycleResetInterval(player, -1)
            }
        }, 2, 1)

        // Exit location
        pane.addItem(GuiItem(createExitLocationItem()) { event ->
            event.isCancelled = true
            if (event.isLeftClick) {
                setExitLocation(player)
            } else if (event.isRightClick) {
                teleportToExit(player)
            }
        }, 6, 1)

        // Row 2: Loot table overrides
        pane.addItem(GuiItem(createNormalLootOverrideItem()) { event ->
            event.isCancelled = true
            if (event.isLeftClick) {
                cycleLootTable(player, VaultType.NORMAL, 1)
            } else if (event.isRightClick) {
                cycleLootTable(player, VaultType.NORMAL, -1)
            } else if (event.isShiftClick && event.isRightClick) {
                clearLootOverride(player, VaultType.NORMAL)
            }
        }, 2, 2)

        pane.addItem(GuiItem(createOminousLootOverrideItem()) { event ->
            event.isCancelled = true
            if (event.isLeftClick) {
                cycleLootTable(player, VaultType.OMINOUS, 1)
            } else if (event.isRightClick) {
                cycleLootTable(player, VaultType.OMINOUS, -1)
            } else if (event.isShiftClick && event.isRightClick) {
                clearLootOverride(player, VaultType.OMINOUS)
            }
        }, 6, 2)

        // Row 4: Navigation
        val backItem = ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Back to Chamber", NamedTextColor.YELLOW))
            }
        }
        pane.addItem(GuiItem(backItem) { event ->
            event.isCancelled = true
            // Refresh chamber from cache to get updated values
            val refreshedChamber = plugin.chamberManager.getCachedChamberById(chamber.id)
            if (refreshedChamber != null) {
                menu.openChamberDetail(player, refreshedChamber)
            } else {
                menu.openChamberList(player)
            }
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

        return gui
    }

    private fun createHeaderItem(): ItemStack {
        return ItemStack(Material.COMPARATOR).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Chamber Settings", NamedTextColor.WHITE)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Configure ${chamber.name}", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Changes are saved immediately", NamedTextColor.YELLOW)
                ))
            }
        }
    }

    private fun createResetIntervalItem(): ItemStack {
        val currentInterval = chamber.resetInterval
        val currentName = RESET_INTERVALS.find { it.first == currentInterval }?.second
            ?: formatDuration(currentInterval * 1000)

        return ItemStack(Material.CLOCK).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Reset Interval", NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("How often the chamber resets", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Current: $currentName", NamedTextColor.YELLOW),
                    Component.empty(),
                    Component.text("Left Click: Increase", NamedTextColor.GREEN),
                    Component.text("Right Click: Decrease", NamedTextColor.RED)
                ))
            }
        }
    }

    private fun createExitLocationItem(): ItemStack {
        val exitLoc = chamber.getExitLocation()
        val exitStr = exitLoc?.let { "${it.blockX}, ${it.blockY}, ${it.blockZ}" } ?: "Not set"

        return ItemStack(Material.OAK_DOOR).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Exit Location", NamedTextColor.AQUA)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Where players teleport on exit", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Current: $exitStr", NamedTextColor.YELLOW),
                    Component.empty(),
                    Component.text("Left Click: Set to your location", NamedTextColor.GREEN),
                    Component.text("Right Click: Teleport to exit", NamedTextColor.AQUA)
                ))
            }
        }
    }

    private fun createNormalLootOverrideItem(): ItemStack {
        val currentOverride = chamber.normalLootTable

        return ItemStack(Material.GREEN_WOOL).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Normal Loot Override", NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Override the loot table for", NamedTextColor.GRAY),
                    Component.text("normal vaults in this chamber", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Current: ", NamedTextColor.WHITE)
                        .append(Component.text(currentOverride ?: "(default)",
                            if (currentOverride != null) NamedTextColor.GREEN else NamedTextColor.GRAY)),
                    Component.empty(),
                    Component.text("Left/Right Click: Cycle tables", NamedTextColor.YELLOW),
                    Component.text("Shift+Right: Clear override", NamedTextColor.RED)
                ))
            }
        }
    }

    private fun createOminousLootOverrideItem(): ItemStack {
        val currentOverride = chamber.ominousLootTable

        return ItemStack(Material.PURPLE_WOOL).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Ominous Loot Override", NamedTextColor.DARK_PURPLE)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Override the loot table for", NamedTextColor.GRAY),
                    Component.text("ominous vaults in this chamber", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Current: ", NamedTextColor.WHITE)
                        .append(Component.text(currentOverride ?: "(default)",
                            if (currentOverride != null) NamedTextColor.DARK_PURPLE else NamedTextColor.GRAY)),
                    Component.empty(),
                    Component.text("Left/Right Click: Cycle tables", NamedTextColor.YELLOW),
                    Component.text("Shift+Right: Clear override", NamedTextColor.RED)
                ))
            }
        }
    }

    // ==================== Action Handlers ====================

    private fun cycleResetInterval(player: Player, direction: Int) {
        val currentIndex = RESET_INTERVALS.indexOfFirst { it.first == chamber.resetInterval }
        val newIndex = if (currentIndex == -1) {
            if (direction > 0) 0 else RESET_INTERVALS.lastIndex
        } else {
            (currentIndex + direction).mod(RESET_INTERVALS.size)
        }

        val newInterval = RESET_INTERVALS[newIndex].first
        val newName = RESET_INTERVALS[newIndex].second

        plugin.launchAsync {
            val success = plugin.chamberManager.updateResetInterval(chamber.id, newInterval)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (success) {
                    player.sendMessage(Component.text("Reset interval set to $newName", NamedTextColor.GREEN))
                    // Refresh the view
                    val refreshedChamber = plugin.chamberManager.getCachedChamberById(chamber.id)
                    if (refreshedChamber != null) {
                        menu.openChamberSettings(player, refreshedChamber)
                    }
                } else {
                    player.sendMessage(Component.text("Failed to update reset interval", NamedTextColor.RED))
                }
            })
        }
    }

    private fun setExitLocation(player: Player) {
        val location = player.location

        plugin.launchAsync {
            val success = plugin.chamberManager.updateExitLocation(
                chamber.id,
                location.x, location.y, location.z,
                location.yaw, location.pitch
            )
            plugin.scheduler.runAtEntity(player, Runnable {
                if (success) {
                    player.sendMessage(Component.text("Exit location set to your current position", NamedTextColor.GREEN))
                    val refreshedChamber = plugin.chamberManager.getCachedChamberById(chamber.id)
                    if (refreshedChamber != null) {
                        menu.openChamberSettings(player, refreshedChamber)
                    }
                } else {
                    player.sendMessage(Component.text("Failed to set exit location", NamedTextColor.RED))
                }
            })
        }
    }

    private fun teleportToExit(player: Player) {
        val exitLoc = chamber.getExitLocation()
        if (exitLoc == null) {
            player.sendMessage(Component.text("No exit location set for this chamber", NamedTextColor.RED))
            return
        }
        player.teleport(exitLoc)
        player.sendMessage(Component.text("Teleported to exit location", NamedTextColor.GREEN))
        player.closeInventory()
    }

    private fun cycleLootTable(player: Player, vaultType: VaultType, direction: Int) {
        val tables = plugin.lootManager.getLootTableNames().sorted()
        if (tables.isEmpty()) {
            player.sendMessage(Component.text("No loot tables available", NamedTextColor.RED))
            return
        }

        val currentOverride = when (vaultType) {
            VaultType.NORMAL -> chamber.normalLootTable
            VaultType.OMINOUS -> chamber.ominousLootTable
        }

        val currentIndex = tables.indexOf(currentOverride)
        val newIndex = if (currentIndex == -1) {
            if (direction > 0) 0 else tables.lastIndex
        } else {
            (currentIndex + direction).mod(tables.size)
        }

        val newTable = tables[newIndex]

        plugin.launchAsync {
            val success = plugin.chamberManager.setLootTable(chamber.name, vaultType, newTable)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (success) {
                    player.sendMessage(Component.text("${vaultType.displayName} loot table set to: $newTable", NamedTextColor.GREEN))
                    val refreshedChamber = plugin.chamberManager.getCachedChamberById(chamber.id)
                    if (refreshedChamber != null) {
                        menu.openChamberSettings(player, refreshedChamber)
                    }
                } else {
                    player.sendMessage(Component.text("Failed to set loot table", NamedTextColor.RED))
                }
            })
        }
    }

    private fun clearLootOverride(player: Player, vaultType: VaultType) {
        plugin.launchAsync {
            val success = plugin.chamberManager.setLootTable(chamber.name, vaultType, null)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (success) {
                    player.sendMessage(Component.text("${vaultType.displayName} loot table override cleared", NamedTextColor.GREEN))
                    val refreshedChamber = plugin.chamberManager.getCachedChamberById(chamber.id)
                    if (refreshedChamber != null) {
                        menu.openChamberSettings(player, refreshedChamber)
                    }
                } else {
                    player.sendMessage(Component.text("Failed to clear loot table override", NamedTextColor.RED))
                }
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
