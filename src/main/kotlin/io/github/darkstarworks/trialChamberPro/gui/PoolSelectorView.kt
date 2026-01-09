package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.Chamber
import io.github.darkstarworks.trialChamberPro.models.LootPool
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Pool selector view - shows all loot pools in a multi-pool loot table.
 * Allows editing specific pools or managing the pool structure.
 */
class PoolSelectorView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val chamber: Chamber,
    private val kind: MenuService.LootKind
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(6, title())
        val pane = StaticPane(0, 0, 9, 6)

        // Get the loot table
        val tableName = getTableName()
        val table = plugin.lootManager.getTable(tableName)

        if (table == null || table.isLegacyFormat()) {
            // Table doesn't exist or is legacy format - edit directly
            menu.openLootEditor(player, chamber, kind, null)
            return gui
        }

        // Get all pools
        val pools = table.pools
        val maxPools = plugin.config.getInt("loot.max-pools-per-table", 5)

        // Display each pool as a clickable item
        pools.forEachIndexed { index, pool ->
            val item = createPoolItem(pool, index)
            val row = 1 + (index / 7)  // Start at row 1, 7 items per row
            val col = 1 + (index % 7)

            pane.addItem(GuiItem(item) { event ->
                event.isCancelled = true
                menu.openLootEditor(player, chamber, kind, pool.name)
            }, col, row)
        }

        // Add "New Pool" button if under limit
        if (pools.size < maxPools) {
            val newPoolItem = createNewPoolItem(pools.size, maxPools)
            val index = pools.size
            val row = 1 + (index / 7)
            val col = 1 + (index % 7)

            pane.addItem(GuiItem(newPoolItem) { event ->
                event.isCancelled = true
                player.sendMessage(plugin.getMessage("gui-pool-create-hint"))
                player.sendMessage(plugin.getMessage("gui-pool-create-coming-soon"))
            }, col, row)
        }

        // Info item
        val info = createInfoItem(pools.size, maxPools)
        pane.addItem(GuiItem(info) { it.isCancelled = true }, 4, 0)

        // Back button
        val back = ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Back", NamedTextColor.YELLOW))
            }
        }
        pane.addItem(GuiItem(back) { event ->
            event.isCancelled = true
            menu.openLootKindSelect(player, chamber)
        }, 0, 5)

        gui.addPane(pane)
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }

        return gui
    }

    private fun title(): String = when (kind) {
        MenuService.LootKind.NORMAL -> "${chamber.name}: Select Normal Pool"
        MenuService.LootKind.OMINOUS -> "${chamber.name}: Select Ominous Pool"
    }

    private fun getTableName(): String {
        val baseName = when (kind) {
            MenuService.LootKind.NORMAL -> "chamber-${chamber.name.lowercase()}"
            MenuService.LootKind.OMINOUS -> "ominous-${chamber.name.lowercase()}"
        }
        return baseName
    }

    private fun createPoolItem(pool: LootPool, index: Int): ItemStack {
        // Choose material based on pool name/index
        val material = when {
            pool.name.contains("common", ignoreCase = true) -> Material.IRON_INGOT
            pool.name.contains("rare", ignoreCase = true) -> Material.DIAMOND
            pool.name.contains("unique", ignoreCase = true) -> Material.NETHER_STAR
            pool.name.contains("epic", ignoreCase = true) -> Material.NETHERITE_INGOT
            else -> Material.CHEST
        }

        val item = ItemStack(material)
        val itemCount = pool.weightedItems.size + pool.guaranteedItems.size

        item.itemMeta = item.itemMeta?.apply {
            displayName(Component.text(pool.name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))
            lore(listOf(
                Component.text("Rolls: ${pool.minRolls}-${pool.maxRolls}", NamedTextColor.GRAY),
                Component.text("Items: $itemCount", NamedTextColor.GRAY),
                Component.text("  Weighted: ${pool.weightedItems.size}", NamedTextColor.DARK_GRAY),
                Component.text("  Guaranteed: ${pool.guaranteedItems.size}", NamedTextColor.DARK_GRAY),
                Component.text(""),
                Component.text("Click to edit this pool", NamedTextColor.YELLOW)
            ))
        }

        return item
    }

    private fun createNewPoolItem(currentPools: Int, maxPools: Int): ItemStack {
        val item = ItemStack(Material.LIME_DYE)

        item.itemMeta = item.itemMeta?.apply {
            displayName(Component.text("+ New Pool", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false))
            lore(listOf(
                Component.text("Add a new loot pool", NamedTextColor.GRAY),
                Component.text(""),
                Component.text("Pools: $currentPools/$maxPools", NamedTextColor.DARK_GRAY),
                Component.text(""),
                Component.text("Click for instructions", NamedTextColor.YELLOW)
            ))
        }

        return item
    }

    private fun createInfoItem(poolCount: Int, maxPools: Int): ItemStack {
        val item = ItemStack(Material.BOOK)

        item.itemMeta = item.itemMeta?.apply {
            displayName(Component.text("Multi-Pool Loot", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false))
            lore(listOf(
                Component.text("This table has $poolCount pool(s)", NamedTextColor.GRAY),
                Component.text(""),
                Component.text("Each pool rolls independently!", NamedTextColor.YELLOW),
                Component.text("Like vanilla: common, rare, unique", NamedTextColor.GRAY),
                Component.text(""),
                Component.text("Max pools: $maxPools", NamedTextColor.DARK_GRAY),
                Component.text("Set in config.yml", NamedTextColor.DARK_GRAY)
            ))
        }

        return item
    }
}
