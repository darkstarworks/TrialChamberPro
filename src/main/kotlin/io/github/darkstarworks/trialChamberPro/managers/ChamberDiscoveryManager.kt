package io.github.darkstarworks.trialChamberPro.managers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Auto-discovery of naturally-generated Trial Chambers.
 *
 * Flow: a seed (VAULT or TRIAL_SPAWNER location) is registered via [seed].
 * After a short delay (to allow adjacent chunks to load), a BFS flood-fill
 * over chamber structural blocks computes a bounding box. If all touched
 * chunks are loaded and the AABB passes sanity checks, the chamber is
 * registered via [ChamberManager.createChamber] using an auto-generated name.
 *
 * If the BFS frontier wants to enter unloaded chunks, the seed is re-queued
 * for retry a limited number of times before giving up.
 */
class ChamberDiscoveryManager(private val plugin: TrialChamberPro) {

    private data class PendingSeed(
        val world: World,
        val blockX: Int,
        val blockY: Int,
        val blockZ: Int,
        val attemptsRemaining: Int
    ) {
        fun toLocation(): Location = Location(world, blockX + 0.5, blockY + 0.5, blockZ + 0.5)
    }

    // Region-bucket -> expiry epoch millis. Debounces further seeds from the same area.
    private val recentlyProcessed = ConcurrentHashMap<String, Long>()

    // Region-buckets currently being scanned, prevents duplicate concurrent BFS.
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /**
     * Register a seed block location to kick off discovery.
     * Safe to call from any thread.
     */
    fun seed(world: World, x: Int, y: Int, z: Int) {
        if (!plugin.config.getBoolean("discovery.enabled", false)) return
        if (world.environment != World.Environment.NORMAL) return

        val key = regionKey(world, x, z)
        val now = System.currentTimeMillis()
        val expiry = recentlyProcessed[key]
        if (expiry != null && now < expiry) return
        if (!inFlight.add(key)) return

        val maxAttempts = 6
        val loc = Location(world, x + 0.5, y + 0.5, z + 0.5)
        plugin.scheduler.runAtLocationLater(loc, Runnable {
            attemptDiscovery(PendingSeed(world, x, y, z, maxAttempts), key)
        }, 100L) // initial 5-second delay so nearby chunks settle
    }

    private fun attemptDiscovery(seed: PendingSeed, key: String) {
        // Runs on region thread owning the seed location.
        try {
            val result = try {
                bfsCompute(seed.world, seed.blockX, seed.blockY, seed.blockZ)
            } catch (e: Exception) {
                plugin.logger.warning("[Discovery] BFS failed at ${seed.blockX},${seed.blockY},${seed.blockZ}: ${e.message}")
                null
            }

            if (result == null) {
                finalizeFailed(key, "BFS error")
                return
            }

            if (result.hitUnloadedChunks && seed.attemptsRemaining > 1) {
                // Defer retry - adjacent chunks may still be loading
                val retryDelay = plugin.config.getLong("discovery.pending-retry-seconds", 30L).coerceAtLeast(5L)
                plugin.scheduler.runAtLocationLater(seed.toLocation(), Runnable {
                    attemptDiscovery(seed.copy(attemptsRemaining = seed.attemptsRemaining - 1), key)
                }, retryDelay * 20L)
                return
            }

            if (!validateResult(result)) {
                finalizeFailed(key, "AABB failed validation (vaults=${result.vaultCount}, spawners=${result.spawnerCount}, size=${result.sizeX}x${result.sizeY}x${result.sizeZ}, centerY=${result.centerY})")
                return
            }

            registerChamber(seed.world, result, key)
        } catch (e: Exception) {
            plugin.logger.severe("[Discovery] Unexpected error during discovery: ${e.message}")
            e.printStackTrace()
            finalizeFailed(key, "exception")
        }
    }

