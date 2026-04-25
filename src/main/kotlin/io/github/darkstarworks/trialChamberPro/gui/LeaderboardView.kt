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
import org.bukkit.inventory.ItemStack

class LeaderboardView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val leaderboardType: String
) {
    fun build(player: Player): ChestGui {
        val titleKey = "gui.leaderboard.title-" + when (leaderboardType) {
            "vaults", "chambers", "mobs", "time" -> leaderboardType
            else -> "default"
        }
        val gui = ChestGui(6, GuiText.plain(plugin, titleKey))
        val pane = StaticPane(0, 0, 9, 6)

        pane.addItem(GuiItem(createHeaderItem()) { it.isCancelled = true }, 4, 0)

        val leaderboard = runBlocking { plugin.statisticsManager.getLeaderboard(leaderboardType, 10) }

        leaderboard.forEachIndexed { index, entry ->
            val row = 1 + (index / 2)
            val col = if (index % 2 == 0) 2 else 6
            if (row <= 4) {
                pane.addItem(GuiItem(createLeaderboardEntry(index + 1, entry)) { event ->
                    event.isCancelled = true
                    menu.openPlayerStats(player, entry.first)
                }, col, row)
            }
        }

        if (leaderboard.isEmpty()) {
            pane.addItem(GuiItem(
                GuiComponents.infoItem(plugin, Material.BARRIER,
                    "gui.leaderboard.empty-name", "gui.leaderboard.empty-lore")
            ) { it.isCancelled = true }, 4, 2)
        }

        pane.addItem(GuiComponents.backButton(plugin, "gui.common.dest-stats") {
            menu.openStatsMenu(player)
        }, 0, 5)

        addCategorySwitcher(pane, player, "vaults", Material.VAULT, 2, 5)
        addCategorySwitcher(pane, player, "chambers", Material.LODESTONE, 3, 5)
        addCategorySwitcher(pane, player, "mobs", Material.IRON_SWORD, 5, 5)
        addCategorySwitcher(pane, player, "time", Material.CLOCK, 6, 5)

        pane.addItem(GuiComponents.closeButton(plugin, player), 8, 5)

        gui.addPane(pane)
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }
        return gui
    }

    private fun createHeaderItem(): ItemStack {
        val (material, nameKey) = when (leaderboardType) {
            "vaults" -> Material.VAULT to "gui.leaderboard.header-vaults-name"
            "chambers" -> Material.LODESTONE to "gui.leaderboard.header-chambers-name"
            "mobs" -> Material.IRON_SWORD to "gui.leaderboard.header-mobs-name"
            "time" -> Material.CLOCK to "gui.leaderboard.header-time-name"
            else -> Material.WRITABLE_BOOK to "gui.leaderboard.header-default-name"
        }
        return GuiComponents.infoItem(plugin, material, nameKey, "gui.leaderboard.header-lore")
    }

    private fun createLeaderboardEntry(rank: Int, entry: Pair<java.util.UUID, Int>): ItemStack {
        val (uuid, value) = entry
        val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
        val playerName = offlinePlayer.name ?: "Unknown"
        val rankColor = when (rank) { 1 -> "6"; 2 -> "7"; 3 -> "4"; else -> "f" }
        val formattedValue = if (leaderboardType == "time") formatTime(value.toLong()) else value.toString()

        return GuiComponents.playerHead(
            plugin, offlinePlayer,
            "gui.leaderboard.entry-name", "gui.leaderboard.entry-lore",
            "rankColor" to rankColor,
            "rank" to rank,
            "player" to playerName,
            "value" to formattedValue
        )
    }

    private fun addCategorySwitcher(pane: StaticPane, player: Player, type: String, material: Material, x: Int, y: Int) {
        val isCurrent = type == leaderboardType
        val item = ItemStack(if (isCurrent) Material.LIME_DYE else material).apply {
            itemMeta = itemMeta?.apply {
                displayName(plugin.getGuiText("gui.leaderboard.switcher-$type"))
                if (isCurrent) {
                    lore(listOf(plugin.getGuiText("gui.leaderboard.switcher-current")))
                }
            }
        }
        pane.addItem(GuiItem(item) { event ->
            event.isCancelled = true
            if (!isCurrent) menu.openLeaderboard(player, type)
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
