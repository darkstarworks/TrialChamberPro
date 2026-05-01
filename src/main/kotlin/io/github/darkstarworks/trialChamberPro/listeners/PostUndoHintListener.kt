package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent

/**
 * Observes WorldEdit `//undo` (and `/undo`) without intercepting it. If the player is
 * standing inside a registered chamber when they run undo, posts a one-line hint that
 * `/tcp delete <name>` may be needed to also clean up the registration. Purely
 * informational — never cancels the event, never locks the player out.
 */
class PostUndoHintListener(private val plugin: TrialChamberPro) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onCommandPreprocess(event: PlayerCommandPreprocessEvent) {
        val msg = event.message.trim().lowercase()
        if (msg != "/undo" && !msg.startsWith("//undo")) return

        val player = event.player
        plugin.launchAsync {
            val chamber = plugin.chamberManager.getChamberAt(player.location) ?: return@launchAsync
            plugin.scheduler.runAtEntity(player, Runnable {
                if (player.isOnline) {
                    player.sendMessage(plugin.getMessageComponent("undo-cleanup-hint",
                        "chamber" to chamber.name))
                }
            })
        }
    }
}
