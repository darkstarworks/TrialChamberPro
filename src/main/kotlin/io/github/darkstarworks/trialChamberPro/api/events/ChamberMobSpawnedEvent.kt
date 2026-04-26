package io.github.darkstarworks.trialChamberPro.api.events

import io.github.darkstarworks.trialChamberPro.models.Chamber
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Fired after a trial-spawner wave mob has been spawned and recorded by
 * `SpawnerWaveManager` — covers BOTH vanilla spawns AND replacement spawns
 * produced by a custom mob provider (MythicMobs, EliteMobs, etc.).
 *
 * Not cancellable; the entity already exists in the world. To prevent or
 * substitute spawns, intercept earlier — either via Bukkit's
 * `CreatureSpawnEvent` with `SpawnReason.TRIAL_SPAWNER`, or by registering a
 * `TrialMobProvider` on the chamber.
 *
 * The point of this event is to give third-party plugins a single hook with
 * **chamber + wave context attached** — what `CreatureSpawnEvent` doesn't
 * carry. Useful for difficulty scaling (level the mob based on wave
 * progress), boss-bar augmentation, ML/analytics, or seasonal modifiers
 * that need to know "which chamber, which spawner, normal or ominous wave".
 *
 * Fires on the spawn callback thread. On Paper that's the primary thread;
 * on Folia it's the entity's region thread. Asynchronous flag is computed
 * at fire time so listeners can rely on `event.isAsynchronous()`.
 *
 * Primary intended consumers:
 * - Premium "Legendary Trials" scaling module (per-mob level adjustment)
 * - Server-specific plugins that decorate trial mobs with extra equipment,
 *   custom names, or status effects
 * - Analytics / metrics pipelines tracking spawner activity by chamber
 *
 * @property entity          The mob that was just spawned. After custom-
 *                           provider replacement this is the *replacement*
 *                           entity, not the original vanilla one (which has
 *                           been removed). Always non-null and alive at
 *                           fire time.
 * @property spawnerLocation The trial spawner block that produced this mob.
 * @property chamber         The registered chamber this spawner belongs to.
 *                           **Null for wild spawners** (placed outside any
 *                           registered chamber) — listeners that only care
 *                           about chamber spawns should bail when this is
 *                           null.
 * @property isOminous       Whether this is an ominous-wave spawn.
 * @property providerId      Id of the [TrialMobProvider] that produced this
 *                           mob. `"vanilla"` if the spawn was not replaced
 *                           by a custom provider; otherwise the provider's
 *                           registered id (`"mythicmobs"`, `"elitemobs"`,
 *                           etc.). Listeners that only care about a
 *                           specific provider can compare directly.
 *
 * @since v1.3.3
 */
class ChamberMobSpawnedEvent(
    val entity: Entity,
    val spawnerLocation: Location,
    val chamber: Chamber?,
    val isOminous: Boolean,
    val providerId: String
) : Event(!Bukkit.isPrimaryThread()) {

    override fun getHandlers(): HandlerList = HANDLERS

    companion object {
        @JvmStatic
        private val HANDLERS = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLERS
    }
}
