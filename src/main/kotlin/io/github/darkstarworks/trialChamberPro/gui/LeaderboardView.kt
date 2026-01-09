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

/**
 * Leaderboard view - displays top players for a specific statistic.
 */
class LeaderboardView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val leaderboardType: String
) {
    fun build(player: Player): ChestGui {
        val title = when (leaderboardType) {
            "vaults" -> "Top Vault Openers"
            "chambers" -> "Top Chamber Completers"
            "mobs" -> "Top Mob Killers"
            "time" -> "Most Time Spent"
            else -> "Leaderboard"
        }

        val gui = ChestGui(6, title)
        val pane = StaticPane(0, 0, 9, 6)

        // Row 0: Header
        pane.addItem(GuiItem(createHeaderItem()) { it.isCancelled = true }, 4, 0)

        // Get leaderboard data
        val leaderboard = runBlocking {
            plugin.statisticsManager.getLeaderboard(leaderboardType, 10)
        }

        // Rows 1-4: Leaderboard entries (2 per row, centered)
        leaderboard.forEachIndexed { index, entry ->
            val row = 1 + (index / 2)
            val col = if (index % 2 == 0) 2 else 6

            if (row <= 4) {
                pane.addItem(GuiItem(createLeaderboardEntry(index + 1, entry)) { event ->
                    event.isCancelled = true
                    // Open player stats when clicked
                    menu.openPlayerStats(player, entry.first)
                }, col, row)
            }
        }

        // If leaderboard is empty
        if (leaderboard.isEmpty()) {
            val emptyItem = ItemStack(Material.BARRIER).apply {
                itemMeta = itemMeta?.apply {
                    displayName(Component.text("No Data", NamedTextColor.GRAY))
                    lore(listOf(
                        Component.text("No players have recorded", NamedTextColor.GRAY),
                        Component.text("any statistics yet.", NamedTextColor.GRAY)
                    ))
                }
            }
            pane.addItem(GuiItem(emptyItem) { it.isCancelled = true }, 4, 2)
        }

        // Row 5: Navigation
        val backItem = ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Back to Statistics", NamedTextColor.YELLOW))
            }
        }
        pane.addItem(GuiItem(backItem) { event ->
            event.isCancelled = true
            menu.openStatsMenu(player)
        }, 0, 5)

        // Category switchers
        addCategorySwitcher(pane, player, "vaults", Material.VAULT, NamedTextColor.GOLD, 2, 5)
        addCategorySwitcher(pane, player, "chambers", Material.LODESTONE, NamedTextColor.AQUA, 3, 5)
        addCategorySwitcher(pane, player, "mobs", Material.IRON_SWORD, NamedTextColor.RED, 5, 5)
        addCategorySwitcher(pane, player, "time", Material.CLOCK, NamedTextColor.YELLOW, 6, 5)

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

    private fun createHeaderItem(): ItemStack {
        val (material, color) = when (leaderboardType) {
            "vaults" -> Material.VAULT to NamedTextColor.GOLD
            "chambers" -> Material.LODESTONE to NamedTextColor.AQUA
            "mobs" -> Material.IRON_SWORD to NamedTextColor.RED
            "time" -> Material.CLOCK to NamedTextColor.YELLOW
            else -> Material.WRITABLE_BOOK to NamedTextColor.WHITE
        }

        val title = when (leaderboardType) {
            "vaults" -> "Vaults Opened"
            "chambers" -> "Chambers Completed"
            "mobs" -> "Mobs Killed"
            "time" -> "Time Spent"
            else -> "Leaderboard"
        }

        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text(title, color)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Top 10 players", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Click a player to view their stats", NamedTextColor.YELLOW)
                ))
            }
        }
    }

    private fun createLeaderboardEntry(rank: Int, entry: Pair<java.util.UUID, Int>): ItemStack {
        val (uuid, value) = entry
        val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
        val playerName = offlinePlayer.name ?: "Unknown"

        val rankColor = when (rank) {
            1 -> NamedTextColor.GOLD
            2 -> NamedTextColor.GRAY
            3 -> NamedTextColor.DARK_RED
            else -> NamedTextColor.WHITE
        }

        val formattedValue = when (leaderboardType) {
            "time" -> formatTime(value.toLong())
            else -> value.toString()
        }

        return ItemStack(Material.PLAYER_HEAD).apply {
            itemMeta = (itemMeta as? SkullMeta)?.apply {
                owningPlayer = offlinePlayer
                displayName(Component.text("#$rank ", rankColor)
                    .append(Component.text(playerName, NamedTextColor.WHITE))
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text(formattedValue, NamedTextColor.YELLOW),
                    Component.empty(),
                    Component.text("Click for full stats", NamedTextColor.GRAY)
                ))
            }
        }
    }

    private fun addCategorySwitcher(pane: StaticPane, player: Player, type: String, material: Material, color: NamedTextColor, x: Int, y: Int) {
        val isCurrentType = type == leaderboardType

        val item = ItemStack(if (isCurrentType) Material.LIME_DYE else material).apply {
            itemMeta = itemMeta?.apply {
                val name = when (type) {
                    "vaults" -> "Vaults"
                    "chambers" -> "Chambers"
                    "mobs" -> "Mobs"
                    "time" -> "Time"
                    else -> type
                }
                displayName(Component.text(name, if (isCurrentType) NamedTextColor.GREEN else color))
                if (isCurrentType) {
                    lore(listOf(Component.text("Currently viewing", NamedTextColor.GRAY)))
                }
            }
        }

        pane.addItem(GuiItem(item) { event ->
            event.isCancelled = true
            if (!isCurrentType) {
                menu.openLeaderboard(player, type)
            }
        }, x, y)
    }

    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${secs}s"
            else -> "${secs}s"
        }
    }
}
