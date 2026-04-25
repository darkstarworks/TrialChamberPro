package io.github.darkstarworks.trialChamberPro.models

/**
 * A named template for a `minecraft:trial_spawner` item with preconfigured
 * `block_entity_data`. Loaded from `spawner_presets.yml` and handed out via
 * `/tcp give <preset>`.
 *
 * The `normalConfig` / `ominousConfig` strings are *resource locations* that
 * point at a datapack-defined trial spawner config (e.g.
 * `namespace:basic_zombie`). The plugin doesn't validate that the
 * datapack exists — that's the server owner's responsibility — but the field
 * IS quoted as a string in the produced NBT so an inline-compound form will
 * not parse correctly here. Keep configs in datapacks.
 *
 * Introduced in v1.3.1.
 *
 * Note: the preset can ONLY produce `Material.TRIAL_SPAWNER` items by design;
 * there is no `material` field in the YAML schema. Other block types (vaults,
 * etc.) are out of scope for the free tier — they belong to a separate
 * "vault preset" / custom-key system in the planned premium module.
 */
data class SpawnerPreset(
    val id: String,
    val normalConfig: String?,
    val ominousConfig: String?,
    val requiredPlayerRange: Int,
    val targetCooldownLength: Int,
    val totalMobs: Int?,
    val simultaneousMobs: Int?,
    val totalMobsAddedPerPlayer: Float?,
    val simultaneousMobsAddedPerPlayer: Float?,
    val ticksBetweenSpawn: Int?,
    val spawnRange: Int?,
    val displayName: String?,
    val lore: List<String>
)
