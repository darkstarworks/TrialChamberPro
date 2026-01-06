package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.OutlinePane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.Chamber
import io.github.darkstarworks.trialChamberPro.models.VaultData
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
 * Vault management view - manage vault cooldowns and view vault states for a chamber.
 */
class VaultManagementView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val chamber: Chamber
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(6, "Vaults: ${chamber.name}")
        val pane = StaticPane(0, 0, 9, 6)

        // Row 0: Header and actions
        pane.addItem(GuiItem(createHeaderItem()) { it.isCancelled = true }, 4, 0)

        // Reset all cooldowns button
        pane.addItem(GuiItem(createResetAllItem()) { event ->
            event.isCancelled = true
            if (event.isShiftClick && event.isLeftClick) {
                resetAllCooldowns(player)
            }
        }, 2, 0)

        // Reset specific player button
        pane.addItem(GuiItem(createResetPlayerItem()) { event ->
            event.isCancelled = true
            player.sendMessage(plugin.getMessage("vault-reset-usage-hint", "chamber" to chamber.name))
        }, 6, 0)

        // Get vaults for this chamber
        val vaults = runBlocking { plugin.vaultManager.getVaultsForChamber(chamber.id) }

        // Vault list (rows 1-4)
        val vaultPane = OutlinePane(0, 1, 9, 4)
        vaults.take(36).forEach { vault ->
            vaultPane.addItem(GuiItem(createVaultItem(vault)) { event ->
                event.isCancelled = true
                if (event.isShiftClick && event.isRightClick) {
                    resetVaultCooldowns(player, vault)
                }
            })
        }
        gui.addPane(vaultPane)

        // Row 5: Navigation
        val backItem = ItemStack(Material.ARROW).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Back to Chamber", NamedTextColor.YELLOW))
            }
        }
        pane.addItem(GuiItem(backItem) { event ->
            event.isCancelled = true
            menu.openChamberDetail(player, chamber)
        }, 0, 5)

        // Online players with locked vaults
        val playersWithLocks = getPlayersWithLockedVaults()
        if (playersWithLocks.isNotEmpty()) {
            pane.addItem(GuiItem(createPlayersWithLocksItem(playersWithLocks)) { it.isCancelled = true }, 4, 5)
        }

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
        val (normalCount, ominousCount) = plugin.vaultManager.getVaultCounts(chamber.id)

        return ItemStack(Material.VAULT).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Vault Management", NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Manage vault cooldowns", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("$normalCount Normal Vaults", NamedTextColor.GREEN),
                    Component.text("$ominousCount Ominous Vaults", NamedTextColor.DARK_PURPLE)
                ))
            }
        }
    }

    private fun createResetAllItem(): ItemStack {
        return ItemStack(Material.TNT).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Reset ALL Cooldowns", NamedTextColor.RED)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Reset cooldowns for ALL players", NamedTextColor.GRAY),
                    Component.text("on ALL vaults in this chamber", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("WARNING: This cannot be undone!", NamedTextColor.RED),
                    Component.empty(),
                    Component.text("Shift+Left Click to confirm", NamedTextColor.YELLOW)
                ))
            }
        }
    }

    private fun createResetPlayerItem(): ItemStack {
        return ItemStack(Material.PLAYER_HEAD).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Reset Player Cooldowns", NamedTextColor.GOLD)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Reset cooldowns for a specific player", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Use command:", NamedTextColor.YELLOW),
                    Component.text("/tcp vault reset ${chamber.name} <player>", NamedTextColor.WHITE)
                ))
            }
        }
    }

    private fun createVaultItem(vault: VaultData): ItemStack {
        val isOminous = vault.type == io.github.darkstarworks.trialChamberPro.models.VaultType.OMINOUS
        val material = if (isOminous) Material.CRYING_OBSIDIAN else Material.CHISELED_TUFF

        // Get lock count for this vault
        val lockCount = runBlocking { plugin.vaultManager.getVaultLockCount(vault.id) }

        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Vault #${vault.id}", if (isOminous) NamedTextColor.DARK_PURPLE else NamedTextColor.GREEN)
                    .decoration(TextDecoration.BOLD, true))
                lore(listOf(
                    Component.text("Type: ${vault.type.displayName}", NamedTextColor.GRAY),
                    Component.text("Location: ${vault.x}, ${vault.y}, ${vault.z}", NamedTextColor.GRAY),
                    Component.text("Loot Table: ${vault.lootTable}", NamedTextColor.GRAY),
                    Component.empty(),
                    Component.text("Locked for: $lockCount player(s)", NamedTextColor.RED),
                    Component.empty(),
                    Component.text("Shift+Right Click to reset", NamedTextColor.YELLOW)
                ))
            }
        }
    }

    private fun createPlayersWithLocksItem(players: List<Pair<Player, Int>>): ItemStack {
        val lore = mutableListOf(
            Component.text("Online players with locked vaults:", NamedTextColor.GRAY),
            Component.empty()
        )

        players.take(8).forEach { (p, count) ->
            lore.add(Component.text("${p.name}: $count locked", NamedTextColor.YELLOW))
        }

        if (players.size > 8) {
            lore.add(Component.text("...and ${players.size - 8} more", NamedTextColor.GRAY))
        }

        return ItemStack(Material.KNOWLEDGE_BOOK).apply {
            itemMeta = itemMeta?.apply {
                displayName(Component.text("Locked Vaults Info", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.BOLD, true))
                lore(lore)
            }
        }
    }

    // ==================== Action Handlers ====================

    private fun resetAllCooldowns(player: Player) {
        player.sendMessage(plugin.getMessage("vault-reset-all-start", "chamber" to chamber.name))

        plugin.launchAsync {
            val vaults = plugin.vaultManager.getVaultsForChamber(chamber.id)
            var resetCount = 0

            vaults.forEach { vault ->
                plugin.vaultManager.resetAllCooldowns(vault.id)
                resetCount++
            }

            plugin.scheduler.runAtEntity(player, Runnable {
                player.sendMessage(plugin.getMessage("vault-reset-all-complete", "count" to resetCount))
                menu.openVaultManagement(player, chamber)
            })
        }
    }

    private fun resetVaultCooldowns(player: Player, vault: VaultData) {
        player.sendMessage(plugin.getMessage("vault-reset-single-start", "id" to vault.id))

        plugin.launchAsync {
            plugin.vaultManager.resetAllCooldowns(vault.id)
            plugin.scheduler.runAtEntity(player, Runnable {
                player.sendMessage(plugin.getMessage("vault-reset-single-complete"))
                menu.openVaultManagement(player, chamber)
            })
        }
    }

    private fun getPlayersWithLockedVaults(): List<Pair<Player, Int>> {
        return Bukkit.getOnlinePlayers().mapNotNull { p ->
            val (normalLocked, ominousLocked) = runBlocking {
                plugin.vaultManager.getLockedVaultCounts(p.uniqueId, chamber.id)
            }
            val total = normalLocked + ominousLocked
            if (total > 0) p to total else null
        }.sortedByDescending { it.second }
    }
}
