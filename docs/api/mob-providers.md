# 🧟 Custom Mob Providers (Developer Guide)

This page is for plugin developers who want to register a new mob source with TrialChamberPro. If you're a server admin looking to *use* an existing provider (MythicMobs, EliteMobs, EcoMobs, LevelledMobs, InfernalMobs, Citizens), see [Custom Mobs](../configuration/custom-mobs.md) instead.

## Overview

Trial spawner waves go through a "replace-after-spawn" pipeline:

1. The vanilla trial spawner spawns its mob normally.
2. `SpawnerWaveListener` checks the chamber's configured provider on `CreatureSpawnEvent`.
3. If the provider is non-vanilla and available, the freshly-spawned vanilla mob is removed the same tick.
4. The provider's `spawnMob(...)` is called at the same location with the configured mob id.
5. The returned entity is tracked by the wave system as if it were the original spawn — boss bars, kill counts, cooldowns, and key drops all continue to work.

Your provider plugs into step 4. Everything else is handled.

## The interface

```kotlin
package io.github.darkstarworks.trialChamberPro.providers

import org.bukkit.Location
import org.bukkit.entity.Entity

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
     * @return the spawned entity, or null on failure. Null causes the wave system
     *         to skip tracking this spawn (the vanilla mob is already gone, so
     *         that wave-slot is effectively skipped).
     */
    fun spawnMob(mobId: String, location: Location, ominous: Boolean): Entity?

    /** Cheap validation for GUI/YAML inputs. May return true optimistically. */
    fun validateMobId(mobId: String): Boolean
}
```

## Implementing a provider

A minimal example that integrates a hypothetical "BossPlugin":

```kotlin
class BossPluginProvider(private val tcp: TrialChamberPro) : TrialMobProvider {
    override val id = "bossplugin"
    override val displayName = "BossPlugin"

    private var available: Boolean? = null

    override fun isAvailable(): Boolean {
        // Cache the answer — `isAvailable` may be called many times per tick.
        available?.let { return it }
        val present = Bukkit.getPluginManager().getPlugin("BossPlugin")?.isEnabled == true
        return (present.also { available = it })
    }

    override fun spawnMob(mobId: String, location: Location, ominous: Boolean): Entity? {
        if (!isAvailable()) return null
        return try {
            // Whatever your plugin's API looks like:
            BossPlugin.getInstance().spawn(mobId, location)
        } catch (e: Exception) {
            if (tcp.config.getBoolean("debug.verbose-logging", false)) {
                tcp.logger.warning("[bossplugin] spawn failed for '$mobId': ${e.message}")
            }
            null
        }
    }

    override fun validateMobId(mobId: String): Boolean = mobId.isNotBlank()
}
```

## Registering with the registry

Register in your `onEnable` after TrialChamberPro has loaded. Use a soft-dependency so you don't hard-fail when TCP is missing:

**plugin.yml:**

```yaml
softdepend: [TrialChamberPro]
```

**MyPlugin.kt:**

```kotlin
override fun onEnable() {
    val tcp = server.pluginManager.getPlugin("TrialChamberPro") as? TrialChamberPro
    if (tcp == null) {
        logger.info("TrialChamberPro not present — skipping provider registration.")
        return
    }
    tcp.trialMobProviderRegistry.register(BossPluginProvider(tcp))
}
```

Once registered, server admins can select your provider via `/tcp mobs <chamber> provider bossplugin` or through the per-chamber **Custom Mob Provider** GUI screen.

## Patterns to follow

The six built-in providers ship reflection-based integrations rather than compile-time bindings. The pattern is consistent across all of them and worth reusing if you want to support multiple plugin versions or avoid pulling in a heavy dependency:

```kotlin
override fun spawnMob(mobId: String, location: Location, ominous: Boolean): Entity? {
    if (!isAvailable()) return null
    return try {
        val cls = Class.forName("com.example.bossplugin.api.BossManager")
        val instance = cls.getMethod("getInstance").invoke(null)
        val spawn = cls.getMethod("spawn", String::class.java, Location::class.java)
        spawn.invoke(instance, mobId, location) as? Entity
    } catch (e: ClassNotFoundException) {
        null  // backing plugin missing — caller falls back to vanilla
    } catch (e: NoSuchMethodException) {
        tcp.logger.warning("[bossplugin] API signature mismatch — version skew?")
        null
    }
}
```

## Conventions and gotchas

- **`spawnMob` runs on the spawner's region thread** during a live `CreatureSpawnEvent`. It must be non-blocking — no I/O, no waiting on async results, no `runBlocking`. If you genuinely need async work to determine the spawn, schedule it and return `null` to skip the slot.
- **Return `null` to fail-soft.** The wave will continue, the spawn-slot will be skipped, and `SpawnerWaveListener` will log a warning under `debug.verbose-logging`. Never throw from `spawnMob` — it runs inside an event handler and an uncaught exception will affect every later listener.
- **Cache `isAvailable()`.** Listeners call it on the hot path; reflective `Bukkit.getPluginManager().getPlugin(...)` checks every tick add up.
- **Mob id format is yours to define.** Use whatever your plugin's natural identifier is. Document it on the [Custom Mobs](../configuration/custom-mobs.md) page (or similar) so admins know what to put in `customMobIdsNormal` / `customMobIdsOminous`.
- **The `ominous` flag is captured at wave start** and cannot flip mid-wave. You can use it to pick a harder mob variant for ominous trials, but the wave system itself handles the ominous boss bar / loot table swap.
- **Register early** — providers registered after a chamber has already started a wave will not be seen by that wave. The startup phase in `TrialChamberPro.onEnable` is the right time.
- **One provider per id.** Re-registering the same `id` overwrites the previous instance.

## Listening to wave events

Once your mobs are integrated, you'll likely also want to react when a wave completes (drop your own loot, run a victory effect, etc.). Use [`SpawnerWaveCompleteEvent`](events.md#spawnerwavecompleteevent-not-cancellable) — it fires for both vanilla and provider-driven waves with a participants set, ominous flag, and the spawner location.

## Versioning

The `TrialMobProvider` interface is part of the v1.3.0+ public API. Method signatures, the `displayName` / `id` contract, and the registry's `register` / `get` / `all` methods are stable; new methods may be added with default implementations in minor releases. Removals or signature changes will go through a deprecation cycle and be flagged in the changelog.
