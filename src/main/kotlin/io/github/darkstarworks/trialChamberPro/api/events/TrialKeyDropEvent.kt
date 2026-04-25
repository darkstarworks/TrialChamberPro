package io.github.darkstarworks.trialChamberPro.api.events

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Fired immediately before the plugin drops a trial key for a wave participant
 * (provider-driven waves only — vanilla spawners drop their own keys via the
 * trial spawner state machine and do not pass through this event).
 *
 * Cancellable — listeners may suppress an individual key drop without
 * affecting other participants in the same wave.
 *
 * Fires once per participant per wave completion, so a four-player wave
 * produces four events.
 *
 * @property location  The drop location (centered on the spawner, slightly
 *                     above its block). Listeners may inspect but should not
 *                     mutate.
 * @property keyType   `Material.TRIAL_KEY` or `Material.OMINOUS_TRIAL_KEY`.
 * @property ownerUuid The participant the key is being dropped for. The
 *                     resulting item entity is tagged with this UUID for the
 *                     owner-only pickup window.
 *
 * @since v1.3.0
 */
class TrialKeyDropEvent(
    val location: Location,
    val keyType: Material,
    val ownerUuid: UUID
) : Event(!Bukkit.isPrimaryThread()), Cancellable {

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
