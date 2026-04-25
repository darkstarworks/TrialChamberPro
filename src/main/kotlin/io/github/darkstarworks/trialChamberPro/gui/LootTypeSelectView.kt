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
import java.util.concurrent.TimeUnit

/**
 * Legacy "Manage Chamber" view, retained for `MenuService.openLootKindSelect` callers.
 * Strings under `gui.loot-type-select.*` and reused chamber-detail keys (v1.3.0).
 */
class LootTypeSelectView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val chamber: Chamber
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(5, GuiText.plain(plugin, "gui.loot-type-select.title", "chamber" to chamber.name))
        val pane = StaticPane(0, 0, 9, 5)

        pane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.GREEN_WOOL,
                "gui.loot-type-select.normal-name", "gui.loot-type-select.normal-lore")
        ) { event ->
            event.isCancelled = true
            handleLootKindClick(player, MenuService.LootKind.NORMAL)
        }, 2, 1)

        pane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.PURPLE_WOOL,
                "gui.loot-type-select.ominous-name", "gui.loot-type-select.ominous-lore")
        ) { event ->
            event.isCancelled = true
            handleLootKindClick(player, MenuService.LootKind.OMINOUS)
        }, 6, 1)

        val playersInside = chamber.getPlayersInside().size
        val lastReset = chamber.lastReset?.let { formatElapsedTime(System.currentTimeMillis() - it) }
            ?: plugin.getMessage("gui.chamber-detail.last-reset-never")
        pane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.CLOCK,
                "gui.chamber-detail.reset-name", "gui.chamber-detail.reset-lore",
                "inside" to playersInside, "lastReset" to lastReset)
        ) { event ->
            event.isCancelled = true
            handleResetChamberClick(player, event.isLeftClick, event.isRightClick, event.isShiftClick)
        }, 2, 3)

        val exitLoc = chamber.getExitLocation()
        val exitStr = exitLoc?.let { "${it.blockX}, ${it.blockY}, ${it.blockZ}" }
            ?: plugin.getMessage("gui.loot-type-select.exit-missing")
        pane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.OAK_DOOR,
                "gui.chamber-detail.exit-players-name", "gui.chamber-detail.exit-players-lore",
                "inside" to playersInside, "exit" to exitStr)
        ) { event ->
            event.isCancelled = true
            handleExitPlayersClick(player, event.isLeftClick, event.isRightClick, event.isShiftClick)
        }, 6, 3)

        @Suppress("DEPRECATION")
        pane.addItem(GuiComponents.backButton(plugin, "gui.common.dest-chambers") {
            menu.openOverview(player)
        }, 0, 4)

        gui.addPane(pane)
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }
        return gui
    }

    private fun handleLootKindClick(player: Player, kind: MenuService.LootKind) {
        val tableName = when (kind) {
            MenuService.LootKind.NORMAL -> "chamber-${chamber.name.lowercase()}"
            MenuService.LootKind.OMINOUS -> "ominous-${chamber.name.lowercase()}"
        }
        val table = plugin.lootManager.getTable(tableName)
        if (table != null && !table.isLegacyFormat()) menu.openPoolSelect(player, chamber, kind)
        else menu.openLootEditor(player, chamber, kind, null)
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
