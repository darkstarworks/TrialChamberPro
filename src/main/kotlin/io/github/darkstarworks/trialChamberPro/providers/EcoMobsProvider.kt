package io.github.darkstarworks.trialChamberPro.providers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity

/**
 * EcoMobs (Auxilor) integration via reflection (v1.3.0).
 *
 * Hard-depends on the Eco framework plugin; EcoMobs will not load without it.
 *
 * Upstream API (verified against https://github.com/Auxilor/EcoMobs):
 *   object EcoMobs { operator fun get(id: String): EcoMob? }
 *   interface EcoMob { fun spawn(loc: Location, reason: SpawnReason): LivingMob? }
 *   interface LivingMob { val entity: Mob }              // exposed as getEntity()
 *
 * Mob id = the entry id from `plugins/EcoMobs/mobs/<id>.yml`.
 */
class EcoMobsProvider(private val plugin: TrialChamberPro) : TrialMobProvider {

    override val id: String = "ecomobs"
    override val displayName: String = "EcoMobs"

    @Volatile private var cachedAvailable: Boolean? = null

    override fun isAvailable(): Boolean {
        cachedAvailable?.let { return it }
        val present = Bukkit.getPluginManager().getPlugin("EcoMobs")?.isEnabled == true
        if (!present) { cachedAvailable = false; return false }
        return try {
            Class.forName("com.willfp.ecomobs.mob.EcoMobs")
            Class.forName("com.willfp.ecomobs.mob.SpawnReason")
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
            val registryCls = Class.forName("com.willfp.ecomobs.mob.EcoMobs")
            val registry = registryCls.getField("INSTANCE").get(null)

            // `operator fun get(String): EcoMob?` — `get` is the Java-visible name.
            val getMethod = registryCls.methods.firstOrNull {
                it.name == "get" && it.parameterCount == 1 && it.parameterTypes[0] == String::class.java
            } ?: return null
            val mob = getMethod.invoke(registry, mobId) ?: return null

            val spawnReasonCls = Class.forName("com.willfp.ecomobs.mob.SpawnReason")
            val spawnReason = spawnReasonCls.enumConstants.firstOrNull {
                (it as Enum<*>).name.equals("CUSTOM", ignoreCase = true)
            } ?: spawnReasonCls.enumConstants.firstOrNull() ?: return null

            val spawn = mob.javaClass.methods.firstOrNull {
                it.name == "spawn" && it.parameterCount == 2 &&
                    it.parameterTypes[0] == Location::class.java &&
                    it.parameterTypes[1] == spawnReasonCls
            } ?: return null
            val livingMob = spawn.invoke(mob, location, spawnReason) ?: return null

            // LivingMob.entity is a Mob (org.bukkit.entity.Mob); exposed as getEntity()
            val getEntity = livingMob.javaClass.methods.firstOrNull {
                it.name == "getEntity" && it.parameterCount == 0
            } ?: return null
            getEntity.invoke(livingMob) as? Entity
        } catch (e: Throwable) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.warning("[EcoMobsProvider] Failed to spawn '$mobId': ${e.javaClass.simpleName}: ${e.message}")
            }
            null
        }
    }

    override fun validateMobId(mobId: String): Boolean = mobId.isNotBlank()
}
