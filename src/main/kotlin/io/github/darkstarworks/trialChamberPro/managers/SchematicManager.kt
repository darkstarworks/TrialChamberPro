package io.github.darkstarworks.trialChamberPro.managers

import com.sk89q.worldedit.EditSession
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.session.ClipboardHolder
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.utils.minecraftDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Location
import org.bukkit.entity.Player
import java.io.File
import java.io.FileInputStream

/**
 * Manages schematic file loading and pasting operations.
 * Handles Trial Chamber .schem files from resources with full NBT support.
 */
class SchematicManager(private val plugin: TrialChamberPro) {

    private val schematicsDir: File = File(plugin.dataFolder, "schematics")
    private var worldEditAvailable: Boolean = false

    /**
     * Initializes the schematic manager and copies default schematics from resources.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        // Create schematics directory
        schematicsDir.mkdirs()

        // Check for WorldEdit/FAWE
        worldEditAvailable = plugin.server.pluginManager.getPlugin("WorldEdit") != null ||
                plugin.server.pluginManager.getPlugin("FastAsyncWorldEdit") != null

        if (worldEditAvailable) {
            val hasFAWE = plugin.server.pluginManager.getPlugin("FastAsyncWorldEdit") != null
            plugin.logger.info("WorldEdit integration enabled${if (hasFAWE) " (FAWE detected)" else ""}")
        } else {
            plugin.logger.warning("WorldEdit/FAWE not found - schematic features disabled")
            return@withContext
        }

        // Copy default schematics from resources if they don't exist
        val defaultSchematics = listOf("trial1.schem", "trial2.schem")
        for (schematicName in defaultSchematics) {
            val targetFile = File(schematicsDir, schematicName)
            if (!targetFile.exists()) {
                plugin.getResource(schematicName)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                    plugin.logger.info("Copied default schematic: $schematicName")
                }
            }
        }

        plugin.logger.info("Schematic Manager initialized with ${schematicsDir.listFiles()?.size ?: 0} schematics")
    }

    /**
     * Checks if WorldEdit/FAWE is available.
     */
    fun isAvailable(): Boolean = worldEditAvailable

