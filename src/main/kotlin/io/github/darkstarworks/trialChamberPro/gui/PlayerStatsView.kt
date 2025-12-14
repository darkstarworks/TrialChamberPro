package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.util.UUID

/**
 * Player stats view - displays detailed statistics for a specific player.
 */
class PlayerStatsView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val targetUuid: UUID
) {
    fun build(player: Player): ChestGui {
        val offlinePlayer = Bukkit.getOfflinePlayer(targetUuid)
        val playerName = offlinePlayer.name ?: "Unknown"

        val gui = ChestGui(5, "Stats: $playerName")
        val pane = StaticPane(0, 0, 9, 5)

        // Get stats
        val stats = runBlocking { plugin.statisticsManager.getStats(targetUuid) }
        val totalVaultsOpened = stats.normalVaultsOpened + stats.ominousVaultsOpened

        // Row 0: Player head
        pane.addItem(GuiItem(createPlayerHeadItem(offlinePlayer, playerName)) { it.isCancelled = true }, 4, 0)

        // Row 1-2: Stats
        pane.addItem(GuiItem(createStatItem("Vaults Opened", totalVaultsOpened, Material.VAULT, NamedTextColor.GOLD)) { it.isCancelled = true }, 1, 1)
        pane.addItem(GuiItem(createStatItem("Normal Vaults", stats.normalVaultsOpened, Material.GREEN_WOOL, NamedTextColor.GREEN)) { it.isCancelled = true }, 3, 1)
        pane.addItem(GuiItem(createStatItem("Ominous Vaults", stats.ominousVaultsOpened, Material.PURPLE_WOOL, NamedTextColor.DARK_PURPLE)) { it.isCancelled = true }, 5, 1)
        pane.addItem(GuiItem(createStatItem("Chambers Completed", stats.chambersCompleted, Material.LODESTONE, NamedTextColor.AQUA)) { it.isCancelled = true }, 7, 1)

        pane.addItem(GuiItem(createStatItem("Mobs Killed", stats.mobsKilled, Material.IRON_SWORD, NamedTextColor.RED)) { it.isCancelled = true }, 2, 2)
        pane.addItem(GuiItem(createStatItem("Deaths", stats.deaths, Material.SKELETON_SKULL, NamedTextColor.DARK_GRAY)) { it.isCancelled = true }, 4, 2)
        pane.addItem(GuiItem(createTimeStatItem("Time Spent", stats.timeSpent)) { it.isCancelled = true }, 6, 2)

        // Row 3: Additional info
        val kd = if (stats.deaths > 0) {
            String.format("%.2f", stats.mobsKilled.toDouble() / stats.deaths)
        } else {
            "Perfect"
        }

        val infoItem = ItemStack(Material.WRITABLE_BOOK).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Summary", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("K/D Ratio: $kd", NamedTextColor.GRAY),
                    Component.text("Avg Vaults per Chamber: ${
                        if (stats.chambersCompleted > 0)
                            String.format("%.1f", totalVaultsOpened.toDouble() / stats.chambersCompleted)
                        else "N/A"
                    }", NamedTextColor.GRAY)
                ))
            }
        }
        pane.addItem(GuiItem(infoItem) { it.isCancelled = true }, 4, 3)

        // Row 4: Navigation
        val backItem = ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Back to Statistics", NamedTextColor.YELLOW))
            }
        }
        pane.addItem(GuiItem(backItem) { event ->
            event.isCancelled = true
            menu.openStatsMenu(player)
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

    private fun createPlayerHeadItem(offlinePlayer: org.bukkit.OfflinePlayer, name: String): ItemStack {
        return ItemStack(Material.PLAYER_HEAD).apply {
            itemMeta = (itemMeta as? SkullMeta)?.apply {
                owningPlayer = offlinePlayer
                displayName(Component.text(name, NamedTextColor.AQUA)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("UUID: ${offlinePlayer.uniqueId}", NamedTextColor.GRAY),
                    if (offlinePlayer.isOnline) {
                        Component.text("Status: Online", NamedTextColor.GREEN)
                    } else {
                        Component.text("Status: Offline", NamedTextColor.RED)
                    }
                ))
            }
        }
    }

    private fun createStatItem(name: String, value: Int, material: Material, color: NamedTextColor): ItemStack {
        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text(name, color)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text(value.toString(), NamedTextColor.WHITE)
                ))
            }
        }
    }

    private fun createTimeStatItem(name: String, seconds: Long): ItemStack {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        val formatted = when {
            hours > 0 -> "${hours}h ${minutes}m ${secs}s"
            minutes > 0 -> "${minutes}m ${secs}s"
            else -> "${secs}s"
        }

        return ItemStack(Material.CLOCK).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text(name, NamedTextColor.YELLOW)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text(formatted, NamedTextColor.WHITE),
                    Component.text("(${seconds}s total)", NamedTextColor.GRAY)
                ))
            }
        }
    }
}
