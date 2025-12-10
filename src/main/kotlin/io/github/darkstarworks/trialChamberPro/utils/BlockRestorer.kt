package io.github.darkstarworks.trialChamberPro.utils

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.BlockSnapshot
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Location

/**
 * Utility class for asynchronously restoring blocks from snapshots.
 * Uses batching and delays to prevent server lag during large restorations.
 *
 * Folia compatible: Uses location-based scheduling to ensure blocks are
 * modified on the correct region thread.
 */
class BlockRestorer(private val plugin: TrialChamberPro) {

    /**
     * Restores blocks from a snapshot asynchronously.
     * Groups blocks by chunk and processes them in batches to prevent lag.
     *
     * @param snapshot Map of locations to block snapshots
     * @param onProgress Optional callback for progress updates (processed, total)
     * @param onComplete Optional callback when restoration is complete
     */
    suspend fun restoreBlocks(
        snapshot: Map<Location, BlockSnapshot>,
        onProgress: ((Int, Int) -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        val blocksPerTick = plugin.config.getInt("global.blocks-per-tick", 500)
        val totalBlocks = snapshot.size

        plugin.logger.info("Starting block restoration: $totalBlocks blocks")

        // Group blocks by chunk for efficient processing
        val blocksByChunk = snapshot.entries.groupBy { it.key.chunk }

        var processedBlocks = 0

        // Process each chunk
        blocksByChunk.forEach { (chunk, blockEntries) ->
            // Ensure chunk is loaded
            ensureChunkLoaded(chunk)

            // Process blocks in batches
            blockEntries.chunked(blocksPerTick).forEach { batch ->
                // Get a representative location for this batch (for Folia region scheduling)
                val representativeLocation = batch.firstOrNull()?.key

                if (representativeLocation != null) {
                    // Schedule on the region thread that owns this location
                    plugin.scheduler.runAtLocation(representativeLocation, Runnable {
                        batch.forEach { (location, blockSnapshot) ->
                            try {
                                restoreBlock(location, blockSnapshot)
                                processedBlocks++
                            } catch (e: Exception) {
                                plugin.logger.warning(
                                    "Failed to restore block at ${location.blockX},${location.blockY},${location.blockZ}: ${e.message}"
                                )
                            }
                        }

                        // Call progress callback
                        onProgress?.invoke(processedBlocks, totalBlocks)
                    })
                }

                // Small delay between batches to prevent lag (1 tick = 50ms)
                delay(50)
            }
        }

        plugin.logger.info("Block restoration complete: $processedBlocks/$totalBlocks blocks restored")

        // Call completion callback on main/global thread
        plugin.scheduler.runTask(Runnable {
            onComplete?.invoke()
        })
    }

    /**
     * Restores a single block from a snapshot.
     * MUST be called from the region thread owning this location (Folia)
     * or the main thread (Paper).
     *
     * @param location The block location
     * @param snapshot The block snapshot data
     */
    private fun restoreBlock(location: Location, snapshot: BlockSnapshot) {
        val block = location.block

        // Parse and set block data
        try {
            // CRITICAL FIX: Reset trial spawner state to waiting_for_players
            // If the snapshot was taken while spawners were in cooldown state,
            // they would be restored in cooldown and not drop keys for 30 minutes!
            val blockDataString = resetTrialSpawnerState(snapshot.blockData)
            val blockData = Bukkit.createBlockData(blockDataString)
            block.setBlockData(blockData, false) // Don't apply physics immediately
        } catch (_: Exception) {
            plugin.logger.warning("Invalid block data at ${location.blockX},${location.blockY},${location.blockZ}: ${snapshot.blockData}")
            return
        }

        // Restore tile entity data if present
        snapshot.tileEntity?.let { tileEntityData ->
            val state = block.state
            if (NBTUtil.restoreTileEntity(state, tileEntityData)) {
                // Successfully restored tile entity
            } else {
                plugin.logger.warning("Failed to restore tile entity at ${location.blockX},${location.blockY},${location.blockZ}")
            }
        }
    }

    /**
     * Ensures a chunk is loaded before restoring blocks.
     * On Folia, chunk loading is handled differently - we schedule to the chunk's region.
     *
     * @param chunk The chunk to load
     */
    private suspend fun ensureChunkLoaded(chunk: Chunk) {
        if (!chunk.isLoaded) {
            // Get a location in this chunk for region scheduling
            val chunkLocation = Location(chunk.world, chunk.x * 16.0, 64.0, chunk.z * 16.0)

            // Load chunk on the appropriate thread
            plugin.scheduler.runAtLocation(chunkLocation, Runnable {
                chunk.load()
            })
            // Wait for chunk to load
            delay(50)
        }
    }

    /**
     * Restores blocks synchronously (use with caution - may cause lag).
     * On Folia, this should only be called from the correct region thread.
     *
     * @param snapshot Map of locations to block snapshots
     * @return Number of blocks successfully restored
     */
    fun restoreBlocksSync(snapshot: Map<Location, BlockSnapshot>): Int {
        var restored = 0

        snapshot.forEach { (location, blockSnapshot) ->
            try {
                restoreBlock(location, blockSnapshot)
                restored++
            } catch (e: Exception) {
                plugin.logger.warning("Failed to restore block: ${e.message}")
            }
        }

        return restored
    }

    /**
     * Estimates restoration time based on block count.
     *
     * @param blockCount Number of blocks to restore
     * @return Estimated time in seconds
     */
    fun estimateRestorationTime(blockCount: Int): Long {
        val blocksPerTick = plugin.config.getInt("global.blocks-per-tick", 500)
        val batches = (blockCount + blocksPerTick - 1) / blocksPerTick
        // Each batch takes ~50ms, plus some overhead
        return ((batches * 50 + 500) / 1000).toLong() // Convert to seconds
    }

    /**
     * Resets trial spawner state in block data string to waiting_for_players.
     *
     * Trial spawners have 6 states: inactive, waiting_for_players, active,
     * waiting_for_reward_ejection, ejecting_reward, cooldown.
     *
     * If a snapshot was taken while spawners were in cooldown (or other non-fresh state),
     * restoring that snapshot would create spawners that won't drop keys!
     *
     * This function modifies the block data string to ensure spawners are restored
     * in the waiting_for_players state, ready to be activated.
     *
     * @param blockData The original block data string
     * @return The modified block data string with reset spawner state
     */
    private fun resetTrialSpawnerState(blockData: String): String {
        // Only process trial spawners
        if (!blockData.contains("trial_spawner")) {
            return blockData
        }

        // All possible trial spawner states that need to be reset
        val statesToReset = listOf(
            "trial_spawner_state=inactive",
            "trial_spawner_state=active",
            "trial_spawner_state=waiting_for_reward_ejection",
            "trial_spawner_state=ejecting_reward",
            "trial_spawner_state=cooldown"
        )

        var result = blockData
        for (state in statesToReset) {
            if (result.contains(state)) {
                result = result.replace(state, "trial_spawner_state=waiting_for_players")
                break // Only one state can be present
            }
        }

        return result
    }
}
