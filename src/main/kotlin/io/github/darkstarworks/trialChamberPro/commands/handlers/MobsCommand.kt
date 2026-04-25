package io.github.darkstarworks.trialChamberPro.commands.handlers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.command.CommandSender

/**
 * `/tcp mobs <providers | <chamber> <list|provider|add|remove> ...>` — manages
 * the per-chamber custom-mob provider configuration introduced in v1.3.0.
 *
 * Extracted from `TCPCommand.handleMobs` in v1.3.0 Phase 3.
 */
class MobsCommand(private val plugin: TrialChamberPro) : SubcommandHandler {

    override fun execute(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.mobs")) {
            sender.sendMessage(plugin.getMessage("no-permission"))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(plugin.getMessage("mobs-usage-root"))
            sender.sendMessage(plugin.getMessage("mobs-usage-providers"))
            return
        }

        if (args[1].equals("providers", ignoreCase = true)) {
            sender.sendMessage(plugin.getMessage("mobs-providers-header"))
            plugin.trialMobProviderRegistry.all().forEach { p ->
                val status = plugin.getMessage(
                    if (p.isAvailable()) "mobs-status-available" else "mobs-status-unavailable"
                )
                sender.sendMessage(plugin.getMessage(
                    "mobs-providers-entry",
                    "id" to p.id,
                    "name" to p.displayName,
                    "status" to status
                ))
            }
            return
        }

        val chamberName = args[1]
        if (args.size < 3) {
            sender.sendMessage(plugin.getMessage("mobs-usage-chamber", "chamber" to chamberName))
            return
        }
        val action = args[2].lowercase()

        plugin.launchAsync {
            val chamber = plugin.chamberManager.getChamber(chamberName)
            if (chamber == null) {
                sender.sendMessage(plugin.getMessage("chamber-not-found", "chamber" to chamberName))
                return@launchAsync
            }

            when (action) {
                "list" -> {
                    val provider = chamber.customMobProvider ?: "vanilla"
                    val normalList = chamber.customMobIdsNormal.takeIf { it.isNotEmpty() }
                        ?.joinToString(", ")
                        ?: plugin.getMessage("mobs-list-empty")
                    val ominousList = chamber.customMobIdsOminous.takeIf { it.isNotEmpty() }
                        ?.joinToString(", ")
                        ?: plugin.getMessage("mobs-list-falls-back")
                    sender.sendMessage(plugin.getMessage(
                        "mobs-list-provider",
                        "chamber" to chamber.name,
                        "provider" to provider
                    ))
                    sender.sendMessage(plugin.getMessage("mobs-list-normal", "mobs" to normalList))
                    sender.sendMessage(plugin.getMessage("mobs-list-ominous", "mobs" to ominousList))
                }
                "provider" -> {
                    if (args.size < 4) {
                        sender.sendMessage(plugin.getMessage("mobs-usage-provider", "chamber" to chamber.name))
                        return@launchAsync
                    }
                    val raw = args[3].lowercase()
                    val provider = if (raw == "none" || raw == "vanilla") null else raw
                    if (provider != null && plugin.trialMobProviderRegistry.get(provider) == null) {
                        sender.sendMessage(plugin.getMessage("mobs-provider-unknown", "provider" to provider))
                        return@launchAsync
                    }
                    val ok = plugin.chamberManager.updateCustomMobProvider(chamber.id, provider)
                    sender.sendMessage(
                        if (ok) plugin.getMessage("mobs-provider-set", "provider" to (provider ?: "vanilla"))
                        else plugin.getMessage("mobs-provider-update-failed")
                    )
                }
                "add", "remove" -> {
                    if (args.size < 5) {
                        sender.sendMessage(plugin.getMessage(
                            "mobs-usage-addremove",
                            "chamber" to chamber.name,
                            "action" to action
                        ))
                        return@launchAsync
                    }
                    val waveType = args[3].lowercase()
                    val mobId = args[4]
                    if (waveType != "normal" && waveType != "ominous") {
                        sender.sendMessage(plugin.getMessage("mobs-bad-wave-type"))
                        return@launchAsync
                    }

                    val currentNormal = chamber.customMobIdsNormal.toMutableList()
                    val currentOminous = chamber.customMobIdsOminous.toMutableList()
                    val target = if (waveType == "ominous") currentOminous else currentNormal

                    if (action == "add") {
                        if (target.contains(mobId)) {
                            sender.sendMessage(plugin.getMessage("mobs-already-present", "id" to mobId, "wave" to waveType))
                            return@launchAsync
                        }
                        target.add(mobId)
                    } else {
                        if (!target.remove(mobId)) {
                            sender.sendMessage(plugin.getMessage("mobs-not-present", "id" to mobId, "wave" to waveType))
                            return@launchAsync
                        }
                    }

                    val ok = plugin.chamberManager.updateCustomMobProvider(
                        chamber.id,
                        chamber.customMobProvider,
                        normalIds = currentNormal,
                        ominousIds = currentOminous
                    )
                    if (ok) {
                        val key = if (action == "add") "mobs-added" else "mobs-removed"
                        sender.sendMessage(plugin.getMessage(
                            key,
                            "id" to mobId,
                            "wave" to waveType,
                            "chamber" to chamber.name
                        ))
                        if (chamber.customMobProvider.isNullOrBlank() || chamber.customMobProvider.equals("vanilla", ignoreCase = true)) {
                            sender.sendMessage(plugin.getMessage("mobs-vanilla-warning", "chamber" to chamber.name))
                        }
                    } else {
                        sender.sendMessage(plugin.getMessage("mobs-update-failed"))
                    }
                }
                else -> sender.sendMessage(plugin.getMessage("mobs-unknown-action", "action" to action))
            }
        }
    }
}
