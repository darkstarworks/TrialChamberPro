package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.OutlinePane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.components.GuiText
import io.github.darkstarworks.trialChamberPro.models.Chamber
import kotlinx.coroutines.runBlocking
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Paginated chamber list view — 36 chambers per page with prev/next and a create-help card.
 * All strings from `messages.yml` under `gui.chamber-list.*` (v1.3.0).
 */
class ChamberListView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService
) {
    companion object {
        private const val ITEMS_PER_PAGE = 36
    }

    fun build(player: Player, page: Int = 0): ChestGui {
        val allChambers = plugin.chamberManager.getCachedChambers().sortedBy { it.name }
        val totalPages = maxOf(1, (allChambers.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE)
        val currentPage = page.coerceIn(0, totalPages - 1)

        val startIndex = currentPage * ITEMS_PER_PAGE
        val endIndex = minOf(startIndex + ITEMS_PER_PAGE, allChambers.size)
        val chambersOnPage = if (allChambers.isNotEmpty()) allChambers.subList(startIndex, endIndex) else emptyList()

        val gui = ChestGui(6, GuiText.plain(plugin, "gui.chamber-list.title",
            "page" to (currentPage + 1), "total" to totalPages))

        val chamberPane = OutlinePane(0, 0, 9, 4).apply { isVisible = true }
        chambersOnPage.forEach { chamber ->
            chamberPane.addItem(GuiItem(createChamberItem(chamber, player)) { event ->
                event.isCancelled = true
                menu.openChamberDetail(player, chamber)
            })
        }
        gui.addPane(chamberPane)

        if (allChambers.isEmpty()) {
            val emptyPane = StaticPane(0, 1, 9, 1)
            emptyPane.addItem(GuiItem(
                GuiComponents.infoItem(plugin, Material.BARRIER,
                    "gui.chamber-list.empty-name", "gui.chamber-list.empty-lore")
            ) { it.isCancelled = true }, 4, 0)
            gui.addPane(emptyPane)
        }

        // Info row (page indicator + prev/next)
        val infoPane = StaticPane(0, 4, 9, 1)
        infoPane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.PAPER,
                "gui.chamber-list.page-info-name", "gui.chamber-list.page-info-lore",
                "page" to (currentPage + 1), "total" to totalPages,
                "total-chambers" to allChambers.size, "on-page" to chambersOnPage.size)
        ) { it.isCancelled = true }, 4, 0)

        if (currentPage > 0) {
            infoPane.addItem(GuiItem(
                GuiComponents.infoItem(plugin, Material.SPECTRAL_ARROW,
                    "gui.chamber-list.prev-page-name", "gui.chamber-list.prev-page-lore",
                    "page" to currentPage)
            ) { event ->
                event.isCancelled = true
                menu.openChamberList(player, currentPage - 1)
            }, 3, 0)
        }
        if (currentPage < totalPages - 1) {
            infoPane.addItem(GuiItem(
                GuiComponents.infoItem(plugin, Material.TIPPED_ARROW,
                    "gui.chamber-list.next-page-name", "gui.chamber-list.next-page-lore",
                    "page" to (currentPage + 2))
            ) { event ->
                event.isCancelled = true
                menu.openChamberList(player, currentPage + 1)
            }, 5, 0)
        }
        gui.addPane(infoPane)

        // Controls row
        val controlsPane = StaticPane(0, 5, 9, 1)
        controlsPane.addItem(GuiComponents.backButton(plugin, "gui.common.dest-main-menu") {
            menu.openMainMenu(player)
        }, 0, 0)
        controlsPane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.LIME_CONCRETE,
                "gui.chamber-list.create-name", "gui.chamber-list.create-lore")
        ) { it.isCancelled = true }, 4, 0)
        controlsPane.addItem(GuiComponents.closeButton(plugin, player), 8, 0)
        gui.addPane(controlsPane)

        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }
        return gui
    }

    private fun createChamberItem(chamber: Chamber, player: Player): ItemStack {
        val timeUntilMs = plugin.resetManager.getTimeUntilReset(chamber)
        val lastResetMs = chamber.lastReset ?: chamber.createdAt
        val sinceLastMs = System.currentTimeMillis() - lastResetMs
        val playersInside = chamber.getPlayersInside().size
        val (normalCount, ominousCount) = plugin.vaultManager.getVaultCounts(chamber.id)
        val (normalLocked, ominousLocked) = runBlocking {
            plugin.vaultManager.getLockedVaultCounts(player.uniqueId, chamber.id)
        }

        val base = GuiComponents.infoItem(
            plugin, Material.LODESTONE,
            "gui.chamber-list.chamber-name", "gui.chamber-list.chamber-lore",
            "chamber" to chamber.name,
            "world" to chamber.world,
            "volume" to chamber.getVolume(),
            "inside" to playersInside,
            "normal" to normalCount, "ominous" to ominousCount,
            "normalLocked" to normalLocked, "ominousLocked" to ominousLocked,
            "reset" to DurationFmt.humanize(plugin, timeUntilMs),
            "lastReset" to DurationFmt.humanize(plugin, sinceLastMs)
        )

        // Optional custom-loot tag appended to lore
        if (chamber.normalLootTable != null || chamber.ominousLootTable != null) {
            base.itemMeta = base.itemMeta?.apply {
                val existing = lore() ?: mutableListOf()
                val withTag = existing.toMutableList().apply {
                    add(net.kyori.adventure.text.Component.empty())
                    add(plugin.getGuiText("gui.chamber-list.chamber-custom-loot-tag"))
                }
                lore(withTag)
            }
        }
        return base
    }
}
