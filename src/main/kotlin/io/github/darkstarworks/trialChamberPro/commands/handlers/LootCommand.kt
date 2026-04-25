package io.github.darkstarworks.trialChamberPro.commands.handlers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.VaultType
import org.bukkit.command.CommandSender

/**
 * `/tcp loot <set|clear|info|list> ...` — manages per-chamber loot table
 * overrides. Wraps the four sub-actions (`set`, `clear`, `info`, `list`) that
 * were previously private methods on `TCPCommand`.
 *
 * Extracted in v1.3.0 Phase 3.
 */
class LootCommand(private val plugin: TrialChamberPro) : SubcommandHandler {

    override fun execute(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.loot")) {
            sender.sendMessage(plugin.getMessage("no-permission"))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(plugin.getMessage("usage-loot"))
            return
        }

        when (args[1].lowercase()) {
            "set" -> handleSet(sender, args)
            "clear" -> handleClear(sender, args)
            "info" -> handleInfo(sender, args)
            "list" -> handleList(sender)
            else -> sender.sendMessage(plugin.getMessage("usage-loot"))
        }
    }

    /** `/tcp loot set <chamber> <normal|ominous> <table>` */
    private fun handleSet(sender: CommandSender, args: Array<out String>) {
        if (args.size < 5) {
            sender.sendMessage(plugin.getMessage("usage-loot-set"))
            return
        }

        val chamberName = args[2]
        val typeStr = args[3].lowercase()
        val tableName = args[4]

        val vaultType = when (typeStr) {
            "normal" -> VaultType.NORMAL
            "ominous" -> VaultType.OMINOUS
            else -> {
                sender.sendMessage(plugin.getMessage("error-invalid-type"))
                return
            }
        }

        if (plugin.lootManager.getTable(tableName) == null) {
            sender.sendMessage(plugin.getMessage("loot-table-not-found", "table" to tableName))
            return
        }

        plugin.launchAsync {
            val chamber = plugin.chamberManager.getChamber(chamberName)
            if (chamber == null) {
                sender.sendMessage(plugin.getMessage("chamber-not-found", "chamber" to chamberName))
                return@launchAsync
            }

            val success = plugin.chamberManager.setLootTable(chamberName, vaultType, tableName)
            if (success) {
                sender.sendMessage(plugin.getMessage("loot-set-success",
                    "type" to vaultType.displayName,
                    "chamber" to chamberName,
                    "table" to tableName))
            } else {
                sender.sendMessage(plugin.getMessage("error-loot-set-failed"))
            }
        }
    }

    /** `/tcp loot clear <chamber> [normal|ominous|all]` */
    private fun handleClear(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage(plugin.getMessage("usage-loot-clear"))
            return
        }

        val chamberName = args[2]
        val typeStr = args.getOrNull(3)?.lowercase() ?: "all"

        plugin.launchAsync {
            val chamber = plugin.chamberManager.getChamber(chamberName)
            if (chamber == null) {
                sender.sendMessage(plugin.getMessage("chamber-not-found", "chamber" to chamberName))
                return@launchAsync
            }

            when (typeStr) {
                "normal" -> {
                    plugin.chamberManager.setLootTable(chamberName, VaultType.NORMAL, null)
                    sender.sendMessage(plugin.getMessage("loot-clear-success", "chamber" to chamberName))
                }
                "ominous" -> {
                    plugin.chamberManager.setLootTable(chamberName, VaultType.OMINOUS, null)
                    sender.sendMessage(plugin.getMessage("loot-clear-success", "chamber" to chamberName))
                }
                "all" -> {
                    plugin.chamberManager.setLootTable(chamberName, VaultType.NORMAL, null)
                    plugin.chamberManager.setLootTable(chamberName, VaultType.OMINOUS, null)
                    sender.sendMessage(plugin.getMessage("loot-clear-success", "chamber" to chamberName))
                }
                else -> sender.sendMessage(plugin.getMessage("error-invalid-type-loot-clear"))
            }
        }
    }

    /** `/tcp loot info <chamber>` */
    private fun handleInfo(sender: CommandSender, args: Array<out String>) {
        if (args.size < 3) {
            sender.sendMessage(plugin.getMessage("usage-loot-info"))
            return
        }

        val chamberName = args[2]

        plugin.launchAsync {
            val chamber = plugin.chamberManager.getChamber(chamberName)
            if (chamber == null) {
                sender.sendMessage(plugin.getMessage("chamber-not-found", "chamber" to chamberName))
                return@launchAsync
            }

            sender.sendMessage(plugin.getMessage("loot-info-header", "chamber" to chamberName))
            val normalTable = chamber.normalLootTable ?: plugin.getMessage("loot-info-default")
            val ominousTable = chamber.ominousLootTable ?: plugin.getMessage("loot-info-default")
            sender.sendMessage(plugin.getMessage("loot-info-normal", "table" to normalTable))
            sender.sendMessage(plugin.getMessage("loot-info-ominous", "table" to ominousTable))
        }
    }

    /** `/tcp loot list` */
    private fun handleList(sender: CommandSender) {
        val tables = plugin.lootManager.getLootTableNames()
        if (tables.isEmpty()) {
            sender.sendMessage(plugin.getMessage("error-no-loot-tables"))
            return
        }

        sender.sendMessage(plugin.getMessage("loot-list-header"))
        tables.sorted().forEach { tableName ->
            sender.sendMessage(plugin.getMessage("loot-list-item", "table" to tableName))
        }
    }
}
