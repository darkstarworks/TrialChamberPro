package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.components.GuiText
import kotlinx.coroutines.runBlocking
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Player stats view — displays detailed statistics for a specific player.
 * All strings come from `messages.yml` under `gui.player-stats.*` (v1.3.0).
 */
class PlayerStatsView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val targetUuid: UUID
) {
    fun build(player: Player): ChestGui {
        val offlinePlayer = Bukkit.getOfflinePlayer(targetUuid)
        val playerName = offlinePlayer.name ?: "Unknown"

        val gui = ChestGui(5, GuiText.plain(plugin, "gui.player-stats.title", "player" to playerName))
        val pane = StaticPane(0, 0, 9, 5)

        val stats = runBlocking { plugin.statisticsManager.getStats(targetUuid) }
        val totalVaultsOpened = stats.normalVaultsOpened + stats.ominousVaultsOpened

        // Row 0: Player head
        val headLoreKey = if (offlinePlayer.isOnline)
            "gui.player-stats.head-lore-online" else "gui.player-stats.head-lore-offline"
        pane.addItem(GuiItem(
            GuiComponents.playerHead(
                plugin, offlinePlayer,
                "gui.player-stats.head-name", headLoreKey,
                "player" to playerName,
                "uuid" to offlinePlayer.uniqueId.toString()
            )
        ) { it.isCancelled = true }, 4, 0)

        // Row 1-2: Stat items
        pane.addItem(statItem(Material.VAULT, "gui.player-stats.stat-vaults-name", totalVaultsOpened), 1, 1)
        pane.addItem(statItem(Material.GREEN_WOOL, "gui.player-stats.stat-normal-name", stats.normalVaultsOpened), 3, 1)
        pane.addItem(statItem(Material.PURPLE_WOOL, "gui.player-stats.stat-ominous-name", stats.ominousVaultsOpened), 5, 1)
        pane.addItem(statItem(Material.LODESTONE, "gui.player-stats.stat-chambers-name", stats.chambersCompleted), 7, 1)

        pane.addItem(statItem(Material.IRON_SWORD, "gui.player-stats.stat-mobs-name", stats.mobsKilled), 2, 2)
        pane.addItem(statItem(Material.SKELETON_SKULL, "gui.player-stats.stat-deaths-name", stats.deaths), 4, 2)
        pane.addItem(timeStatItem(stats.timeSpent), 6, 2)

        // Row 3: Summary
        val kd = if (stats.deaths > 0)
            String.format("%.2f", stats.mobsKilled.toDouble() / stats.deaths)
        else
            "Perfect"
        val avg = if (stats.chambersCompleted > 0)
            String.format("%.1f", totalVaultsOpened.toDouble() / stats.chambersCompleted)
        else
            "N/A"

        pane.addItem(GuiItem(
            GuiComponents.infoItem(
                plugin, Material.WRITABLE_BOOK,
                "gui.player-stats.summary-name", "gui.player-stats.summary-lore",
                "kd" to kd, "avg" to avg
            )
        ) { it.isCancelled = true }, 4, 3)

        // Row 4: Navigation
        pane.addItem(GuiComponents.backButton(plugin, "gui.common.dest-stats") {
            menu.openStatsMenu(player)
        }, 0, 4)
        pane.addItem(GuiComponents.closeButton(plugin, player), 8, 4)

        gui.addPane(pane)
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }
        return gui
    }

    private fun statItem(material: Material, nameKey: String, value: Int): GuiItem {
        return GuiItem(
            GuiComponents.infoItem(
                plugin, material, nameKey, "gui.player-stats.stat-value-lore",
                "value" to value
            )
        ) { it.isCancelled = true }
    }

    private fun timeStatItem(seconds: Long): GuiItem {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        val formatted = when {
            hours > 0 -> "${hours}h ${minutes}m ${secs}s"
            minutes > 0 -> "${minutes}m ${secs}s"
            else -> "${secs}s"
        }
        return GuiItem(
            GuiComponents.infoItem(
                plugin, Material.CLOCK,
                "gui.player-stats.time-stat-name", "gui.player-stats.time-stat-lore",
                "formatted" to formatted, "seconds" to seconds
            )
        ) { it.isCancelled = true }
    }
}
