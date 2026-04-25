package io.github.darkstarworks.trialChamberPro.providers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity

/**
 * LevelledMobs integration via reflection (v1.3.0).
 *
 * LevelledMobs doesn't add new mob types — it levels existing ones. This
 * provider therefore spawns a vanilla [EntityType] and then asks LevelledMobs
 * to apply a level. Mob id format:
 *
 *   `ENTITY_TYPE[:level]`     e.g. `ZOMBIE`, `ZOMBIE:15`, `HUSK:ominous`
 *
 * The optional `:level` suffix can be a number or the keyword `ominous` to let
 * LevelledMobs pick a level using the wave's ominous flag (maps to a random
 * high-tier roll). Unknown suffixes fall back to LevelledMobs' default rolling.
 */
class LevelledMobsProvider(private val plugin: TrialChamberPro) : TrialMobProvider {

    override val id: String = "levelledmobs"
    override val displayName: String = "LevelledMobs"

    @Volatile private var cachedAvailable: Boolean? = null

    override fun isAvailable(): Boolean {
        cachedAvailable?.let { return it }
        val present = Bukkit.getPluginManager().getPlugin("LevelledMobs")?.isEnabled == true
        cachedAvailable = present
        return present
    }

    fun invalidate() { cachedAvailable = null }

    override fun spawnMob(mobId: String, location: Location, ominous: Boolean): Entity? {
        val (typeStr, levelPart) = parseId(mobId)
        val type = runCatching { EntityType.valueOf(typeStr.uppercase()) }.getOrNull() ?: return null
        if (!type.isSpawnable || !LivingEntity::class.java.isAssignableFrom(type.entityClass ?: return null)) return null

        val world = location.world ?: return null
        val entity = world.spawnEntity(location, type) as? LivingEntity ?: return null

        if (!isAvailable()) return entity

        val requestedLevel: Int? = when {
            levelPart == null -> null
            levelPart.equals("ominous", ignoreCase = true) -> if (ominous) 20 else 10
            else -> levelPart.toIntOrNull()
        }

        try {
            val level = requestedLevel ?: return entity

            // LevelledMobs API: wrap LivingEntity in LivingEntityWrapper, then call
            // LevelInterface2.applyLevelToMob(wrapper, level, isSummoned, bypassLimits, additionalInfo)
            val wrapperCls = Class.forName("io.github.arcaneplugins.levelledmobs.wrappers.LivingEntityWrapper")
            val getInstanceWrap = wrapperCls.methods.firstOrNull {
                it.name == "getInstance" && it.parameterCount == 1 &&
                    it.parameterTypes[0].isAssignableFrom(LivingEntity::class.java)
            } ?: return entity
            val wrapper = getInstanceWrap.invoke(null, entity) ?: return entity

            val lmCls = Class.forName("io.github.arcaneplugins.levelledmobs.LevelledMobs")
            val lm = lmCls.getMethod("getInstance").invoke(null) ?: return entity

            val levelInterface = runCatching { lm.javaClass.getMethod("getLevelInterface").invoke(lm) }
                .getOrElse { runCatching { lm.javaClass.getMethod("getLevelManager").invoke(lm) }.getOrNull() }
                ?: return entity

            val apply = levelInterface.javaClass.methods.firstOrNull {
                it.name == "applyLevelToMob" && it.parameterCount >= 2 &&
                    it.parameterTypes[0] == wrapperCls &&
                    it.parameterTypes[1] == java.lang.Integer.TYPE
            } ?: return entity

            val args = arrayOfNulls<Any>(apply.parameterCount)
            args[0] = wrapper
            args[1] = level
            // Fill remaining params. Known 5-arg signature: (wrapper, level, isSummoned, bypassLimits, additionalInfo)
            for (i in 2 until apply.parameterCount) {
                args[i] = when (apply.parameterTypes[i]) {
                    java.lang.Boolean.TYPE -> i == 2 // isSummoned=true, bypassLimits=false
                    else -> null // MutableSet<AdditionalLevelInformation> accepts null
                }
            }
            apply.invoke(levelInterface, *args)
        } catch (e: Throwable) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.warning("[LevelledMobsProvider] Could not apply level to '$mobId': ${e.javaClass.simpleName}: ${e.message}")
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
