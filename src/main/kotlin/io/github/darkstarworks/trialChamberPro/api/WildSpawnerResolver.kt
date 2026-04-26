package io.github.darkstarworks.trialChamberPro.api

import org.bukkit.Location

/**
 * Pluggable lookup for "should this wild trial spawner spawn custom-plugin
 * mobs, and if so, which provider + mob ids?".
 *
 * **Why this exists:** TCP's free-tier custom mob provider system
 * (v1.3.0+) only intercepts spawns inside a *registered chamber* — see the
 * `chamber != null && chamber.hasCustomMobProvider(...)` gate at
 * `SpawnerWaveListener.kt`. Wild spawners (placed by players, often from
 * a `/tcp give <preset>` item) skip the intercept entirely and spawn
 * whatever vanilla mob their datapack config defines. Lifting this
 * limitation is part of the planned premium "Wild Custom-Mob Spawners"
 * module — but the *seam* lives in the free plugin so the premium module
 * can register a resolver and TCP will consult it transparently.
 *
 * **How a premium / third-party module integrates:**
 * 1. Implement [resolve] to return a [Config] (or null) for a given
 *    spawner location + optional preset id (`tcp:preset_id` PDC tag,
 *    written by `SpawnerPresetManager` v1.4.0+).
 * 2. Register the implementation as a Bukkit service in your plugin's
 *    `onEnable`:
 *    ```kotlin
 *    Bukkit.getServicesManager().register(
 *        WildSpawnerResolver::class.java,
 *        MyResolver(),
 *        myPlugin,
 *        ServicePriority.Normal
 *    )
 *    ```
 * 3. From now on, every wild trial-spawner spawn TCP observes will pass
 *    through your resolver. Returning a non-null [Config] triggers TCP's
 *    standard replace-after-spawn logic (vanilla mob removed same tick,
 *    custom provider mob spawned at the same location, wave tracking
 *    preserved). Returning `null` leaves the vanilla spawn intact.
 *
 * Only one resolver is active at a time (the one with the highest service
 * priority). To compose multiple sources, the active resolver itself
 * dispatches between them.
 *
 * @since v1.4.0
 */
interface WildSpawnerResolver {

    /**
     * Configuration for a custom-mob substitution at a wild spawner.
     *
     * @property providerId Id of a registered [io.github.darkstarworks.trialChamberPro.providers.TrialMobProvider]
     *                      (e.g. `mythicmobs`, `elitemobs`). Must match a
     *                      provider currently in `plugin.trialMobProviderRegistry`,
     *                      otherwise the spawn falls back to vanilla.
     * @property normalIds  Mob ids to choose from on normal waves. May be
     *                      empty.
     * @property ominousIds Mob ids to choose from on ominous waves. If
     *                      empty, ominous waves fall back to [normalIds]
     *                      (matching the chamber-mode behavior).
     */
    data class Config(
        val providerId: String,
        val normalIds: List<String>,
        val ominousIds: List<String>
    ) {
        /**
         * Returns a single mob id to spawn for this wave, or null if no
         * mob id is available (caller falls back to the vanilla spawn).
         * Picks at random from the appropriate list.
         */
        fun pickMobId(isOminous: Boolean): String? {
            val list = if (isOminous && ominousIds.isNotEmpty()) ominousIds else normalIds
            return list.takeIf { it.isNotEmpty() }?.random()
        }
    }

    /**
     * Decide whether this wild spawner should spawn custom mobs. Called on
     * every wild-spawner spawn observation, so implementations should be
     * fast and side-effect-free (a hashmap lookup against config, ideally).
     *
     * @param spawnerLocation The trial spawner block location.
     * @param presetId If the spawner was placed from a TCP preset item
     *                 (via `/tcp give`), the preset's id. `null` for
     *                 spawners placed by other means (vanilla `/give`,
     *                 schematic paste, etc.).
     * @return A [Config] to apply, or `null` to leave the vanilla spawn
     *         intact.
     */
    fun resolve(spawnerLocation: Location, presetId: String?): Config?
}
