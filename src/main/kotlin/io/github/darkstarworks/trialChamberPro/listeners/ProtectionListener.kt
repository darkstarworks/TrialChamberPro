package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.block.Container
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent

/**
 * Handles protection for Trial Chambers.
 * Prevents unauthorized block modifications, container access, and mob griefing.
 */
class ProtectionListener(private val plugin: TrialChamberPro) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (!plugin.config.getBoolean("protection.enabled", true)) return
        if (!plugin.config.getBoolean("protection.prevent-block-break", true)) return

        val player = event.player
        val location = event.block.location

        // Check bypass permission
        if (player.hasPermission("tcp.bypass.protection")) return

        // Check if in chamber (synchronous, cache-based)
        if (plugin.chamberManager.isInChamber(location)) {
            event.isCancelled = true
            player.sendMessage(plugin.getMessage("cannot-break-blocks"))
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (!plugin.config.getBoolean("protection.enabled", true)) return
        if (!plugin.config.getBoolean("protection.prevent-block-place", true)) return

        val player = event.player
        val location = event.block.location

        // Check bypass permission
        if (player.hasPermission("tcp.bypass.protection")) return

        // Check if in chamber (synchronous, cache-based)
        if (plugin.chamberManager.isInChamber(location)) {
            event.isCancelled = true
            player.sendMessage(plugin.getMessage("cannot-place-blocks"))
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onContainerAccess(event: PlayerInteractEvent) {
        if (!plugin.config.getBoolean("protection.enabled", true)) return
        if (!plugin.config.getBoolean("protection.prevent-container-access", false)) return

        val block = event.clickedBlock ?: return
        if (block.state !is Container) return

        val player = event.player
        val location = block.location

        // Check bypass permission
        if (player.hasPermission("tcp.bypass.protection")) return

        // Allow vault access (handled by VaultInteractListener)
        if (block.type.name.contains("VAULT")) return

        // Check if in chamber (cache-only, sync)
        if (plugin.chamberManager.isInChamber(location)) {
            event.isCancelled = true
            player.sendMessage(plugin.getMessage("cannot-access-container"))
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        if (!plugin.config.getBoolean("protection.enabled", true)) return
        if (!plugin.config.getBoolean("protection.prevent-mob-griefing", true)) return

        val location = event.location

        // Use cache-only sync check first; if any cached chamber contains the explosion center,
        // filter out blocks that would be affected inside its bounds.
        val chamber = plugin.chamberManager.getCachedChamberAt(location)
        if (chamber != null) {
            event.blockList().removeIf { block ->
                chamber.contains(block.location)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        if (!plugin.config.getBoolean("protection.enabled", true)) return
        if (!plugin.config.getBoolean("protection.prevent-mob-griefing", true)) return

        val location = event.block.location

        // Allow players
        if (event.entity is Player) return

        // Prevent endermen, silverfish, etc. from modifying blocks
        if (plugin.chamberManager.isInChamber(location)) {
            event.isCancelled = true
        }
    }

    /**
     * WorldGuard integration check.
     * This is a placeholder - actual WorldGuard integration would check regions here.
     */
    private fun checkWorldGuard(location: org.bukkit.Location, player: Player): Boolean {
        if (!plugin.config.getBoolean("protection.worldguard-integration", true)) return true

        val worldGuard = plugin.server.pluginManager.getPlugin("WorldGuard")
        if (worldGuard == null || !worldGuard.isEnabled) return true

        // TODO: Implement actual WorldGuard region check
        // For now, return true (allow)
        return true
    }
}
