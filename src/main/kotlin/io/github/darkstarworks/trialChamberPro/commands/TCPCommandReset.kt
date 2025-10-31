package io.github.darkstarworks.trialChamberPro.commands

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.bukkit.command.CommandSender

private val resetCommandScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

fun handleReset(plugin: TrialChamberPro, sender: CommandSender, args: Array<out String>) {
    if (!sender.hasPermission("tcp.admin.reset")) {
        sender.sendMessage(plugin.getMessage("no-permission"))
        return
    }

    if (args.size < 2) {
        sender.sendMessage("§cUsage: /tcp reset <chamber>")
        return
    }

    val chamberName = args[1]

    resetCommandScope.launch {
        val chamber = plugin.chamberManager.getChamber(chamberName)
        if (chamber == null) {
            sender.sendMessage(plugin.getMessage("chamber-not-found", "chamber" to chamberName))
            return@launch
        }

        sender.sendMessage(plugin.getMessage("chamber-resetting", "chamber" to chamberName))

        val success = plugin.resetManager.resetChamber(chamber)
        if (success) {
            sender.sendMessage(plugin.getMessage("reset-success", "chamber" to chamberName))
        } else {
            sender.sendMessage(plugin.getMessage("reset-failed", "error" to "Check console for details"))
        }
    }
}
