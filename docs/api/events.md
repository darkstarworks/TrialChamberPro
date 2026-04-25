# 📡 Event API

TrialChamberPro fires Bukkit events at the key points in its lifecycle so other plugins can hook in without forking. All events live under `io.github.darkstarworks.trialChamberPro.api.events` and follow the standard Bukkit `Event` / `Cancellable` contracts — register a listener with `@EventHandler` and you're done.

<div data-gb-custom-block data-tag="hint" data-style="info">

**Threading note**: every TrialChamberPro event reports `isAsynchronous() == !Bukkit.isPrimaryThread()` at construction. On Paper, vault and key-drop events fire from the main/region thread (sync); reset and discovery events fire from coroutine-IO threads (async). On Folia, all of them are dispatched as async events because Folia has no single primary thread. **Listeners that touch the Bukkit API must schedule themselves onto the appropriate region thread, not assume sync delivery.**

</div>

## Events

### `ChamberResetEvent` (cancellable)

Fired immediately before a chamber begins resetting. Cancel to abort the reset entirely (useful for "don't reset while a player is mid-vault" or replacing the reset with a custom implementation).

| Field | Type | Notes |
|---|---|---|
| `chamber` | `Chamber` | The chamber about to reset. |
| `reason` | `Reason` | `SCHEDULED`, `MANUAL`, or `FORCED`. |
| `triggeringPlayer` | `Player?` | Null for `SCHEDULED` resets. |

```kotlin
@EventHandler
fun onChamberReset(event: ChamberResetEvent) {
    if (event.reason == ChamberResetEvent.Reason.SCHEDULED &&
        event.chamber.getPlayersInside().isNotEmpty()) {
        event.isCancelled = true  // Defer auto-resets while occupied
    }
}
```

### `ChamberResetCompleteEvent` (not cancellable)

Fired after a chamber has finished resetting. Useful for follow-up announcements, scoreboard updates, or webhook notifications.

| Field | Type | Notes |
|---|---|---|
| `chamber` | `Chamber` | The chamber that just reset. |
| `durationMs` | `Long` | Wall-clock duration of the reset. |
| `blocksRestored` | `Int` | Blocks the snapshot apply touched. `0` if no snapshot. |

### `VaultOpenedEvent` (not cancellable)

Fired immediately after a player has successfully opened a vault — the loot has been generated and delivered, the key has been consumed.

| Field | Type | Notes |
|---|---|---|
| `player` | `Player` | The player who opened the vault. |
| `vault` | `VaultData` | Database row for the opened vault. |
| `chamber` | `Chamber?` | Null in the pathological case of a deleted-while-open chamber. |
| `lootTableName` | `String` | Effective loot table (chamber override resolved against vault default). |
| `items` | `List<ItemStack>` | Snapshot clones — safe to inspect after the player's inventory mutates. |

```kotlin
@EventHandler
fun onVaultOpen(event: VaultOpenedEvent) {
    val totalValue = event.items.sumOf { economy.priceOf(it) }
    discordWebhook.send("${event.player.name} looted $totalValue from ${event.chamber?.name}")
}
```

### `SpawnerWaveCompleteEvent` (not cancellable)

Fired when a trial spawner finishes a wave (all spawned mobs killed). Fires for both registered chambers and wild spawners.

| Field | Type | Notes |
|---|---|---|
| `spawnerLocation` | `Location` | Block-aligned location of the spawner. |
| `chamber` | `Chamber?` | Null for wild spawners. |
| `ominous` | `Boolean` | True if the wave was ominous-mode at start. |
| `participants` | `Set<UUID>` | UUIDs credited as participants. |
| `durationMs` | `Long` | Wall-clock duration of the wave. |

### `ChamberDiscoveredEvent` (cancellable)

Fired by the auto-discovery system after a candidate chamber passes validation but **before** it is registered. Cancel to abort auto-registration (e.g. world-restricted whitelist, custom registration logic).

| Field | Type | Notes |
|---|---|---|
| `world` | `World` | World the candidate is in. |
| `suggestedName` | `String` | `auto_<world>_<x>_<z>`. |
| `minCorner` | `Location` | Inclusive AABB min corner. |
| `maxCorner` | `Location` | Inclusive AABB max corner. |
| `vaultCount` | `Int` | Vault blocks counted inside the AABB. |
| `spawnerCount` | `Int` | Trial spawner blocks counted inside the AABB. |
| `method` | `Method` | `CHUNK_LOAD` or `STARTUP_SWEEP`. |

```kotlin
@EventHandler
fun gateDiscovery(event: ChamberDiscoveredEvent) {
    if (event.world.name != "survival") {
        event.isCancelled = true   // only auto-register in the main world
    }
}
```

### `TrialKeyDropEvent` (cancellable)

Fired immediately before the plugin drops a trial key for a wave participant. **Provider-driven waves only** — vanilla trial spawners drop their own keys via the spawner state machine and do not pass through this event.

Fires once per participant per wave completion (so a four-player wave produces four events). Cancel to suppress an individual key drop without affecting other participants.

| Field | Type | Notes |
|---|---|---|
| `location` | `Location` | The drop location (centered on the spawner, slightly above). |
| `keyType` | `Material` | `TRIAL_KEY` or `OMINOUS_TRIAL_KEY`. |
| `ownerUuid` | `UUID` | The participant the key is being dropped for. |

## Registering listeners

Standard Bukkit registration:

```kotlin
class MyPlugin : JavaPlugin() {
    override fun onEnable() {
        server.pluginManager.registerEvents(MyTcpListener(), this)
    }
}

class MyTcpListener : Listener {
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onVaultOpen(event: VaultOpenedEvent) { /* ... */ }
}
```

If your project pulls in TrialChamberPro as a soft dependency, gate listener registration behind a `pluginManager.getPlugin("TrialChamberPro") != null` check — the API classes won't be on the classpath if TCP isn't installed, so referencing them eagerly will `ClassNotFoundException`.

## Versioning

The event API is part of v1.3.0+. Event class names, field names, and `Reason`/`Method` enum constants are considered stable; new events and new enum constants may be added in minor releases. Removals or renames will be flagged in the changelog and given a deprecation cycle.
