package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.Chamber
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.concurrent.TimeUnit

class LootTypeSelectView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val chamber: Chamber
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(5, "Manage: ${chamber.name}")
        val pane = StaticPane(0, 0, 9, 5)

        // Normal Loot
        val normal = ItemStack(Material.GREEN_WOOL).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Normal Loot", NamedTextColor.GREEN))
                lore(listOf(
                    Component.text("Edit the normal loot table", NamedTextColor.GRAY)
                ))
            }
        }

        // Ominous Loot
        val ominous = ItemStack(Material.PURPLE_WOOL).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Ominous Loot", NamedTextColor.LIGHT_PURPLE))
                lore(listOf(
                    Component.text("Edit the ominous loot table", NamedTextColor.GRAY)
                ))
            }
        }

        // Reset Chamber Block
        val resetChamber = createResetChamberItem()

        // Exit Players Block
        val exitPlayers = createExitPlayersItem()

        // Layout: Row 1 - Normal and Ominous loot
        pane.addItem(GuiItem(normal) {
            it.isCancelled = true
            menu.openLootEditor(player, chamber, MenuService.LootKind.NORMAL)
        }, 2, 1)

        pane.addItem(GuiItem(ominous) {
            it.isCancelled = true
            menu.openLootEditor(player, chamber, MenuService.LootKind.OMINOUS)
        }, 6, 1)

        // Row 2 - Reset Chamber and Exit Players
        pane.addItem(GuiItem(resetChamber) { event ->
            event.isCancelled = true
            handleResetChamberClick(player, event.isLeftClick, event.isRightClick, event.isShiftClick)
        }, 2, 3)

        pane.addItem(GuiItem(exitPlayers) { event ->
            event.isCancelled = true
            handleExitPlayersClick(player, event.isLeftClick, event.isRightClick, event.isShiftClick)
        }, 6, 3)

        // Back button
        val back = ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Back", NamedTextColor.YELLOW))
            }
        }
        pane.addItem(GuiItem(back) {
            it.isCancelled = true
            menu.openOverview(player)
        }, 0, 4)

        gui.addPane(pane)
        return gui
    }

    private fun createResetChamberItem(): ItemStack {
        val item = ItemStack(Material.CLOCK)

        // Players currently inside
        val playersInside = chamber.getPlayersInside().size

        // Last reset time
        val lastReset = chamber.lastReset?.let { last ->
            val elapsed = System.currentTimeMillis() - last
            formatElapsedTime(elapsed)
        } ?: "Never"

        item.itemMeta = item.itemMeta?.apply {
            displayName(Component.text("Reset Chamber", NamedTextColor.GOLD))
            lore(listOf(
                Component.text("Timed Reset:", NamedTextColor.YELLOW),
                Component.text("  Left: 5 minute timer", NamedTextColor.AQUA),
                Component.text("  Right: 1 minute timer", NamedTextColor.AQUA),
                Component.text("", NamedTextColor.GRAY),
                Component.text("Force Reset ⚠️:", NamedTextColor.RED),
                Component.text("  Shift + Right Click", NamedTextColor.GRAY),
                Component.text("", NamedTextColor.LIGHT_PURPLE),
                Component.text("Players inside: $playersInside", NamedTextColor.DARK_AQUA),
                Component.text("Last reset: $lastReset", NamedTextColor.DARK_AQUA)
            ))
        }
        return item
    }

    private fun createExitPlayersItem(): ItemStack {
        val item = ItemStack(Material.OAK_DOOR)
        val playersInside = chamber.getPlayersInside().size

        // Get exit location
        var exitLocation = "Exit missing, using World Spawn"
        chamber.getExitLocation()?.let { exit ->
            exitLocation = "${exit.blockX}, ${exit.blockY}, ${exit.blockZ}"
        }

        item.itemMeta = item.itemMeta?.apply {
            displayName(Component.text("Exit Players", NamedTextColor.RED))
            lore(listOf(
                Component.text("Timed Exit:", NamedTextColor.YELLOW),
                Component.text("  Left: 15 seconds timer", NamedTextColor.AQUA),
                Component.text("  Right: 30 seconds timer", NamedTextColor.AQUA),
                Component.text("", NamedTextColor.GRAY),
                Component.text("Force Exit ⚠️:", NamedTextColor.RED),
                Component.text("  Shift + Right Click", NamedTextColor.GRAY),
                Component.text("", NamedTextColor.LIGHT_PURPLE),
                Component.text("Players inside: $playersInside", NamedTextColor.DARK_AQUA),
                Component.text("Exit Location:", NamedTextColor.DARK_AQUA),
                Component.text("  $exitLocation", NamedTextColor.GRAY)
            ))
        }
        return item
    }

    private fun handleResetChamberClick(player: Player, left: Boolean, right: Boolean, shift: Boolean) {
        when {
            shift && right -> {
                // Force reset immediately (async), then notify on main thread
                player.sendMessage(Component.text("Forcing reset for '${chamber.name}'...", NamedTextColor.YELLOW))
                plugin.launchAsync {
                    try {
                        plugin.resetManager.resetChamber(chamber)
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            player.sendMessage(Component.text("Chamber '${chamber.name}' has been reset!", NamedTextColor.GREEN))
                            player.closeInventory()
                        })
                    } catch (e: Exception) {
                        Bukkit.getScheduler().runTask(plugin, Runnable {
                            player.sendMessage(Component.text("Failed to reset chamber: ${e.message}", NamedTextColor.RED))
                        })
                    }
                }
            }
            left -> {
                // Schedule reset in 5 minutes
                scheduleReset(player, 5 * 60)
            }
            right -> {
                // Schedule reset in 1 minute
                scheduleReset(player, 60)
            }
        }
    }

    private fun handleExitPlayersClick(player: Player, left: Boolean, right: Boolean, shift: Boolean) {
        val playersInChamber = chamber.getPlayersInside()

        if (playersInChamber.isEmpty()) {
            player.sendMessage(Component.text("No players are currently in this chamber.", NamedTextColor.YELLOW))
            return
        }

        when {
            shift && right -> {
                // Force exit immediately
                exitPlayers(playersInChamber)
                player.sendMessage(Component.text("Ejected ${playersInChamber.size} player(s) from '${chamber.name}'!", NamedTextColor.GREEN))
                player.closeInventory()
            }
            left -> {
                // Schedule exit in 15 seconds
                scheduleExit(player, playersInChamber, 15)
            }
            right -> {
                // Schedule exit in 30 seconds
                scheduleExit(player, playersInChamber, 30)
            }
        }
    }

    private fun scheduleReset(player: Player, seconds: Int) {
        player.sendMessage(Component.text("Chamber '${chamber.name}' will reset in $seconds seconds.", NamedTextColor.YELLOW))

        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            plugin.launchAsync {
                try {
                    plugin.resetManager.resetChamber(chamber)
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        player.sendMessage(Component.text("Chamber '${chamber.name}' has been reset!", NamedTextColor.GREEN))
                    })
                } catch (e: Exception) {
                    Bukkit.getScheduler().runTask(plugin, Runnable {
                        player.sendMessage(Component.text("Failed to reset chamber: ${e.message}", NamedTextColor.RED))
                    })
                }
            }
        }, seconds * 20L) // Convert seconds to ticks (20 ticks = 1 second)
    }

    private fun scheduleExit(player: Player, playersToExit: List<Player>, seconds: Int) {
        player.sendMessage(Component.text("Players will be ejected from '${chamber.name}' in $seconds seconds.", NamedTextColor.YELLOW))

        // Warn players inside
        playersToExit.forEach { p ->
            p.sendMessage(Component.text("You will be ejected from this chamber in $seconds seconds!", NamedTextColor.RED))
        }

        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            exitPlayers(playersToExit)
            player.sendMessage(Component.text("Ejected ${playersToExit.size} player(s) from '${chamber.name}'!", NamedTextColor.GREEN))
        }, seconds * 20L)
    }

    private fun exitPlayers(players: List<Player>) {
        val dest = chamber.getExitLocation() ?: chamber.getWorld()?.spawnLocation

        players.forEach { p ->
            if (dest != null) {
                p.teleport(dest)
                p.sendMessage(Component.text("You have been ejected from the chamber!", NamedTextColor.YELLOW))
            }
        }
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