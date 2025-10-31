package io.github.darkstarworks.trialChamberPro.models

import java.io.Serializable

/**
 * Represents a snapshot of a single block's data.
 * Used for chamber restoration after resets.
 *
 * @property blockData The BlockData string representation
 * @property tileEntity NBT data for tile entities (spawners, vaults, pots), nullable
 */
data class BlockSnapshot(
    val blockData: String,
    val tileEntity: Map<String, Any>? = null
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
