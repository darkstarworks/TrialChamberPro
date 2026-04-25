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

class ChambersOverviewView(private val plugin: TrialChamberPro, private val menu: MenuService) {

    fun build(player: Player): ChestGui {
        val chambers = plugin.chamberManager.getCachedChambers()
        val gui = ChestGui(6, GuiText.plain(plugin, "gui.chambers-overview.title"))

        gui.addPane(StaticPane(0, 0, 9, 6))

        val page = OutlinePane(0, 0, 9, 5).apply {
            isVisible = true
            gap = 1
        }

        chambers.take(45).forEach { chamber ->
            page.addItem(GuiItem(createChamberItem(chamber, player)) { event ->
                event.isCancelled = true
                menu.openLootKindSelect(player, chamber)
            })
        }

        gui.addPane(page)

        if (chambers.isEmpty()) {
            val emptyPane = StaticPane(0, 2, 9, 1)
            emptyPane.addItem(GuiItem(
                GuiComponents.infoItem(plugin, org.bukkit.Material.BARRIER,
                    "gui.chambers-overview.empty-name", "gui.chambers-overview.empty-lore")
            ) { it.isCancelled = true }, 4, 0)
            gui.addPane(emptyPane)
        }

        val controls = StaticPane(0, 5, 9, 1)
        controls.addItem(GuiComponents.closeButton(plugin, player), 8, 0)
        gui.addPane(controls)

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

        return GuiComponents.infoItem(
            plugin, Material.LODESTONE,
            "gui.chambers-overview.chamber-name", "gui.chambers-overview.chamber-lore",
            "chamber" to chamber.name,
            "world" to chamber.world,
            "minX" to chamber.minX, "minY" to chamber.minY, "minZ" to chamber.minZ,
            "maxX" to chamber.maxX, "maxY" to chamber.maxY, "maxZ" to chamber.maxZ,
            "inside" to playersInside,
            "normal" to normalCount, "ominous" to ominousCount,
            "normalLocked" to normalLocked, "ominousLocked" to ominousLocked,
            "reset" to DurationFmt.humanize(plugin, timeUntilMs),
            "lastReset" to DurationFmt.humanize(plugin, sinceLastMs)
        )
    }
}

/**
 * Shared duration formatter for chamber list views. Produces "2d 3h", "5m", or the
 * localized "due now" token when `durationMs <= 0`.
 */
internal object DurationFmt {
    fun humanize(plugin: TrialChamberPro, durationMs: Long): String {
        if (durationMs <= 0) return plugin.getMessage("gui.chamber-list.duration-due-now")
        var seconds = durationMs / 1000
        val months = seconds / (30L * 24 * 3600); seconds %= (30L * 24 * 3600)
        val weeks = seconds / (7L * 24 * 3600);   seconds %= (7L * 24 * 3600)
        val days = seconds / (24 * 3600);          seconds %= (24 * 3600)
        val hours = seconds / 3600;                seconds %= 3600
        val minutes = seconds / 60;                seconds %= 60
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
