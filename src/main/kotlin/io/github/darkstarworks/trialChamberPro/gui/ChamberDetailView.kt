package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.components.GuiText
import io.github.darkstarworks.trialChamberPro.models.Chamber
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.concurrent.TimeUnit

/**
 * Chamber detail view — central management screen for a single chamber.
 * All strings from `messages.yml` under `gui.chamber-detail.*` (v1.3.0).
 */
class ChamberDetailView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val chamber: Chamber
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(6, GuiText.plain(plugin, "gui.chamber-detail.title", "chamber" to chamber.name))
        val pane = StaticPane(0, 0, 9, 6)

        pane.addItem(GuiItem(createChamberInfoItem()) { it.isCancelled = true }, 4, 0)

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

        pane.addItem(GuiItem(createSettingsItem()) { event ->
            event.isCancelled = true
            menu.openChamberSettings(player, chamber)
        }, 2, 2)

        pane.addItem(GuiItem(createVaultManagementItem()) { event ->
            event.isCancelled = true
            menu.openVaultManagement(player, chamber)
        }, 4, 2)

        pane.addItem(GuiItem(createTeleportItem()) { event ->
            event.isCancelled = true
            handleTeleport(player, event.isLeftClick, event.isRightClick)
        }, 6, 2)

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

        pane.addItem(GuiComponents.backButton(plugin, "gui.common.dest-chambers") {
            menu.openChamberList(player)
        }, 0, 5)
        pane.addItem(GuiComponents.closeButton(plugin, player), 8, 5)

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

        return GuiComponents.infoItem(
            plugin, Material.LODESTONE,
            "gui.chamber-detail.info-name", "gui.chamber-detail.info-lore",
            "chamber" to chamber.name,
            "world" to chamber.world,
            "minX" to chamber.minX, "minY" to chamber.minY, "minZ" to chamber.minZ,
            "maxX" to chamber.maxX, "maxY" to chamber.maxY, "maxZ" to chamber.maxZ,
            "volume" to chamber.getVolume(),
            "inside" to playersInside,
            "normal" to normalCount, "ominous" to ominousCount,
            "reset" to formatDuration(timeUntilMs)
        )
    }

    private fun createNormalLootItem(): ItemStack {
        val tableName = "chamber-${chamber.name.lowercase()}"
        val tableExists = plugin.lootManager.getTable(tableName) != null
        val loreKey = if (tableExists)
            "gui.chamber-detail.normal-loot-lore-custom" else "gui.chamber-detail.normal-loot-lore-default"
        return GuiComponents.infoItem(
            plugin, Material.GREEN_WOOL,
            "gui.chamber-detail.normal-loot-name", loreKey,
            "table" to tableName
        )
    }

    private fun createOminousLootItem(): ItemStack {
        val tableName = "ominous-${chamber.name.lowercase()}"
        val tableExists = plugin.lootManager.getTable(tableName) != null
        val loreKey = if (tableExists)
            "gui.chamber-detail.ominous-loot-lore-custom" else "gui.chamber-detail.ominous-loot-lore-default"
        return GuiComponents.infoItem(
            plugin, Material.PURPLE_WOOL,
            "gui.chamber-detail.ominous-loot-name", loreKey,
            "table" to tableName
        )
    }

    private fun createLootOverridesItem(): ItemStack {
        val normalOverride = chamber.normalLootTable
        val ominousOverride = chamber.ominousLootTable
        val hasOverrides = normalOverride != null || ominousOverride != null
        val defaultTag = plugin.getMessage("gui.chamber-detail.overrides-default-tag")
        return GuiComponents.infoItem(
            plugin, if (hasOverrides) Material.ENCHANTED_BOOK else Material.BOOK,
            "gui.chamber-detail.overrides-name", "gui.chamber-detail.overrides-lore",
            "normal" to (normalOverride ?: defaultTag),
            "ominous" to (ominousOverride ?: defaultTag)
        )
    }

    private fun createSettingsItem(): ItemStack {
        val exitLoc = chamber.getExitLocation()
        val exitStr = exitLoc?.let { "${it.blockX}, ${it.blockY}, ${it.blockZ}" }
            ?: plugin.getMessage("gui.chamber-detail.exit-not-set")
        return GuiComponents.infoItem(
            plugin, Material.COMPARATOR,
            "gui.chamber-detail.settings-name", "gui.chamber-detail.settings-lore",
            "interval" to formatDuration(chamber.resetInterval * 1000),
            "exit" to exitStr
        )
    }

    private fun createVaultManagementItem(): ItemStack {
        val (normalCount, ominousCount) = plugin.vaultManager.getVaultCounts(chamber.id)
        return GuiComponents.infoItem(
            plugin, Material.VAULT,
            "gui.chamber-detail.vault-mgmt-name", "gui.chamber-detail.vault-mgmt-lore",
            "normal" to normalCount, "ominous" to ominousCount
        )
    }

    private fun createTeleportItem(): ItemStack {
        return GuiComponents.infoItem(
            plugin, Material.ENDER_PEARL,
            "gui.chamber-detail.teleport-name", "gui.chamber-detail.teleport-lore"
        )
    }

    private fun createResetChamberItem(): ItemStack {
        val playersInside = chamber.getPlayersInside().size
        val lastReset = chamber.lastReset?.let { formatElapsedTime(System.currentTimeMillis() - it) }
            ?: plugin.getMessage("gui.chamber-detail.last-reset-never")
        return GuiComponents.infoItem(
            plugin, Material.CLOCK,
            "gui.chamber-detail.reset-name", "gui.chamber-detail.reset-lore",
            "inside" to playersInside, "lastReset" to lastReset
        )
    }

    private fun createExitPlayersItem(): ItemStack {
        val playersInside = chamber.getPlayersInside().size
        val exitLoc = chamber.getExitLocation()
        val exitStr = exitLoc?.let { "${it.blockX}, ${it.blockY}, ${it.blockZ}" }
            ?: plugin.getMessage("gui.chamber-detail.exit-players-default")
        return GuiComponents.infoItem(
            plugin, Material.OAK_DOOR,
            "gui.chamber-detail.exit-players-name", "gui.chamber-detail.exit-players-lore",
            "inside" to playersInside, "exit" to exitStr
        )
    }

    private fun createSnapshotItem(): ItemStack {
        val snapshotExists = chamber.getSnapshotFile()?.exists() == true
        val loreKey = if (snapshotExists)
            "gui.chamber-detail.snapshot-lore-exists" else "gui.chamber-detail.snapshot-lore-missing"
        return GuiComponents.infoItem(
            plugin, Material.SPYGLASS,
            "gui.chamber-detail.snapshot-name", loreKey
        )
    }

    // ==================== Click Handlers (unchanged behavior) ====================

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

    private fun handleTeleport(player: Player, left: Boolean, right: Boolean) {
        val world = chamber.getWorld()
        if (world == null) {
            player.sendMessage(plugin.getMessage("gui-chamber-world-not-loaded"))
            return
        }
        when {
            right -> {
                val exitLoc = chamber.getExitLocation()
                if (exitLoc == null) {
                    player.sendMessage(plugin.getMessage("gui-no-exit-location"))
                    return
                }
                player.teleport(exitLoc)
                player.sendMessage(plugin.getMessage("gui-teleport-to-exit", "chamber" to chamber.name))
            }
            else -> {
                val centerX = (chamber.minX + chamber.maxX) / 2.0
                val centerY = (chamber.minY + chamber.maxY) / 2.0
                val centerZ = (chamber.minZ + chamber.maxZ) / 2.0
                val location = org.bukkit.Location(world, centerX, centerY, centerZ)
                player.teleport(location)
                player.sendMessage(plugin.getMessage("gui-teleport-to-center", "chamber" to chamber.name))
            }
        }
        player.closeInventory()
    }

    private fun handleResetChamberClick(player: Player, left: Boolean, right: Boolean, shift: Boolean) {
        when {
            shift && right -> {
                player.sendMessage(plugin.getMessage("gui-forcing-reset", "chamber" to chamber.name))
                plugin.launchAsync {
                    try {
                        plugin.resetManager.resetChamber(
                            chamber, player,
                            io.github.darkstarworks.trialChamberPro.api.events.ChamberResetEvent.Reason.FORCED
                        )
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
        val dest = chamber.getExitLocation() ?: chamber.getWorld()?.spawnLocation ?: return
        players.forEach { p ->
            plugin.scheduler.runAtEntity(p, Runnable {
                if (p.isOnline) {
                    p.teleport(dest)
                    p.sendMessage(plugin.getMessage("gui-player-ejected"))
                }
            })
        }
    }

    private fun formatDuration(milliseconds: Long): String {
        val nowToken = plugin.getMessage("gui.chamber-detail.duration-now")
        if (milliseconds <= 0) return nowToken
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
        }.trim().ifEmpty { nowToken }
    }

    private fun formatElapsedTime(milliseconds: Long): String {
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        val days = TimeUnit.MILLISECONDS.toDays(milliseconds)
        val key = when {
            days > 0 -> "gui.chamber-detail.elapsed-days"
            hours > 0 -> "gui.chamber-detail.elapsed-hours"
            minutes > 0 -> "gui.chamber-detail.elapsed-minutes"
            else -> "gui.chamber-detail.elapsed-seconds"
        }
        val n = when {
            days > 0 -> days
            hours > 0 -> hours
            minutes > 0 -> minutes
            else -> seconds
        }
        return plugin.getMessage(key, "n" to n)
    }
}
