package io.github.darkstarworks.trialChamberPro.commands.handlers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.utils.RegionUtil
import io.github.darkstarworks.trialChamberPro.utils.WEVarStore
import io.github.darkstarworks.trialChamberPro.utils.WorldEditUtil
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import kotlin.math.ceil

/**
 * `/tcp generate <mode> ...` — handles the four generation modes (`value`,
 * `coords`, `wand`, `blocks`) and the `value save|list|delete` sub-ops.
 *
 * Extracted from `TCPCommand.handleGenerate` in v1.3.0 Phase 3 along with all
 * generation-only helpers (region validation, coord parsing, dimension
 * computation, player-relative placement) that were private to `TCPCommand`
 * and used nowhere else.
 *
 * Minimum chamber dimensions ([MIN_XZ] / [MIN_Y]) are duplicated here rather
 * than imported from `TCPCommand` because they're a property of the generation
 * flow, not the dispatcher — and `TCPCommand` no longer references them after
 * extraction.
 */
class GenerateCommand(private val plugin: TrialChamberPro) : SubcommandHandler {

    companion object {
        private const val MIN_XZ = 31
        private const val MIN_Y = 15
    }

    private data class ParsedCoords(
        val world: org.bukkit.World,
        val p1: Triple<Int, Int, Int>,
        val p2: Triple<Int, Int, Int>,
        val name: String
    )

    private data class QuadrupleInt(val a: Int, val b: Int, val c: Int, val d: Int)

