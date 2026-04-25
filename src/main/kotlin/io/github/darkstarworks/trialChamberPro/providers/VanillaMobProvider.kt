package io.github.darkstarworks.trialChamberPro.providers

import org.bukkit.Location
import org.bukkit.entity.Entity

/**
 * No-op provider representing vanilla Minecraft trial spawners.
 *
 * The wave listener never actually calls [spawnMob] for this provider — when
 * `customMobProvider` is `"vanilla"` or null, the vanilla spawn is left alone
 * and tracked as-is. This object exists so lookups always succeed and the GUI
 * can render "Vanilla" as a selectable option.
 */
object VanillaMobProvider : TrialMobProvider {

    override val id: String = "vanilla"
    override val displayName: String = "Vanilla"

    override fun isAvailable(): Boolean = true

    /** Vanilla mobs are spawned by the trial spawner itself; this should never be invoked. */
    override fun spawnMob(mobId: String, location: Location, ominous: Boolean): Entity? = null

    /** No concept of a "mob id" for vanilla — the spawner controls it. Accept anything. */
    override fun validateMobId(mobId: String): Boolean = true
}
