package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.managers.SpawnerPresetManager
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.block.TileState
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.persistence.PersistentDataType

/**
 * Allows players to recover TCP-preset trial spawners that were placed outside
 * any registered chamber ("orphaned" spawners).
 *
 * Vanilla trial spawners can never be mined for a drop — they always drop
 * nothing regardless of tool or enchantment. TCP's protection listener only
 * guards spawners *inside* chambers; outside a chamber it returns early,
 * leaving vanilla's silent-drop behaviour in place. This creates a usability
 * trap: a spawner given via `/tcp give` and placed anywhere on the map is
 * permanently stuck there.
 *
 * Fix:
 *   - Silk Touch tool → cancel vanilla nothing-drop, drop the full preset item
 *     (PDC tag intact for re-placement), play the vanilla break effect.
 *   - No Silk Touch → cancel the break entirely, send a hint. Prevents
 *     accidental permanent loss.
 *
 * TCP-WildSpawners (when installed) handles wild-preset spawner recovery
 * without Silk Touch via its own configurable hardness system. There is no
 * conflict: WildSpawners drives mining via [BlockDamageEvent] and sets the
 * block to AIR directly — [BlockBreakEvent] never fires for spawners it
 * manages, so this listener only ever runs when WildSpawners is absent.
 *
 * "Orphaned" means: block has `tcp:preset_id` on its TileState AND
 * [io.github.darkstarworks.trialChamberPro.managers.ChamberManager.getCachedChamberAt]
 * returns null for its location.
 *
 * Added in v1.4.5.
 */
class OrphanSpawnerMineListener(private val plugin: TrialChamberPro) : Listener {

    private val presetIdKey: NamespacedKey =
        NamespacedKey(plugin, SpawnerPresetManager.PRESET_ID_KEY_NAME)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        if (block.type != Material.TRIAL_SPAWNER) return

        // Only act on TCP-tagged spawners
        val tileState = block.state as? TileState ?: return
        val presetId = tileState.persistentDataContainer
            .get(presetIdKey, PersistentDataType.STRING) ?: return

        // If inside a registered chamber, leave it alone — ProtectionListener handles that.
        if (plugin.chamberManager.getCachedChamberAt(block.location) != null) return

        event.isCancelled = true

        val tool = event.player.inventory.itemInMainHand
        if (!tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            event.player.sendMessage(plugin.getMessageComponent("orphan-spawner-needs-silk-touch"))
            return
        }

        // Drop the full preset item so it can be re-placed and re-identified.
        val preset = plugin.spawnerPresetManager.get(presetId)
        val drop = if (preset != null) {
            plugin.spawnerPresetManager.getItem(preset, 1)
        } else {
            // Preset was removed from spawner_presets.yml after this spawner was placed.
            // Fall back to a plain trial_spawner so the block is never permanently unrecoverable.
            org.bukkit.inventory.ItemStack(Material.TRIAL_SPAWNER)
        }

        block.world.dropItemNaturally(block.location, drop)
        block.world.playEffect(block.location, org.bukkit.Effect.STEP_SOUND, Material.TRIAL_SPAWNER)
        block.type = Material.AIR
    }
}