    override fun execute(sender: CommandSender, args: Array<out String>) {
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
                    createChamberAsync(sender, chamberName, loc1, loc2)
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
                        val (loc1, loc2) = selection
                        val box = RegionUtil.createBoundingBox(loc1, loc2)!!
                        if (!validateRegionAndNotify(sender, box)) return
                        createChamberAsync(sender, chamberName, loc1, loc2)
                    } else {
                        sender.sendMessage(plugin.getMessage("wevar-not-found", "name" to varName))
                    }
                }
            }
            "coords" -> {
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
                createChamberAsync(sender, name, loc1, loc2)
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
                createChamberAsync(sender, name, loc1, loc2)
            }
            "blocks" -> {
                if (sender !is Player) {
                    sender.sendMessage(plugin.getMessage("player-only"))
                    return
                }
                val amount = args.getOrNull(2)?.toIntOrNull()
                if (amount == null || amount <= 0) {
                    sender.sendMessage(plugin.getMessage("usage-generate-blocks"))
                    return
                }
                val name = args.getOrNull(3) ?: "chamber-${System.currentTimeMillis()}"
                val roundAllowanceArg = args.getOrNull(4)?.toIntOrNull()
                val defaultAllowance = plugin.config.getInt("generation.blocks.rounding-allowance", 1000).coerceAtLeast(0)
                val allowance = roundAllowanceArg ?: defaultAllowance

                val (dx, dy, dz, volume) = computeDimsForBlocks(amount, allowance)
                val (loc1, loc2) = placeBoxFromPlayer(sender, dx, dy, dz)
                val box = RegionUtil.createBoundingBox(loc1, loc2)!!
                if (!validateRegionAndNotify(sender, box)) return

                val overhead = volume - amount
                if (overhead > allowance) {
                    sender.sendMessage(plugin.getMessage("generate-rounding-note",
                        "requested" to amount, "actual" to volume, "overhead" to overhead))
                } else {
                    sender.sendMessage(plugin.getMessage("generate-rounding-info",
                        "volume" to volume, "overhead" to overhead))
                }
                createChamberAsync(sender, name, loc1, loc2)
            }
            else -> sender.sendMessage(plugin.getMessage("usage-generate"))
        }
    }

    /**
     * Shared async chamber-creation flow. Pulled out of the four mode branches
     * since each had near-identical "check duplicate name → create → optional
     * scan → optional snapshot" sequences.
     */
    private fun createChamberAsync(sender: CommandSender, name: String, loc1: Location, loc2: Location) {
        plugin.launchAsync {
            val existing = plugin.chamberManager.getChamber(name)
            if (existing != null) {
                sender.sendMessage(plugin.getMessage("generation-cancelled-name-in-use", "name" to name))
                return@launchAsync
            }
            val chamber = plugin.chamberManager.createChamber(name, loc1, loc2)
            if (chamber == null) {
                sender.sendMessage(plugin.getMessage("error-chamber-creation-failed"))
                return@launchAsync
            }
            sender.sendMessage(plugin.getMessage("chamber-created", "chamber" to name))
            if (sender is Player) {
                io.github.darkstarworks.trialChamberPro.utils.UndoTracker.setLast(sender.uniqueId, chamber.name)
            }
            if (plugin.config.getBoolean("global.auto-scan-on-register", true)) {
                sender.sendMessage(plugin.getMessage("scan-started", "chamber" to name))
                val (vaults, spawners, pots) = plugin.chamberManager.scanChamber(chamber)
                sender.sendMessage(plugin.getMessage("scan-complete",
                    "vaults" to vaults, "spawners" to spawners, "pots" to pots))
            }
            if (plugin.config.getBoolean("global.auto-snapshot-on-register", true)) {
                sender.sendMessage(plugin.getMessage("snapshot-creating", "chamber" to name))
                val file = plugin.snapshotManager.createSnapshot(chamber)
                plugin.chamberManager.setSnapshotFile(name, file.absolutePath)
                sender.sendMessage(plugin.getMessage("snapshot-created",
                    "chamber" to name,
                    "blocks" to chamber.getVolume(),
                    "size" to io.github.darkstarworks.trialChamberPro.utils.CompressionUtil.formatSize(file.length())))
            }
        }
    }

    private fun validateRegionAndNotify(sender: CommandSender, box: RegionUtil.BoundingBox): Boolean {
        val dx = box.maxX - box.minX + 1
        val dy = box.maxY - box.minY + 1
        val dz = box.maxZ - box.minZ + 1
        val volume = dx * dy * dz

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

    private fun parseCoordsArgs(sender: CommandSender, args: Array<out String>): ParsedCoords? {
        // world-first variant
        val worldCandidate = args.getOrNull(2)
        val worldFromFirst = org.bukkit.Bukkit.getWorld(worldCandidate ?: "")
        if (worldFromFirst != null) {
            val t1s = args.getOrNull(3) ?: return null
            val t2s = args.getOrNull(4) ?: return null
            val name = args.getOrNull(5) ?: return null
            val t1 = CoordinateParser.parseTriple(t1s) ?: return null
            val t2 = CoordinateParser.parseTriple(t2s) ?: return null
            return ParsedCoords(worldFromFirst, t1, t2, name)
        }

        // two-triple variant
        val t1s = args.getOrNull(2)
        val t2s = args.getOrNull(3)
        if (CoordinateParser.isTripleString(t1s) && CoordinateParser.isTripleString(t2s)) {
            val t1 = CoordinateParser.parseTriple(t1s!!)
            val t2 = CoordinateParser.parseTriple(t2s!!)
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
            val pair = CoordinateParser.parseHyphenated(legacy)
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

    /** Approximate dimensions (dx, dy, dz, volume) for a desired block count, respecting minimums. */
    private fun computeDimsForBlocks(amount: Int, allowance: Int): QuadrupleInt {
        val baseMinVolume = MIN_XZ * MIN_Y * MIN_XZ
        if (amount <= baseMinVolume) {
            return QuadrupleInt(MIN_XZ, MIN_Y, MIN_XZ, baseMinVolume)
        }

        val requiredArea = ceil(amount.toDouble() / MIN_Y.toDouble()).toInt()
        val dx = maxOf(MIN_XZ, ceil(kotlin.math.sqrt(requiredArea.toDouble())).toInt())
        var dz = maxOf(MIN_XZ, ceil(requiredArea.toDouble() / dx.toDouble()).toInt())
        var dy = MIN_Y
        var volume = dx * dy * dz

        while (volume < amount) {
            dz++
            volume = dx * dy * dz
            if (volume - amount > allowance && dy < 30) {
                dy++
                volume = dx * dy * dz
            }
        }

        return QuadrupleInt(dx, dy, dz, volume)
    }

    private fun placeBoxFromPlayer(player: Player, dx: Int, dy: Int, dz: Int): Pair<Location, Location> {
        val world = player.world
        val origin = player.location
        val facing = player.facing
        val halfWidth = dz / 2

        var minX: Int; var maxX: Int; var minZ: Int; var maxZ: Int
        when (facing) {
            org.bukkit.block.BlockFace.NORTH -> {
                minZ = origin.blockZ - dx - 1; maxZ = origin.blockZ - 1
                minX = origin.blockX - halfWidth; maxX = minX + dz - 1
            }
            org.bukkit.block.BlockFace.SOUTH -> {
                minZ = origin.blockZ + 1; maxZ = origin.blockZ + dx
                minX = origin.blockX - halfWidth; maxX = minX + dz - 1
            }
            org.bukkit.block.BlockFace.EAST -> {
                minX = origin.blockX + 1; maxX = origin.blockX + dx
                minZ = origin.blockZ - halfWidth; maxZ = minZ + dz - 1
            }
            org.bukkit.block.BlockFace.WEST -> {
                minX = origin.blockX - dx - 1; maxX = origin.blockX - 1
                minZ = origin.blockZ - halfWidth; maxZ = minZ + dz - 1
            }
            else -> {
                minZ = origin.blockZ + 1; maxZ = origin.blockZ + dx
                minX = origin.blockX - halfWidth; maxX = minX + dz - 1
            }
        }

        val worldMinY = world.minHeight
        val worldMaxY = world.maxHeight - 1
        var minY = (origin.blockY - (dy / 2)).coerceAtLeast(worldMinY)
        var maxY = (minY + dy - 1).coerceAtMost(worldMaxY)
        if (maxY - minY + 1 < dy) {
            minY = (worldMaxY - dy + 1).coerceAtLeast(worldMinY)
            maxY = minY + dy - 1
        }

        val loc1 = Location(world, minX.toDouble(), minY.toDouble(), minZ.toDouble())
        val loc2 = Location(world, maxX.toDouble(), maxY.toDouble(), maxZ.toDouble())
        return Pair(loc1, loc2)
    }
}
