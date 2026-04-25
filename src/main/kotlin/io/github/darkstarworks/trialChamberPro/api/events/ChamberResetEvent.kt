package io.github.darkstarworks.trialChamberPro.api.events

import io.github.darkstarworks.trialChamberPro.models.Chamber
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Fired immediately before a chamber begins resetting. Cancellable — listeners
 * may abort the reset entirely (e.g. to defer it until a region is empty, or to
 * substitute a custom reset implementation).
 *
 * Fires on whichever thread `ResetManager.resetChamber` is invoked from. On
 * Paper that is typically a coroutine-IO thread (not primary), so the event is
 * delivered as asynchronous; on Folia the same applies. Listeners that need
 * Bukkit-API access should schedule onto the appropriate region thread
 * themselves.
 *
 * @property chamber The chamber about to reset.
 * @property reason  Why the reset was triggered.
 * @property triggeringPlayer The player who initiated the reset, if any. Null
 *           for scheduled / automatic resets.
 *
 * @since v1.3.0
 */
class ChamberResetEvent(
    val chamber: Chamber,
    val reason: Reason,
    val triggeringPlayer: Player?
) : Event(!Bukkit.isPrimaryThread()), Cancellable {

    enum class Reason {
        /** Auto-reset triggered by the chamber's `reset-interval`. */
        SCHEDULED,
        /** Operator/admin invoked `/tcp reset` or used the GUI Reset button. */
        MANUAL,
        /** A force-reset bypassing normal preconditions (Shift+Right-click in GUI, etc.). */
        FORCED
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
