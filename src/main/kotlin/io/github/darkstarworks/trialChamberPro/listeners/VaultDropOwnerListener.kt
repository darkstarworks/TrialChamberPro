package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.NamespacedKey
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/**
 * Enforces owner-only pickup for vault loot dropped via the vanilla-style
 * ejection path. Items are tagged with the owner UUID and a grace timestamp
 * when ResetManager / VaultInteractListener drop them; after the grace period
 * elapses the tag is ignored and anyone can pick the item up.
 */
class VaultDropOwnerListener(private val plugin: TrialChamberPro) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) {
        val picker = event.entity as? Player ?: return
        val item = event.item
        val ownerId = readOwner(item) ?: return

        val graceSeconds = plugin.config.getLong("vaults.drop-loot-owner-grace-seconds", 30L)
        if (graceSeconds > 0) {
            val droppedAt = readDropTime(item) ?: return
            val elapsedMs = System.currentTimeMillis() - droppedAt
            if (elapsedMs >= graceSeconds * 1000L) {
                return // grace expired — free-for-all
            }
        }

        if (picker.uniqueId != ownerId && !picker.hasPermission("tcp.bypass.droplock")) {
            event.isCancelled = true
        }
    }

    private fun readOwner(item: Item): UUID? {
        val raw = item.persistentDataContainer.get(OWNER_KEY, PersistentDataType.STRING) ?: return null
        return try { UUID.fromString(raw) } catch (_: IllegalArgumentException) { null }
    }

    private fun readDropTime(item: Item): Long? {
        return item.persistentDataContainer.get(DROPPED_AT_KEY, PersistentDataType.LONG)
    }

    companion object {
        lateinit var OWNER_KEY: NamespacedKey
            private set
        lateinit var DROPPED_AT_KEY: NamespacedKey
            private set

        fun init(plugin: TrialChamberPro) {
            OWNER_KEY = NamespacedKey(plugin, "vault_owner")
            DROPPED_AT_KEY = NamespacedKey(plugin, "vault_dropped_at")
        }
    }
}