    /**
     * Lists all available schematic files.
     */
    fun listSchematics(): List<String> {
        return schematicsDir.listFiles { file -> file.extension == "schem" }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    /**
     * Pastes a schematic at the specified location.
     * File loading happens asynchronously, but pasting happens on the main thread
     * to satisfy Paper's async safety checks for WorldEdit operations.
     *
     * If a player is provided, the EditSession is added to their WorldEdit history,
     * making the paste operation undoable with `//undo`.
     *
     * @param schematicName Name of the schematic file (without extension)
     * @param location Location to paste the schematic (origin point)
     * @param player Optional player to link the paste to WorldEdit history (for //undo support)
     * @param ignoreAir Whether to ignore air blocks when pasting
     * @return True if the paste was successful, false otherwise
     */
    suspend fun pasteSchematic(
        schematicName: String,
        location: Location,
        player: Player? = null,
        ignoreAir: Boolean = false
    ): Boolean {
        if (!worldEditAvailable) {
            plugin.logger.warning("Cannot paste schematic - WorldEdit/FAWE not available")
            return false
        }

        try {
            // Load schematic file on IO thread (file reading)
            val clipboard = withContext(Dispatchers.IO) {
                val schematicFile = File(schematicsDir, "$schematicName.schem")
                if (!schematicFile.exists()) {
                    plugin.logger.warning("Schematic file not found: ${schematicFile.name}")
                    return@withContext null
                }

                // Parse schematic format
                val format = ClipboardFormats.findByFile(schematicFile)
                    ?: throw IllegalArgumentException("Unknown schematic format for file: ${schematicFile.name}")

                // Load clipboard
                format.getReader(FileInputStream(schematicFile)).use { reader ->
                    reader.read()
                }
            } ?: return false

            // Paste on main thread (block placement)
            return withContext(plugin.minecraftDispatcher) {
                val world = BukkitAdapter.adapt(location.world)
                val pasteLocation = BlockVector3.at(
                    location.blockX,
                    location.blockY,
                    location.blockZ
                )

                // Get player's WorldEdit session if player provided
                val localSession = player?.let { p ->
                    try {
                        val actor = BukkitAdapter.adapt(p)
                        WorldEdit.getInstance().sessionManager.get(actor)
                    } catch (e: Exception) {
                        plugin.logger.warning("Failed to get WorldEdit session for ${p.name}: ${e.message}")
                        null
                    }
                }

                // Create EditSession and paste
                WorldEdit.getInstance().newEditSessionBuilder()
                    .world(world)
                    .maxBlocks(-1) // No block limit
                    .build()
                    .use { editSession: EditSession ->
                        val operation = ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(pasteLocation)
                            .ignoreAirBlocks(ignoreAir)
                            .build()

                        Operations.complete(operation)

                        // Remember this EditSession in player's WorldEdit history for //undo
                        localSession?.remember(editSession)
                    }

                plugin.logger.info("Successfully pasted schematic '$schematicName' at ${location.blockX}, ${location.blockY}, ${location.blockZ}")
                true
            }

        } catch (e: Exception) {
            plugin.logger.severe("Failed to paste schematic '$schematicName': ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Gets the world-space bounding box for where a schematic will be pasted.
     * Takes into account the schematic's origin and region offset.
     *
     * @param schematicName Name of the schematic file (without extension)
     * @param pasteLocation The location where the schematic will be pasted
     * @return Pair of (minLocation, maxLocation) representing the actual paste bounds, or null if error
     */
    suspend fun getSchematicBounds(schematicName: String, pasteLocation: Location): Pair<Location, Location>? = withContext(Dispatchers.IO) {
        if (!worldEditAvailable) return@withContext null

        try {
            val schematicFile = File(schematicsDir, "$schematicName.schem")
            if (!schematicFile.exists()) return@withContext null

            val format = ClipboardFormats.findByFile(schematicFile) ?: return@withContext null

            val clipboard: Clipboard = format.getReader(FileInputStream(schematicFile)).use { reader ->
                reader.read()
            }

            // Get the clipboard's region (in clipboard-local coordinates)
            val region = clipboard.region
            val origin = clipboard.origin
            
            // Calculate world-space bounds
            // When pasting, the origin is placed at pasteLocation
            // So blocks are at: pasteLocation + (blockPos - origin)
            val regionMin = region.minimumPoint
            val regionMax = region.maximumPoint
            
            // World min = pasteLocation + (regionMin - origin)
            val worldMinX = pasteLocation.blockX + (regionMin.x() - origin.x())
            val worldMinY = pasteLocation.blockY + (regionMin.y() - origin.y())
            val worldMinZ = pasteLocation.blockZ + (regionMin.z() - origin.z())
            
            // World max = pasteLocation + (regionMax - origin)
            val worldMaxX = pasteLocation.blockX + (regionMax.x() - origin.x())
            val worldMaxY = pasteLocation.blockY + (regionMax.y() - origin.y())
            val worldMaxZ = pasteLocation.blockZ + (regionMax.z() - origin.z())
            
            val minLoc = Location(pasteLocation.world, worldMinX.toDouble(), worldMinY.toDouble(), worldMinZ.toDouble())
            val maxLoc = Location(pasteLocation.world, worldMaxX.toDouble(), worldMaxY.toDouble(), worldMaxZ.toDouble())
            
            return@withContext Pair(minLoc, maxLoc)

        } catch (e: Exception) {
            plugin.logger.warning("Failed to read schematic bounds: ${e.message}")
            return@withContext null
        }
    }

    /**
     * Checks if a schematic file exists.
     */
    fun schematicExists(schematicName: String): Boolean {
        return File(schematicsDir, "$schematicName.schem").exists()
    }
}
