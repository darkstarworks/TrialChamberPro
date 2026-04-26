package io.github.darkstarworks.trialChamberPro.commands

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.commands.handlers.GenerateCommand
import io.github.darkstarworks.trialChamberPro.commands.handlers.GiveCommand
import io.github.darkstarworks.trialChamberPro.commands.handlers.LootCommand
import io.github.darkstarworks.trialChamberPro.commands.handlers.MobsCommand
import io.github.darkstarworks.trialChamberPro.utils.MessageUtil
import io.github.darkstarworks.trialChamberPro.utils.RegionUtil
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Main command dispatcher for `/tcp` subcommands. Large/self-contained handlers
 * (`generate`, `mobs`, the `loot` family) live in `commands/handlers/` as
 * dedicated [SubcommandHandler] classes. Smaller handlers remain inline as
 * private methods.
 */
class TCPCommand(private val plugin: TrialChamberPro) : CommandExecutor {

    private val generateHandler = GenerateCommand(plugin)
    private val lootHandler = LootCommand(plugin)
    private val mobsHandler = MobsCommand(plugin)
    private val giveHandler = GiveCommand(plugin)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        // Guard commands until plugin finished async initialization, but allow help
        val sub = args[0].lowercase()
        if (!plugin.isReady && sub !in setOf("help")) {
            sender.sendMessage(plugin.getMessageComponent("plugin-starting-up"))
            return true
        }

        when (sub) {
            "help" -> sendHelp(sender)
            "reload" -> handleReload(sender)
            "scan" -> handleScan(sender, args)
            "setexit" -> handleSetExit(sender, args)
            "snapshot" -> handleSnapshot(sender, args)
            "reset" -> handleReset(sender, args)
            "list" -> handleList(sender)
            "info" -> handleInfo(sender, args)
            "delete" -> handleDelete(sender, args)
            "vault" -> handleVault(sender, args)
            "key" -> handleKey(sender, args)
            "stats" -> handleStats(sender, args)
            "leaderboard", "lb", "top" -> handleLeaderboard(sender, args)
            "generate" -> generateHandler.execute(sender, args)
            "paste" -> handlePaste(sender, args)
            "menu" -> handleMenu(sender)
            "loot" -> lootHandler.execute(sender, args)
            "mobs" -> mobsHandler.execute(sender, args)
            "give" -> giveHandler.execute(sender, args)
            else -> sender.sendMessage(plugin.getMessageComponent("unknown-command"))
        }

