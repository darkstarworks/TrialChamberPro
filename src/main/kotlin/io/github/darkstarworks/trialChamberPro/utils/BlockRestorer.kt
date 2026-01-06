package io.github.darkstarworks.trialChamberPro.utils

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.BlockSnapshot
import kotlinx.coroutines.delay
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.entity.Player

/**
 * Utility class for asynchronously restoring blocks from snapshots.
 * Uses batching and delays to prevent server lag during large restorations.
 *
 * Folia compatible: Uses location-based scheduling to ensure blocks are
 * modified on the correct region thread.
 *
 * WorldEdit integration: When a player is provided and WorldEdit is available,
 * block changes are recorded in WorldEdit's EditSession so they can be undone
 * with //undo. This doesn't replace WorldEdit's undo queue - it adds to it.
 */
class BlockRestorer(private val plugin: TrialChamberPro) {

    /**
     * Restores blocks from a snapshot asynchronously.
     * Groups blocks by chunk and processes them in batches to prevent lag.
     *
     * @param snapshot Map of locations to block snapshots
     * @param onProgress Optional callback for progress updates (processed, total)
     * @param onComplete Optional callback when restoration is complete
     * @param initiatingPlayer Optional player who initiated the restoration (for WorldEdit undo support)
     */
    suspend fun restoreBlocks(
        snapshot: Map<Location, BlockSnapshot>,
        onProgress: ((Int, Int) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
        initiatingPlayer: Player? = null
    ) {
        val blocksPerTick = plugin.config.getInt("global.blocks-per-tick", 500)
        val totalBlocks = snapshot.size

        plugin.logger.info("Starting block restoration: $totalBlocks blocks")

        // Try to set up WorldEdit integration if player provided and WorldEdit available
        val weSession = if (initiatingPlayer != null && WorldEditUtil.isAvailable()) {
            try {
                createWorldEditSession(initiatingPlayer, snapshot)
            } catch (e: Exception) {
                plugin.logger.warning("Failed to create WorldEdit session for undo support: ${e.message}")
                null
            }
        } else null

        if (weSession != null) {
            plugin.logger.info("WorldEdit integration enabled - changes can be undone with //undo")
        }

        // Group blocks by chunk for efficient processing
        val blocksByChunk = snapshot.entries.groupBy { it.key.chunk }

        // Use atomic counter for thread-safe progress tracking across multiple region threads (Folia)
        val processedBlocks = java.util.concurrent.atomic.AtomicInteger(0)

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
                                // Use WorldEdit if available, otherwise direct Bukkit API
                                if (weSession != null) {
                                    restoreBlockWithWorldEdit(location, blockSnapshot, weSession)
                                } else {
                                    restoreBlock(location, blockSnapshot)
                                }
                                processedBlocks.incrementAndGet()
                            } catch (e: Exception) {
                                plugin.logger.warning(
                                    "Failed to restore block at ${location.blockX},${location.blockY},${location.blockZ}: ${e.message}"
                                )
                            }
                        }