    private fun registerChamber(world: World, result: BfsResult, key: String) {
        val worldName = world.name
        val name = "auto_${worldName}_${result.centerX}_${result.centerZ}"

        // Skip if a chamber already registered covering this AABB center (e.g. manual /tcp generate)
        val centerLoc = Location(world, result.centerX.toDouble(), result.centerY.toDouble(), result.centerZ.toDouble())
        if (plugin.chamberManager.getCachedChamberAt(centerLoc) != null) {
            finalizeFailed(key, "location already covered by existing chamber")
            return
        }

        plugin.launchAsync {
            try {
                val corner1 = Location(world, result.minX.toDouble(), result.minY.toDouble(), result.minZ.toDouble())
                val corner2 = Location(world, result.maxX.toDouble(), result.maxY.toDouble(), result.maxZ.toDouble())
                val chamber = plugin.chamberManager.createChamber(name, corner1, corner2)
                if (chamber == null) {
                    finalizeFailed(key, "createChamber returned null (duplicate name or DB error)")
                    return@launchAsync
                }

                plugin.logger.info("[Discovery] Registered chamber '$name' (${result.sizeX}x${result.sizeY}x${result.sizeZ}, ${result.vaultCount} vaults, ${result.spawnerCount} spawners)")

                if (plugin.config.getBoolean("discovery.notify-ops", true)) {
                    val msg = plugin.getMessage(
                        "discovery-registered",
                        "name" to name,
                        "vaults" to result.vaultCount,
                        "spawners" to result.spawnerCount
                    )
                    plugin.scheduler.runTask(Runnable {
                        plugin.server.onlinePlayers
                            .filter { it.hasPermission("tcp.discovery.notify") }
                            .forEach { it.sendMessage(msg) }
                    })
                }

                // createChamber does not auto-scan; command flows call scanChamber themselves.
                plugin.chamberManager.scanChamber(chamber)

                if (plugin.config.getBoolean("discovery.auto-snapshot", false)) {
                    try {
                        plugin.snapshotManager.createSnapshot(chamber)
                    } catch (e: Exception) {
                        plugin.logger.warning("[Discovery] Auto-snapshot failed for '$name': ${e.message}")
                    }
                }

                markProcessed(key)
            } catch (e: Exception) {
                plugin.logger.severe("[Discovery] Failed to register chamber '$name': ${e.message}")
                e.printStackTrace()
                finalizeFailed(key, "registration exception")
            }
        }
    }

