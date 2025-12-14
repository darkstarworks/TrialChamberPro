package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.OutlinePane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.Chamber
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Paginated chamber list view showing all registered trial chambers.
 * Replaces the old ChambersOverviewView with pagination support for large servers.
 */
class ChamberListView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService
) {
    companion object {
        private const val ITEMS_PER_PAGE = 36 // 4 rows of 9 items
    }

    fun build(player: Player, page: Int = 0): ChestGui {
        val allChambers = plugin.chamberManager.getCachedChambers().sortedBy { it.name }
        val totalPages = maxOf(1, (allChambers.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE)
        val currentPage = page.coerceIn(0, totalPages - 1)

        val startIndex = currentPage * ITEMS_PER_PAGE
        val endIndex = minOf(startIndex + ITEMS_PER_PAGE, allChambers.size)
        val chambersOnPage = if (allChambers.isNotEmpty()) allChambers.subList(startIndex, endIndex) else emptyList()

        val gui = ChestGui(6, "Chambers (Page ${currentPage + 1}/$totalPages)")

        // Chamber grid (rows 0-3)
        val chamberPane = OutlinePane(0, 0, 9, 4)
        chamberPane.isVisible = true

        chambersOnPage.forEach { chamber ->
            chamberPane.addItem(GuiItem(createChamberItem(chamber, player)) { event ->
                event.isCancelled = true
                menu.openChamberDetail(player, chamber)
            })
        }

        gui.addPane(chamberPane)

        // Info row (row 4)
        val infoPane = StaticPane(0, 4, 9, 1)

        // Page info
        val pageInfoItem = ItemStack(Material.PAPER).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Page ${currentPage + 1} of $totalPages", NamedTextColor.YELLOW))
                lore(listOf(
                    Component.text("${allChambers.size} chambers total", NamedTextColor.GRAY),
                    Component.text("Showing ${chambersOnPage.size} on this page", NamedTextColor.GRAY)
                ))
            }
        }
        infoPane.addItem(GuiItem(pageInfoItem) { it.isCancelled = true }, 4, 0)

        // Previous page
        if (currentPage > 0) {
            val prevItem = ItemStack(Material.ARROW).apply {
                itemMeta = itemMeta?.apply {
                    displayName(Component.text("Previous Page", NamedTextColor.YELLOW))
                    lore(listOf(Component.text("Go to page $currentPage", NamedTextColor.GRAY)))
                }
            }
            infoPane.addItem(GuiItem(prevItem) { event ->
                event.isCancelled = true
                menu.openChamberList(player, currentPage - 1)
            }, 3, 0)
        }

        // Next page
        if (currentPage < totalPages - 1) {
            val nextItem = ItemStack(Material.ARROW).apply {
                itemMeta = itemMeta?.apply {
                    displayName(Component.text("Next Page", NamedTextColor.YELLOW))
                    lore(listOf(Component.text("Go to page ${currentPage + 2}", NamedTextColor.GRAY)))
                }
            }
            infoPane.addItem(GuiItem(nextItem) { event ->
                event.isCancelled = true
                menu.openChamberList(player, currentPage + 1)
            }, 5, 0)
        }

        gui.addPane(infoPane)

        // Controls row (row 5)
        val controlsPane = StaticPane(0, 5, 9, 1)

        // Back to main menu
        val backItem = ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Back to Main Menu", NamedTextColor.YELLOW))
            }
        }
        controlsPane.addItem(GuiItem(backItem) { event ->
            event.isCancelled = true
            menu.openMainMenu(player)
        }, 0, 0)

        // Create chamber info
        val createInfoItem = ItemStack(Material.LIME_CONCRETE).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Create Chamber", NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("To create a new chamber:", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("/tcp generate wand <name>", NamedTextColor.YELLOW),
                    Component.text("  Use WorldEdit selection", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("/tcp generate coords ...", NamedTextColor.YELLOW),
                    Component.text("  Use coordinates", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("/tcp paste <schematic>", NamedTextColor.YELLOW),
                    Component.text("  Paste a schematic", NamedTextColor.GRAY)
                ))
            }
        }
        controlsPane.addItem(GuiItem(createInfoItem) { it.isCancelled = true }, 4, 0)

        // Close
        val closeItem = ItemStack(Material.BARRIER).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Close", NamedTextColor.RED))
            }
        }
        controlsPane.addItem(GuiItem(closeItem) { event ->
            event.isCancelled = true
            player.closeInventory()
        }, 8, 0)

        gui.addPane(controlsPane)

        gui.setOnGlobalClick { it.isCancelled = true }

        return gui
    }

    private fun createChamberItem(chamber: Chamber, player: Player): ItemStack {
        val item = ItemStack(Material.LODESTONE)

        // Time calculations
        val timeUntilMs = plugin.resetManager.getTimeUntilReset(chamber)
        val lastResetMs = chamber.lastReset ?: chamber.createdAt
        val sinceLastMs = System.currentTimeMillis() - lastResetMs

        // Players inside
        val playersInside = chamber.getPlayersInside().size

        // Vault counts (cached)
        val (normalCount, ominousCount) = plugin.vaultManager.getVaultCounts(chamber.id)

        // Locked vault counts for this player
        val (normalLocked, ominousLocked) = runBlocking {
            plugin.vaultManager.getLockedVaultCounts(player.uniqueId, chamber.id)
        }

        // Check for custom loot tables
        val hasCustomLoot = chamber.normalLootTable != null || chamber.ominousLootTable != null

        val lore = mutableListOf(
            Component.text("World: ${chamber.world}", NamedTextColor.GRAY),
            Component.text("Volume: ${chamber.getVolume()} blocks", NamedTextColor.GRAY),
            Component.empty(),
            Component.text("Players: ", NamedTextColor.WHITE)
                .append(Component.text("$playersInside inside", if (playersInside > 0) NamedTextColor.GREEN else NamedTextColor.GRAY)),
            Component.text("Vaults: ", NamedTextColor.WHITE)
                .append(Component.text("$normalCount", NamedTextColor.GREEN))
                .append(Component.text(" Normal, ", NamedTextColor.GRAY))
                .append(Component.text("$ominousCount", NamedTextColor.DARK_PURPLE))
                .append(Component.text(" Ominous", NamedTextColor.GRAY)),
            Component.text("Your Locked: ", NamedTextColor.WHITE)
                .append(Component.text("$normalLocked", NamedTextColor.RED))
                .append(Component.text(" Normal, ", NamedTextColor.GRAY))
                .append(Component.text("$ominousLocked", NamedTextColor.RED))
                .append(Component.text(" Ominous", NamedTextColor.GRAY)),
            Component.empty(),
            Component.text("Reset: ", NamedTextColor.WHITE)
                .append(Component.text(humanizeDuration(timeUntilMs), NamedTextColor.YELLOW)),
            Component.text("Last Reset: ", NamedTextColor.WHITE)
                .append(Component.text(humanizeDuration(sinceLastMs) + " ago", NamedTextColor.GRAY))
        )

        if (hasCustomLoot) {
            lore.add(Component.empty())
            lore.add(Component.text("Custom Loot Tables", NamedTextColor.LIGHT_PURPLE))
        }

        lore.add(Component.empty())
        lore.add(Component.text("Click to manage", NamedTextColor.GREEN))

        item.itemMeta = item.itemMeta?.apply {
            displayName(Component.text(chamber.name, NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true))
            lore(lore)
        }

        return item
    }

    private fun humanizeDuration(durationMs: Long): String {
        if (durationMs <= 0) return "due now"
        var seconds = durationMs / 1000

        val months = seconds / (30L * 24 * 3600)
        seconds %= (30L * 24 * 3600)
        val weeks = seconds / (7L * 24 * 3600)
        seconds %= (7L * 24 * 3600)
        val days = seconds / (24 * 3600)
        seconds %= (24 * 3600)
        val hours = seconds / 3600
        seconds %= 3600
        val minutes = seconds / 60
        seconds %= 60

        val parts = mutableListOf<String>()
        if (months > 0) parts += "${months}mo"
        if (weeks > 0) parts += "${weeks}w"
        if (days > 0) parts += "${days}d"
        if (hours > 0) parts += "${hours}h"
        if (minutes > 0) parts += "${minutes}m"
        if (parts.isEmpty() && seconds > 0) parts += "${seconds}s"

        return parts.take(2).joinToString(" ")
    }
}
