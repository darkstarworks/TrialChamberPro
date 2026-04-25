package io.github.darkstarworks.trialChamberPro.managers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.SpawnerPreset
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import java.io.File

/**
 * Loads and serves named [SpawnerPreset] templates. Each preset materializes
 * as a `minecraft:trial_spawner` item with the preset's `block_entity_data`
 * baked in, ready to place. Hand-out happens via `/tcp give <preset>`.
 *
 * Introduced in v1.3.1.
 *
 * **Hardening:** the YAML schema has no `material` field — every preset
 * always produces `Material.TRIAL_SPAWNER`. This is deliberate: the planned
 * premium custom-keys / vault-crate module owns vault presets and any other
 * block types. Keeping the free tier strictly trial-spawner-only makes the
 * boundary unambiguous.
 *
 * **Thread-safety:** [presets] is a snapshot read; reloads atomically swap
 * the map reference. `getItem` is safe to call from any thread (it does not
 * touch Bukkit world state — only an ItemFactory parse, which is safe).
 */
class SpawnerPresetManager(private val plugin: TrialChamberPro) {

    @Volatile
    private var presets: Map<String, SpawnerPreset> = emptyMap()

    private val file: File
        get() = File(plugin.dataFolder, FILE_NAME)

    /**
     * Read presets from disk, replacing the in-memory map. Safe to call from
     * any thread (pure file I/O + YAML parse).
     */
    fun load() {
        if (!file.exists()) {
            plugin.saveResource(FILE_NAME, false)
        }

        val yaml = YamlConfiguration.loadConfiguration(file)
        val section = yaml.getConfigurationSection("presets")
        if (section == null) {
            presets = emptyMap()
            plugin.logger.info("Loaded 0 spawner presets (no 'presets:' section in $FILE_NAME)")
            return
        }

        val loaded = mutableMapOf<String, SpawnerPreset>()
        for (id in section.getKeys(false)) {
            val node = section.getConfigurationSection(id) ?: continue
            try {
                val preset = SpawnerPreset(
                    id = id,
                    normalConfig = node.getString("normal-config"),
                    ominousConfig = node.getString("ominous-config"),
                    requiredPlayerRange = node.getInt("required-player-range", 14),
                    targetCooldownLength = node.getInt("target-cooldown-length", 36000),
                    totalMobs = if (node.contains("total-mobs")) node.getInt("total-mobs") else null,
                    simultaneousMobs = if (node.contains("simultaneous-mobs")) node.getInt("simultaneous-mobs") else null,
                    totalMobsAddedPerPlayer = if (node.contains("total-mobs-added-per-player"))
                        node.getDouble("total-mobs-added-per-player").toFloat() else null,
                    simultaneousMobsAddedPerPlayer = if (node.contains("simultaneous-mobs-added-per-player"))
                        node.getDouble("simultaneous-mobs-added-per-player").toFloat() else null,
                    ticksBetweenSpawn = if (node.contains("ticks-between-spawn")) node.getInt("ticks-between-spawn") else null,
                    spawnRange = if (node.contains("spawn-range")) node.getInt("spawn-range") else null,
                    displayName = node.getString("display-name"),
                    lore = node.getStringList("lore")
                )
                if (preset.normalConfig.isNullOrBlank() && preset.ominousConfig.isNullOrBlank()) {
                    plugin.logger.warning("Spawner preset '$id' has neither normal-config nor ominous-config; it will spawn nothing. Skipping.")
                    continue
                }
                loaded[id.lowercase()] = preset
            } catch (e: Exception) {
                plugin.logger.warning("Failed to parse spawner preset '$id': ${e.message}")
            }
        }
        presets = loaded
        plugin.logger.info("Loaded ${loaded.size} spawner preset(s)")
    }

    fun getNames(): Set<String> = presets.keys

    fun get(id: String): SpawnerPreset? = presets[id.lowercase()]

    /**
     * Build a `minecraft:trial_spawner` ItemStack with the preset's NBT baked
     * into the `block_entity_data` data component. Equivalent to the vanilla
     * `/give <player> trial_spawner[block_entity_data={...}] <amount>` form.
     *
     * @throws IllegalArgumentException if the preset's NBT cannot be parsed
     * by Paper's ItemFactory (malformed datapack reference, etc.). Callers
     * should surface this to the sender rather than swallowing it.
     */
    fun getItem(preset: SpawnerPreset, amount: Int): ItemStack {
        val snbt = buildBlockEntitySnbt(preset)
        val itemArg = "minecraft:trial_spawner[minecraft:block_entity_data=$snbt]"

        // ItemFactory.createItemStack accepts the same syntax as vanilla /give.
        // Available since Paper 1.20.5+. We use it instead of the deprecated
        // Bukkit.getUnsafe().modifyItemStack(...) path.
        val item = Bukkit.getItemFactory().createItemStack(itemArg)
        item.amount = amount.coerceIn(1, item.maxStackSize)

        // Apply display name + lore via ItemMeta (NOT NBT) so the values survive
        // any future component remap and don't fight the block_entity_data tag.
        val displayName = preset.displayName
        if (displayName != null || preset.lore.isNotEmpty()) {
            item.editMeta { meta ->
                if (displayName != null) {
                    meta.displayName(
                        LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(displayName)
                            .decoration(TextDecoration.ITALIC, false)
                    )
                }
                if (preset.lore.isNotEmpty()) {
                    meta.lore(preset.lore.map { line ->
                        LegacyComponentSerializer.legacyAmpersand()
                            .deserialize(line)
                            .decoration(TextDecoration.ITALIC, false)
                    })
                }
            }
        }
        return item
    }

    private fun buildBlockEntitySnbt(preset: SpawnerPreset): String {
        // Build the SNBT compound by hand. We deliberately keep this string-based
        // rather than reaching for NMS — Paper exposes no Bukkit-API path to
        // construct an arbitrary CompoundTag for the BLOCK_ENTITY_DATA component,
        // and ItemFactory.createItemStack happily accepts the SNBT string form.
        val parts = mutableListOf<String>()
        parts += """id:"trial_spawner""""
        preset.normalConfig?.takeIf { it.isNotBlank() }?.let {
            parts += """normal_config:${quoteSnbt(it)}"""
        }
        preset.ominousConfig?.takeIf { it.isNotBlank() }?.let {
            parts += """ominous_config:${quoteSnbt(it)}"""
        }
        parts += "required_player_range:${preset.requiredPlayerRange}"
        parts += "target_cooldown_length:${preset.targetCooldownLength}"
        preset.totalMobs?.let { parts += "total_mobs:$it" }
        preset.simultaneousMobs?.let { parts += "simultaneous_mobs:$it" }
        preset.totalMobsAddedPerPlayer?.let { parts += "total_mobs_added_per_player:${it}f" }
        preset.simultaneousMobsAddedPerPlayer?.let { parts += "simultaneous_mobs_added_per_player:${it}f" }
        preset.ticksBetweenSpawn?.let { parts += "ticks_between_spawn:$it" }
        preset.spawnRange?.let { parts += "spawn_range:$it" }
        return "{" + parts.joinToString(",") + "}"
    }

    private fun quoteSnbt(value: String): String {
        // Wrap in double-quotes and escape any embedded backslash / quote.
        val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }

    @Suppress("unused") // sanity-check used by tests
    fun targetMaterial(): Material = Material.TRIAL_SPAWNER

    companion object {
        const val FILE_NAME = "spawner_presets.yml"
    }
}