    private fun finalizeFailed(key: String, reason: String) {
        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[Discovery] Skipped region $key: $reason")
        }
        markProcessed(key)
    }

    private fun markProcessed(key: String) {
        val cooldownMs = plugin.config.getLong("discovery.cooldown-seconds", 300L) * 1000L
        recentlyProcessed[key] = System.currentTimeMillis() + cooldownMs
        inFlight.remove(key)
        // Trim stale entries occasionally to prevent unbounded growth
        if (recentlyProcessed.size > 512) {
            val now = System.currentTimeMillis()
            recentlyProcessed.entries.removeIf { it.value < now }
        }
    }

    private data class BfsResult(
        val minX: Int, val minY: Int, val minZ: Int,
        val maxX: Int, val maxY: Int, val maxZ: Int,
        val vaultCount: Int,
        val spawnerCount: Int,
        val structuralCount: Int,
        val hitUnloadedChunks: Boolean
    ) {
        val sizeX get() = maxX - minX + 1
        val sizeY get() = maxY - minY + 1
        val sizeZ get() = maxZ - minZ + 1
        val centerX get() = (minX + maxX) / 2
        val centerY get() = (minY + maxY) / 2
        val centerZ get() = (minZ + maxZ) / 2
    }

    /**
     * BFS flood-fill over connected structural blocks, capped by configured radii.
     * Must be called on the region thread owning the seed location.
     */
    private fun bfsCompute(world: World, sx: Int, sy: Int, sz: Int): BfsResult {
        val maxRadiusXZ = plugin.config.getInt("discovery.max-radius-xz", 60)
        val maxRadiusY = plugin.config.getInt("discovery.max-radius-y", 45)

        var minX = sx; var minY = sy; var minZ = sz
        var maxX = sx; var maxY = sy; var maxZ = sz
        var vaults = 0
        var spawners = 0
        var structural = 0
        var hitUnloaded = false

        val visited = HashSet<Long>(4096)
        val queue = ArrayDeque<IntArray>()
        queue.add(intArrayOf(sx, sy, sz))
        visited.add(packKey(sx, sy, sz))

        val dirs = arrayOf(
            intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0),
            intArrayOf(0, 1, 0), intArrayOf(0, -1, 0),
            intArrayOf(0, 0, 1), intArrayOf(0, 0, -1)
        )

        val hardCap = 50_000 // safety bound on BFS node count

        while (queue.isNotEmpty() && visited.size < hardCap) {
            val cur = queue.poll()
            val cx = cur[0]; val cy = cur[1]; val cz = cur[2]

            // Radius check from seed
            if (Math.abs(cx - sx) > maxRadiusXZ || Math.abs(cz - sz) > maxRadiusXZ || Math.abs(cy - sy) > maxRadiusY) continue

            // Chunk must be loaded to safely read the block
            val chunkX = cx shr 4
            val chunkZ = cz shr 4
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                hitUnloaded = true
                continue
            }

            val block = world.getBlockAt(cx, cy, cz)
            val type = block.type
            if (!isChamberStructuralOrFeature(type)) continue

            structural++
            when (type) {
                Material.VAULT -> vaults++
                Material.TRIAL_SPAWNER -> spawners++
                else -> {}
            }

            if (cx < minX) minX = cx
            if (cy < minY) minY = cy
            if (cz < minZ) minZ = cz
            if (cx > maxX) maxX = cx
            if (cy > maxY) maxY = cy
            if (cz > maxZ) maxZ = cz

            for (d in dirs) {
                val nx = cx + d[0]; val ny = cy + d[1]; val nz = cz + d[2]
                val k = packKey(nx, ny, nz)
                if (visited.add(k)) queue.add(intArrayOf(nx, ny, nz))
            }
        }

        return BfsResult(
            minX, minY, minZ, maxX, maxY, maxZ,
            vaults, spawners, structural, hitUnloaded
        )
    }

    private fun validateResult(r: BfsResult): Boolean {
        val minTotal = plugin.config.getInt("discovery.min-vaults-plus-spawners", 2)
        if (r.vaultCount + r.spawnerCount < minTotal) return false
        // Vanilla minimum chamber dimensions
        if (r.sizeX < 31 || r.sizeY < 15 || r.sizeZ < 31) return false
        val maxCenterY = plugin.config.getInt("discovery.max-center-y", 10)
        if (r.centerY > maxCenterY) return false
        val maxVolume = plugin.config.getLong("generation.max-volume", 750_000L)
        if (r.sizeX.toLong() * r.sizeY.toLong() * r.sizeZ.toLong() > maxVolume) return false
        return true
    }

    private fun packKey(x: Int, y: Int, z: Int): Long {
        // y is [-64, 320], x/z fit into 21 bits comfortably for a 60-radius search
        return (x.toLong() and 0x3FFFFF) or
                ((z.toLong() and 0x3FFFFF) shl 22) or
                ((y.toLong() and 0xFFFF) shl 44)
    }

    private fun regionKey(world: World, x: Int, z: Int): String {
        // ~128 block buckets (8x8 chunks), large enough to cover a whole chamber
        return "${world.name}:${x shr 7}:${z shr 7}"
    }

    companion object {
        fun isChamberStructuralOrFeature(m: Material): Boolean = when (m) {
            Material.VAULT,
            Material.TRIAL_SPAWNER,
            Material.HEAVY_CORE,
            // Tuff brick family (crafted, do not naturally spawn outside chambers)
            Material.TUFF_BRICKS,
            Material.TUFF_BRICK_SLAB,
            Material.TUFF_BRICK_STAIRS,
            Material.TUFF_BRICK_WALL,
            Material.CHISELED_TUFF,
            Material.CHISELED_TUFF_BRICKS,
            Material.POLISHED_TUFF,
            Material.POLISHED_TUFF_SLAB,
            Material.POLISHED_TUFF_STAIRS,
            Material.POLISHED_TUFF_WALL,
            // Copper feature blocks (crafted/decorative variants)
            Material.CHISELED_COPPER,
            Material.EXPOSED_CHISELED_COPPER,
            Material.WEATHERED_CHISELED_COPPER,
            Material.OXIDIZED_CHISELED_COPPER,
            Material.WAXED_CHISELED_COPPER,
            Material.WAXED_EXPOSED_CHISELED_COPPER,
            Material.WAXED_WEATHERED_CHISELED_COPPER,
            Material.WAXED_OXIDIZED_CHISELED_COPPER,
            Material.COPPER_GRATE,
            Material.EXPOSED_COPPER_GRATE,
            Material.WEATHERED_COPPER_GRATE,
            Material.OXIDIZED_COPPER_GRATE,
            Material.WAXED_COPPER_GRATE,
            Material.WAXED_EXPOSED_COPPER_GRATE,
            Material.WAXED_WEATHERED_COPPER_GRATE,
            Material.WAXED_OXIDIZED_COPPER_GRATE,
            Material.COPPER_BULB,
            Material.EXPOSED_COPPER_BULB,
            Material.WEATHERED_COPPER_BULB,
            Material.OXIDIZED_COPPER_BULB,
            Material.WAXED_COPPER_BULB,
            Material.WAXED_EXPOSED_COPPER_BULB,
            Material.WAXED_WEATHERED_COPPER_BULB,
            Material.WAXED_OXIDIZED_COPPER_BULB,
            Material.COPPER_DOOR,
            Material.EXPOSED_COPPER_DOOR,
            Material.WEATHERED_COPPER_DOOR,
            Material.OXIDIZED_COPPER_DOOR,
            Material.WAXED_COPPER_DOOR,
            Material.WAXED_EXPOSED_COPPER_DOOR,
            Material.WAXED_WEATHERED_COPPER_DOOR,
            Material.WAXED_OXIDIZED_COPPER_DOOR,
            Material.COPPER_TRAPDOOR,
            Material.EXPOSED_COPPER_TRAPDOOR,
            Material.WEATHERED_COPPER_TRAPDOOR,
            Material.OXIDIZED_COPPER_TRAPDOOR,
            Material.WAXED_COPPER_TRAPDOOR,
            Material.WAXED_EXPOSED_COPPER_TRAPDOOR,
            Material.WAXED_WEATHERED_COPPER_TRAPDOOR,
            Material.WAXED_OXIDIZED_COPPER_TRAPDOOR -> true
            else -> false
        }

        fun isSeedBlock(m: Material): Boolean = m == Material.VAULT || m == Material.TRIAL_SPAWNER
    }
}
