package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent

/**
 * Handles spectator mode chat commands and movement restrictions.
 */
class SpectatorListener(private val plugin: TrialChamberPro) : Listener {

    /**
     * Handles spectator chat commands (spectate/exit).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerChat(event: AsyncChatEvent) {
        if (!plugin.isReady) return
        if (!plugin.config.getBoolean("spectator-mode.enabled", true)) return

        val player = event.player
        val message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim().lowercase()

        // Check for spectator confirmation
        if (plugin.spectatorManager.hasPendingOffer(player)) {
            when (message) {
                "spectate", "yes", "y" -> {
                    event.isCancelled = true
                    plugin.scheduler.runTask(Runnable {
                        plugin.spectatorManager.acceptSpectatorMode(player)
                    })
                    return
                }
                "no", "n", "decline" -> {
                    event.isCancelled = true
                    plugin.scheduler.runTask(Runnable {
                        plugin.spectatorManager.declineSpectatorMode(player)
                    })
                    return
                }
            }
        }

        // Check for spectator exit command
        if (plugin.spectatorManager.isSpectating(player)) {
            when (message) {
                "exit", "leave", "quit", "stop" -> {
                    event.isCancelled = true
                    plugin.scheduler.runTask(Runnable {
                        plugin.spectatorManager.exitSpectatorMode(player)
                    })
                    return
                }
            }
        }
    }

    /**
     * Restricts spectator movement to chamber bounds.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        if (!plugin.isReady) return
        if (!plugin.config.getBoolean("spectator-mode.enabled", true)) return
        if (!plugin.config.getBoolean("spectator-mode.restrict-to-chamber", true)) return

        val player = event.player
        if (!plugin.spectatorManager.isSpectating(player)) return

        // Only check on block changes
        val from = event.from
        val to = event.to
        if (from.blockX == to.blockX && from.blockY == to.blockY && from.blockZ == to.blockZ) {
            return
        }

        // Check if destination is within bounds
        if (!plugin.spectatorManager.isWithinSpectatorBounds(player, to)) {
            event.isCancelled = true

            // Optionally teleport back to chamber center
            val chamber = plugin.spectatorManager.getSpectatingChamber(player)
            if (chamber != null) {
                player.sendMessage(plugin.getMessage("spectate-boundary-warning"))
            }
        }
    }

    /**
     * Prevents spectators from teleporting outside chamber bounds.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        if (!plugin.isReady) return
        if (!plugin.config.getBoolean("spectator-mode.enabled", true)) return
        if (!plugin.config.getBoolean("spectator-mode.restrict-to-chamber", true)) return

        val player = event.player
        if (!plugin.spectatorManager.isSpectating(player)) return

        // Allow spectator teleports within bounds
        val to = event.to
        if (!plugin.spectatorManager.isWithinSpectatorBounds(player, to)) {
            // Only cancel non-plugin teleports (allow our exit teleport)
            if (event.cause != PlayerTeleportEvent.TeleportCause.PLUGIN) {
                event.isCancelled = true
                player.sendMessage(plugin.getMessage("spectate-boundary-warning"))
            }
        }
    }

    /**
     * Cleans up spectator data on player quit.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        if (!plugin.isReady) return

        plugin.spectatorManager.handlePlayerQuit(event.player)
    }
}
