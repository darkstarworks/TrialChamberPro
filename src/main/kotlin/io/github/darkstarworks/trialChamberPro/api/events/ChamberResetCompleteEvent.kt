package io.github.darkstarworks.trialChamberPro.api.events

import io.github.darkstarworks.trialChamberPro.models.Chamber
import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Fired after a chamber has finished resetting (block restoration done, vault
 * cooldowns cleared, spawners reset). Not cancellable — at this point the work
 * has already happened.
 *
 * Useful for triggering follow-up announcements, scoreboards, or webhook
 * notifications. The matching pre-event is [ChamberResetEvent].
 *
 * @property chamber         The chamber that finished resetting.
 * @property durationMs      Wall-clock duration of the reset, in milliseconds.
 *                           Includes the snapshot restore and post-restore
 *                           bookkeeping.
 * @property blocksRestored  Number of blocks the snapshot apply touched. Zero
 *                           when the chamber had no snapshot (an unusual but
 *                           supported configuration).
 *
 * @since v1.3.0
 */
class ChamberResetCompleteEvent(
    val chamber: Chamber,
    val durationMs: Long,
    val blocksRestored: Int
) : Event(!Bukkit.isPrimaryThread()) {

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
