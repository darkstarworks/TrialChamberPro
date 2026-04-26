package io.github.darkstarworks.trialChamberPro.api

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import org.bukkit.plugin.Plugin

/**
 * Lifecycle contract for an external plugin that extends TrialChamberPro.
 *
 * Premium add-on modules and third-party integrations both implement this
 * interface and register themselves with [TCPModuleRegistry] during their
 * own `onEnable`. The registry calls back into [onLoad] once TrialChamberPro
 * is fully initialized (i.e. `plugin.isReady == true`), so modules can
 * safely touch managers, listeners, and the database from within [onLoad]
 * without worrying about startup-order races.
 *
 * Modules unregister automatically when their backing plugin is disabled —
 * the registry watches `PluginDisableEvent` and invokes [onUnload] on
 * matching modules. Manual unregistration is also available via
 * [TCPModuleRegistry.unregister] for hot-reload scenarios.
 *
 * Implementations should be idempotent: [onLoad] may be called once per
 * TCP enable cycle, [onUnload] once per disable. Modules must NOT throw
 * from either method — uncaught exceptions are logged and the module is
 * marked failed but the registry continues processing other modules.
 *
 * @since v1.3.3
 */
interface TCPModule {
    /**
     * The owning Bukkit plugin. Used for log prefixes, listener
     * registration, and tying the module's lifecycle to its plugin's
     * disable. Must return the same instance for the lifetime of the module.
     */
    val plugin: Plugin

    /**
     * Stable id for this module. Used in registry lookups, logs, and
     * the module's own persistent state. Should match the convention
     * `vendor:module-name` (e.g. `darkstarworks:vault-crate`) but the
     * registry only enforces uniqueness, not format.
     */
    val id: String

    /**
     * Human-readable display name for log messages and `/tcp modules`-style
     * status output (a future free-side command, not shipped in v1.3.3).
     */
    val displayName: String

    /**
     * Called by [TCPModuleRegistry] once registration completes AND
     * TrialChamberPro is fully initialized. Modules typically use this to
     * register their Bukkit listeners, install database extensions, and
     * connect to TCP managers.
     *
     * Always invoked on the primary thread. Safe to touch all Bukkit APIs.
     *
     * @param tcp The TrialChamberPro plugin instance. Provides access to
     *            managers, scheduler, database, etc.
     */
    fun onLoad(tcp: TrialChamberPro)

    /**
     * Called when the module's backing plugin is disabled, OR when
     * TrialChamberPro itself is disabled (in which case modules are
     * unloaded in reverse registration order before TCP tears down). Use
     * this to unregister listeners, close database connections opened by
     * the module, and release any references to TCP internals.
     *
     * Always invoked on the primary thread.
     *
     * @param tcp The TrialChamberPro plugin instance.
     */
    fun onUnload(tcp: TrialChamberPro)
}
