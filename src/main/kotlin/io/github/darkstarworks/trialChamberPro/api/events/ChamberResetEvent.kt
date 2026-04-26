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
 * **Snapshot substitution (v1.4.0+):** Listeners can override the snapshot
 * data restored during the reset by setting [snapshotOverride] to a non-null
 * `ByteArray`. The bytes must be a gzip-compressed serialized
 * `SnapshotData` in TCP's native snapshot format. Typically the override
 * comes from a separate snapshot file (read via `Files.readAllBytes`) or
 * from a snapshot blob produced by serializing fresh `SnapshotData`. If
 * the override bytes fail to deserialize, the reset falls back to the
 * chamber's on-disk snapshot and a warning is logged. If [snapshotOverride]
 * is left null (the default), the chamber's saved snapshot file is used
 * as normal.
 *
 * Designed for premium / third-party "schematic injection" or "instance
 * variant" workflows that want to swap room contents per reset cycle
 * without persistently modifying the chamber's snapshot on disk.
 *
 * @property chamber The chamber about to reset.
 * @property reason  Why the reset was triggered.
 * @property triggeringPlayer The player who initiated the reset, if any. Null
 *           for scheduled / automatic resets.
 * @property snapshotOverride Mutable. If set non-null by a listener, the
 *           specified gzip-compressed snapshot bytes are restored instead
 *           of the chamber's on-disk snapshot. Defaults to `null`. Added
 *           in v1.4.0.
 *
 * @since v1.3.0
 */
class ChamberResetEvent(
    val chamber: Chamber,
    val reason: Reason,
    val triggeringPlayer: Player?,
    var snapshotOverride: ByteArray? = null
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
