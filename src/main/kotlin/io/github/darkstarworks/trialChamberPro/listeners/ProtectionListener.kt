package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.Material
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

        // Check if in chamber (synchronous, cache-based); skip protection for paused chambers
        val chamber = plugin.chamberManager.getCachedChamberAt(location) ?: return
        if (chamber.isPaused) return
        event.isCancelled = true
        player.sendMessage(plugin.getMessageComponent("cannot-break-blocks"))
    }

    /**
     * MONITOR-priority handler that auto-pauses a chamber when enough critical blocks
     * (vaults or trial spawners) inside it have been destroyed.
     *
     * Only active when `protection.auto-pause-on-destruction: true`.
     * `protection.auto-pause-threshold` (default 6) sets how many critical blocks must
     * be broken before the pause fires — so 1–2 stray breaks don't trigger it, but
     * systematic demolition (≥ threshold) does.
     *
     * The counter resets to zero whenever the chamber's pause state changes (via
     * [ChamberManager.resetDestructionCounter]), so a resumed chamber always starts fresh.
     *
     * Fires only on breaks that were NOT cancelled by any higher-priority handler.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockBreakMonitor(event: BlockBreakEvent) {
        if (!plugin.config.getBoolean("protection.auto-pause-on-destruction", false)) return
        val type = event.block.type
        if (type != Material.VAULT && type != Material.TRIAL_SPAWNER) return

        val chamber = plugin.chamberManager.getCachedChamberAt(event.block.location) ?: return
        if (chamber.isPaused) return

        val threshold = plugin.config.getInt("protection.auto-pause-threshold", 6).coerceAtLeast(1)
        val count = plugin.chamberManager.incrementDestructionCounter(chamber.id)
        if (count < threshold) return

        plugin.launchAsync {
            val success = plugin.chamberManager.setPaused(chamber.id, true)
            if (success) {
                plugin.scheduler.runTask(Runnable {
                    plugin.server.onlinePlayers
                        .filter { it.hasPermission("tcp.discovery.notify") }
                        .forEach { p ->
                            p.sendMessage(plugin.getMessageComponent(
                                "chamber-auto-paused",
                                "chamber" to chamber.name,
                                "block" to type.name.lowercase().replace('_', ' '),
                                "count" to count
                            ))
                        }
                })
            }
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

        // Check if in chamber (synchronous, cache-based); skip protection for paused chambers
        val chamber = plugin.chamberManager.getCachedChamberAt(location) ?: return
        if (chamber.isPaused) return
        event.isCancelled = true
        player.sendMessage(plugin.getMessageComponent("cannot-place-blocks"))
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

        // Check if in chamber (cache-only, sync); skip protection for paused chambers
        val chamber = plugin.chamberManager.getCachedChamberAt(location) ?: return
        if (chamber.isPaused) return
        event.isCancelled = true
        player.sendMessage(plugin.getMessageComponent("cannot-access-container"))
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        if (!plugin.config.getBoolean("protection.enabled", true)) return
        if (!plugin.config.getBoolean("protection.prevent-mob-griefing", true)) return

        val location = event.location

        // Use cache-only sync check first; if any cached non-paused chamber contains the
        // explosion center, filter out blocks that would be affected inside its bounds.
        val chamber = plugin.chamberManager.getCachedChamberAt(location)
        if (chamber != null && !chamber.isPaused) {
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

        // Prevent endermen, silverfish, etc. from modifying blocks in non-paused chambers
        val chamber = plugin.chamberManager.getCachedChamberAt(location) ?: return
        if (chamber.isPaused) return
        event.isCancelled = true
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
