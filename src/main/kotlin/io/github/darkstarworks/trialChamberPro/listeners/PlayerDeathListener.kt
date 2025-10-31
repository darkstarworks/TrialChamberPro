package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.bukkit.entity.Monster
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent

/**
 * Tracks player deaths and mob kills within Trial Chambers.
 */
class PlayerDeathListener(private val plugin: TrialChamberPro) : Listener {

    private val deathScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        if (!plugin.config.getBoolean("statistics.enabled", true)) return

        val player = event.entity
        val location = player.location

        // Check if player died in a chamber
        deathScope.launch {
            val chamber = plugin.chamberManager.getChamberAt(location)
            if (chamber != null) {
                plugin.statisticsManager.incrementDeaths(player.uniqueId)

                plugin.logger.info("Player ${player.name} died in chamber ${chamber.name}")

                // Optional: Send custom death message
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
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (!plugin.config.getBoolean("statistics.enabled", true)) return

        val entity = event.entity

        // Only track mob kills (not players, animals, etc.)
        if (entity !is Monster) return

        val killer = entity.killer ?: return
        val location = entity.location

        // Check if mob was killed in a chamber
        deathScope.launch {
            val chamber = plugin.chamberManager.getChamberAt(location)
            if (chamber != null) {
                plugin.statisticsManager.incrementMobsKilled(killer.uniqueId)
            }
        }
    }

    // Note: Chamber completion tracking can be implemented later with more sophisticated logic
    // For now, we focus on death and mob kill statistics
}
