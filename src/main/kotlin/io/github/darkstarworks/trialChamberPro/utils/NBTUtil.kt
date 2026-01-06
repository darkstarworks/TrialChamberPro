package io.github.darkstarworks.trialChamberPro.utils

import org.bukkit.block.BlockState
import org.bukkit.block.DecoratedPot
import org.bukkit.block.TrialSpawner
import org.bukkit.block.Vault

/**
 * Utility class for handling NBT data from tile entities.
 * Captures and restores data for Trial Spawners, Vaults, and Decorated Pots.
 */
object NBTUtil {

    /**
     * Captures tile entity data from a block state.
     *
     * @param state The block state
     * @return Map of NBT data, or null if not a tile entity
     */
    fun captureTileEntity(state: BlockState): Map<String, Any>? {
        return when (state) {
            is TrialSpawner -> captureTrialSpawner(state)
            is Vault -> captureVault(state)
            is DecoratedPot -> captureDecoratedPot(state)
            else -> null
        }
    }

    /**
     * Captures Trial Spawner NBT data.
     * Stores cooldown settings and ominous state for proper restoration.
     */
    private fun captureTrialSpawner(spawner: TrialSpawner): Map<String, Any> {
        return try {
            mapOf(
                "type" to "TRIAL_SPAWNER",
                "ominous" to spawner.isOminous,
                "cooldownLength" to spawner.cooldownLength,
                "requiredPlayerRange" to spawner.requiredPlayerRange
            )
        } catch (_: Exception) {
            // Fallback if API methods not available
            mapOf("type" to "TRIAL_SPAWNER")
        }
    }

    /**
     * Captures Vault NBT data.
     * Stores vault type and state information.
     */
    private fun captureVault(vault: Vault): Map<String, Any> {
        return mapOf(
            "type" to "VAULT",
            "material" to vault.block.type.name
            // Note: Vault state is handled by BlockData
        )
    }

    /**
     * Captures Decorated Pot NBT data.
     * Stores sherd information for all sides.
     */
    private fun captureDecoratedPot(pot: DecoratedPot): Map<String, Any> {
        val sherds = mutableMapOf<String, String>()

        DecoratedPot.Side.entries.forEach { side ->
            val sherd = pot.getSherd(side)
            sherds[side.name] = sherd.name
        }

        return mapOf(
            "type" to "DECORATED_POT",
            "sherds" to sherds
        )
    }

    /**
     * Restores tile entity data to a block state.
     *
     * @param state The block state to restore to
     * @param data The NBT data map
     * @return True if restoration was successful
     */
    fun restoreTileEntity(state: BlockState, data: Map<String, Any>): Boolean {
        val type = data["type"] as? String ?: return false

        return when (type) {
            "TRIAL_SPAWNER" -> restoreTrialSpawner(state as? TrialSpawner ?: return false, data)
            "VAULT" -> restoreVault(state as? Vault ?: return false, data)
            "DECORATED_POT" -> restoreDecoratedPot(state as? DecoratedPot ?: return false, data)
            else -> false
        }
    }

    /**
     * Restores Trial Spawner data and resets its state.
     * CRITICAL: This clears tracked players so the spawner can be reactivated
     * and will drop trial keys again when completed.
     *
     * NOTE: Cooldown length is NOT restored from snapshot - it's controlled by
     * the config setting (reset.spawner-cooldown-minutes) and applied in
     * ResetManager.resetTrialSpawners() which runs AFTER block restoration.
     */
    private fun restoreTrialSpawner(spawner: TrialSpawner, data: Map<String, Any>): Boolean {
        return try {
            // Clear all tracked players - this is the KEY fix for trial key drops!
            // Without this, the spawner "remembers" players who already completed it
            // and won't spawn mobs or drop keys for them.
            spawner.trackedPlayers.forEach { player ->
                spawner.stopTrackingPlayer(player)
            }

            // Clear all tracked entities (spawned mobs that haven't been killed)
            spawner.trackedEntities.forEach { entity ->
                spawner.stopTrackingEntity(entity)
            }

            // Restore ominous state from snapshot
            val wasOminous = data["ominous"] as? Boolean ?: false
            spawner.isOminous = wasOminous

            // NOTE: cooldownLength is intentionally NOT restored from snapshot.
            // The cooldown is controlled by config (reset.spawner-cooldown-minutes)
            // and set by ResetManager.resetTrialSpawners() after block restoration.
            // This ensures the config setting always takes precedence.

            // Restore required player range if captured
            val requiredRange = data["requiredPlayerRange"] as? Int
            if (requiredRange != null && requiredRange > 0) {
                spawner.requiredPlayerRange = requiredRange
            }

            // Commit the changes
            spawner.update(true, false)
            true
        } catch (e: Exception) {
            // Log but don't fail - BlockData restoration still works
            false
        }
    }

    /**
     * Restores Vault data.
     */
    private fun restoreVault(vault: Vault, data: Map<String, Any>): Boolean {
        try {
            vault.update(true, false)
            return true
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Restores Decorated Pot data.
     */
    private fun restoreDecoratedPot(pot: DecoratedPot, data: Map<String, Any>): Boolean {
        try {
            @Suppress("UNCHECKED_CAST")
            val sherds = data["sherds"] as? Map<String, String> ?: return false

            sherds.forEach { (sideName, sherdName) ->
                val side = DecoratedPot.Side.valueOf(sideName)
                val material = try {
                    org.bukkit.Material.valueOf(sherdName)
                } catch (_: IllegalArgumentException) {
                    null
                }

                if (material != null) {
                    pot.setSherd(side, material)
                }
            }

            pot.update(true, false)
            return true
        } catch (_: Exception) {
            return false
        }
    }
}
