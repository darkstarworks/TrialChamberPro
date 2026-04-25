package io.github.darkstarworks.trialChamberPro.providers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity

/**
 * Citizens integration via reflection (v1.3.0).
 *
 * Mob id is either a numeric Citizens NPC id (preferred — stable across
 * renames) or an exact NPC name. The provider looks the NPC up in the
 * default registry, `clone()`s it so the template NPC isn't moved, and
 * spawns the clone at [location].
 *
 *   CitizensAPI.getNPCRegistry().getById(int) / iterator → NPC
 *   npc.clone() → NPC
 *   clone.spawn(Location) → boolean
 *   clone.getEntity() → org.bukkit.entity.Entity
 *
 * If the template lookup fails the provider returns null and the wave falls
 * back to vanilla for this spawn.
 */
class CitizensProvider(private val plugin: TrialChamberPro) : TrialMobProvider {

    override val id: String = "citizens"
    override val displayName: String = "Citizens"

    @Volatile private var cachedAvailable: Boolean? = null

    override fun isAvailable(): Boolean {
        cachedAvailable?.let { return it }
        val present = Bukkit.getPluginManager().getPlugin("Citizens")?.isEnabled == true
        if (!present) { cachedAvailable = false; return false }
        return try {
            Class.forName("net.citizensnpcs.api.CitizensAPI")
            cachedAvailable = true
            true
        } catch (_: Throwable) {
            cachedAvailable = false
            false
        }
    }

    fun invalidate() { cachedAvailable = null }

    override fun spawnMob(mobId: String, location: Location, ominous: Boolean): Entity? {
        if (!isAvailable()) return null
        return try {
            val apiCls = Class.forName("net.citizensnpcs.api.CitizensAPI")
            val registry = apiCls.getMethod("getNPCRegistry").invoke(null) ?: return null

            val template: Any = run<Any?> {
                val asInt = mobId.toIntOrNull()
                if (asInt != null) {
                    val getById = registry.javaClass.methods.firstOrNull {
                        it.name == "getById" && it.parameterCount == 1 && it.parameterTypes[0] == java.lang.Integer.TYPE
                    }
                    getById?.invoke(registry, asInt)
                } else {
                    // Iterate the registry looking for a name match.
                    val iteratorMethod = registry.javaClass.methods.firstOrNull { it.name == "iterator" && it.parameterCount == 0 }
                    val it = iteratorMethod?.invoke(registry) as? Iterator<*> ?: return@run null
                    var found: Any? = null
                    while (it.hasNext()) {
                        val npc = it.next() ?: continue
                        val name = runCatching { npc.javaClass.getMethod("getName").invoke(npc) as? String }.getOrNull()
                        if (name?.equals(mobId, ignoreCase = true) == true) { found = npc; break }
                    }
                    found
                }
            } ?: return null

            val cloneMethod = template.javaClass.methods.firstOrNull { it.name == "clone" && it.parameterCount == 0 }
                ?: return null
            val clone = cloneMethod.invoke(template) ?: return null
            val spawn = clone.javaClass.methods.firstOrNull {
                it.name == "spawn" && it.parameterCount == 1 && it.parameterTypes[0] == Location::class.java
            } ?: return null
            spawn.invoke(clone, location)
            val getEntity = clone.javaClass.methods.firstOrNull { it.name == "getEntity" && it.parameterCount == 0 }
            getEntity?.invoke(clone) as? Entity
        } catch (e: Throwable) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.warning("[CitizensProvider] Failed to spawn NPC '$mobId': ${e.javaClass.simpleName}: ${e.message}")
            }
            null
        }
    }

    override fun validateMobId(mobId: String): Boolean = mobId.isNotBlank()
}
