package io.github.darkstarworks.trialChamberPro.utils

import io.github.darkstarworks.trialChamberPro.scheduler.SchedulerAdapter
import kotlinx.coroutines.CoroutineDispatcher
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine dispatcher that runs on Bukkit's main thread (Paper)
 * or the global region thread (Folia).
 *
 * Required for operations that must execute on the main/region thread.
 */
fun Plugin.minecraftDispatcher(scheduler: SchedulerAdapter): CoroutineDispatcher {
    return object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (!scheduler.isFolia && Bukkit.isPrimaryThread()) {
                block.run()
            } else {
                scheduler.runTask(block)
            }
        }
    }
}

/**
 * Coroutine dispatcher that runs on the region thread owning a specific location.
 * On Paper, this is the main thread.
 * On Folia, this is the region thread for that location.
 *
 * Use this when modifying blocks or accessing location-specific world data.
 */
fun Plugin.locationDispatcher(scheduler: SchedulerAdapter, location: Location): CoroutineDispatcher {
    return object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            scheduler.runAtLocation(location, block)
        }
    }
}

/**
 * Coroutine dispatcher that runs on the region thread owning a specific entity.
 * On Paper, this is the main thread.
 * On Folia, this is the region thread for that entity.
 *
 * Use this when modifying entity state, inventory, or teleporting.
 */
fun Plugin.entityDispatcher(scheduler: SchedulerAdapter, entity: Entity): CoroutineDispatcher {
    return object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            scheduler.runAtEntity(entity, block)
        }
    }
}

/**
 * Legacy dispatcher for backwards compatibility.
 * Prefer using minecraftDispatcher(scheduler) for new code.
 */
val Plugin.minecraftDispatcher: CoroutineDispatcher
    get() = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (server.isPrimaryThread) {
                block.run()
            } else {
                server.scheduler.runTask(this@minecraftDispatcher, block)
            }
        }
    }