        return true
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(plugin.getMessageComponent("help-header"))
        sender.sendMessage(plugin.getMessageComponent("help-generate"))
        sender.sendMessage(plugin.getMessageComponent("help-scan"))
        sender.sendMessage(plugin.getMessageComponent("help-setexit"))
        sender.sendMessage(plugin.getMessageComponent("help-snapshot"))
        sender.sendMessage(plugin.getMessageComponent("help-reset"))
        sender.sendMessage(plugin.getMessageComponent("help-list"))
        sender.sendMessage(plugin.getMessageComponent("help-info"))
        sender.sendMessage(plugin.getMessageComponent("help-delete"))
        sender.sendMessage(plugin.getMessageComponent("help-stats"))
        sender.sendMessage(plugin.getMessageComponent("help-leaderboard"))
        sender.sendMessage(plugin.getMessageComponent("help-key"))
        sender.sendMessage(plugin.getMessageComponent("help-vault"))
        sender.sendMessage(plugin.getMessageComponent("help-paste"))
        sender.sendMessage(plugin.getMessageComponent("help-menu"))
        sender.sendMessage(plugin.getMessageComponent("help-loot"))
        sender.sendMessage(plugin.getMessageComponent("help-give"))
        sender.sendMessage(plugin.getMessageComponent("help-reload"))
    }

    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission("tcp.admin.reload")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }

        plugin.reloadPluginConfig()
        plugin.chamberManager.clearCache()
        plugin.vaultManager.clearCache()
        sender.sendMessage(plugin.getMessageComponent("reload-success"))
    }



    private fun handleScan(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.scan")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(plugin.getMessageComponent("usage-scan"))
            return
        }

        val chamberName = args[1]

        plugin.launchAsync {
            val chamber = plugin.chamberManager.getChamber(chamberName)
            if (chamber == null) {
                sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
                return@launchAsync
            }

            sender.sendMessage(plugin.getMessageComponent("scan-started", "chamber" to chamberName))
            val (vaults, spawners, pots) = plugin.chamberManager.scanChamber(chamber)
            sender.sendMessage(plugin.getMessageComponent("scan-complete",
                "vaults" to vaults,
                "spawners" to spawners,
                "pots" to pots
            ))
        }
    }

    private fun handleSetExit(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.create")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }

        if (sender !is Player) {
            sender.sendMessage(plugin.getMessageComponent("player-only"))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(plugin.getMessageComponent("usage-setexit"))
            return
        }

        val chamberName = args[1]

        plugin.launchAsync {
            val success = plugin.chamberManager.setExitLocation(chamberName, sender.location)
            if (success) {
                sender.sendMessage(plugin.getMessageComponent("exit-set", "chamber" to chamberName))
            } else {
                sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
            }
        }
    }

    private fun handleSnapshot(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.snapshot")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }

        if (args.size < 3) {
            sender.sendMessage(plugin.getMessageComponent("usage-snapshot"))
            return
        }

        val action = args[1].lowercase()
        val chamberName = args[2]

        when (action) {
            "create" -> {
                plugin.launchAsync {
                    val chamber = plugin.chamberManager.getChamber(chamberName)
                    if (chamber == null) {
                        sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
                        return@launchAsync
                    }

                    sender.sendMessage(plugin.getMessageComponent("snapshot-creating", "chamber" to chamberName))
                    val file = plugin.snapshotManager.createSnapshot(chamber)
                    plugin.chamberManager.setSnapshotFile(chamberName, file.absolutePath)

                    sender.sendMessage(plugin.getMessageComponent("snapshot-created",
                        "chamber" to chamberName,
                        "blocks" to chamber.getVolume(),
                        "size" to io.github.darkstarworks.trialChamberPro.utils.CompressionUtil.formatSize(file.length())
                    ))
                }
            }
            "restore" -> {
                plugin.launchAsync {
                    val chamber = plugin.chamberManager.getChamber(chamberName)
                    if (chamber == null) {
                        sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
                        return@launchAsync
                    }

                    sender.sendMessage(plugin.getMessageComponent("snapshot-restoring", "chamber" to chamberName))

                    // Pass player for WorldEdit undo support if sender is a player
                    val initiatingPlayer = sender as? Player
                    val success = plugin.resetManager.resetChamber(chamber, initiatingPlayer)
                    if (success) {
                        sender.sendMessage(plugin.getMessageComponent("snapshot-restored"))
                    } else {
                        sender.sendMessage(plugin.getMessageComponent("snapshot-failed", "error" to "Check console for details"))
                    }
                }
            }
            else -> {
                sender.sendMessage(plugin.getMessageComponent("usage-snapshot"))
            }
        }
    }

    private fun handleList(sender: CommandSender) {
        if (!sender.hasPermission("tcp.admin")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }

        plugin.launchAsync {
            val chambers = plugin.chamberManager.getAllChambers()

            if (chambers.isEmpty()) {
                sender.sendMessage(plugin.getMessageComponent("chamber-list-empty"))
                return@launchAsync
            }

            sender.sendMessage(plugin.getMessageComponent("chamber-list-header"))
            chambers.forEach { chamber ->
                sender.sendMessage(plugin.getMessageComponent("chamber-list-item",
                    "chamber" to chamber.name,
                    "world" to chamber.world,
                    "volume" to chamber.getVolume()
                ))
            }
        }
    }

    private fun handleInfo(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }

        // If no chamber name provided, show plugin info
        if (args.size < 2) {
            showPluginInfo(sender)
            return
        }

        // Otherwise show chamber info
        val chamberName = args[1]

        plugin.launchAsync {
            val chamber = plugin.chamberManager.getChamber(chamberName)
            if (chamber == null) {
                sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
                return@launchAsync
            }

            val exitLoc = chamber.getExitLocation()
            val exitStr = if (exitLoc != null) {
                plugin.getMessage("info-exit-location-set",
                    "x" to exitLoc.blockX,
                    "y" to exitLoc.blockY,
                    "z" to exitLoc.blockZ
                )
            } else {
                plugin.getMessage("info-exit-location-not-set")
            }

            val lastResetStr = if (chamber.lastReset != null) {
                MessageUtil.formatRelativeTime(chamber.lastReset)
            } else {
                plugin.getMessage("time-never")
            }

            sender.sendMessage(plugin.getMessageComponent("info-header", "chamber" to chamber.name))
            sender.sendMessage(plugin.getMessageComponent("info-world", "world" to chamber.world))
            sender.sendMessage(plugin.getMessageComponent("info-bounds",
                "minX" to chamber.minX, "minY" to chamber.minY, "minZ" to chamber.minZ,
                "maxX" to chamber.maxX, "maxY" to chamber.maxY, "maxZ" to chamber.maxZ
            ))
            sender.sendMessage(plugin.getMessageComponent("info-volume", "volume" to chamber.getVolume()))
            sender.sendMessage(plugin.getMessageComponent("info-exit", "exit" to exitStr))
            sender.sendMessage(plugin.getMessageComponent("info-reset-interval",
                "interval" to MessageUtil.formatTimeSeconds(chamber.resetInterval)
            ))
            sender.sendMessage(plugin.getMessageComponent("info-last-reset", "time" to lastResetStr))

            val snapshotStatus = if (chamber.snapshotFile != null) {
                plugin.getMessage("info-snapshot-created")
            } else {
                plugin.getMessage("info-snapshot-not-created")
            }
            sender.sendMessage(plugin.getMessageComponent("info-snapshot", "status" to snapshotStatus))
        }
    }

    /**
     * Shows plugin information including version, authors, integrations, and status.
     */
    private fun showPluginInfo(sender: CommandSender) {
        val meta = plugin.pluginMeta
        val version = meta.version
        val authors = meta.authors.joinToString(", ")

        // Check integrations
        val worldEdit = plugin.server.pluginManager.getPlugin("WorldEdit") != null ||
                        plugin.server.pluginManager.getPlugin("FastAsyncWorldEdit") != null
        val worldGuard = plugin.server.pluginManager.getPlugin("WorldGuard") != null
        val placeholderApi = plugin.server.pluginManager.getPlugin("PlaceholderAPI") != null
        val vault = plugin.server.pluginManager.getPlugin("Vault") != null

        // Get chamber count
        val chamberCount = plugin.chamberManager.getCachedChambers().size

        // Database type
        val dbType = plugin.databaseManager.databaseType

        // Folia detection
        val isFolia = try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            true
        } catch (_: ClassNotFoundException) {
            false
        }

        sender.sendMessage(plugin.getMessageComponent("plugin-info-header"))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-version", "version" to version))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-authors", "authors" to authors))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-database", "type" to dbType))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-chambers", "count" to chamberCount))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-platform",
            "platform" to if (isFolia) "Folia" else "Paper/Spigot"
        ))

        // Integrations
        sender.sendMessage(plugin.getMessageComponent("plugin-info-integrations-header"))

        val weStatus = if (worldEdit) "§a✓" else "§c✗"
        val wgStatus = if (worldGuard) "§a✓" else "§c✗"
        val papiStatus = if (placeholderApi) "§a✓" else "§c✗"
        val vaultStatus = if (vault) "§a✓" else "§c✗"

        sender.sendMessage(plugin.getMessageComponent("plugin-info-integration-worldedit", "status" to weStatus))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-integration-worldguard", "status" to wgStatus))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-integration-papi", "status" to papiStatus))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-integration-vault", "status" to vaultStatus))

        // Config status
        sender.sendMessage(plugin.getMessageComponent("plugin-info-config-header"))
        val perPlayerLoot = if (plugin.config.getBoolean("vaults.per-player-loot", true)) "§a✓" else "§c✗"
        val spawnerWaves = if (plugin.config.getBoolean("spawner-waves.enabled", true)) "§a✓" else "§c✗"
        val spectatorMode = if (plugin.config.getBoolean("spectator-mode.enabled", true)) "§a✓" else "§c✗"
        val statistics = if (plugin.config.getBoolean("statistics.enabled", true)) "§a✓" else "§c✗"

        sender.sendMessage(plugin.getMessageComponent("plugin-info-config-per-player", "status" to perPlayerLoot))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-config-spawner-waves", "status" to spawnerWaves))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-config-spectator", "status" to spectatorMode))
        sender.sendMessage(plugin.getMessageComponent("plugin-info-config-statistics", "status" to statistics))
    }


    private fun handleDelete(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.create")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(plugin.getMessageComponent("usage-delete"))
            return
        }

        val chamberName = args[1]

        plugin.launchAsync {
            val success = plugin.chamberManager.deleteChamber(chamberName)
            if (success) {
                sender.sendMessage(plugin.getMessageComponent("chamber-deleted", "chamber" to chamberName))
            } else {
                sender.sendMessage(plugin.getMessageComponent("chamber-not-found", "chamber" to chamberName))
            }
        }
    }

    private fun handleVault(sender: CommandSender, args: Array<out String>) {
        handleVault(plugin, sender, args)
    }

    private fun handleKey(sender: CommandSender, args: Array<out String>) {
        handleKey(plugin, sender, args)
    }

    private fun handleReset(sender: CommandSender, args: Array<out String>) {
        handleReset(plugin, sender, args)
    }

    private fun handleMenu(sender: CommandSender) {
        if (sender !is Player) {
            sender.sendMessage(plugin.getMessageComponent("player-only"))
            return
        }
        if (!sender.hasPermission("tcp.admin.menu")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }
        if (!plugin.isReady) {
            sender.sendMessage(plugin.getMessageComponent("plugin-starting-up"))
            return
        }
        try {
            plugin.menuService.openFor(sender)
        } catch (e: Exception) {
            sender.sendMessage(plugin.getMessageComponent("error-menu-failed", "error" to (e.message ?: "Unknown error")))
            e.printStackTrace()
        }
    }

    private fun handleStats(sender: CommandSender, args: Array<out String>) {
        if (!plugin.config.getBoolean("statistics.enabled", true)) {
            sender.sendMessage(plugin.getMessageComponent("statistics-disabled"))
            return
        }

        plugin.launchAsync {
            if (args.size < 2) {
                // Show own stats
                if (sender !is Player) {
                    sender.sendMessage(plugin.getMessageComponent("player-only"))
                    return@launchAsync
                }

                val stats = plugin.statisticsManager.getStats(sender.uniqueId)
                sender.sendMessage(plugin.getMessageComponent("stats-header", "player" to sender.name))
                sender.sendMessage(plugin.getMessageComponent("stats-chambers", "count" to stats.chambersCompleted))
                sender.sendMessage(plugin.getMessageComponent("stats-normal-vaults", "count" to stats.normalVaultsOpened))
                sender.sendMessage(plugin.getMessageComponent("stats-ominous-vaults", "count" to stats.ominousVaultsOpened))
                sender.sendMessage(plugin.getMessageComponent("stats-mobs", "count" to stats.mobsKilled))
                sender.sendMessage(plugin.getMessageComponent("stats-deaths", "count" to stats.deaths))
                sender.sendMessage(plugin.getMessageComponent("stats-time",
                    "time" to plugin.statisticsManager.formatTime(stats.timeSpent)))
            } else {
                // Show another player's stats
                if (!sender.hasPermission("tcp.admin.stats")) {
                    sender.sendMessage(plugin.getMessageComponent("no-permission"))
                    return@launchAsync
                }

                val targetPlayer = plugin.server.getPlayer(args[1])
                if (targetPlayer == null) {
                    sender.sendMessage(plugin.getMessageComponent("player-not-found", "player" to args[1]))
                    return@launchAsync
                }

                val stats = plugin.statisticsManager.getStats(targetPlayer.uniqueId)
                sender.sendMessage(plugin.getMessageComponent("stats-header", "player" to targetPlayer.name))
                sender.sendMessage(plugin.getMessageComponent("stats-chambers", "count" to stats.chambersCompleted))
                sender.sendMessage(plugin.getMessageComponent("stats-normal-vaults", "count" to stats.normalVaultsOpened))
                sender.sendMessage(plugin.getMessageComponent("stats-ominous-vaults", "count" to stats.ominousVaultsOpened))
                sender.sendMessage(plugin.getMessageComponent("stats-mobs", "count" to stats.mobsKilled))
                sender.sendMessage(plugin.getMessageComponent("stats-deaths", "count" to stats.deaths))
                sender.sendMessage(plugin.getMessageComponent("stats-time",
                    "time" to plugin.statisticsManager.formatTime(stats.timeSpent)))
            }
        }
    }

    private fun handleLeaderboard(sender: CommandSender, args: Array<out String>) {
        if (!plugin.config.getBoolean("statistics.enabled", true)) {
            sender.sendMessage(plugin.getMessageComponent("statistics-disabled"))
            return
        }

        plugin.launchAsync {
            // Determine which stat to show
            val statType = if (args.size >= 2) args[1].lowercase() else "chambers"
            val limit = plugin.config.getInt("statistics.top-players-count", 10)

            val statName = when (statType) {
                "chambers", "completions" -> "chambers"
                "normal", "normalvaults" -> "normal_vaults"
                "ominous", "ominousvaults" -> "ominous_vaults"
                "mobs", "kills" -> "mobs"
                "time", "playtime" -> "time"
                else -> {
                    sender.sendMessage(plugin.getMessageComponent("invalid-stat-type"))
                    return@launchAsync
                }
            }

            val displayName = when (statName) {
                "chambers" -> "Chambers Completed"
                "normal_vaults" -> "Normal Vaults Opened"
                "ominous_vaults" -> "Ominous Vaults Opened"
                "mobs" -> "Mobs Killed"
                "time" -> "Time Spent"
                else -> "Unknown"
            }

            val leaderboard = plugin.statisticsManager.getLeaderboard(statName, limit)

            sender.sendMessage(plugin.getMessageComponent("leaderboard-header", "stat" to displayName))

            if (leaderboard.isEmpty()) {
                sender.sendMessage(plugin.getMessageComponent("leaderboard-empty"))
                return@launchAsync
            }

            leaderboard.forEachIndexed { index, (uuid, value) ->
                val playerName = plugin.server.getOfflinePlayer(uuid).name ?: "Unknown"
                val displayValue = if (statName == "time") {
                    plugin.statisticsManager.formatTime(value.toLong())
                } else {
                    value.toString()
                }

                sender.sendMessage(
                    plugin.getMessage("leaderboard-entry",
                        "rank" to (index + 1),
                        "player" to playerName,
                        "value" to displayValue
                    )
                )
            }
        }
    }

    private fun handlePaste(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.generate")) {
            sender.sendMessage(plugin.getMessageComponent("no-permission"))
            return
        }

        if (sender !is Player) {
            sender.sendMessage(plugin.getMessageComponent("player-only"))
            return
        }

        if (!plugin.schematicManager.isAvailable()) {
            sender.sendMessage(plugin.getMessageComponent("worldedit-not-available"))
            return
        }

        val availableList = plugin.schematicManager.listSchematics().joinToString(", ")
        if (args.size < 2) {
            sender.sendMessage(plugin.getMessageComponent("schematic-usage-hint"))
            if (availableList.isNotEmpty()) {
                sender.sendMessage(plugin.getMessageComponent("schematic-usage", "list" to availableList))
            } else {
                sender.sendMessage(plugin.getMessageComponent("schematic-no-schematics"))
            }
            return
        }

        val schematicName = args[1]

        // Validate schematic exists
        if (!plugin.schematicManager.schematicExists(schematicName)) {
            sender.sendMessage(plugin.getMessageComponent("schematic-not-found", "name" to schematicName))
            if (availableList.isNotEmpty()) {
                sender.sendMessage(plugin.getMessageComponent("schematic-usage", "list" to availableList))
            }
            return
        }

        // Determine paste location
        val location = if (args.size >= 5) {
            // Coordinates provided
            val x = args[2].toIntOrNull()
            val y = args[3].toIntOrNull()
            val z = args[4].toIntOrNull()

            if (x == null || y == null || z == null) {
                sender.sendMessage(plugin.getMessageComponent("error-invalid-coordinates"))
                return
            }

            Location(sender.world, x.toDouble(), y.toDouble(), z.toDouble())
        } else {
            // Use player location
            sender.location
        }

        sender.sendMessage(plugin.getMessageComponent("paste-loading"))

        // Load schematic and calculate actual paste bounds
        plugin.launchAsync {
            val bounds = plugin.schematicManager.getSchematicBounds(schematicName, location)
            if (bounds == null) {
                sender.sendMessage(plugin.getMessageComponent("paste-failed"))
                return@launchAsync
            }

            val (min, max) = bounds
            
            // Calculate dimensions for display
            val width = (max.blockX - min.blockX + 1)
            val height = (max.blockY - min.blockY + 1)
            val length = (max.blockZ - min.blockZ + 1)

            // Create pending paste request
            plugin.pasteConfirmationManager.createPending(sender, schematicName, location)

            // Show visualization with correct bounds
            plugin.particleVisualizer.showBox(sender, min, max)

            // Notify player
            val remaining = plugin.pasteConfirmationManager.getPending(sender)?.getRemainingSeconds() ?: 300
            sender.sendMessage(plugin.getMessageComponent("paste-preview-shown",
                "schematic" to schematicName,
                "x" to location.blockX,
                "y" to location.blockY,
                "z" to location.blockZ,
                "width" to width,
                "height" to height,
                "length" to length,
                "time" to remaining
            ))
            sender.sendMessage(plugin.getMessageComponent("paste-confirm-hint", "time" to remaining))
        }
    }

}