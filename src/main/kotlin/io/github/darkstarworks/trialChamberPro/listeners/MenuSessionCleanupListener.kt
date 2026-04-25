package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Drops a player's cached GUI navigation state ([MenuService.Session]) when they
 * leave the server so the internal map doesn't grow without bound on long-running
 * shards. Added in v1.3.0; pairs with the soft-cap eviction inside MenuService
 * itself for defence-in-depth.
 */
class MenuSessionCleanupListener(private val plugin: TrialChamberPro) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        plugin.menuService.clearSession(event.player.uniqueId)
    }
}
