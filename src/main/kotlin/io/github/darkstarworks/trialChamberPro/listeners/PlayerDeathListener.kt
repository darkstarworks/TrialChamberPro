package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.Location
import org.bukkit.entity.Monster
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks player deaths and mob kills within Trial Chambers.
 */
class PlayerDeathListener(private val plugin: TrialChamberPro) : Listener {

    // Track pending spectator offers keyed by player UUID
    private data class PendingOffer(val chamberId: Int, val chamberName: String, val deathLocation: Location)
    private val pendingOffers = ConcurrentHashMap<UUID, PendingOffer>()

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (!plugin.config.getBoolean("statistics.enabled", true)) return

        val player = event.entity
        val location = player.location

        // Use synchronous cache lookup so we can modify the event before it's processed
        val chamber = plugin.chamberManager.getCachedChamberAt(location) ?: return

        // Track death statistics asynchronously
        plugin.launchAsync {
            plugin.statisticsManager.incrementDeaths(player.uniqueId)
        }

        plugin.logger.info("Player ${player.name} died in chamber ${chamber.name}")

        // Set custom death message synchronously (while event is still being processed)
        if (plugin.config.getBoolean("messages.custom-death-message", false)) {
            event.deathMessage(
                net.kyori.adventure.text.Component.text(
                    plugin.getMessage("player-died-in-chamber",
                        "player" to player.name,
                        "chamber" to chamber.name
                    )
                )
            )
        }

        // Queue spectator offer for when player respawns
        if (plugin.config.getBoolean("spectator-mode.enabled", true)) {
            pendingOffers[player.uniqueId] = PendingOffer(chamber.id, chamber.name, location.clone())
        }
    }

    /**
     * Offers spectator mode after player respawns (fixes race condition with fixed delay).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val pending = pendingOffers.remove(player.uniqueId) ?: return

        if (!plugin.config.getBoolean("spectator-mode.enabled", true)) return

        // Get the chamber (might have been deleted since death)
        val chamber = plugin.chamberManager.getCachedChamberById(pending.chamberId)
        if (chamber == null) return

        // Small delay after respawn to ensure player is fully ready
        plugin.scheduler.runAtEntity(player, Runnable {
            if (player.isOnline && !player.isDead) {
                plugin.spectatorManager.offerSpectatorMode(player, chamber, pending.deathLocation)
            }
        })
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (!plugin.config.getBoolean("statistics.enabled", true)) return

        val entity = event.entity

        // Only track mob kills (not players, animals, etc.)
        if (entity !is Monster) return

        val killer = entity.killer ?: return
        val location = entity.location

        // Use synchronous cache lookup, then track stats asynchronously
        val chamber = plugin.chamberManager.getCachedChamberAt(location) ?: return
        plugin.launchAsync {
            plugin.statisticsManager.incrementMobsKilled(killer.uniqueId)
        }
    }

    // Note: Chamber completion tracking can be implemented later with more sophisticated logic
    // For now, we focus on death and mob kill statistics

    fun shutdown() {
        pendingOffers.clear()
    }
}
