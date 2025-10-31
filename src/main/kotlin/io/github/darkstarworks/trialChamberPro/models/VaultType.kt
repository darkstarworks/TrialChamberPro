package io.github.darkstarworks.trialChamberPro.models

/**
 * Represents the type of vault in the Trial Chamber.
 * Normal vaults require normal trial keys, ominous vaults require ominous trial keys.
 */
enum class VaultType {
    NORMAL,
    OMINOUS;

    val displayName: String
        get() = when (this) {
            NORMAL -> "Normal"
            OMINOUS -> "Ominous"
        }
}
