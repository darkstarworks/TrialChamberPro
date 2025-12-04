package io.github.darkstarworks.trialChamberPro.commands

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.utils.WEVarStore
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/**
 * Tab completion for /tcp commands.
 */
class TCPTabCompleter(private val plugin: TrialChamberPro) : TabCompleter {

    private val subcommands = listOf(
        "help", "reload", "generate", "paste", "scan", "setexit",
        "snapshot", "list", "info", "delete", "vault", "key",
        "stats", "leaderboard", "lb", "top", "reset", "menu", "loot"
    )

    private val snapshotActions = listOf("create", "restore")
    private val statTypes = listOf("chambers", "normal", "ominous", "mobs", "time")
    private val lootActions = listOf("set", "clear", "info", "list")
    private val vaultTypes = listOf("normal", "ominous")

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        return when (args.size) {
            1 -> {
                // First argument - subcommand
                subcommands.filter { it.startsWith(args[0].lowercase()) }
            }
            2 -> {
                // Second argument - depends on subcommand
                when (args[0].lowercase()) {
                    "snapshot" -> snapshotActions.filter { it.startsWith(args[1].lowercase()) }
                    "generate" -> listOf("value", "coords", "wand", "blocks").filter { it.startsWith(args[1].lowercase()) }
                    "paste" -> {
                        // Schematic names
                        try {
                            plugin.schematicManager.listSchematics().filter { it.startsWith(args[1].lowercase()) }
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                    "scan", "setexit", "info", "delete", "reset" -> {
                        // Chamber names
                        getChamberNames().filter { it.startsWith(args[1].lowercase()) }
                    }
                    "stats" -> {
                        // Player names for stats
                        plugin.server.onlinePlayers.map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
                    }
                    "leaderboard", "lb", "top" -> {
                        // Stat types for leaderboard
                        statTypes.filter { it.startsWith(args[1].lowercase()) }
                    }
                    "loot" -> {
                        // Loot subcommands
                        lootActions.filter { it.startsWith(args[1].lowercase()) }
                    }
                    else -> emptyList()
                }
            }
            3 -> {
                // Third argument
                when (args[0].lowercase()) {
                    "snapshot" -> {
                        // Chamber names for snapshot command
                        getChamberNames().filter { it.startsWith(args[2].lowercase()) }
                    }
                    "generate" -> {
                        when (args[1].lowercase()) {
                            "value" -> {
                                val ops = listOf("save", "list", "delete")
                                val names = try { WEVarStore.list(plugin.dataFolder).map { it.first } } catch (_: Exception) { emptyList() }
                                (ops + names).filter { it.startsWith(args[2].lowercase()) }
                            }
                            else -> emptyList()
                        }
                    }
                    "loot" -> {
                        // Chamber names for set/clear/info
                        when (args[1].lowercase()) {
                            "set", "clear", "info" -> getChamberNames().filter { it.startsWith(args[2].lowercase()) }
                            else -> emptyList()
                        }
                    }
                    else -> emptyList()
                }
            }
            4 -> {
                when (args[0].lowercase()) {
                    "generate" -> {
                        when (args[1].lowercase()) {
                            "value" -> {
                                when (args[2].lowercase()) {
                                    "delete" -> {
                                        try { WEVarStore.list(plugin.dataFolder).map { it.first } } catch (_: Exception) { emptyList() }
                                            .filter { it.startsWith(args[3].lowercase()) }
                                    }
                                    else -> emptyList()
                                }
                            }
                            else -> emptyList()
                        }
                    }
                    "loot" -> {
                        when (args[1].lowercase()) {
                            "set" -> vaultTypes.filter { it.startsWith(args[3].lowercase()) }
                            "clear" -> (vaultTypes + "all").filter { it.startsWith(args[3].lowercase()) }
                            else -> emptyList()
                        }
                    }
                    else -> emptyList()
                }
            }
            5 -> {
                when (args[0].lowercase()) {
                    "loot" -> {
                        when (args[1].lowercase()) {
                            "set" -> {
                                // Loot table names
                                getLootTableNames().filter { it.startsWith(args[4].lowercase()) }
                            }
                            else -> emptyList()
                        }
                    }
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }
    }

    private fun getChamberNames(): List<String> {
        return try {
            val names = plugin.chamberManager.getCachedChamberNames()
            names.ifEmpty { emptyList() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getLootTableNames(): List<String> {
        return try {
            plugin.lootManager.getLootTableNames().toList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
