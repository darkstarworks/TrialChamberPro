package io.github.darkstarworks.trialChamberPro.models

/**
 * Represents a vault in a Trial Chamber.
 *
 * @property id Database ID
 * @property chamberId Chamber ID this vault belongs to
 * @property x X coordinate
 * @property y Y coordinate
 * @property z Z coordinate
 * @property type Vault type (Normal or Ominous)
 * @property lootTable Name of the loot table to use
 */
data class VaultData(
    val id: Int,
    val chamberId: Int,
    val x: Int,
    val y: Int,
    val z: Int,
    val type: VaultType,
    val lootTable: String
)
