package io.github.darkstarworks.trialChamberPro.providers

import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of [TrialMobProvider]s available at runtime.
 *
 * Seeded in [io.github.darkstarworks.trialChamberPro.TrialChamberPro.onEnable]
 * after [org.bukkit.event.server.PluginEnableEvent]s fire, so soft-dependencies
 * (MythicMobs, EliteMobs, etc.) are guaranteed to be up before their providers
 * report [TrialMobProvider.isAvailable].
 *
 * Not thread-safe during registration — all `register` calls should happen on
 * the main thread during plugin startup. Lookups via [get] are thread-safe.
 */
class TrialMobProviderRegistry {

    private val providers = ConcurrentHashMap<String, TrialMobProvider>()

    /** Vanilla is always registered and always available; the fallback for every missing/invalid id. */
    val vanilla: TrialMobProvider = VanillaMobProvider

    init {
        register(vanilla)
    }

    fun register(provider: TrialMobProvider) {
        providers[provider.id.lowercase()] = provider
    }

    /** Returns the provider with [id], or null. Use [getOrVanilla] when a fallback is desired. */
    fun get(id: String?): TrialMobProvider? {
        if (id.isNullOrBlank()) return null
        return providers[id.lowercase()]
    }

    /** Returns the provider with [id], or [vanilla] if it isn't registered. */
    fun getOrVanilla(id: String?): TrialMobProvider = get(id) ?: vanilla

    /** All registered providers, in no particular order. */
    fun all(): Collection<TrialMobProvider> = providers.values

    /** Available (backing plugin present + enabled) providers, for GUI pickers. */
    fun available(): List<TrialMobProvider> = providers.values.filter { it.isAvailable() }
}
