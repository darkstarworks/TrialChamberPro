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
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

class ChambersOverviewView(private val plugin: TrialChamberPro, private val menu: MenuService) {

    fun build(player: Player): ChestGui {
        val chambers = plugin.chamberManager.getCachedChambers()
        val gui = ChestGui(6, "Trial Chambers Overview")

        val background = StaticPane(0, 0, 9, 6)
        // No background filler to keep default visuals
        gui.addPane(background)

        val page = OutlinePane(0, 0, 9, 5)
        page.isVisible = true
        page.gap = 1

        chambers.take(45).forEach { chamber ->
            page.addItem(GuiItem(createChamberItem(chamber, player)) { event ->
                event.isCancelled = true
                menu.openLootKindSelect(player, chamber)
            })
        }

        gui.addPane(page)

        // Bottom controls and close/back
        val controls = StaticPane(0, 5, 9, 1)
        val closeItem = ItemStack(Material.BARRIER).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Close", NamedTextColor.RED))
            }
        }
        controls.addItem(GuiItem(closeItem) {
            it.isCancelled = true
            player.closeInventory()
        }, 8, 0)

        gui.addPane(controls)

        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }

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

        // Vault counts (Normal/Ominous) via lightweight TTL cache (non-blocking)
        val (normalCount, ominousCount) = plugin.vaultManager.getVaultCounts(chamber.id)

        // Get locked vault counts for this player
        val (normalLocked, ominousLocked) = runBlocking {
            plugin.vaultManager.getLockedVaultCounts(player.uniqueId, chamber.id)
        }

        item.itemMeta = (item.itemMeta ?: plugin.server.itemFactory.getItemMeta(item.type))?.apply {
            displayName(Component.text(chamber.name, NamedTextColor.AQUA))
            lore(
                listOf(
                    Component.text("World: ${chamber.world}", NamedTextColor.GRAY),
                    Component.text("Bounds: (${chamber.minX},${chamber.minY},${chamber.minZ}) -> (${chamber.maxX},${chamber.maxY},${chamber.maxZ})", NamedTextColor.GRAY),
                    Component.text("Players inside: $playersInside", NamedTextColor.GRAY),
                    Component.text("Vaults: $normalCount Normal, $ominousCount Ominous", NamedTextColor.GRAY),
                    Component.text("Locked: $normalLocked Normal, $ominousLocked Ominous", NamedTextColor.RED),
                    Component.text("Time until reset: ${humanizeDuration(timeUntilMs)}", NamedTextColor.YELLOW),
                    Component.text("Since last reset: ${humanizeDuration(sinceLastMs)}", NamedTextColor.YELLOW),
                    Component.text("Click to Manage", NamedTextColor.GREEN)
                )
            )
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
        if (months > 0) parts += "$months month" + if (months != 1L) "s" else ""
        if (weeks > 0) parts += "$weeks week" + if (weeks != 1L) "s" else ""
        if (days > 0) parts += "$days day" + if (days != 1L) "s" else ""
        if (hours > 0) parts += "$hours hour" + if (hours != 1L) "s" else ""
        if (minutes > 0) parts += "$minutes minute" + if (minutes != 1L) "s" else ""
        // Include seconds only if nothing else
        if (parts.isEmpty() && seconds > 0) parts += "$seconds second" + if (seconds != 1L) "s" else ""

        // Limit to a reasonable number of components (max 3) for readability
        return parts.take(3).joinToString(", ")
    }
}