                        // Call progress callback
                        onProgress?.invoke(processedBlocks.get(), totalBlocks)
                    })
                }

                // Small delay between batches to prevent lag (1 tick = 50ms)
                delay(50)
            }
        }

        // Finalize WorldEdit session if used
        if (weSession != null) {
            try {
                finalizeWorldEditSession(weSession, initiatingPlayer!!)
                plugin.logger.info("WorldEdit session finalized - use //undo to revert changes")
            } catch (e: Exception) {
                plugin.logger.warning("Failed to finalize WorldEdit session: ${e.message}")
            }
        }

        plugin.logger.info("Block restoration complete: ${processedBlocks.get()}/$totalBlocks blocks restored")

        // Call completion callback on main/global thread
        plugin.scheduler.runTask(Runnable {
            onComplete?.invoke()
        })
    }

    /**
     * Holder for WorldEdit session data during restoration.
     */
    private data class WorldEditSessionData(
        val editSession: Any, // com.sk89q.worldedit.EditSession
        val localSession: Any  // com.sk89q.worldedit.LocalSession
    )

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

    // ==================== WorldEdit Integration ====================

    /**
     * Creates a WorldEdit EditSession for recording block changes.
     * Uses reflection to work with WorldEdit's API without compile-time dependency.
     */
    private fun createWorldEditSession(player: Player, snapshot: Map<Location, BlockSnapshot>): WorldEditSessionData? {
        if (snapshot.isEmpty()) return null

        val firstLocation = snapshot.keys.first()
        val world = firstLocation.world ?: return null

        try {
            // Get WorldEdit and FAWE classes via reflection
            val worldEditClass = Class.forName("com.sk89q.worldedit.WorldEdit")
            val bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter")

            // Get WorldEdit instance
            val getInstanceMethod = worldEditClass.getMethod("getInstance")
            val worldEditInstance = getInstanceMethod.invoke(null)

            // Get session manager
            val getSessionManagerMethod = worldEditClass.getMethod("getSessionManager")
            val sessionManager = getSessionManagerMethod.invoke(worldEditInstance)

            // Adapt player to WorldEdit actor
            val adaptPlayerMethod = bukkitAdapterClass.getMethod("adapt", Player::class.java)
            val actor = adaptPlayerMethod.invoke(null, player)

            // Get player's local session
            val sessionManagerClass = Class.forName("com.sk89q.worldedit.session.SessionManager")
            val getMethod = sessionManagerClass.getMethod("get", Class.forName("com.sk89q.worldedit.extension.platform.SessionOwner"))
            val localSession = getMethod.invoke(sessionManager, actor)

            // Adapt world
            val adaptWorldMethod = bukkitAdapterClass.getMethod("adapt", org.bukkit.World::class.java)
            val weWorld = adaptWorldMethod.invoke(null, world)

            // Create EditSession using builder pattern
            val newEditSessionBuilderMethod = worldEditClass.getMethod("newEditSessionBuilder")
            val builder = newEditSessionBuilderMethod.invoke(worldEditInstance)

            val builderClass = builder.javaClass
            val worldMethod = builderClass.getMethod("world", Class.forName("com.sk89q.worldedit.world.World"))
            worldMethod.invoke(builder, weWorld)

            val maxBlocksMethod = builderClass.getMethod("maxBlocks", Int::class.java)
            maxBlocksMethod.invoke(builder, -1) // No limit

            val buildMethod = builderClass.getMethod("build")
            val editSession = buildMethod.invoke(builder)

            return WorldEditSessionData(editSession, localSession)
        } catch (e: Exception) {
            plugin.logger.fine("WorldEdit integration not available: ${e.message}")
            return null
        }
    }

    /**
     * Restores a block using WorldEdit's EditSession for undo support.
     */
    private fun restoreBlockWithWorldEdit(location: Location, snapshot: BlockSnapshot, weSession: WorldEditSessionData) {
        try {
            val bukkitAdapterClass = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter")

            // Parse block data
            val blockDataString = resetTrialSpawnerState(snapshot.blockData)
            val bukkitBlockData = Bukkit.createBlockData(blockDataString)

            // Adapt to WorldEdit BlockState
            val adaptBlockDataMethod = bukkitAdapterClass.getMethod("adapt", org.bukkit.block.data.BlockData::class.java)
            val weBlockState = adaptBlockDataMethod.invoke(null, bukkitBlockData)

            // Create BlockVector3
            val blockVector3Class = Class.forName("com.sk89q.worldedit.math.BlockVector3")
            val atMethod = blockVector3Class.getMethod("at", Int::class.java, Int::class.java, Int::class.java)
            val position = atMethod.invoke(null, location.blockX, location.blockY, location.blockZ)

            // Set block through EditSession
            val editSessionClass = weSession.editSession.javaClass
            val setBlockMethod = editSessionClass.getMethod("setBlock",
                blockVector3Class,
                Class.forName("com.sk89q.worldedit.world.block.BlockStateHolder"))
            setBlockMethod.invoke(weSession.editSession, position, weBlockState)

            // Handle tile entity data separately with Bukkit API (WorldEdit doesn't support all NBT)
            snapshot.tileEntity?.let { tileEntityData ->
                val block = location.block
                val state = block.state
                NBTUtil.restoreTileEntity(state, tileEntityData)
            }
        } catch (e: Exception) {
            // Fall back to direct Bukkit API
            restoreBlock(location, snapshot)
        }
    }

    /**
     * Finalizes the WorldEdit session by flushing changes and adding to undo history.
     */
    private fun finalizeWorldEditSession(weSession: WorldEditSessionData, player: Player) {
        try {
            // Flush the EditSession to apply all changes
            val editSessionClass = weSession.editSession.javaClass

            // Try to call flushSession() or close()
            try {
                val flushMethod = editSessionClass.getMethod("flushSession")
                flushMethod.invoke(weSession.editSession)
            } catch (_: NoSuchMethodException) {
                // Try close() for AutoCloseable
                try {
                    val closeMethod = editSessionClass.getMethod("close")
                    closeMethod.invoke(weSession.editSession)
                } catch (_: Exception) {
                    // Ignore if neither method exists
                }
            }

            // Remember this session in player's undo history
            val localSessionClass = weSession.localSession.javaClass
            val rememberMethod = localSessionClass.getMethod("remember", editSessionClass.interfaces.first { it.simpleName == "EditSession" } ?: editSessionClass)
            rememberMethod.invoke(weSession.localSession, weSession.editSession)

            plugin.logger.info("WorldEdit undo history updated for ${player.name}")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to finalize WorldEdit session: ${e.message}")
        }
    }
}
