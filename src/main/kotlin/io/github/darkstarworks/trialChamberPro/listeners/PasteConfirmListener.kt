package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.papermc.paper.event.player.AsyncChatEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Listens for chat messages to confirm or cancel pending schematic pastes.
 */
class PasteConfirmListener(private val plugin: TrialChamberPro) : Listener {
    
    private val listenerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    @EventHandler(priority = EventPriority.LOWEST)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        
        // Check if player has a pending paste
        if (!plugin.pasteConfirmationManager.hasPending(player)) {
            return
        }
        
        // Get message text
        val message = PlainTextComponentSerializer.plainText().serialize(event.message()).lowercase().trim()
        
        when (message) {
            "confirm" -> {
                event.isCancelled = true
                
                val pending = plugin.pasteConfirmationManager.getPending(player) ?: return
                plugin.pasteConfirmationManager.cancelPending(player, silent = true)
                
                player.sendMessage(plugin.getMessage("paste-confirming"))
                
                listenerScope.launch {
                    val success = plugin.schematicManager.pasteSchematic(
                        pending.schematicName,
                        pending.location,
                        player
                    )
                    
                    if (success) {
                        player.sendMessage(plugin.getMessage("paste-success",
                            "schematic" to pending.schematicName,
                            "x" to pending.location.blockX,
                            "y" to pending.location.blockY,
                            "z" to pending.location.blockZ
                        ))
                        player.sendMessage(plugin.getMessage("paste-undo-hint"))
                    } else {
                        player.sendMessage(plugin.getMessage("paste-failed"))
                    }
                }
            }
            
            "cancel" -> {
                event.isCancelled = true
                plugin.pasteConfirmationManager.cancelPending(player)
            }
        }
    }
    
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        // Clean up pending pastes when player quits
        plugin.pasteConfirmationManager.cancelPending(event.player, silent = true)
    }
}
