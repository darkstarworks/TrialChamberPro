package io.github.darkstarworks.trialChamberPro.gui

import com.github.stefvanschie.inventoryframework.gui.GuiItem
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui
import com.github.stefvanschie.inventoryframework.pane.OutlinePane
import com.github.stefvanschie.inventoryframework.pane.StaticPane
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.gui.components.GuiComponents
import io.github.darkstarworks.trialChamberPro.gui.components.GuiText
import io.github.darkstarworks.trialChamberPro.models.Chamber
import io.github.darkstarworks.trialChamberPro.models.VaultData
import io.github.darkstarworks.trialChamberPro.models.VaultType
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Vault management view — view and reset vault cooldowns for a chamber.
 * All strings from `messages.yml` under `gui.vault-management.*` (v1.3.0).
 */
class VaultManagementView(
    private val plugin: TrialChamberPro,
    private val menu: MenuService,
    private val chamber: Chamber
) {
    fun build(player: Player): ChestGui {
        val gui = ChestGui(6, GuiText.plain(plugin, "gui.vault-management.title", "chamber" to chamber.name))
        val pane = StaticPane(0, 0, 9, 6)

        val (normalCount, ominousCount) = plugin.vaultManager.getVaultCounts(chamber.id)
        pane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.VAULT,
                "gui.vault-management.header-name", "gui.vault-management.header-lore",
                "normal" to normalCount, "ominous" to ominousCount)
        ) { it.isCancelled = true }, 4, 0)

        pane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.TNT,
                "gui.vault-management.reset-all-name", "gui.vault-management.reset-all-lore")
        ) { event ->
            event.isCancelled = true
            if (event.isShiftClick && event.isLeftClick) resetAllCooldowns(player)
        }, 2, 0)

        pane.addItem(GuiItem(
            GuiComponents.infoItem(plugin, Material.PLAYER_HEAD,
                "gui.vault-management.reset-player-name", "gui.vault-management.reset-player-lore",
                "chamber" to chamber.name)
        ) { event ->
            event.isCancelled = true
            player.sendMessage(plugin.getMessage("vault-reset-usage-hint", "chamber" to chamber.name))
        }, 6, 0)

        val vaults = runBlocking { plugin.vaultManager.getVaultsForChamber(chamber.id) }
        val vaultPane = OutlinePane(0, 1, 9, 4)
        vaults.take(36).forEach { vault ->
            vaultPane.addItem(GuiItem(createVaultItem(vault)) { event ->
                event.isCancelled = true
                if (event.isShiftClick && event.isRightClick) resetVaultCooldowns(player, vault)
            })
        }
        gui.addPane(vaultPane)

        if (vaults.isEmpty()) {
            val emptyPane = StaticPane(0, 2, 9, 1)
            emptyPane.addItem(GuiItem(
                GuiComponents.infoItem(plugin, Material.BARRIER,
                    "gui.vault-management.empty-name", "gui.vault-management.empty-lore",
                    "chamber" to chamber.name)
            ) { it.isCancelled = true }, 4, 0)
            gui.addPane(emptyPane)
        }

        pane.addItem(GuiComponents.backButton(plugin, "gui.common.dest-chamber") {
            menu.openChamberDetail(player, chamber)
        }, 0, 5)

        val playersWithLocks = getPlayersWithLockedVaults()
        if (playersWithLocks.isNotEmpty()) {
            pane.addItem(GuiItem(createPlayersWithLocksItem(playersWithLocks)) { it.isCancelled = true }, 4, 5)
        }

        pane.addItem(GuiComponents.closeButton(plugin, player), 8, 5)

        gui.addPane(pane)
        gui.setOnGlobalClick { it.isCancelled = true }
        gui.setOnGlobalDrag { it.isCancelled = true }
        return gui
    }

    private fun createVaultItem(vault: VaultData): ItemStack {
        val isOminous = vault.type == VaultType.OMINOUS
        val material = if (isOminous) Material.CRYING_OBSIDIAN else Material.CHISELED_TUFF
        val nameKey = if (isOminous)
            "gui.vault-management.vault-name-ominous" else "gui.vault-management.vault-name-normal"
        val lockCount = runBlocking { plugin.vaultManager.getVaultLockCount(vault.id) }
        return GuiComponents.infoItem(plugin, material,
            nameKey, "gui.vault-management.vault-lore",
            "id" to vault.id,
            "type" to vault.type.displayName,
            "x" to vault.x, "y" to vault.y, "z" to vault.z,
            "table" to vault.lootTable,
            "locks" to lockCount)
    }

    private fun createPlayersWithLocksItem(players: List<Pair<Player, Int>>): ItemStack {
        val lore = mutableListOf<Component>(
            plugin.getGuiText("gui.vault-management.locks-info-header"),
            Component.empty()
        )
        players.take(8).forEach { (p, count) ->
            lore.add(plugin.getGuiText("gui.vault-management.locks-info-line",
                "name" to p.name, "count" to count))
        }
        if (players.size > 8) {
            lore.add(plugin.getGuiText("gui.vault-management.locks-info-overflow",
                "extra" to (players.size - 8)))
        }
        return ItemStack(Material.KNOWLEDGE_BOOK).apply {
            itemMeta = itemMeta?.apply {
                displayName(plugin.getGuiText("gui.vault-management.locks-info-name"))
                lore(lore)
            }
        }
    }

    // ==================== Action Handlers (unchanged behavior) ====================

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
