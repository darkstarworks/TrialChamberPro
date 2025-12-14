package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Help menu view - displays command reference, permissions, and plugin info.
 */
class HelpMenuView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(6, "Help & Information")
        val pane = StaticPane(0, 0, 9, 6)

        // Row 0: Header
        pane.addItem(GuiItem(createHeaderItem()) { it.isCancelled = true }, 4, 0)

        // Row 1: Main categories
        pane.addItem(GuiItem(createCommandsItem()) { it.isCancelled = true }, 2, 1)
        pane.addItem(GuiItem(createPermissionsItem()) { it.isCancelled = true }, 4, 1)
        pane.addItem(GuiItem(createAboutItem()) { it.isCancelled = true }, 6, 1)

        // Row 2-3: Quick command reference
        pane.addItem(GuiItem(createChamberCommandsItem()) { it.isCancelled = true }, 1, 2)
        pane.addItem(GuiItem(createLootCommandsItem()) { it.isCancelled = true }, 3, 2)
        pane.addItem(GuiItem(createVaultCommandsItem()) { it.isCancelled = true }, 5, 2)
        pane.addItem(GuiItem(createStatsCommandsItem()) { it.isCancelled = true }, 7, 2)

        pane.addItem(GuiItem(createSnapshotCommandsItem()) { it.isCancelled = true }, 2, 3)
        pane.addItem(GuiItem(createAdminCommandsItem()) { it.isCancelled = true }, 4, 3)
        pane.addItem(GuiItem(createGenerateCommandsItem()) { it.isCancelled = true }, 6, 3)

        // Row 5: Navigation
        val backItem = ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Back to Main Menu", NamedTextColor.YELLOW))
            }
        }
        pane.addItem(GuiItem(backItem) { event ->
            event.isCancelled = true
            menu.openMainMenu(player)
        }, 0, 5)

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

        return gui
    }

    private fun createHeaderItem(): ItemStack {
        return ItemStack(Material.KNOWLEDGE_BOOK).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Help & Information", NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Commands, permissions, and info", NamedTextColor.GRAY)
                ))
            }
        }
    }

    private fun createCommandsItem(): ItemStack {
        return ItemStack(Material.COMMAND_BLOCK).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Commands", NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Main command: /tcp", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Use /tcp help for full list", NamedTextColor.YELLOW)
                ))
            }
        }
    }

    private fun createPermissionsItem(): ItemStack {
        return ItemStack(Material.BOOK).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Permissions", NamedTextColor.AQUA)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("tcp.admin - Admin access", NamedTextColor.GRAY),
                    Component.text("tcp.admin.reload - Reload config", NamedTextColor.GRAY),
                    Component.text("tcp.admin.generate - Create chambers", NamedTextColor.GRAY),
                    Component.text("tcp.admin.loot - Manage loot tables", NamedTextColor.GRAY),
                    Component.text("tcp.use - Basic player access", NamedTextColor.GRAY)
                ))
            }
        }
    }

    private fun createAboutItem(): ItemStack {
        return ItemStack(Material.TRIAL_KEY).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("About", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("TrialChamberPro", NamedTextColor.WHITE),
                    Component.text("Version: ${plugin.description.version}", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Transform Trial Chambers into", NamedTextColor.GRAY),
                    Component.text("renewable multiplayer content!", NamedTextColor.GRAY)
                ))
            }
        }
    }

    private fun createChamberCommandsItem(): ItemStack {
        return ItemStack(Material.LODESTONE).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Chamber Commands", NamedTextColor.AQUA)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("/tcp list", NamedTextColor.YELLOW)
                        .append(Component.text(" - List chambers", NamedTextColor.GRAY)),
                    Component.text("/tcp info <name>", NamedTextColor.YELLOW)
                        .append(Component.text(" - Chamber info", NamedTextColor.GRAY)),
                    Component.text("/tcp delete <name>", NamedTextColor.YELLOW)
                        .append(Component.text(" - Delete chamber", NamedTextColor.GRAY)),
                    Component.text("/tcp setexit <name>", NamedTextColor.YELLOW)
                        .append(Component.text(" - Set exit point", NamedTextColor.GRAY)),
                    Component.text("/tcp reset <name>", NamedTextColor.YELLOW)
                        .append(Component.text(" - Force reset", NamedTextColor.GRAY))
                ))
            }
        }
    }

    private fun createLootCommandsItem(): ItemStack {
        return ItemStack(Material.CHEST).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Loot Commands", NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("/tcp loot list", NamedTextColor.YELLOW)
                        .append(Component.text(" - List tables", NamedTextColor.GRAY)),
                    Component.text("/tcp loot info <chamber>", NamedTextColor.YELLOW)
                        .append(Component.text(" - Show config", NamedTextColor.GRAY)),
                    Component.text("/tcp loot set ...", NamedTextColor.YELLOW)
                        .append(Component.text(" - Set override", NamedTextColor.GRAY)),
                    Component.text("/tcp loot clear ...", NamedTextColor.YELLOW)
                        .append(Component.text(" - Clear override", NamedTextColor.GRAY))
                ))
            }
        }
    }

    private fun createVaultCommandsItem(): ItemStack {
        return ItemStack(Material.VAULT).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Vault Commands", NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("/tcp vault reset <chamber> <player>", NamedTextColor.YELLOW),
                    Component.text("  Reset player vault cooldowns", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("/tcp key give <player> <amt> [type]", NamedTextColor.YELLOW),
                    Component.text("  Give trial keys", NamedTextColor.GRAY)
                ))
            }
        }
    }

    private fun createStatsCommandsItem(): ItemStack {
        return ItemStack(Material.WRITABLE_BOOK).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Stats Commands", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("/tcp stats [player]", NamedTextColor.YELLOW)
                        .append(Component.text(" - View stats", NamedTextColor.GRAY)),
                    Component.text("/tcp leaderboard <type>", NamedTextColor.YELLOW)
                        .append(Component.text(" - View top", NamedTextColor.GRAY)),
                    Component.empty(),
                    Component.text("Types: chambers, normal,", NamedTextColor.GRAY),
                    Component.text("ominous, mobs, time", NamedTextColor.GRAY)
                ))
            }
        }
    }

    private fun createSnapshotCommandsItem(): ItemStack {
        return ItemStack(Material.SPYGLASS).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Snapshot Commands", NamedTextColor.BLUE)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("/tcp snapshot create <name>", NamedTextColor.YELLOW),
                    Component.text("  Create block snapshot", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("/tcp snapshot restore <name>", NamedTextColor.YELLOW),
                    Component.text("  Restore from snapshot", NamedTextColor.GRAY)
                ))
            }
        }
    }

    private fun createAdminCommandsItem(): ItemStack {
        return ItemStack(Material.COMPARATOR).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Admin Commands", NamedTextColor.RED)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("/tcp reload", NamedTextColor.YELLOW)
                        .append(Component.text(" - Reload configs", NamedTextColor.GRAY)),
                    Component.text("/tcp menu", NamedTextColor.YELLOW)
                        .append(Component.text(" - Open this GUI", NamedTextColor.GRAY)),
                    Component.text("/tcp scan <name>", NamedTextColor.YELLOW)
                        .append(Component.text(" - Scan vaults", NamedTextColor.GRAY))
                ))
            }
        }
    }

    private fun createGenerateCommandsItem(): ItemStack {
        return ItemStack(Material.GOLDEN_PICKAXE).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Generate Commands", NamedTextColor.YELLOW)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("/tcp generate wand <name>", NamedTextColor.YELLOW),
                    Component.text("  From WorldEdit selection", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("/tcp generate coords ...", NamedTextColor.YELLOW),
                    Component.text("  From coordinates", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("/tcp paste <schematic>", NamedTextColor.YELLOW),
                    Component.text("  Paste a schematic", NamedTextColor.GRAY)
                ))
            }
        }
    }
}
