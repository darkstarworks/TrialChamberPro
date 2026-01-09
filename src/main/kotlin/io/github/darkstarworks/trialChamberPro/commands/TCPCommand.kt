package io.github.darkstarworks.trialChamberPro.commands

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.utils.MessageUtil
import io.github.darkstarworks.trialChamberPro.utils.RegionUtil
import io.github.darkstarworks.trialChamberPro.utils.WEVarStore
import io.github.darkstarworks.trialChamberPro.utils.WorldEditUtil
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import kotlin.math.ceil

/**
 * Main command handler for /tcp commands.
 */
class TCPCommand(private val plugin: TrialChamberPro) : CommandExecutor {

    // Minimum Trial Chamber dimensions (hardcoded baseline)
    private val MIN_XZ = 31
    private val MIN_Y = 15

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sendHelp(sender)
            return true
        }

        // Guard commands until plugin finished async initialization, but allow help
        val sub = args[0].lowercase()
        if (!plugin.isReady && sub !in setOf("help")) {
            sender.sendMessage(plugin.getMessage("plugin-starting-up"))
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
            "generate" -> handleGenerate(sender, args)
            "paste" -> handlePaste(sender, args)
            "menu" -> handleMenu(sender)
            "loot" -> handleLoot(sender, args)
            else -> sender.sendMessage(plugin.getMessage("unknown-command"))
        }

        return true
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(plugin.getMessage("help-header"))
        sender.sendMessage(plugin.getMessage("help-generate"))
        sender.sendMessage(plugin.getMessage("help-scan"))
        sender.sendMessage(plugin.getMessage("help-setexit"))
        sender.sendMessage(plugin.getMessage("help-snapshot"))
        sender.sendMessage(plugin.getMessage("help-reset"))
        sender.sendMessage(plugin.getMessage("help-list"))
        sender.sendMessage(plugin.getMessage("help-info"))
        sender.sendMessage(plugin.getMessage("help-delete"))
        sender.sendMessage(plugin.getMessage("help-stats"))
        sender.sendMessage(plugin.getMessage("help-leaderboard"))
        sender.sendMessage(plugin.getMessage("help-key"))
        sender.sendMessage(plugin.getMessage("help-vault"))
        sender.sendMessage(plugin.getMessage("help-paste"))
        sender.sendMessage(plugin.getMessage("help-menu"))
        sender.sendMessage(plugin.getMessage("help-loot"))
        sender.sendMessage(plugin.getMessage("help-reload"))
    }

    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission("tcp.admin.reload")) {
            sender.sendMessage(plugin.getMessage("no-permission"))
            return
        }

        plugin.reloadPluginConfig()
        plugin.chamberManager.clearCache()
        plugin.vaultManager.clearCache()
        sender.sendMessage(plugin.getMessage("reload-success"))
    }



    private fun handleScan(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.scan")) {
            sender.sendMessage(plugin.getMessage("no-permission"))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(plugin.getMessage("usage-scan"))
            return
        }

        val chamberName = args[1]

        plugin.launchAsync {
            val chamber = plugin.chamberManager.getChamber(chamberName)
            if (chamber == null) {
                sender.sendMessage(plugin.getMessage("chamber-not-found", "chamber" to chamberName))
                return@launchAsync
            }

            sender.sendMessage(plugin.getMessage("scan-started", "chamber" to chamberName))
            val (vaults, spawners, pots) = plugin.chamberManager.scanChamber(chamber)
            sender.sendMessage(plugin.getMessage("scan-complete",
                "vaults" to vaults,
                "spawners" to spawners,
                "pots" to pots
            ))
        }
    }

    private fun handleSetExit(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.create")) {
            sender.sendMessage(plugin.getMessage("no-permission"))
            return
        }

        if (sender !is Player) {
            sender.sendMessage(plugin.getMessage("player-only"))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(plugin.getMessage("usage-setexit"))
            return
        }

        val chamberName = args[1]

        plugin.launchAsync {
            val success = plugin.chamberManager.setExitLocation(chamberName, sender.location)
            if (success) {
                sender.sendMessage(plugin.getMessage("exit-set", "chamber" to chamberName))
            } else {
                sender.sendMessage(plugin.getMessage("chamber-not-found", "chamber" to chamberName))
            }
        }
    }

    private fun handleSnapshot(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.snapshot")) {
            sender.sendMessage(plugin.getMessage("no-permission"))
            return
        }

        if (args.size < 3) {
            sender.sendMessage(plugin.getMessage("usage-snapshot"))
            return
        }

        val action = args[1].lowercase()
        val chamberName = args[2]

        when (action) {
            "create" -> {
                plugin.launchAsync {
                    val chamber = plugin.chamberManager.getChamber(chamberName)
                    if (chamber == null) {
                        sender.sendMessage(plugin.getMessage("chamber-not-found", "chamber" to chamberName))
                        return@launchAsync
                    }

                    sender.sendMessage(plugin.getMessage("snapshot-creating", "chamber" to chamberName))
                    val file = plugin.snapshotManager.createSnapshot(chamber)
                    plugin.chamberManager.setSnapshotFile(chamberName, file.absolutePath)

                    sender.sendMessage(plugin.getMessage("snapshot-created",
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
                        sender.sendMessage(plugin.getMessage("chamber-not-found", "chamber" to chamberName))
                        return@launchAsync
                    }

                    sender.sendMessage(plugin.getMessage("snapshot-restoring", "chamber" to chamberName))

                    // Pass player for WorldEdit undo support if sender is a player
                    val initiatingPlayer = sender as? Player
                    val success = plugin.resetManager.resetChamber(chamber, initiatingPlayer)
                    if (success) {
                        sender.sendMessage(plugin.getMessage("snapshot-restored"))
                    } else {
                        sender.sendMessage(plugin.getMessage("snapshot-failed", "error" to "Check console for details"))
                    }
                }
            }
            else -> {
                sender.sendMessage(plugin.getMessage("usage-snapshot"))
            }
        }
    }

    private fun handleList(sender: CommandSender) {
        if (!sender.hasPermission("tcp.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission"))
            return
        }

        plugin.launchAsync {
            val chambers = plugin.chamberManager.getAllChambers()

            if (chambers.isEmpty()) {
                sender.sendMessage(plugin.getMessage("chamber-list-empty"))
                return@launchAsync
            }

            sender.sendMessage(plugin.getMessage("chamber-list-header"))
            chambers.forEach { chamber ->
                sender.sendMessage(plugin.getMessage("chamber-list-item",
                    "chamber" to chamber.name,
                    "world" to chamber.world,
                    "volume" to chamber.getVolume()
                ))
            }
        }
    }

    private fun handleInfo(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission"))
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
                sender.sendMessage(plugin.getMessage("chamber-not-found", "chamber" to chamberName))
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

            sender.sendMessage(plugin.getMessage("info-header", "chamber" to chamber.name))
            sender.sendMessage(plugin.getMessage("info-world", "world" to chamber.world))
            sender.sendMessage(plugin.getMessage("info-bounds",
                "minX" to chamber.minX, "minY" to chamber.minY, "minZ" to chamber.minZ,
                "maxX" to chamber.maxX, "maxY" to chamber.maxY, "maxZ" to chamber.maxZ
            ))
            sender.sendMessage(plugin.getMessage("info-volume", "volume" to chamber.getVolume()))
            sender.sendMessage(plugin.getMessage("info-exit", "exit" to exitStr))
            sender.sendMessage(plugin.getMessage("info-reset-interval",
                "interval" to MessageUtil.formatTimeSeconds(chamber.resetInterval)
            ))
            sender.sendMessage(plugin.getMessage("info-last-reset", "time" to lastResetStr))

            val snapshotStatus = if (chamber.snapshotFile != null) {
                plugin.getMessage("info-snapshot-created")
            } else {
                plugin.getMessage("info-snapshot-not-created")
            }
            sender.sendMessage(plugin.getMessage("info-snapshot", "status" to snapshotStatus))
        }
    }

    /**
     * Shows plugin information including version, authors, integrations, and status.
     */
    private fun showPluginInfo(sender: CommandSender) {
        val desc = plugin.description
        val version = desc.version
        val authors = desc.authors.joinToString(", ")

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

        sender.sendMessage(plugin.getMessage("plugin-info-header"))
        sender.sendMessage(plugin.getMessage("plugin-info-version", "version" to version))
        sender.sendMessage(plugin.getMessage("plugin-info-authors", "authors" to authors))
        sender.sendMessage(plugin.getMessage("plugin-info-database", "type" to dbType))
        sender.sendMessage(plugin.getMessage("plugin-info-chambers", "count" to chamberCount))
        sender.sendMessage(plugin.getMessage("plugin-info-platform",
            "platform" to if (isFolia) "Folia" else "Paper/Spigot"
        ))

        // Integrations
        sender.sendMessage(plugin.getMessage("plugin-info-integrations-header"))

        val weStatus = if (worldEdit) "§a✓" else "§c✗"
        val wgStatus = if (worldGuard) "§a✓" else "§c✗"
        val papiStatus = if (placeholderApi) "§a✓" else "§c✗"
        val vaultStatus = if (vault) "§a✓" else "§c✗"

        sender.sendMessage(plugin.getMessage("plugin-info-integration-worldedit", "status" to weStatus))
        sender.sendMessage(plugin.getMessage("plugin-info-integration-worldguard", "status" to wgStatus))
        sender.sendMessage(plugin.getMessage("plugin-info-integration-papi", "status" to papiStatus))
        sender.sendMessage(plugin.getMessage("plugin-info-integration-vault", "status" to vaultStatus))

        // Config status
        sender.sendMessage(plugin.getMessage("plugin-info-config-header"))
        val perPlayerLoot = if (plugin.config.getBoolean("vaults.per-player-loot", true)) "§a✓" else "§c✗"
        val spawnerWaves = if (plugin.config.getBoolean("spawner-waves.enabled", true)) "§a✓" else "§c✗"
        val spectatorMode = if (plugin.config.getBoolean("spectator-mode.enabled", true)) "§a✓" else "§c✗"
        val statistics = if (plugin.config.getBoolean("statistics.enabled", true)) "§a✓" else "§c✗"

        sender.sendMessage(plugin.getMessage("plugin-info-config-per-player", "status" to perPlayerLoot))
        sender.sendMessage(plugin.getMessage("plugin-info-config-spawner-waves", "status" to spawnerWaves))
        sender.sendMessage(plugin.getMessage("plugin-info-config-spectator", "status" to spectatorMode))
        sender.sendMessage(plugin.getMessage("plugin-info-config-statistics", "status" to statistics))
    }

    private fun handleGenerate(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.generate")) {
            sender.sendMessage(plugin.getMessage("no-permission"))
            return
        }

        if (args.size < 3) {
            sender.sendMessage(plugin.getMessage("usage-generate-help-value"))
            sender.sendMessage(plugin.getMessage("usage-generate-help-or-coords"))
            sender.sendMessage(plugin.getMessage("usage-generate-coords-legacy"))
            sender.sendMessage(plugin.getMessage("usage-generate-help-or-wand"))
            sender.sendMessage(plugin.getMessage("usage-generate-help-or-blocks"))
            return
        }

        val mode = args[1].lowercase()
        when (mode) {
            "value" -> {
                // Sub-ops: save/list/delete or generate from saved var
                when (args[2].lowercase()) {
                    "save" -> {
                        if (!WorldEditUtil.isAvailable()) {
                            sender.sendMessage(plugin.getMessage("worldedit-not-found"))
                            return
                        }
                        if (sender !is Player) {
                            sender.sendMessage(plugin.getMessage("player-only"))
                            return
                        }
                        val varName = args.getOrNull(3)
                        if (varName.isNullOrBlank()) {
                            sender.sendMessage(plugin.getMessage("usage-generate-value-save"))
                            return
                        }
                        val selection = WorldEditUtil.getSelection(sender)
                        if (selection == null) {
                            sender.sendMessage(plugin.getMessage("no-selection"))
                            return
                        }
                        val ok = WEVarStore.save(plugin.dataFolder, varName, selection.first, selection.second)
                        if (ok) {
                            sender.sendMessage(plugin.getMessage("wevar-saved", "name" to varName))
                        } else {
                            sender.sendMessage(plugin.getMessage("wevar-save-failed", "name" to varName))
                        }
                        return
                    }
                    "list" -> {
                        val list = WEVarStore.list(plugin.dataFolder)
                        if (list.isEmpty()) {
                            sender.sendMessage(plugin.getMessage("wevar-list-empty"))
                        } else {
                            sender.sendMessage(plugin.getMessage("wevar-list-header"))
                            list.forEach { (name, r) ->
                                sender.sendMessage(plugin.getMessage("wevar-list-item",
                                    "name" to name,
                                    "world" to r.world,
                                    "minX" to r.minX, "minY" to r.minY, "minZ" to r.minZ,
                                    "maxX" to r.maxX, "maxY" to r.maxY, "maxZ" to r.maxZ
                                ))
                            }
                        }
                        return
                    }
                    "delete" -> {
                        val varName = args.getOrNull(3)
                        if (varName.isNullOrBlank()) {
                            sender.sendMessage(plugin.getMessage("usage-generate-value-delete"))
                            return
                        }
                        val ok = WEVarStore.delete(plugin.dataFolder, varName)
                        if (ok) sender.sendMessage(plugin.getMessage("wevar-deleted", "name" to varName))
                        else sender.sendMessage(plugin.getMessage("wevar-not-found", "name" to varName))
                        return
                    }
                }

                // Generation from saved var name: /tcp generate value <varName> [chamberName]
                val varName = args.getOrNull(2)
                val chamberName = args.getOrNull(3) ?: varName
                if (varName.isNullOrBlank() || chamberName.isNullOrBlank()) {
                    sender.sendMessage(plugin.getMessage("usage-generate-value"))
                    sender.sendMessage(plugin.getMessage("usage-generate-value-extra"))
                    return
                }

                val saved = WEVarStore.get(plugin.dataFolder, varName)
                if (saved != null) {
                    val locs = WEVarStore.toLocations(saved)
                    if (locs == null) {
                        sender.sendMessage(plugin.getMessage("error-world-not-loaded", "world" to saved.world, "name" to varName))
                        return
                    }
                    val (loc1, loc2) = locs
                    val box = RegionUtil.createBoundingBox(loc1, loc2)!!
                    if (!validateRegionAndNotify(sender, box)) return

                    plugin.launchAsync {
                        val existing = plugin.chamberManager.getChamber(chamberName)
                        if (existing != null) {
                            sender.sendMessage(plugin.getMessage("generation-cancelled-name-in-use", "name" to chamberName))
                            return@launchAsync
                        }
                        val chamber = plugin.chamberManager.createChamber(chamberName, loc1, loc2)
                        if (chamber != null) {
                            sender.sendMessage(plugin.getMessage("chamber-created", "chamber" to chamberName))
                            if (sender is Player) io.github.darkstarworks.trialChamberPro.utils.UndoTracker.setLast(sender.uniqueId, chamber.name)
                            if (plugin.config.getBoolean("global.auto-scan-on-register", true)) {
                                sender.sendMessage(plugin.getMessage("scan-started", "chamber" to chamberName))
                                val (vaults, spawners, pots) = plugin.chamberManager.scanChamber(chamber)
                                sender.sendMessage(plugin.getMessage("scan-complete",
                                    "vaults" to vaults, "spawners" to spawners, "pots" to pots
                                ))
                            }
                            if (sender is Player) io.github.darkstarworks.trialChamberPro.utils.UndoTracker.setLast(sender.uniqueId, chamber.name)
                            if (plugin.config.getBoolean("global.auto-snapshot-on-register", true)) {
                                sender.sendMessage(plugin.getMessage("snapshot-creating", "chamber" to chamberName))
                                val file = plugin.snapshotManager.createSnapshot(chamber)
                                plugin.chamberManager.setSnapshotFile(chamberName, file.absolutePath)
                                sender.sendMessage(plugin.getMessage("snapshot-created",
                                    "chamber" to chamberName,
                                    "blocks" to chamber.getVolume(),
                                    "size" to io.github.darkstarworks.trialChamberPro.utils.CompressionUtil.formatSize(file.length())
                                ))
                            }
                        } else {
                            sender.sendMessage(plugin.getMessage("error-chamber-creation-failed"))
                        }
                    }
                } else {
                    // Fallback: if player with WE selection, treat varName as chamber name (legacy behavior)
                    if (sender is Player) {
                        if (!WorldEditUtil.isAvailable()) {
                            sender.sendMessage(plugin.getMessage("worldedit-not-found"))
                            return
                        }
                        val selection = WorldEditUtil.getSelection(sender)
                        if (selection == null) {
                            sender.sendMessage(plugin.getMessage("wevar-not-found", "name" to varName))
                            return
                        }
                        val loc1 = selection.first
                        val loc2 = selection.second
                        val box = RegionUtil.createBoundingBox(loc1, loc2)!!
                        if (!validateRegionAndNotify(sender, box)) return
                        plugin.launchAsync {
                            val existing = plugin.chamberManager.getChamber(chamberName)
                            if (existing != null) {
                                sender.sendMessage(plugin.getMessage("generation-cancelled-name-in-use", "name" to chamberName))
                                return@launchAsync
                            }
                            val chamber = plugin.chamberManager.createChamber(chamberName, loc1, loc2)
                            if (chamber != null) {
                                sender.sendMessage(plugin.getMessage("chamber-created", "chamber" to chamberName))
                                if (plugin.config.getBoolean("global.auto-scan-on-register", true)) {
                                    sender.sendMessage(plugin.getMessage("scan-started", "chamber" to chamberName))
                                    val (vaults, spawners, pots) = plugin.chamberManager.scanChamber(chamber)
                                    sender.sendMessage(plugin.getMessage("scan-complete",
                                        "vaults" to vaults, "spawners" to spawners, "pots" to pots
                                    ))
                                }
                                if (plugin.config.getBoolean("global.auto-snapshot-on-register", true)) {
                                    sender.sendMessage(plugin.getMessage("snapshot-creating", "chamber" to chamberName))
                                    val file = plugin.snapshotManager.createSnapshot(chamber)
                                    plugin.chamberManager.setSnapshotFile(chamberName, file.absolutePath)
                                    sender.sendMessage(plugin.getMessage("snapshot-created",
                                        "chamber" to chamberName,
                                        "blocks" to chamber.getVolume(),
                                        "size" to io.github.darkstarworks.trialChamberPro.utils.CompressionUtil.formatSize(file.length())
                                    ))
                                }
                            } else {
                                sender.sendMessage(plugin.getMessage("error-chamber-creation-failed"))
                            }
                        }
                    } else {
                        sender.sendMessage(plugin.getMessage("wevar-not-found", "name" to varName))
                    }
                }
            }
            "coords" -> {
                // Accept:
                // - coords <x1,y1,z1> <x2,y2,z2> [world] <name>
                // - coords <world> <x1,y1,z1> <x2,y2,z2> <name>
                val parsed = parseCoordsArgs(sender, args)
                if (parsed == null) {
                    sender.sendMessage(plugin.getMessage("usage-generate-coords"))
                    return
                }
                val (world, p1, p2, name) = parsed
                val loc1 = Location(world, p1.first.toDouble(), p1.second.toDouble(), p1.third.toDouble())
                val loc2 = Location(world, p2.first.toDouble(), p2.second.toDouble(), p2.third.toDouble())

                val box = RegionUtil.createBoundingBox(loc1, loc2)!!
                if (!validateRegionAndNotify(sender, box)) return

                // Create chamber asynchronously
                plugin.launchAsync {
                    val existing = plugin.chamberManager.getChamber(name)
                    if (existing != null) {
                        sender.sendMessage(plugin.getMessage("generation-cancelled-name-in-use", "name" to name))
                        return@launchAsync
                    }
                    val chamber = plugin.chamberManager.createChamber(name, loc1, loc2)
                    if (chamber != null) {
                        sender.sendMessage(plugin.getMessage("chamber-created", "chamber" to name))
                        if (sender is Player) io.github.darkstarworks.trialChamberPro.utils.UndoTracker.setLast(sender.uniqueId, chamber.name)

                        if (plugin.config.getBoolean("global.auto-scan-on-register", true)) {
                            sender.sendMessage(plugin.getMessage("scan-started", "chamber" to name))
                            val (vaults, spawners, pots) = plugin.chamberManager.scanChamber(chamber)
                            sender.sendMessage(plugin.getMessage("scan-complete",
                                "vaults" to vaults, "spawners" to spawners, "pots" to pots
                            ))
                        }
                        if (plugin.config.getBoolean("global.auto-snapshot-on-register", true)) {
                            sender.sendMessage(plugin.getMessage("snapshot-creating", "chamber" to name))
                            val file = plugin.snapshotManager.createSnapshot(chamber)
                            plugin.chamberManager.setSnapshotFile(name, file.absolutePath)
                            sender.sendMessage(plugin.getMessage("snapshot-created",
                                "chamber" to name,
                                "blocks" to chamber.getVolume(),
                                "size" to io.github.darkstarworks.trialChamberPro.utils.CompressionUtil.formatSize(file.length())
                            ))
                        }
                    } else {
                        sender.sendMessage(plugin.getMessage("error-chamber-creation-failed"))
                    }
                }
            }
            "wand" -> {
                if (sender !is Player) {
                    sender.sendMessage(plugin.getMessage("player-only"))
                    return
                }
                if (!WorldEditUtil.isAvailable()) {
                    sender.sendMessage(plugin.getMessage("worldedit-not-found"))
                    return
                }
                val name = args.getOrNull(2)
                if (name.isNullOrBlank()) {
                    sender.sendMessage(plugin.getMessage("usage-generate-wand"))
                    return
                }
                val selection = WorldEditUtil.getSelection(sender)
                if (selection == null) {
                    sender.sendMessage(plugin.getMessage("no-selection"))
                    return
                }
                val (loc1, loc2) = selection
                val box = RegionUtil.createBoundingBox(loc1, loc2)!!
                if (!validateRegionAndNotify(sender, box)) return

                plugin.launchAsync {
                    val existing = plugin.chamberManager.getChamber(name)
                    if (existing != null) {
                        sender.sendMessage(plugin.getMessage("generation-cancelled-name-in-use", "name" to name))
                        return@launchAsync
                    }
                    val chamber = plugin.chamberManager.createChamber(name, loc1, loc2)
                    if (chamber != null) {
                        sender.sendMessage(plugin.getMessage("chamber-created", "chamber" to name))
                        io.github.darkstarworks.trialChamberPro.utils.UndoTracker.setLast(sender.uniqueId, chamber.name)
                        if (plugin.config.getBoolean("global.auto-scan-on-register", true)) {
                            sender.sendMessage(plugin.getMessage("scan-started", "chamber" to name))
                            val (vaults, spawners, pots) = plugin.chamberManager.scanChamber(chamber)
                            sender.sendMessage(plugin.getMessage("scan-complete",
                                "vaults" to vaults, "spawners" to spawners, "pots" to pots
                            ))
                        }
                        if (plugin.config.getBoolean("global.auto-snapshot-on-register", true)) {
                            sender.sendMessage(plugin.getMessage("snapshot-creating", "chamber" to name))
                            val file = plugin.snapshotManager.createSnapshot(chamber)
                            plugin.chamberManager.setSnapshotFile(name, file.absolutePath)
                            sender.sendMessage(plugin.getMessage("snapshot-created",
                                "chamber" to name,
                                "blocks" to chamber.getVolume(),
                                "size" to io.github.darkstarworks.trialChamberPro.utils.CompressionUtil.formatSize(file.length())
                            ))
                        }
                    } else {
                        sender.sendMessage(plugin.getMessage("error-chamber-creation-failed"))
                    }
                }
            }
            "blocks" -> {
                if (sender !is Player) {
                    sender.sendMessage(plugin.getMessage("player-only"))
                    return
                }
                val amountStr = args.getOrNull(2)
                val amount = amountStr?.toIntOrNull()
                if (amount == null || amount <= 0) {
                    sender.sendMessage(plugin.getMessage("usage-generate-blocks"))
                    return
                }
                val name = args.getOrNull(3) ?: "chamber-${System.currentTimeMillis()}"
                val roundAllowanceArg = args.getOrNull(4)?.toIntOrNull()
                val defaultAllowance = plugin.config.getInt("generation.blocks.rounding-allowance", 1000).coerceAtLeast(0)
                val allowance = roundAllowanceArg ?: defaultAllowance

                sender.world
                val dims = computeDimsForBlocks(amount, allowance)
                val (dx, dy, dz, volume) = dims

                val placement = placeBoxFromPlayer(sender, dx, dy, dz)
                val (loc1, loc2) = placement
                val box = RegionUtil.createBoundingBox(loc1, loc2)!!

                // Validate against global max-volume
                if (!validateRegionAndNotify(sender, box)) return

                val overhead = volume - amount
                if (overhead > allowance) {
                    sender.sendMessage(plugin.getMessage("generate-rounding-note",
                        "requested" to amount, "actual" to volume, "overhead" to overhead))
                } else {
                    sender.sendMessage(plugin.getMessage("generate-rounding-info",
                        "volume" to volume, "overhead" to overhead))
                }

                plugin.launchAsync {
                    val chamber = plugin.chamberManager.createChamber(name, loc1, loc2)
                    if (chamber != null) {
                        sender.sendMessage(plugin.getMessage("chamber-created", "chamber" to name))
                        io.github.darkstarworks.trialChamberPro.utils.UndoTracker.setLast(sender.uniqueId, chamber.name)
                        if (plugin.config.getBoolean("global.auto-scan-on-register", true)) {
                            sender.sendMessage(plugin.getMessage("scan-started", "chamber" to name))
                            val (vaults, spawners, pots) = plugin.chamberManager.scanChamber(chamber)
                            sender.sendMessage(plugin.getMessage("scan-complete",
                                "vaults" to vaults, "spawners" to spawners, "pots" to pots
                            ))
                        }
                        if (plugin.config.getBoolean("global.auto-snapshot-on-register", true)) {
                            sender.sendMessage(plugin.getMessage("snapshot-creating", "chamber" to name))
                            val file = plugin.snapshotManager.createSnapshot(chamber)
                            plugin.chamberManager.setSnapshotFile(name, file.absolutePath)
                            sender.sendMessage(plugin.getMessage("snapshot-created",
                                "chamber" to name,
                                "blocks" to chamber.getVolume(),
                                "size" to io.github.darkstarworks.trialChamberPro.utils.CompressionUtil.formatSize(file.length())
                            ))
                        }
                    } else {
                        sender.sendMessage(plugin.getMessage("error-chamber-creation-failed"))
                    }
                }
            }
            else -> {
                sender.sendMessage(plugin.getMessage("usage-generate"))
            }
        }
    }

    private fun validateRegionAndNotify(sender: CommandSender, box: RegionUtil.BoundingBox): Boolean {
        val dx = box.maxX - box.minX + 1
        val dy = box.maxY - box.minY + 1
        val dz = box.maxZ - box.minZ + 1
        val volume = dx * dy * dz

        // Check minimum dimensions
        if (dx < MIN_XZ || dz < MIN_XZ || dy < MIN_Y) {
            sender.sendMessage(plugin.getMessage("error-region-too-small",
                "minXZ" to MIN_XZ, "minY" to MIN_Y, "dx" to dx, "dy" to dy, "dz" to dz))
            return false
        }

        val maxVolume = plugin.config.getInt("generation.max-volume", 500000).coerceAtLeast(1)
        if (volume > maxVolume) {
            sender.sendMessage(plugin.getMessage("error-region-too-large",
                "maxVolume" to maxVolume, "volume" to volume))
            return false
        }
        return true
    }

    // Legacy parser: parses a single string of the form x1,y1,z1-x2,y2,z2 with proper handling of negative values
    private fun parseCoords(input: String): Pair<Triple<Int, Int, Int>, Triple<Int, Int, Int>>? {
        val regex = Regex("^\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*-\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*$")
        val m = regex.matchEntire(input) ?: return null
        val x1 = m.groupValues[1].toInt()
        val y1 = m.groupValues[2].toInt()
        val z1 = m.groupValues[3].toInt()
        val x2 = m.groupValues[4].toInt()
        val y2 = m.groupValues[5].toInt()
        val z2 = m.groupValues[6].toInt()
        return Pair(Triple(x1, y1, z1), Triple(x2, y2, z2))
    }

    private data class ParsedCoords(val world: org.bukkit.World, val p1: Triple<Int, Int, Int>, val p2: Triple<Int, Int, Int>, val name: String)

    private fun parseCoordsArgs(sender: CommandSender, args: Array<out String>): ParsedCoords? {
        // args: [tcp, generate, coords, ...]
        fun parseTriple(str: String): Triple<Int, Int, Int>? {
            val parts = str.split(",")
            if (parts.size != 3) return null
            val x = parts[0].trim().toIntOrNull() ?: return null
            val y = parts[1].trim().toIntOrNull() ?: return null
            val z = parts[2].trim().toIntOrNull() ?: return null
            return Triple(x, y, z)
        }

        fun isTripleString(str: String?): Boolean {
            if (str.isNullOrBlank()) return false
            val parts = str.split(",")
            if (parts.size != 3) return false
            return parts.all { it.trim().toIntOrNull() != null }
        }

        // world-first variant
        val worldCandidate = args.getOrNull(2)
        val worldFromFirst = org.bukkit.Bukkit.getWorld(worldCandidate ?: "")
        if (worldFromFirst != null) {
            val t1s = args.getOrNull(3) ?: return null
            val t2s = args.getOrNull(4) ?: return null
            val name = args.getOrNull(5) ?: return null
            val t1 = parseTriple(t1s) ?: return null
            val t2 = parseTriple(t2s) ?: return null
            return ParsedCoords(worldFromFirst, t1, t2, name)
        }

        // two-triple variant
        val t1s = args.getOrNull(2)
        val t2s = args.getOrNull(3)
        if (isTripleString(t1s) && isTripleString(t2s)) {
            val t1 = parseTriple(t1s!!)
            val t2 = parseTriple(t2s!!)
            // optional world next
            val wc = args.getOrNull(4)
            val world = org.bukkit.Bukkit.getWorld(wc ?: "")
            return if (world != null) {
                val name = args.getOrNull(5) ?: return null
                ParsedCoords(world, t1!!, t2!!, name)
            } else {
                val worldFromSender = if (sender is Player) sender.world else null
                val name = args.getOrNull(4) ?: return null
                if (worldFromSender == null) return null
                ParsedCoords(worldFromSender, t1!!, t2!!, name)
            }
        }

        // legacy single-string with hyphen variant
        val legacy = args.getOrNull(2)
        if (!legacy.isNullOrBlank()) {
            val pair = parseCoords(legacy)
            if (pair != null) {
                val world = org.bukkit.Bukkit.getWorld(args.getOrNull(3) ?: "") ?: if (sender is Player) sender.world else null
                val name = if (org.bukkit.Bukkit.getWorld(args.getOrNull(3) ?: "") != null) args.getOrNull(4) else args.getOrNull(3)
                if (world != null && !name.isNullOrBlank()) {
                    return ParsedCoords(world, pair.first, pair.second, name)
                }
            }
        }
        return null
    }

    // Compute dimensions (dx, dy, dz, volume) to approximate a desired block count, respecting minimums
    private fun computeDimsForBlocks(amount: Int, allowance: Int): QuadrupleInt {
        val minXz = MIN_XZ
        val minY = MIN_Y
        val baseMinVolume = minXz * minY * minXz

        if (amount <= baseMinVolume) {
            return QuadrupleInt(minXz, minY, minXz, baseMinVolume)
        }

        val requiredArea = ceil(amount.toDouble() / minY.toDouble()).toInt()
        val dx = maxOf(minXz, ceil(kotlin.math.sqrt(requiredArea.toDouble())).toInt())
        var dz = maxOf(minXz, ceil(requiredArea.toDouble() / dx.toDouble()).toInt())
        var dy = minY
        var volume = dx * dy * dz

        // If still under (due to rounding issues), bump dz
        while (volume < amount) {
            dz++
            volume = dx * dy * dz
            if (volume - amount > allowance && dy < 30) { // slight flexibility on height if far off
                dy++
                volume = dx * dy * dz
            }
        }

        return QuadrupleInt(dx, dy, dz, volume)
    }

    private data class QuadrupleInt(val a: Int, val b: Int, val c: Int, val d: Int)

    private fun placeBoxFromPlayer(player: Player, dx: Int, dy: Int, dz: Int): Pair<Location, Location> {
        val world = player.world
        val origin = player.location
        val facing = player.facing

        val halfWidth = dz / 2

        var minX: Int
        var maxX: Int
        var minZ: Int
        var maxZ: Int

        when (facing) {
            org.bukkit.block.BlockFace.NORTH -> {
                minZ = origin.blockZ - dx - 1
                maxZ = origin.blockZ - 1
                minX = origin.blockX - halfWidth
                maxX = minX + dz - 1
            }
            org.bukkit.block.BlockFace.SOUTH -> {
                minZ = origin.blockZ + 1
                maxZ = origin.blockZ + dx
                minX = origin.blockX - halfWidth
                maxX = minX + dz - 1
            }
            org.bukkit.block.BlockFace.EAST -> {
                minX = origin.blockX + 1
                maxX = origin.blockX + dx
                minZ = origin.blockZ - halfWidth
                maxZ = minZ + dz - 1
            }
            org.bukkit.block.BlockFace.WEST -> {
                minX = origin.blockX - dx - 1
                maxX = origin.blockX - 1
                minZ = origin.blockZ - halfWidth
                maxZ = minZ + dz - 1
            }
            else -> {
                // Default to SOUTH if diagonal/other
                minZ = origin.blockZ + 1
                maxZ = origin.blockZ + dx
                minX = origin.blockX - halfWidth
                maxX = minX + dz - 1
            }
        }

        val worldMinY = world.minHeight
        val worldMaxY = world.maxHeight - 1
        var minY = (origin.blockY - (dy / 2)).coerceAtLeast(worldMinY)
        var maxY = (minY + dy - 1).coerceAtMost(worldMaxY)
        // Adjust bottom if clamped top reduced
        if (maxY - minY + 1 < dy) {
            minY = (worldMaxY - dy + 1).coerceAtLeast(worldMinY)
            maxY = minY + dy - 1
        }

        val loc1 = Location(world, minX.toDouble(), minY.toDouble(), minZ.toDouble())
        val loc2 = Location(world, maxX.toDouble(), maxY.toDouble(), maxZ.toDouble())
        return Pair(loc1, loc2)
    }

    private fun handleDelete(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.create")) {
            sender.sendMessage(plugin.getMessage("no-permission"))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(plugin.getMessage("usage-delete"))
            return
        }

        val chamberName = args[1]

        plugin.launchAsync {
            val success = plugin.chamberManager.deleteChamber(chamberName)
            if (success) {
                sender.sendMessage(plugin.getMessage("chamber-deleted", "chamber" to chamberName))
            } else {
                sender.sendMessage(plugin.getMessage("chamber-not-found", "chamber" to chamberName))
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
            sender.sendMessage(plugin.getMessage("player-only"))
            return
        }
        if (!sender.hasPermission("tcp.admin.menu")) {
            sender.sendMessage(plugin.getMessage("no-permission"))
            return
        }
        if (!plugin.isReady) {
            sender.sendMessage(plugin.getMessage("plugin-starting-up"))
            return
        }
        try {
            plugin.menuService.openFor(sender)
        } catch (e: Exception) {
            sender.sendMessage(plugin.getMessage("error-menu-failed", "error" to (e.message ?: "Unknown error")))
            e.printStackTrace()
        }
    }

    private fun handleStats(sender: CommandSender, args: Array<out String>) {
        if (!plugin.config.getBoolean("statistics.enabled", true)) {
            sender.sendMessage(plugin.getMessage("statistics-disabled"))
            return
        }

        plugin.launchAsync {
            if (args.size < 2) {
                // Show own stats
                if (sender !is Player) {
                    sender.sendMessage(plugin.getMessage("player-only"))
                    return@launchAsync
                }

                val stats = plugin.statisticsManager.getStats(sender.uniqueId)
                sender.sendMessage(plugin.getMessage("stats-header", "player" to sender.name))
                sender.sendMessage(plugin.getMessage("stats-chambers", "count" to stats.chambersCompleted))
                sender.sendMessage(plugin.getMessage("stats-normal-vaults", "count" to stats.normalVaultsOpened))
                sender.sendMessage(plugin.getMessage("stats-ominous-vaults", "count" to stats.ominousVaultsOpened))
                sender.sendMessage(plugin.getMessage("stats-mobs", "count" to stats.mobsKilled))
                sender.sendMessage(plugin.getMessage("stats-deaths", "count" to stats.deaths))
                sender.sendMessage(plugin.getMessage("stats-time",
                    "time" to plugin.statisticsManager.formatTime(stats.timeSpent)))
            } else {
                // Show another player's stats
                if (!sender.hasPermission("tcp.admin.stats")) {
                    sender.sendMessage(plugin.getMessage("no-permission"))
                    return@launchAsync
                }

                val targetPlayer = plugin.server.getPlayer(args[1])
                if (targetPlayer == null) {
                    sender.sendMessage(plugin.getMessage("player-not-found", "player" to args[1]))
                    return@launchAsync
                }

                val stats = plugin.statisticsManager.getStats(targetPlayer.uniqueId)
                sender.sendMessage(plugin.getMessage("stats-header", "player" to targetPlayer.name))
                sender.sendMessage(plugin.getMessage("stats-chambers", "count" to stats.chambersCompleted))
                sender.sendMessage(plugin.getMessage("stats-normal-vaults", "count" to stats.normalVaultsOpened))
                sender.sendMessage(plugin.getMessage("stats-ominous-vaults", "count" to stats.ominousVaultsOpened))
                sender.sendMessage(plugin.getMessage("stats-mobs", "count" to stats.mobsKilled))
                sender.sendMessage(plugin.getMessage("stats-deaths", "count" to stats.deaths))
                sender.sendMessage(plugin.getMessage("stats-time",
                    "time" to plugin.statisticsManager.formatTime(stats.timeSpent)))
            }
        }
    }

    private fun handleLeaderboard(sender: CommandSender, args: Array<out String>) {
        if (!plugin.config.getBoolean("statistics.enabled", true)) {
            sender.sendMessage(plugin.getMessage("statistics-disabled"))
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
                    sender.sendMessage(plugin.getMessage("invalid-stat-type"))
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

            sender.sendMessage(plugin.getMessage("leaderboard-header", "stat" to displayName))

            if (leaderboard.isEmpty()) {
                sender.sendMessage(plugin.getMessage("leaderboard-empty"))
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
            sender.sendMessage(plugin.getMessage("no-permission"))
            return
        }

        if (sender !is Player) {
            sender.sendMessage(plugin.getMessage("player-only"))
            return
        }

        if (!plugin.schematicManager.isAvailable()) {
            sender.sendMessage(plugin.getMessage("worldedit-not-available"))
            return
        }

        val availableList = plugin.schematicManager.listSchematics().joinToString(", ")
        if (args.size < 2) {
            sender.sendMessage(plugin.getMessage("schematic-usage-hint"))
            if (availableList.isNotEmpty()) {
                sender.sendMessage(plugin.getMessage("schematic-usage", "list" to availableList))
            } else {
                sender.sendMessage(plugin.getMessage("schematic-no-schematics"))
            }
            return
        }

        val schematicName = args[1]

        // Validate schematic exists
        if (!plugin.schematicManager.schematicExists(schematicName)) {
            sender.sendMessage(plugin.getMessage("schematic-not-found", "name" to schematicName))
            if (availableList.isNotEmpty()) {
                sender.sendMessage(plugin.getMessage("schematic-usage", "list" to availableList))
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
                sender.sendMessage(plugin.getMessage("error-invalid-coordinates"))
                return
            }

            Location(sender.world, x.toDouble(), y.toDouble(), z.toDouble())
        } else {
            // Use player location
            sender.location
        }

        sender.sendMessage(plugin.getMessage("paste-loading"))

        // Load schematic and calculate actual paste bounds
        plugin.launchAsync {
            val bounds = plugin.schematicManager.getSchematicBounds(schematicName, location)
            if (bounds == null) {
                sender.sendMessage(plugin.getMessage("paste-failed"))
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
            sender.sendMessage(plugin.getMessage("paste-preview-shown",
                "schematic" to schematicName,
                "x" to location.blockX,
                "y" to location.blockY,
                "z" to location.blockZ,
                "width" to width,
                "height" to height,
                "length" to length,
                "time" to remaining
            ))
            sender.sendMessage(plugin.getMessage("paste-confirm-hint", "time" to remaining))
        }
    }

    private fun handleLoot(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("tcp.admin.loot")) {
            sender.sendMessage(plugin.getMessage("no-permission"))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(plugin.getMessage("usage-loot"))
            return
        }

        when (args[1].lowercase()) {
            "set" -> handleLootSet(sender, args)
            "clear" -> handleLootClear(sender, args)
            "info" -> handleLootInfo(sender, args)
            "list" -> handleLootList(sender)
            else -> {
                sender.sendMessage(plugin.getMessage("usage-loot"))
            }
        }
    }

    private fun handleLootSet(sender: CommandSender, args: Array<out String>) {
        // /tcp loot set <chamber> <normal|ominous> <table>
        if (args.size < 5) {
            sender.sendMessage(plugin.getMessage("usage-loot-set"))
            return
        }

        val chamberName = args[2]
        val typeStr = args[3].lowercase()
        val tableName = args[4]

        val vaultType = when (typeStr) {
            "normal" -> io.github.darkstarworks.trialChamberPro.models.VaultType.NORMAL
            "ominous" -> io.github.darkstarworks.trialChamberPro.models.VaultType.OMINOUS
            else -> {
                sender.sendMessage(plugin.getMessage("error-invalid-type"))
                return
            }
        }

        // Validate loot table exists
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
                    "table" to tableName
                ))
            } else {
                sender.sendMessage(plugin.getMessage("error-loot-set-failed"))
            }
        }
    }

    private fun handleLootClear(sender: CommandSender, args: Array<out String>) {
        // /tcp loot clear <chamber> [normal|ominous|all]
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
                    plugin.chamberManager.setLootTable(chamberName, io.github.darkstarworks.trialChamberPro.models.VaultType.NORMAL, null)
                    sender.sendMessage(plugin.getMessage("loot-clear-success", "chamber" to chamberName))
                }
                "ominous" -> {
                    plugin.chamberManager.setLootTable(chamberName, io.github.darkstarworks.trialChamberPro.models.VaultType.OMINOUS, null)
                    sender.sendMessage(plugin.getMessage("loot-clear-success", "chamber" to chamberName))
                }
                "all" -> {
                    plugin.chamberManager.setLootTable(chamberName, io.github.darkstarworks.trialChamberPro.models.VaultType.NORMAL, null)
                    plugin.chamberManager.setLootTable(chamberName, io.github.darkstarworks.trialChamberPro.models.VaultType.OMINOUS, null)
                    sender.sendMessage(plugin.getMessage("loot-clear-success", "chamber" to chamberName))
                }
                else -> {
                    sender.sendMessage(plugin.getMessage("error-invalid-type-loot-clear"))
                }
            }
        }
    }

    private fun handleLootInfo(sender: CommandSender, args: Array<out String>) {
        // /tcp loot info <chamber>
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

    private fun handleLootList(sender: CommandSender) {
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