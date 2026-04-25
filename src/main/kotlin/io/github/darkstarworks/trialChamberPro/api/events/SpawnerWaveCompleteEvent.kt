package io.github.darkstarworks.trialChamberPro.api.events

import io.github.darkstarworks.trialChamberPro.models.Chamber
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.util.UUID

/**
 * Fired when a trial spawner finishes a wave (all spawned mobs have been
 * killed). Not cancellable. Wired in `SpawnerWaveManager.completeWave` so it
 * fires for both registered chambers and wild spawners.
 *
 * @property spawnerLocation Block-aligned location of the trial spawner.
 * @property chamber         The chamber containing the spawner, or `null` for
 *                           a wild spawner (one not inside any registered
 *                           chamber).
 * @property ominous         `true` if the wave was ominous-mode at the moment
 *                           it started. Captured at wave creation; cannot flip
 *                           mid-wave.
 * @property participants    UUIDs of every player credited as a participant
 *                           during the wave. May be empty if mobs were killed
 *                           by environmental damage with no nearby players.
 * @property durationMs      Wall-clock duration of the wave, from spawn-start
 *                           to last-mob-killed.
 *
 * @since v1.3.0
 */
class SpawnerWaveCompleteEvent(
    val spawnerLocation: Location,
    val chamber: Chamber?,
    val ominous: Boolean,
    val participants: Set<UUID>,
    val durationMs: Long
) : Event(!Bukkit.isPrimaryThread()) {

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
