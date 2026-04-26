package io.github.darkstarworks.trialChamberPro.api

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginDisableEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Central registry for [TCPModule] implementations. One instance lives on
 * the [TrialChamberPro] plugin and is exposed as
 * `plugin.moduleRegistry`.
 *
 * **Registration order is preserved**: [getAll] returns modules in the
 * order they registered, and [shutdownAll] unloads them in reverse. This
 * matters when modules depend on each other (a dependent module
 * registered later sees its dependency unloaded first only if it
 * registered later — modules with cross-dependencies should declare them
 * via Bukkit's `softdepend` so the plugin manager handles the order).
 *
 * **Lifecycle integration**:
 * - [register] called by an external plugin's `onEnable` adds the module
 *   to the map and, if TCP is already ready, immediately invokes
 *   `module.onLoad(tcp)`. If TCP is still initializing, the call is
 *   deferred until [loadAllPending] is invoked at the end of TCP startup.
 * - [unregister] manually removes a module and invokes its `onUnload`.
 * - [PluginDisableEvent] is observed: when an external plugin is disabled,
 *   any modules backed by it are auto-unregistered in reverse registration
 *   order.
 * - [shutdownAll] is called by `TrialChamberPro.onDisable` to unload every
 *   remaining module before TCP itself tears down.
 *
 * **Thread-safety**: all public methods are safe to call from any thread.
 * Lifecycle callbacks ([TCPModule.onLoad], [TCPModule.onUnload]) are
 * always dispatched on the primary thread via the scheduler adapter, even
 * if the originating call came from an async context.
 *
 * @since v1.3.3
 */
class TCPModuleRegistry(private val plugin: TrialChamberPro) : Listener {

    private val modules = ConcurrentHashMap<String, TCPModule>()
    // Preserve registration order independently of the ConcurrentHashMap iteration order
    // for predictable load/unload sequencing.
    private val order = CopyOnWriteArrayList<String>()
    private val pending = CopyOnWriteArrayList<TCPModule>()

    @Volatile
    private var loadedAll = false

    /**
     * Register a module. If TCP is already fully initialized, the module's
     * `onLoad` runs immediately on the primary thread; otherwise the
     * module is queued and loaded by [loadAllPending] when TCP finishes
     * its async startup.
     *
     * Re-registering an id is rejected with a warning — modules are
     * expected to be singletons.
     */
    fun register(module: TCPModule) {
        val existing = modules.putIfAbsent(module.id, module)
        if (existing != null) {
            plugin.logger.warning(
                "[TCPModule] Refusing to register duplicate module id '${module.id}' (already registered by ${existing.plugin.name})"
            )
            return
        }
        order += module.id
        plugin.logger.info("[TCPModule] Registered: ${module.displayName} (${module.id}) from ${module.plugin.name}")

        if (loadedAll) {
            dispatchLoad(module)
        } else {
            pending += module
        }
    }

    /**
     * Manually unregister a module. Invokes [TCPModule.onUnload] on the
     * primary thread, then removes the module from the registry. Safe to
     * call multiple times — second call is a no-op.
     */
    fun unregister(id: String) {
        val module = modules.remove(id) ?: return
        order -= id
        pending -= module
        dispatchUnload(module)
    }

    fun get(id: String): TCPModule? = modules[id]

    /**
     * Snapshot of all currently-registered modules in registration order.
     * Safe to iterate; the list is a copy.
     */
    fun getAll(): List<TCPModule> = order.mapNotNull { modules[it] }

    /**
     * Called once by [TrialChamberPro] at the end of its async startup
     * (after `isReady = true`). Loads every module that registered before
     * TCP was ready.
     *
     * Safe to call multiple times; subsequent calls are no-ops.
     */
    fun loadAllPending() {
        if (loadedAll) return
        loadedAll = true
        val toLoad = pending.toList()
        pending.clear()
        for (module in toLoad) {
            dispatchLoad(module)
        }
    }

    /**
     * Called by [TrialChamberPro.onDisable]. Unloads every remaining
     * module in reverse registration order, then clears the registry.
     */
    fun shutdownAll() {
        for (id in order.reversed().toList()) {
            val module = modules.remove(id) ?: continue
            dispatchUnload(module)
        }
        order.clear()
        pending.clear()
    }

    /**
     * Auto-unregister any modules whose backing plugin is being disabled.
     * Registered as a Bukkit listener on TCP's plugin manager.
     */
    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        val disabled = event.plugin
        // Snapshot to avoid mutating the order list while iterating.
        val victims = order.mapNotNull { modules[it] }.filter { it.plugin === disabled }
        for (module in victims.reversed()) {
            unregister(module.id)
        }
    }

    private fun dispatchLoad(module: TCPModule) {
        plugin.scheduler.runTask(Runnable {
            try {
                module.onLoad(plugin)
                plugin.logger.info("[TCPModule] Loaded: ${module.displayName}")
            } catch (e: Throwable) {
                plugin.logger.severe("[TCPModule] onLoad failed for ${module.id}: ${e.message}")
                e.printStackTrace()
            }
        })
    }

    private fun dispatchUnload(module: TCPModule) {
        plugin.scheduler.runTask(Runnable {
            try {
                module.onUnload(plugin)
                plugin.logger.info("[TCPModule] Unloaded: ${module.displayName}")
            } catch (e: Throwable) {
                plugin.logger.severe("[TCPModule] onUnload failed for ${module.id}: ${e.message}")
                e.printStackTrace()
            }
        })
    }
}
