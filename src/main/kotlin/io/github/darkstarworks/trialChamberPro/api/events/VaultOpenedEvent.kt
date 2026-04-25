package io.github.darkstarworks.trialChamberPro.api.events

import io.github.darkstarworks.trialChamberPro.models.Chamber
import io.github.darkstarworks.trialChamberPro.models.VaultData
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack

/**
 * Fired immediately after a player has successfully opened a vault — the loot
 * is generated, the key is consumed, and the items have been delivered to the
 * player (either into the inventory or popped out as item entities, depending
 * on `vaults.drop-loot-at-vault`). Not cancellable; the open already happened.
 *
 * Useful for stat tracking, custom announcements, or chained loot effects.
 *
 * @property player        The player who opened the vault.
 * @property vault         Database row for the opened vault.
 * @property chamber       The chamber the vault belongs to. May be null in the
 *                         pathological case of a vault whose chamber row was
 *                         deleted while the vault was open — listeners should
 *                         tolerate the null.
 * @property lootTableName The effective loot table that produced [items]
 *                         (chamber override resolved against vault default).
 * @property items         Snapshot of the items the player received from this
 *                         open. Held as `ItemStack` clones so mutating the
 *                         player's inventory afterward does not corrupt the
 *                         event payload.
 *
 * @since v1.3.0
 */
class VaultOpenedEvent(
    val player: Player,
    val vault: VaultData,
    val chamber: Chamber?,
    val lootTableName: String,
    val items: List<ItemStack>
) : Event(!Bukkit.isPrimaryThread()) {

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
