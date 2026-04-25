package io.github.darkstarworks.trialChamberPro.providers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity

/**
 * InfernalMobs integration via reflection (v1.3.0).
 *
 * InfernalMobs adds boss abilities to vanilla mobs. Mob id format is simply
 * an entity type name:
 *
 *   `ENTITY_TYPE`   e.g. `ZOMBIE`, `BLAZE`, `WITHER_SKELETON`
 *
 * A `:abilities` suffix is tolerated for backward compatibility but ignored —
 * the upstream `makeInfernal` method picks abilities randomly from config.
 *
 * Upstream (SpigotMC 2156 fork, okocraft/InfernalMobs):
 *   class io.hotmail.com.jacob_vejvoda.infernal_mobs.infernal_mobs
 *     fun makeInfernal(Entity entity, boolean fixed)
 */
class InfernalMobsProvider(private val plugin: TrialChamberPro) : TrialMobProvider {

    override val id: String = "infernalmobs"
    override val displayName: String = "InfernalMobs"

    @Volatile private var cachedAvailable: Boolean? = null

    override fun isAvailable(): Boolean {
        cachedAvailable?.let { return it }
        val present = Bukkit.getPluginManager().getPlugin("InfernalMobs")?.isEnabled == true
        if (!present) { cachedAvailable = false; return false }
        return try {
            Class.forName("io.hotmail.com.jacob_vejvoda.infernal_mobs.infernal_mobs")
            cachedAvailable = true
            true
        } catch (_: Throwable) {
            cachedAvailable = false
            false
        }
    }

    fun invalidate() { cachedAvailable = null }

    override fun spawnMob(mobId: String, location: Location, ominous: Boolean): Entity? {
        val (typeStr, _) = parseId(mobId)
        val type = runCatching { EntityType.valueOf(typeStr.uppercase()) }.getOrNull() ?: return null
        if (!type.isSpawnable || !LivingEntity::class.java.isAssignableFrom(type.entityClass ?: return null)) return null

        val world = location.world ?: return null
        val entity = world.spawnEntity(location, type) as? LivingEntity ?: return null

        if (!isAvailable()) return entity

        try {
            val cls = Class.forName("io.hotmail.com.jacob_vejvoda.infernal_mobs.infernal_mobs")
            val pluginInst = Bukkit.getPluginManager().getPlugin("InfernalMobs") ?: return entity
            val makeInfernal = cls.getMethod("makeInfernal", Entity::class.java, java.lang.Boolean.TYPE)
            makeInfernal.invoke(pluginInst, entity, false)
        } catch (e: Throwable) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.warning("[InfernalMobsProvider] Could not enrich '$mobId': ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        return entity
    }

    override fun validateMobId(mobId: String): Boolean {
        val (typeStr, _) = parseId(mobId)
        return runCatching { EntityType.valueOf(typeStr.uppercase()) }.isSuccess
    }

    private fun parseId(raw: String): Pair<String, String?> {
        val trimmed = raw.trim()
        val idx = trimmed.indexOf(':')
        return if (idx < 0) trimmed to null else trimmed.substring(0, idx) to trimmed.substring(idx + 1)
    }
}
