package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.managers.SpawnerPresetManager
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.TileState
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.persistence.PersistentDataType

/**
 * Copies the `tcp:preset_id` PDC tag from a placed trial-spawner item onto
 * the resulting block's [TileState]. Lets downstream code (notably the
 * premium [io.github.darkstarworks.trialChamberPro.api.WildSpawnerResolver])
 * identify which TCP preset a placed spawner came from, even after the
 * source ItemStack is gone.
 *
 * Only acts on `Material.TRIAL_SPAWNER` placements where the item carries
 * the tag. Vanilla `/give minecraft:trial_spawner` items have no tag and
 * are ignored. The tag survives chamber resets / chunk unloads / world
 * reloads via Minecraft's standard TileEntity persistence.
 *
 * Added in v1.4.0.
 */
class SpawnerPresetPlaceListener(private val plugin: TrialChamberPro) : Listener {

    private val presetIdKey: NamespacedKey =
        NamespacedKey(plugin, SpawnerPresetManager.PRESET_ID_KEY_NAME)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        if (event.blockPlaced.type != Material.TRIAL_SPAWNER) return

        val item = event.itemInHand
        val itemMeta = item.itemMeta ?: return
        val presetId = itemMeta.persistentDataContainer
            .get(presetIdKey, PersistentDataType.STRING)
            ?: return  // not a preset-sourced spawner, leave alone

        val state = event.blockPlaced.state as? TileState ?: return
        state.persistentDataContainer.set(presetIdKey, PersistentDataType.STRING, presetId)
        state.update()

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            val loc = event.blockPlaced.location
            plugin.logger.info(
                "[SpawnerPreset] Tagged placed trial spawner at " +
                    "${loc.blockX},${loc.blockY},${loc.blockZ} with preset_id='$presetId'"
            )
        }
    }
}
