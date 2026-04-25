package io.github.darkstarworks.trialChamberPro.providers

import org.bukkit.Location
import org.bukkit.entity.Entity

/**
 * Pluggable mob source for trial spawner waves.
 *
 * A provider is consulted from the [CreatureSpawnEvent] handler when a chamber
 * has a non-vanilla `customMobProvider` configured. The vanilla mob spawned by
 * the trial spawner is removed the same tick and [spawnMob] is called at the
 * same location. The returned entity is then tracked by
 * [io.github.darkstarworks.trialChamberPro.managers.SpawnerWaveManager] exactly
 * like a vanilla mob would be.
 *
 * Implementations must be non-blocking — they are called from the main/region
 * thread during a live event. If async work is required, schedule it internally
 * and return null to skip this spawn.
 */
interface TrialMobProvider {

    /** Short stable identifier stored in configuration (e.g. "vanilla", "mythicmobs"). */
    val id: String

    /** Human-readable name shown in GUI and messages. */
    val displayName: String

    /** Returns true when the backing plugin is present, enabled, and its API is reachable. */
    fun isAvailable(): Boolean

    /**
     * Spawns a custom mob at [location].
     *
     * @param mobId Provider-specific identifier (e.g. MythicMobs internal name)
     * @param location Where to spawn; caller has already validated chunk/region ownership
     * @param ominous True when the originating spawner was in ominous mode
     * @return The spawned entity, or null on failure (invalid id, plugin offline, etc.).
     *         Returning null causes the wave system to skip tracking this spawn.
     */
    fun spawnMob(mobId: String, location: Location, ominous: Boolean): Entity?

    /** Cheap validation for GUI/YAML inputs. May return true optimistically if the backing plugin isn't loaded yet. */
    fun validateMobId(mobId: String): Boolean
}
