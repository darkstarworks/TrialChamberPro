package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.Chamber
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.concurrent.TimeUnit

/**
 * Chamber detail view - comprehensive management screen for a single chamber.
 * Provides access to loot editing, settings, vault management, and reset controls.
 * Replaces the old LootTypeSelectView with expanded functionality.
 */
class ChamberDetailView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val chamber: Chamber
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(6, "Chamber: ${chamber.name}")
        val pane = StaticPane(0, 0, 9, 6)

        // Row 0: Chamber info header
        pane.addItem(GuiItem(createChamberInfoItem()) { it.isCancelled = true }, 4, 0)

        // Row 1: Loot editing (Normal, Ominous, Loot Overrides)
        pane.addItem(GuiItem(createNormalLootItem()) { event ->
            event.isCancelled = true
            handleLootKindClick(player, MenuService.LootKind.NORMAL)
        }, 2, 1)

        pane.addItem(GuiItem(createOminousLootItem()) { event ->
            event.isCancelled = true
            handleLootKindClick(player, MenuService.LootKind.OMINOUS)
        }, 4, 1)

        pane.addItem(GuiItem(createLootOverridesItem()) { event ->
            event.isCancelled = true
            menu.openChamberSettings(player, chamber)
        }, 6, 1)

        // Row 2: Chamber settings and vault management
        pane.addItem(GuiItem(createSettingsItem()) { event ->
            event.isCancelled = true
            menu.openChamberSettings(player, chamber)
        }, 2, 2)

        pane.addItem(GuiItem(createVaultManagementItem()) { event ->
            event.isCancelled = true
            menu.openVaultManagement(player, chamber)
        }, 4, 2)

        pane.addItem(GuiItem(createTeleportItem(player)) { event ->
            event.isCancelled = true
            handleTeleport(player)
        }, 6, 2)

        // Row 3: Reset and exit controls
        pane.addItem(GuiItem(createResetChamberItem()) { event ->
            event.isCancelled = true
            handleResetChamberClick(player, event.isLeftClick, event.isRightClick, event.isShiftClick)
        }, 2, 3)

        pane.addItem(GuiItem(createExitPlayersItem()) { event ->
            event.isCancelled = true
            handleExitPlayersClick(player, event.isLeftClick, event.isRightClick, event.isShiftClick)
        }, 4, 3)

        pane.addItem(GuiItem(createSnapshotItem()) { event ->
            event.isCancelled = true
            handleSnapshotClick(player, event.isLeftClick, event.isShiftClick)
        }, 6, 3)

        // Row 5: Navigation
        val backItem = ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Back to Chambers", NamedTextColor.YELLOW))
            }
        }
        pane.addItem(GuiItem(backItem) { event ->
            event.isCancelled = true
            menu.openChamberList(player)
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
        gui.setOnGlobalDrag { it.isCancelled = true }

        return gui
    }

    // ==================== Item Creators ====================

    private fun createChamberInfoItem(): ItemStack {
        val playersInside = chamber.getPlayersInside().size
        val (normalCount, ominousCount) = plugin.vaultManager.getVaultCounts(chamber.id)
        val timeUntilMs = plugin.resetManager.getTimeUntilReset(chamber)

        return ItemStack(Material.LODESTONE).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text(chamber.name, NamedTextColor.AQUA)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("World: ${chamber.world}", NamedTextColor.GRAY),
                    Component.text("Bounds: (${chamber.minX},${chamber.minY},${chamber.minZ})", NamedTextColor.GRAY),
                    Component.text("     to (${chamber.maxX},${chamber.maxY},${chamber.maxZ})", NamedTextColor.GRAY),
                    Component.text("Volume: ${chamber.getVolume()} blocks", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Players Inside: $playersInside", NamedTextColor.YELLOW),
                    Component.text("Vaults: $normalCount Normal, $ominousCount Ominous", NamedTextColor.YELLOW),
                    Component.text("Reset in: ${formatDuration(timeUntilMs)}", NamedTextColor.YELLOW)
                ))
            }
        }
    }

    private fun createNormalLootItem(): ItemStack {
        val tableName = "chamber-${chamber.name.lowercase()}"
        val tableExists = plugin.lootManager.getTable(tableName) != null

        return ItemStack(Material.GREEN_WOOL).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Normal Loot", NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Edit normal vault loot table", NamedTextColor.GRAY),
                    Component.empty(),
                    if (tableExists) {
                        Component.text("Table: $tableName", NamedTextColor.YELLOW)
                    } else {
                        Component.text("No custom table (using default)", NamedTextColor.GRAY)
                    },
                    Component.empty(),
                    Component.text("Click to edit", NamedTextColor.GREEN)
                ))
            }
        }
    }

    private fun createOminousLootItem(): ItemStack {
        val tableName = "ominous-${chamber.name.lowercase()}"
        val tableExists = plugin.lootManager.getTable(tableName) != null

        return ItemStack(Material.PURPLE_WOOL).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Ominous Loot", NamedTextColor.DARK_PURPLE)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Edit ominous vault loot table", NamedTextColor.GRAY),
                    Component.empty(),
                    if (tableExists) {
                        Component.text("Table: $tableName", NamedTextColor.YELLOW)
                    } else {
                        Component.text("No custom table (using default)", NamedTextColor.GRAY)
                    },
                    Component.empty(),
                    Component.text("Click to edit", NamedTextColor.GREEN)
                ))
            }
        }
    }

    private fun createLootOverridesItem(): ItemStack {
        val normalOverride = chamber.normalLootTable
        val ominousOverride = chamber.ominousLootTable
        val hasOverrides = normalOverride != null || ominousOverride != null

        return ItemStack(if (hasOverrides) Material.ENCHANTED_BOOK else Material.BOOK).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Loot Table Overrides", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Override which loot tables this", NamedTextColor.GRAY),
                    Component.text("chamber uses for vaults", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Normal: ", NamedTextColor.GREEN)
                        .append(Component.text(normalOverride ?: "(default)",
                            if (normalOverride != null) NamedTextColor.WHITE else NamedTextColor.GRAY)),
                    Component.text("Ominous: ", NamedTextColor.DARK_PURPLE)
                        .append(Component.text(ominousOverride ?: "(default)",
                            if (ominousOverride != null) NamedTextColor.WHITE else NamedTextColor.GRAY)),
                    Component.empty(),
                    Component.text("Click to configure", NamedTextColor.GREEN)
                ))
            }
        }
    }

    private fun createSettingsItem(): ItemStack {
        val exitLoc = chamber.getExitLocation()
        val exitStr = exitLoc?.let { "${it.blockX}, ${it.blockY}, ${it.blockZ}" } ?: "Not set"

        return ItemStack(Material.COMPARATOR).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Chamber Settings", NamedTextColor.WHITE)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Configure chamber-specific settings", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Reset Interval: ${formatDuration(chamber.resetInterval * 1000)}", NamedTextColor.YELLOW),
                    Component.text("Exit Location: $exitStr", NamedTextColor.YELLOW),
                    Component.empty(),
                    Component.text("Click to configure", NamedTextColor.GREEN)
                ))
            }
        }
    }

    private fun createVaultManagementItem(): ItemStack {
        val (normalCount, ominousCount) = plugin.vaultManager.getVaultCounts(chamber.id)

        return ItemStack(Material.VAULT).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Vault Management", NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Manage vault cooldowns and locks", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("$normalCount Normal Vaults", NamedTextColor.GREEN),
                    Component.text("$ominousCount Ominous Vaults", NamedTextColor.DARK_PURPLE),
                    Component.empty(),
                    Component.text("Click to manage", NamedTextColor.GREEN)
                ))
            }
        }
    }

    private fun createTeleportItem(player: Player): ItemStack {
        return ItemStack(Material.ENDER_PEARL).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Teleport", NamedTextColor.AQUA)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Teleport to this chamber", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Left Click: Center of chamber", NamedTextColor.YELLOW),
                    Component.text("Right Click: Exit location", NamedTextColor.YELLOW),
                    Component.empty(),
                    Component.text("Click to teleport", NamedTextColor.GREEN)
                ))
            }
        }
    }

    private fun createResetChamberItem(): ItemStack {
        val playersInside = chamber.getPlayersInside().size
        val lastReset = chamber.lastReset?.let { formatElapsedTime(System.currentTimeMillis() - it) } ?: "Never"

        return ItemStack(Material.CLOCK).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Reset Chamber", NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Timed Reset:", NamedTextColor.YELLOW),
                    Component.text("  Left: 5 minute timer", NamedTextColor.AQUA),
                    Component.text("  Right: 1 minute timer", NamedTextColor.AQUA),
                    Component.empty(),
                    Component.text("Force Reset:", NamedTextColor.RED),
                    Component.text("  Shift + Right Click", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Players inside: $playersInside", NamedTextColor.DARK_AQUA),
                    Component.text("Last reset: $lastReset", NamedTextColor.DARK_AQUA)
                ))
            }
        }
    }

    private fun createExitPlayersItem(): ItemStack {
        val playersInside = chamber.getPlayersInside().size
        val exitLoc = chamber.getExitLocation()
        val exitStr = exitLoc?.let { "${it.blockX}, ${it.blockY}, ${it.blockZ}" } ?: "World Spawn"

        return ItemStack(Material.OAK_DOOR).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Exit Players", NamedTextColor.RED)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Timed Exit:", NamedTextColor.YELLOW),
                    Component.text("  Left: 15 seconds timer", NamedTextColor.AQUA),
                    Component.text("  Right: 30 seconds timer", NamedTextColor.AQUA),
                    Component.empty(),
                    Component.text("Force Exit:", NamedTextColor.RED),
                    Component.text("  Shift + Right Click", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Players inside: $playersInside", NamedTextColor.DARK_AQUA),
                    Component.text("Exit to: $exitStr", NamedTextColor.DARK_AQUA)
                ))
            }
        }
    }

    private fun createSnapshotItem(): ItemStack {
        val snapshotExists = chamber.getSnapshotFile()?.exists() == true

        return ItemStack(Material.SPYGLASS).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Snapshot", NamedTextColor.BLUE)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Manage chamber snapshots", NamedTextColor.GRAY),
                    Component.empty(),
                    if (snapshotExists) {
                        Component.text("Snapshot: Exists", NamedTextColor.GREEN)
                    } else {
                        Component.text("Snapshot: None", NamedTextColor.RED)
                    },
                    Component.empty(),
                    Component.text("Left Click: Create snapshot", NamedTextColor.YELLOW),
                    Component.text("Shift+Left: Restore snapshot", NamedTextColor.YELLOW)
                ))
            }
        }
    }

    // ==================== Click Handlers ====================

    private fun handleLootKindClick(player: Player, kind: MenuService.LootKind) {
        val tableName = when (kind) {
            MenuService.LootKind.NORMAL -> "chamber-${chamber.name.lowercase()}"
            MenuService.LootKind.OMINOUS -> "ominous-${chamber.name.lowercase()}"
        }

        val table = plugin.lootManager.getTable(tableName)
        if (table != null && !table.isLegacyFormat()) {
            menu.openPoolSelect(player, chamber, kind)
        } else {
            menu.openLootEditor(player, chamber, kind, null)
        }
    }

    private fun handleTeleport(player: Player) {
        val world = chamber.getWorld()
        if (world == null) {
            player.sendMessage(plugin.getMessage("gui-chamber-world-not-loaded"))
            return
        }

        val centerX = (chamber.minX + chamber.maxX) / 2.0
        val centerY = (chamber.minY + chamber.maxY) / 2.0
        val centerZ = (chamber.minZ + chamber.maxZ) / 2.0

        val location = org.bukkit.Location(world, centerX, centerY, centerZ)
        player.teleport(location)
        player.sendMessage(plugin.getMessage("gui-teleport-to-center", "chamber" to chamber.name))
        player.closeInventory()
    }

    private fun handleResetChamberClick(player: Player, left: Boolean, right: Boolean, shift: Boolean) {
        when {
            shift && right -> {
                player.sendMessage(plugin.getMessage("gui-forcing-reset", "chamber" to chamber.name))
                plugin.launchAsync {
                    try {
                        plugin.resetManager.resetChamber(chamber, player)
                        plugin.scheduler.runAtEntity(player, Runnable {
                            player.sendMessage(plugin.getMessage("gui-chamber-reset-complete", "chamber" to chamber.name))
                            player.closeInventory()
                        })
                    } catch (e: Exception) {
                        plugin.scheduler.runAtEntity(player, Runnable {
                            player.sendMessage(plugin.getMessage("gui-reset-failed", "error" to (e.message ?: "Unknown error")))
                        })
                    }
                }
            }
            left -> scheduleReset(player, 5 * 60)
            right -> scheduleReset(player, 60)
        }
    }

    private fun handleExitPlayersClick(player: Player, left: Boolean, right: Boolean, shift: Boolean) {
        val playersInChamber = chamber.getPlayersInside()

        if (playersInChamber.isEmpty()) {
            player.sendMessage(plugin.getMessage("gui-no-players-in-chamber"))
            return
        }

        when {
            shift && right -> {
                exitPlayers(playersInChamber)
                player.sendMessage(plugin.getMessage("gui-players-ejected", "count" to playersInChamber.size, "chamber" to chamber.name))
                player.closeInventory()
            }
            left -> scheduleExit(player, playersInChamber, 15)
            right -> scheduleExit(player, playersInChamber, 30)
        }
    }

    private fun handleSnapshotClick(player: Player, left: Boolean, shift: Boolean) {
        when {
            shift && left -> {
                // Restore snapshot
                val snapshotFile = chamber.getSnapshotFile()
                if (snapshotFile == null || !snapshotFile.exists()) {
                    player.sendMessage(plugin.getMessage("gui-no-snapshot-exists"))
                    return
                }
                player.sendMessage(plugin.getMessage("gui-restoring-snapshot", "chamber" to chamber.name))
                plugin.launchAsync {
                    try {
                        plugin.resetManager.resetChamber(chamber, player)
                        plugin.scheduler.runAtEntity(player, Runnable {
                            player.sendMessage(plugin.getMessage("gui-snapshot-restored"))
                        })
                    } catch (e: Exception) {
                        plugin.scheduler.runAtEntity(player, Runnable {
                            player.sendMessage(plugin.getMessage("gui-restore-failed", "error" to (e.message ?: "Unknown error")))
                        })
                    }
                }
            }
            left -> {
                // Create snapshot
                player.sendMessage(plugin.getMessage("gui-creating-snapshot", "chamber" to chamber.name))
                plugin.launchAsync {
                    try {
                        plugin.snapshotManager.createSnapshot(chamber)
                        plugin.scheduler.runAtEntity(player, Runnable {
                            player.sendMessage(plugin.getMessage("gui-snapshot-created"))
                        })
                    } catch (e: Exception) {
                        plugin.scheduler.runAtEntity(player, Runnable {
                            player.sendMessage(plugin.getMessage("gui-snapshot-create-failed", "error" to (e.message ?: "Unknown error")))
                        })
                    }
                }
            }
        }
    }

    // ==================== Utility Methods ====================

    private fun scheduleReset(player: Player, seconds: Int) {
        player.sendMessage(plugin.getMessage("gui-reset-scheduled", "chamber" to chamber.name, "seconds" to seconds))

        plugin.scheduler.runTaskLater(Runnable {
            plugin.launchAsync {
                try {
                    plugin.resetManager.resetChamber(chamber, player)
                    plugin.scheduler.runAtEntity(player, Runnable {
                        player.sendMessage(plugin.getMessage("gui-chamber-reset-complete", "chamber" to chamber.name))
                    })
                } catch (e: Exception) {
                    plugin.scheduler.runAtEntity(player, Runnable {
                        player.sendMessage(plugin.getMessage("gui-reset-failed", "error" to (e.message ?: "Unknown error")))
                    })
                }
            }
        }, seconds * 20L)
    }

    private fun scheduleExit(player: Player, playersToExit: List<Player>, seconds: Int) {
        player.sendMessage(plugin.getMessage("gui-exit-scheduled", "chamber" to chamber.name, "seconds" to seconds))

        playersToExit.forEach { p ->
            plugin.scheduler.runAtEntity(p, Runnable {
                p.sendMessage(plugin.getMessage("gui-exit-warning", "seconds" to seconds))
            })
        }

        plugin.scheduler.runTaskLater(Runnable {
            exitPlayers(playersToExit)
            plugin.scheduler.runAtEntity(player, Runnable {
                player.sendMessage(plugin.getMessage("gui-players-ejected", "count" to playersToExit.size, "chamber" to chamber.name))
            })
        }, seconds * 20L)
    }

    private fun exitPlayers(players: List<Player>) {
        val dest = chamber.getExitLocation() ?: chamber.getWorld()?.spawnLocation
        if (dest == null) return

        players.forEach { p ->
            // Use Folia-safe scheduling - each player may be in a different region
            plugin.scheduler.runAtEntity(p, Runnable {
                if (p.isOnline) {
                    p.teleport(dest)
                    p.sendMessage(plugin.getMessage("gui-player-ejected"))
                }
            })
        }
    }

    private fun formatDuration(milliseconds: Long): String {
        if (milliseconds <= 0) return "Now"

        val seconds = milliseconds / 1000
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            if (secs > 0 && days == 0L) append("${secs}s")
        }.trim().ifEmpty { "Now" }
    }

    private fun formatElapsedTime(milliseconds: Long): String {
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        val days = TimeUnit.MILLISECONDS.toDays(milliseconds)

        return when {
            days > 0 -> "$days day(s) ago"
            hours > 0 -> "$hours hour(s) ago"
            minutes > 0 -> "$minutes minute(s) ago"
            else -> "$seconds second(s) ago"
        }
    }
}
