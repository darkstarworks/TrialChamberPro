package io.github.darkstarworks.trialChamberPro.api.events

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Fired by `ChamberDiscoveryManager` after a candidate chamber has been
 * validated (size, vault count, center-Y) but **before** it is registered in
 * the database. Cancellable — listeners may abort the auto-registration.
 *
 * Useful for custom whitelists ("only auto-register chambers in survival
 * worlds"), dimension restrictions, or integrations that want to register the
 * chamber under their own rules.
 *
 * The event is fired with a *preliminary* description of the chamber rather
 * than a `Chamber` instance, since the row does not yet exist when the event
 * fires. Listeners that want the registered `Chamber` instance should listen
 * for a subsequent management event (none currently emitted; track via
 * `ChamberManager.getChamber(name)` after this event).
 *
 * @property world         World the candidate is in.
 * @property suggestedName Auto-generated name (`auto_<world>_<x>_<z>`) the
 *                         registration will use unless aborted.
 * @property minCorner     Inclusive min corner of the bounding box.
 * @property maxCorner     Inclusive max corner of the bounding box.
 * @property vaultCount    Vault blocks counted inside the bounding box.
 * @property spawnerCount  Trial spawner blocks counted inside the bounding box.
 * @property method        How the discovery was triggered.
 *
 * @since v1.3.0
 */
class ChamberDiscoveredEvent(
    val world: World,
    val suggestedName: String,
    val minCorner: Location,
    val maxCorner: Location,
    val vaultCount: Int,
    val spawnerCount: Int,
    val method: Method
) : Event(!Bukkit.isPrimaryThread()), Cancellable {

    enum class Method {
        /** Triggered by a `ChunkLoadEvent` after server start. */
        CHUNK_LOAD,
        /** Triggered by the plugin-enable startup sweep over already-loaded chunks. */
        STARTUP_SWEEP
    }

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled
    override fun setCancelled(cancel: Boolean) { cancelled = cancel }
    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
