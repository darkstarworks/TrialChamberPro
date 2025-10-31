package io.github.darkstarworks.trialChamberPro.commands

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.VaultType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.inventory.ItemStack

/**
 * Extension functions for TCPCommand to handle vault and key commands.
 */

private val commandScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

fun handleVault(plugin: TrialChamberPro, sender: CommandSender, args: Array<out String>) {
    if (!sender.hasPermission("tcp.admin.vault")) {
        sender.sendMessage(plugin.getMessage("no-permission"))
        return
    }

    if (args.size < 2) {
        sender.sendMessage("§cUsage: /tcp vault reset <chamber> <player> [normal|ominous]")
        return
    }

    when (args[1].lowercase()) {
        "reset" -> {
            if (args.size < 4) {
                sender.sendMessage("§cUsage: /tcp vault reset <chamber> <player> [normal|ominous]")
                return
            }

            val chamberName = args[2]
            val playerName = args[3]
            val vaultTypeStr = args.getOrNull(4)?.uppercase()

            commandScope.launch {
                val chamber = plugin.chamberManager.getChamber(chamberName)
                if (chamber == null) {
                    sender.sendMessage(plugin.getMessage("chamber-not-found", "chamber" to chamberName))
                    return@launch
                }

                val targetPlayer = Bukkit.getOfflinePlayer(playerName)
                val vaults = plugin.vaultManager.getVaultsForChamber(chamber.id)

                var resetCount = 0
                vaults.forEach { vault ->
                    // Filter by type if specified
                    if (vaultTypeStr != null) {
                        val filterType = try {
                            VaultType.valueOf(vaultTypeStr)
                        } catch (_: IllegalArgumentException) {
                            null
                        }

                        if (filterType != null && vault.type != filterType) {
                            return@forEach
                        }
                    }

                    if (plugin.vaultManager.resetCooldown(targetPlayer.uniqueId, vault.id)) {
                        resetCount++
                    }
                }

                sender.sendMessage(plugin.getMessage("vault-reset",
                    "player" to playerName,
                    "count" to resetCount
                ))
            }
        }
        else -> {
            sender.sendMessage("§cUsage: /tcp vault reset <chamber> <player> [normal|ominous]")
        }
    }
}

fun handleKey(plugin: TrialChamberPro, sender: CommandSender, args: Array<out String>) {
    if (!sender.hasPermission("tcp.admin.key")) {
        sender.sendMessage(plugin.getMessage("no-permission"))
        return
    }

    if (args.size < 2) {
        sender.sendMessage("§cUsage: /tcp key give <player> <amount> [normal|ominous]")
        return
    }

    when (args[1].lowercase()) {
        "give" -> {
            if (args.size < 4) {
                sender.sendMessage("§cUsage: /tcp key give <player> <amount> [normal|ominous]")
                return
            }

            val playerName = args[2]
            val amountStr = args[3]
            val keyTypeStr = args.getOrNull(4)?.lowercase() ?: "normal"

            val amount = amountStr.toIntOrNull()
            if (amount == null || amount <= 0) {
                sender.sendMessage(plugin.getMessage("key-invalid-amount"))
                return
            }

            val targetPlayer = Bukkit.getPlayer(playerName)
            if (targetPlayer == null) {
                sender.sendMessage("§cPlayer not found or not online.")
                return
            }

            val keyMaterial = when (keyTypeStr) {
                "ominous" -> {
                    // Try to get ominous trial key
                    try {
                        Material.valueOf("OMINOUS_TRIAL_KEY")
                    } catch (_: IllegalArgumentException) {
                        sender.sendMessage("§cOminous Trial Keys are not available in this server version.")
                        return
                    }
                }
                else -> Material.TRIAL_KEY
            }

            val keyItem = ItemStack(keyMaterial, amount)
            targetPlayer.inventory.addItem(keyItem)

            sender.sendMessage(plugin.getMessage("key-given",
                "amount" to amount,
                "type" to keyTypeStr.replaceFirstChar { it.uppercase() },
                "player" to playerName
            ))
        }
        "check" -> {
            if (args.size < 3) {
                sender.sendMessage("§cUsage: /tcp key check <player>")
                return
            }

            val playerName = args[2]
            val targetPlayer = Bukkit.getPlayer(playerName)
            if (targetPlayer == null) {
                sender.sendMessage("§cPlayer not found or not online.")
                return
            }

            var normalKeys = 0
            var ominousKeys = 0

            targetPlayer.inventory.contents.forEach { item ->
                if (item != null) {
                    when (item.type) {
                        Material.TRIAL_KEY -> normalKeys += item.amount
                        else -> {
                            if (item.type.name == "OMINOUS_TRIAL_KEY") {
                                ominousKeys += item.amount
                            }
                        }
                    }
                }
            }

            sender.sendMessage(plugin.getMessage("key-check",
                "player" to playerName,
                "normal" to normalKeys,
                "ominous" to ominousKeys
            ))
        }
        else -> {
            sender.sendMessage("§cUsage: /tcp key <give|check>")
        }
    }
}
