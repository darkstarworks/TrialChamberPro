package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.components.GuiText
import io.github.darkstarworks.trialChamberPro.models.Chamber
import io.github.darkstarworks.trialChamberPro.models.LootPool
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Pool selector view — pick which pool of a multi-pool loot table to edit.
 * All strings from `messages.yml` under `gui.pool-selector.*` (v1.3.0).
 */
class PoolSelectorView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val chamber: Chamber?,
    private val kind: MenuService.LootKind,
    private val globalTableName: String? = null
) {
    init {
        require(chamber != null || globalTableName != null) {
            "PoolSelectorView requires either a chamber or a globalTableName"
        }
    }

    fun build(player: Player): ChestGui {
        val gui = ChestGui(6, title())
        val pane = StaticPane(0, 0, 9, 6)

        val tableName = getTableName()
        val table = plugin.lootManager.getTable(tableName)

        if (table == null || table.isLegacyFormat()) {
            if (chamber != null) menu.openLootEditor(player, chamber, kind, null)
            else menu.openGlobalLootEditor(player, globalTableName!!)
            return gui
        }

        val pools = table.pools
        val maxPools = plugin.config.getInt("loot.max-pools-per-table", 5)

        pools.forEachIndexed { index, pool ->
            val item = createPoolItem(pool)
            val row = 1 + (index / 7)
            val col = 1 + (index % 7)
            pane.addItem(GuiItem(item) { event ->
                event.isCancelled = true
                if (chamber != null) menu.openLootEditor(player, chamber, kind, pool.name)
                else menu.openGlobalLootEditor(player, globalTableName!!, pool.name)
            }, col, row)
        }

        if (pools.size < maxPools) {
            val index = pools.size
            val row = 1 + (index / 7)
            val col = 1 + (index % 7)
            pane.addItem(GuiItem(createNewPoolItem(pools.size, maxPools)) { event ->
                event.isCancelled = true
                player.sendMessage(plugin.getMessageComponent("gui-pool-create-hint"))
                player.sendMessage(plugin.getMessageComponent("gui-pool-create-coming-soon"))
            }, col, row)
        }

        pane.addItem(GuiItem(createInfoItem(pools.size, maxPools)) { it.isCancelled = true }, 4, 0)
        pane.addItem(GuiComponents.backButton(plugin, "gui.common.dest-loot") {
            if (chamber != null) menu.openLootKindSelect(player, chamber)
            else menu.openLootTableList(player)
        }, 0, 5)

        gui.addPane(pane)
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }
        return gui
    }

    private fun title(): String {
        return if (chamber != null) {
            val key = when (kind) {
                MenuService.LootKind.NORMAL -> "gui.pool-selector.title-chamber-normal"
                MenuService.LootKind.OMINOUS -> "gui.pool-selector.title-chamber-ominous"
            }
            GuiText.plain(plugin, key, "chamber" to chamber.name)
        } else {
            GuiText.plain(plugin, "gui.pool-selector.title-global", "table" to (globalTableName ?: ""))
        }
    }

    private fun getTableName(): String {
        if (chamber == null) return globalTableName!!
        return when (kind) {
            MenuService.LootKind.NORMAL -> "chamber-${chamber.name.lowercase()}"
            MenuService.LootKind.OMINOUS -> "ominous-${chamber.name.lowercase()}"
        }
    }

    private fun createPoolItem(pool: LootPool): ItemStack {
        val material = when {
            pool.name.contains("common", ignoreCase = true) -> Material.IRON_INGOT
            pool.name.contains("rare", ignoreCase = true) -> Material.DIAMOND
            pool.name.contains("unique", ignoreCase = true) -> Material.NETHER_STAR
            pool.name.contains("epic", ignoreCase = true) -> Material.NETHERITE_INGOT
            else -> Material.CHEST
        }
        val itemCount = pool.weightedItems.size + pool.guaranteedItems.size
        return GuiComponents.infoItem(plugin, material,
            "gui.pool-selector.pool-name", "gui.pool-selector.pool-lore",
            "name" to pool.name,
            "minRolls" to pool.minRolls, "maxRolls" to pool.maxRolls,
            "items" to itemCount,
            "weighted" to pool.weightedItems.size,
            "guaranteed" to pool.guaranteedItems.size)
    }

    private fun createNewPoolItem(currentPools: Int, maxPools: Int): ItemStack {
        return GuiComponents.infoItem(plugin, Material.LIME_DYE,
            "gui.pool-selector.new-pool-name", "gui.pool-selector.new-pool-lore",
            "current" to currentPools, "max" to maxPools)
    }

    private fun createInfoItem(poolCount: Int, maxPools: Int): ItemStack {
        return GuiComponents.infoItem(plugin, Material.BOOK,
            "gui.pool-selector.info-name", "gui.pool-selector.info-lore",
            "count" to poolCount, "max" to maxPools)
    }
}
