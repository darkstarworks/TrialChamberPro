package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.components.GuiText
import io.github.darkstarworks.trialChamberPro.listeners.MobIdInputListener
import io.github.darkstarworks.trialChamberPro.models.Chamber
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Per-chamber Custom Mob Provider configuration (v1.3.0).
 * All strings from `messages.yml` under `gui.custom-mob.*`.
 */
class CustomMobProviderView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val chamber: Chamber
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(6, GuiText.plain(plugin, "gui.custom-mob.title", "chamber" to chamber.name))
        val pane = StaticPane(0, 0, 9, 6)

        // Top-row navigation: back (slot 0) + close (slot 8); header centered on row 0.
        pane.addItem(GuiComponents.backButton(plugin, "gui.common.dest-settings") {
            val refreshed = plugin.chamberManager.getCachedChamberById(chamber.id)
            if (refreshed != null) menu.openChamberSettings(player, refreshed)
            else menu.openChamberList(player)
        }, 0, 0)
        pane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.SPAWNER,
                "gui.custom-mob.header-name", "gui.custom-mob.header-lore",
                "chamber" to chamber.name)
        ) { it.isCancelled = true }, 4, 0)
        pane.addItem(GuiComponents.closeButton(plugin, player), 8, 0)

        pane.addItem(GuiItem(createProviderItem()) { event ->
            event.isCancelled = true
            when {
                event.isShiftClick -> clearProvider(player)
                event.isLeftClick -> cycleProvider(player, 1)
                event.isRightClick -> cycleProvider(player, -1)
            }
        }, 4, 1)

        pane.addItem(GuiItem(sectionHeader(true, chamber.customMobIdsNormal.size)) { it.isCancelled = true }, 0, 2)
        pane.addItem(GuiItem(addItem("normal")) { event ->
            event.isCancelled = true
            promptAdd(player, MobIdInputListener.Section.NORMAL)
        }, 8, 2)
        renderIdRow(pane, row = 3, ids = chamber.customMobIdsNormal, player = player,
            section = MobIdInputListener.Section.NORMAL)

        pane.addItem(GuiItem(sectionHeader(false, chamber.customMobIdsOminous.size)) { it.isCancelled = true }, 0, 4)
        pane.addItem(GuiItem(addItem("ominous")) { event ->
            event.isCancelled = true
            promptAdd(player, MobIdInputListener.Section.OMINOUS)
        }, 8, 4)
        renderIdRow(pane, row = 5, ids = chamber.customMobIdsOminous, player = player,
            section = MobIdInputListener.Section.OMINOUS)

        gui.addPane(pane)
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }
        return gui
    }

    private fun renderIdRow(
        pane: StaticPane,
        row: Int,
        ids: List<String>,
        player: Player,
        section: MobIdInputListener.Section
    ) {
        if (ids.isEmpty()) {
            val empty = GuiComponents.infoItem(plugin, Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                "gui.custom-mob.empty-name", "gui.custom-mob.empty-lore")
            pane.addItem(GuiItem(empty) { it.isCancelled = true }, 4, row)
            return
        }
        val visible = ids.take(if (ids.size > 9) 8 else 9)
        visible.forEachIndexed { index, id ->
            val item = GuiComponents.infoItem(plugin, Material.ZOMBIE_HEAD,
                "gui.custom-mob.id-name", "gui.custom-mob.id-lore",
                "id" to id)
            pane.addItem(GuiItem(item) { event ->
                event.isCancelled = true
                removeId(player, section, id)
            }, index, row)
        }
        if (ids.size > 9) {
            val overflow = GuiComponents.infoItem(plugin, Material.PAPER,
                "gui.custom-mob.overflow-name", "gui.custom-mob.overflow-lore",
                "extra" to (ids.size - 9))
            pane.addItem(GuiItem(overflow) { it.isCancelled = true }, 8, row)
        }
    }

    private fun createProviderItem(): ItemStack {
        val current = chamber.customMobProvider ?: "vanilla"
        val registered = plugin.trialMobProviderRegistry.get(current)
        val available = registered?.isAvailable() ?: current.equals("vanilla", ignoreCase = true)
        val loreKey = if (available)
            "gui.custom-mob.provider-lore-available" else "gui.custom-mob.provider-lore-unavailable"
        return GuiComponents.infoItem(plugin, Material.COMMAND_BLOCK,
            "gui.custom-mob.provider-name", loreKey,
            "provider" to current)
    }

    private fun sectionHeader(normal: Boolean, count: Int): ItemStack {
        val mat = if (normal) Material.GREEN_BANNER else Material.PURPLE_BANNER
        val nameKey = if (normal)
            "gui.custom-mob.section-normal-name" else "gui.custom-mob.section-ominous-name"
        return GuiComponents.infoItem(plugin, mat, nameKey, "gui.custom-mob.section-lore",
            "count" to count)
    }

    private fun addItem(section: String): ItemStack {
        return GuiComponents.infoItem(plugin, Material.LIME_CONCRETE,
            "gui.custom-mob.add-name", "gui.custom-mob.add-lore",
            "section" to section)
    }

    // ==================== Actions (unchanged behavior) ====================

    private fun providerCycleList(): List<String> {
        val base = mutableListOf("vanilla")
        plugin.trialMobProviderRegistry.all()
            .map { it.id.lowercase() }
            .filter { it != "vanilla" }
            .sorted()
            .forEach { base += it }
        return base
    }

    private fun cycleProvider(player: Player, direction: Int) {
        val list = providerCycleList()
        val current = (chamber.customMobProvider ?: "vanilla").lowercase()
        val idx = list.indexOf(current).let { if (it == -1) 0 else it }
        val next = list[(idx + direction).mod(list.size)]
        applyProvider(player, next)
    }

    private fun clearProvider(player: Player) = applyProvider(player, "vanilla")

    private fun applyProvider(player: Player, providerId: String) {
        plugin.launchAsync {
            val stored = if (providerId.equals("vanilla", ignoreCase = true)) null else providerId
            val ok = plugin.chamberManager.updateCustomMobProvider(chamber.id, stored)
            plugin.scheduler.runAtEntity(player, Runnable {
                if (ok) {
                    player.sendMessage(plugin.getMessage("gui-provider-set", "id" to (stored ?: "vanilla")))
                    plugin.chamberManager.getCachedChamberById(chamber.id)?.let { menu.openCustomMobProvider(player, it) }
                } else player.sendMessage(plugin.getMessage("gui-provider-failed"))
            })
        }
    }

    private fun promptAdd(player: Player, section: MobIdInputListener.Section) {
        MobIdInputListener.awaitInput(player.uniqueId, chamber.id, section)
        player.closeInventory()
        player.sendMessage(plugin.getMessage("gui-mob-input-prompt",
            "section" to section.name.lowercase()))
    }

    private fun removeId(player: Player, section: MobIdInputListener.Section, id: String) {
        plugin.launchAsync {
            val normal = chamber.customMobIdsNormal.toMutableList()
            val ominous = chamber.customMobIdsOminous.toMutableList()
            val target = if (section == MobIdInputListener.Section.NORMAL) normal else ominous
            val removed = target.removeAll { it.equals(id, ignoreCase = true) }
            if (!removed) {
                plugin.scheduler.runAtEntity(player, Runnable {
                    player.sendMessage(plugin.getMessage("gui-mob-remove-missing", "id" to id))
                })
                return@launchAsync
            }
            val ok = plugin.chamberManager.updateCustomMobProvider(
                chamber.id, chamber.customMobProvider, normal, ominous
            )
            plugin.scheduler.runAtEntity(player, Runnable {
                if (ok) {
                    player.sendMessage(plugin.getMessage("gui-mob-removed", "id" to id))
                    plugin.chamberManager.getCachedChamberById(chamber.id)?.let { menu.openCustomMobProvider(player, it) }
                } else player.sendMessage(plugin.getMessage("gui-mob-remove-failed"))
            })
        }
    }
}
