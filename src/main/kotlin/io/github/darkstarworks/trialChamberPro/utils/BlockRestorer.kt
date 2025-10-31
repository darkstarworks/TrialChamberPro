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
                // Schedule on main thread
                Bukkit.getScheduler().runTask(plugin, Runnable {
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

                // Small delay between batches to prevent lag (1 tick = 50ms)
                delay(50)
            }
        }

        plugin.logger.info("Block restoration complete: $processedBlocks/$totalBlocks blocks restored")

        // Call completion callback on main thread
        Bukkit.getScheduler().runTask(plugin, Runnable {
            onComplete?.invoke()
        })
    }

    /**
     * Restores a single block from a snapshot.
     *
     * @param location The block location
     * @param snapshot The block snapshot data
     */
    private fun restoreBlock(location: Location, snapshot: BlockSnapshot) {
        val block = location.block

        // Parse and set block data
        try {
            val blockData = Bukkit.createBlockData(snapshot.blockData)
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
     *
     * @param chunk The chunk to load
     */
    private suspend fun ensureChunkLoaded(chunk: Chunk) {
        if (!chunk.isLoaded) {
            // Load chunk on main thread
            Bukkit.getScheduler().runTask(plugin, Runnable {
                chunk.load()
            })
            // Wait for chunk to load
            delay(50)
        }
    }

    /**
     * Restores blocks synchronously (use with caution - may cause lag).
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
}
