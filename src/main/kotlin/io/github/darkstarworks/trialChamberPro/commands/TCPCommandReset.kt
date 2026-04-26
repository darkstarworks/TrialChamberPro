package io.github.darkstarworks.trialChamberPro.commands

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

private val resetCommandScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

fun handleReset(plugin: TrialChamberPro, sender: CommandSender, args: Array<out String>) {
    if (!sender.hasPermission("tcp.admin.reset")) {
        sender.sendMessage(plugin.getMessageComponent("no-permission"))
        return
    }

    if (args.size < 2) {
        sender.sendMessage(plugin.getMessageComponent("usage-reset"))
        return
    }

    val chamberName = args[1]

    resetCommandScope.launch {
        val chamber = plugin.chamberManager.getChamber(chamberName)
        if (chamber == null) {
            sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
            return@launch
        }

        sender.sendMessage(plugin.getMessageComponent("chamber-resetting", "chamber" to chamberName))

        // Pass player for WorldEdit undo support if sender is a player
        val initiatingPlayer = sender as? Player
        val success = plugin.resetManager.resetChamber(chamber, initiatingPlayer)
        if (success) {
            sender.sendMessage(plugin.getMessageComponent("reset-success", "chamber" to chamberName))
        } else {
            sender.sendMessage(plugin.getMessageComponent("reset-failed", "error" to "Check console for details"))
        }
    }
}
