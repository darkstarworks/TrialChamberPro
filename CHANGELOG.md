# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

## [1.3.1] - 2026-04-26
### Added
- **Spawner presets + `/tcp give` command** — server admins can now define named templates of trial-spawner configurations in a new `spawner_presets.yml` and hand them out as preconfigured items via `/tcp give <preset> [player] [amount]`. Each preset materializes as a `minecraft:trial_spawner` item with `block_entity_data` baked in (the same NBT vanilla `/give` accepts), so placing it produces a working spawner with the configured `normal_config` / `ominous_config` (datapack resource locations), `required_player_range`, `target_cooldown_length`, and any optional wave-shape overrides (`total_mobs`, `simultaneous_mobs`, `ticks_between_spawn`, `spawn_range`, etc.). Display name and lore are applied via `ItemMeta` so they survive future component remaps.
- **`SpawnerPresetManager`** loads `spawner_presets.yml` on enable and on `/tcp reload`. Map swaps are atomic — in-flight `/tcp give` calls aren't affected by reloads. ItemStacks are built through `Bukkit.getItemFactory().createItemStack(...)` (the modern, non-deprecated path that parses the same `material[components]` syntax as vanilla `/give`), not via `Bukkit.getUnsafe().modifyItemStack`.
- **`tcp.give` permission** (default op, included in the `tcp.admin.*` aggregate) gates the new command. Tab completion lists every loaded preset id and online player names; lookups are case-insensitive.
- **Trial-spawner-only hardening** — the YAML schema deliberately has no `material:` field. Every preset always produces `Material.TRIAL_SPAWNER`. This is an intentional architectural seam: custom keys, vault crates, and other custom items are out of scope for this file (they belong to the planned premium "Vault Crate" module, which will mirror the same preset/give pattern under its own `vault_presets.yml` + `/tcp vault give` command).
- **Documentation** — new GitBook page [docs/configuration/spawner-presets.yml.md](docs/configuration/spawner-presets.yml.md) with full field reference, command usage, troubleshooting, and a cross-link to the [Trial Spawner wiki](https://minecraft.wiki/w/Trial_Spawner#Spawner_configuration) for the datapack JSON format. `commands.md` and `permissions.md` updated with the new entries.

### Changed
- `TrialChamberPro.onEnable` now constructs `SpawnerPresetManager` after `LootManager.loadLootTables()` and calls `load()`. `reloadPluginConfig()` re-loads the preset file alongside loot tables.

## [1.3.0] - 2026-04-23
### Added
- **Custom Mob Provider API** — new pluggable `TrialMobProvider` interface (`providers/TrialMobProvider.kt`) lets trial-spawner waves source mobs from external plugins instead of vanilla. Two built-in providers ship in this release: `vanilla` (default, unchanged behavior) and `mythicmobs` (reflection-based soft-dep on MythicMobs — no compile-time binding).
- **Replace-after-spawn strategy** — when a chamber is configured with a non-vanilla provider, `SpawnerWaveListener` lets the vanilla trial spawner spawn its mob, then removes that entity the same tick and asks the provider to spawn its replacement at the same location. The vanilla spawner's state machine (tracked UUIDs, wave counter, cooldown) is preserved, so boss bars, glow outlines, and cooldown management all continue to work with custom mobs.
- **Plugin-driven Trial Key drops** — vanilla's key-ejection path relies on tracked mob UUIDs that no longer exist when we replace the spawns, so `SpawnerWaveManager.maybeDropProviderKeys` drops `TRIAL_KEY` / `OMINOUS_TRIAL_KEY` itself: one per unique participant, above the spawner block, with a small upward velocity that approximates vanilla's pop. The key type is captured at wave start (`WaveState.isOminous` is a `val`) and cannot flip mid-wave.
- **Owner-only spawner-key pickup** — new `SpawnerKeyDropOwnerListener` sibling of `VaultDropOwnerListener` (v1.2.28). Drops are PDC-tagged with `tcp:spawner_key_owner` + `tcp:spawner_key_dropped_at`; non-owner pickups are cancelled during the grace window. Config: `reset.spawner-key-drop-owner-grace-seconds` (default `30`, `0` = owner-locked until despawn). Shared `tcp.bypass.droplock` permission with vault drops.
- **Per-chamber provider configuration** — new DB columns `custom_mob_provider`, `custom_mob_ids_normal`, `custom_mob_ids_ominous` (JSON-encoded id lists) on `chambers` via idempotent migration. Ominous waves fall back to the normal list if the ominous list is empty, matching the loot-table override pattern. `ChamberManager.updateCustomMobProvider(id, provider, normalIds, ominousIds)` is the single write path; `Chamber.pickMobId(ominous)` and `Chamber.hasCustomMobProvider(ominous)` are the read-side helpers.
- **`CustomMobProviderView` GUI** — new 6-row admin view reachable from `ChamberSettingsView` (spawner icon, row 2 centre). Cycle providers with left/right-click, shift-click to reset to vanilla; add mob ids via a one-shot chat input (`MobIdInputListener`) with 30-second timeout and `cancel` keyword; click a mob-id icon to remove it. All writes go through `ChamberManager.updateCustomMobProvider` and the view refreshes on the player's region thread after each change.
- **`/tcp mobs` command** — CLI path for per-chamber provider config:
  - `/tcp mobs providers` — list registered providers and their availability
  - `/tcp mobs <chamber> provider <id|vanilla|none>` — set the provider
  - `/tcp mobs <chamber> add normal|ominous <mobId>` — add a mob id to the pool
  - `/tcp mobs <chamber> remove normal|ominous <mobId>` — remove a mob id
  - `/tcp mobs <chamber> list` — show current config
  - Permission: `tcp.admin.mobs` (default op)
- **MythicMobs integration** as a soft-dependency via `plugin.yml` `softdepend`. `MythicMobsProvider` resolves `io.lumine.mythic.bukkit.MythicBukkit` + `MobManager.spawnMob(String, Location, double)` reflectively and degrades gracefully if the API shape changes.
- **Additional provider integrations** — five more providers, all zero-compile-time-dependency reflection with cached availability and verbose-logging-gated warnings:
  - `elitemobs` — EliteMobs custom-boss spawning via `new CustomBossEntity(filename)` + `spawn(Location, 0, true)` + `getLivingEntity()`. Mob id = boss filename (with or without `.yml`).
  - `ecomobs` — Auxilor [EcoMobs](https://github.com/Auxilor/EcoMobs) (hard-depends on [Eco](https://github.com/Auxilor/eco)) via `EcoMobs[id].spawn(Location, SpawnReason.CUSTOM)`. Mob id = entry id from `plugins/EcoMobs/mobs/<id>.yml`.
  - `levelledmobs` — spawns a vanilla `EntityType` and applies a LevelledMobs level through `LevelInterface2.applyLevelToMob(LivingEntityWrapper, int, ...)`. Mob id format `ENTITY_TYPE[:level|ominous]` (e.g. `ZOMBIE:15`, `HUSK:ominous`). The ominous keyword maps to tiered levels (20 for ominous waves, 10 otherwise).
  - `infernalmobs` — spawns a vanilla `EntityType` and enriches it via `infernal_mobs.makeInfernal(Entity, false)` (abilities are rolled by the plugin from config). Mob id format `ENTITY_TYPE`.
  - `citizens` — clones a template NPC from the default `NPCRegistry` (by numeric id or exact name) and spawns the clone so template NPCs are never moved. Mob id = Citizens NPC id or name.
- **Plugin.yml softdepends** — `EliteMobs`, `Eco`, `EcoMobs`, `LevelledMobs`, `InfernalMobs`, `Citizens` added alongside `MythicMobs` so load order guarantees the provider registry sees every backing plugin before seeding.

### Changed
- `TrialChamberPro.onEnable` now constructs a `TrialMobProviderRegistry` after manager init and conditionally registers `MythicMobsProvider` when MythicMobs is present on the server. Accessible via `plugin.trialMobProviderRegistry`.

### Added — GUI translatability
- **Full GUI localization** — every name, lore line, and button label across all 18 admin GUI views is now driven by `messages.yml` under a nested `gui.*` section (~330 new keys). No hard-coded `Component.text(...)` calls left in any view file. See [docs/configuration/localization.md](docs/configuration/localization.md) for the key naming conventions and translator workflow.
- **`gui/components/GuiComponents.kt`** — reusable builders (`infoItem`, `toggleItem`, `backButton`, `closeButton`, `prevPageButton`, `nextPageButton`, `playerHead` x2) plus a class-doc that codifies icon material conventions in 5 categories (Navigation, Action buttons, State indicators, Settings categories, Domain icons). All views now build through this layer; future GUI code uses the localization layer by default.
- **`TrialChamberPro` helper additions** — `loadedMessages()`, `getMessageList(key, ...)`, `getGuiText(key, ...)` returning a `Component`, `getGuiLore(key, ...)` returning a `List<Component>`. `getMessage()` auto-skips the chat prefix for any key starting with `gui.` so admin items don't get `[TCP]` prepended.
- **Empty-state placeholders** added to 6 list/grid views (chamber list, chambers overview, loot table list, vault management, custom mob provider, loot editor) so admins always see a localized "no entries" card instead of a confusing blank pane.

### Added — Public event API
- **6 events under `api/events/`** — Bukkit-style events that third-party plugins can hook without forking. See [docs/api/events.md](docs/api/events.md) for field tables and integration examples.
  - `ChamberResetEvent` (cancellable) with `Reason` enum (SCHEDULED / MANUAL / FORCED)
  - `ChamberResetCompleteEvent` — post-reset with duration + blocks restored
  - `VaultOpenedEvent` — post-open with cloned item snapshot
  - `SpawnerWaveCompleteEvent` — post-wave with participants set
  - `ChamberDiscoveredEvent` (cancellable) with `Method` enum (CHUNK_LOAD / STARTUP_SWEEP)
  - `TrialKeyDropEvent` (cancellable) — per-participant key drop for provider-driven waves
- All events use `Event(!Bukkit.isPrimaryThread())` so they correctly identify as async when fired from coroutines or Folia region threads.
- **5 fire sites wired**: `ResetManager.resetChamber` (pre + post; `reason` parameter added with `MANUAL` default — scheduled paths pass `SCHEDULED`, GUI shift+right force-reset paths pass `FORCED`); `VaultInteractListener.openVault` (post, with cloned items snapshot); `SpawnerWaveManager.completeWave` (post); `SpawnerWaveManager.maybeDropProviderKeys` (per-participant pre/cancellable); `ChamberDiscoveryManager.registerChamber` (pre/cancellable).

### Added — Stability + reliability
- **`MenuSessionCleanupListener`** — drops a player's GUI session on `PlayerQuitEvent`. Belt-and-braces soft-cap eviction at 500 sessions inside `MenuService` covers any future code paths that forget to clean up.
- **`config/ConfigValidator`** — startup sanity check that clamps ~12 numeric config values back into safe ranges with logged warnings (no hard-fail). Sentinel values (e.g. `-1` for "use vanilla cooldown") pass through untouched. Runs early in `onEnable` so the rest of the bootstrap sees corrected values.
- **`/tcp mobs` tab completer** — second/third/fourth-arg completion for `providers`, `provider`, `add`, `remove`, `list` and the `normal`/`ominous` wave keywords.
- **`/tcp mobs` localization** — ~23 new `mobs-*` keys in `messages.yml`; the command no longer hard-codes any English strings.

### Added — Test foundation
- **67 unit tests across 5 test classes** (`./gradlew test` runs in seconds; covers gzip roundtripping, Chamber bounds math, coordinate parsing, LootEditorDraft mutability/equality, and ConfigValidator clamp behavior). Paper API added to `testImplementation` so tests can mock Bukkit types via MockK without spinning up a server.

### Changed — Internal structure
- **`models/LootEditorDraft`** — extracted from `LootEditorView.Draft` so amount/loot editors and `MenuService` no longer reach into the view class for a shared data type.
- **`commands/handlers/`** — three of the largest `/tcp` subcommands extracted to dedicated `SubcommandHandler` classes: `GenerateCommand` (548 → 432 lines, with all `parse*` / `validate*` / `placeBoxFromPlayer` helpers and `MIN_XZ` / `MIN_Y` constants moved out of `TCPCommand`), `MobsCommand`, and `LootCommand` (with the four sub-actions — set, clear, info, list — as private methods on the class). The four `generate` mode branches now share a `createChamberAsync` helper that deduplicated ~120 lines of identical scan/snapshot follow-up code.
- **`TCPCommand.kt`: 1461 → 627 lines (57% reduction).** The dispatcher is a thin when-chain that routes to size-appropriate cases — small handlers stay inline, big ones delegate.
- **`commands/handlers/CoordinateParser`** — pure parsing logic (`parseTriple`, `parseHyphenated`, `isTripleString`) extracted from `GenerateCommand` to enable unit testing without mocking `Bukkit.getWorld()`. `GenerateCommand.parseCoordsArgs` now delegates to `CoordinateParser` for the pure parts and keeps only the world-aware composition logic.

### Notes
- **Not persisted across restart**: in-progress waves still reset to vanilla on server stop (matches pre-1.3.0 behavior). Acceptable for this release; revisit if restart-mid-wave becomes a common report.
- **All planned providers ship in 1.3.0**: the six providers originally split between 1.3.0 (vanilla + MythicMobs) and 1.3.1+ (EliteMobs, EcoMobs, LevelledMobs, InfernalMobs, Citizens) are now all in the initial release. The `TrialMobProvider` interface is stable for third-party providers to register against — see [docs/api/mob-providers.md](docs/api/mob-providers.md) for the developer guide.
- **Public API stability**: the v1.3.0 event classes and `TrialMobProvider` interface are considered stable. Field names, enum constants, and method signatures will go through a deprecation cycle before any breaking change.
- **No config file format changes** — existing `config.yml`, `loot.yml`, and `messages.yml` files continue to work. Existing chambers, loot tables, and snapshots are unaffected. Translators who already customized `messages.yml` will see the new GUI translation keys auto-merged from the bundled defaults on first reload.

## [1.2.29] - 2026-04-22
### Added
- **CraftEngine custom-item support** in loot tables: `type: CUSTOM_ITEM` with `plugin: CraftEngine` and `item-id: <namespace:item>`. Items are resolved via `Key.from(id)` → `CraftEngineItems.byId(key)` → `CustomItem.buildItemStack()`. IDs accept the standard namespaced form (e.g. `"my_pack:legendary_sword"`); a bare id defaults to the `minecraft` namespace per CraftEngine's `Key.from` contract, so namespaced IDs are strongly recommended
- **MythicCrucible custom-item support** in loot tables: `type: CUSTOM_ITEM` with `plugin: MythicCrucible` (alias `Crucible`) and `item-id: <your_item>`. Crucible items are registered into the MythicMobs item manager, so the gate check uses `MythicMobs` — install MythicMobs + MythicCrucible, define the item in Crucible, then reference it by id

### Changed
- `CUSTOM_ITEM` supported-plugin warning now lists all five supported plugins: Nexo, ItemsAdder, Oraxen, CraftEngine, MythicCrucible

### Notes
- No new compile-time dependencies — both integrations use reflection behind an `isPluginEnabled` gate, consistent with the existing Nexo/ItemsAdder/Oraxen pattern. Plugins ship zero runtime cost when the target plugin isn't installed

## [1.2.28] - 2026-04-20
### Fixed
- **Vault cooldown not cleared on chamber reset** (long-standing, surfaced on any chamber larger than a few thousand blocks): players kept seeing "You have already opened this Vault! It will unlock when the chamber resets" even after an automatic reset
  - Root cause: `BlockRestorer.restoreBlocks` scheduled region-thread batches via `runAtLocation` (fire-and-forget) and returned as soon as every batch was *queued*, not when they had *run*. `ResetManager.resetChamber` then waited just `delay(200)` before running Step 4 (spawner reset) and Step 6 (`resetAllCooldowns` → `clearAllVaultRewardedPlayers`). For anything beyond a trivially small chamber, those steps fired against blocks that had not yet been restored — `clearAllVaultRewardedPlayers` saw `block.type != VAULT` and returned without touching `rewarded_players`, so the native Vault API kept reporting the player as already rewarded forever
  - Fix: `BlockRestorer.restoreBlocks` now tracks pending batches with an `AtomicInteger` + `CompletableDeferred` and `await()`s actual completion before returning. `ResetManager.resetChamber` drops the `delay(200)` hack — Steps 4 and 6 now always run against fully-restored blocks

### Added
- **Vanilla-style vault loot ejection** (opt-in): When enabled, vault loot pops out of the vault block with a short upward velocity instead of teleporting straight into the opener's inventory
  - New `VaultDropOwnerListener` enforces owner-only pickup via `EntityPickupItemEvent` — dropped items are tagged with the opener's UUID in their `PersistentDataContainer` plus a drop timestamp. After the grace window elapses the tag is ignored and any player can pick up, preventing loot from lingering forever if the owner logs off
  - Command rewards and status-effect rewards (Bad Omen etc.) still apply directly to the player — only itemized loot drops from the vault
  - Defaults preserve existing behavior (feature is off by default)

### Config
- New `vaults.drop-loot-at-vault: false` — master switch for vanilla-style ejection
- New `vaults.drop-loot-owner-only: true` — restrict pickup to the opener during the grace window
- New `vaults.drop-loot-owner-grace-seconds: 30` — how long owner-only enforcement lasts (`0` = until item despawns)

### Permissions
- New `tcp.bypass.droplock` (default `op`) — bypass owner-only pickup restrictions on vault drops

## [1.2.27] - 2026-04-20
### Added
- **Glow outline on active trial spawners** (opt-in): when a spawner activates, a colored outline is rendered around it that is visible through walls, making it easy to find the remaining active spawners in large chambers
  - Implemented by spawning a non-responsive, non-persistent `Interaction` entity sized to wrap the spawner (1.2x1.2), with `isGlowing = true` and `glowColorOverride` set per wave type
  - Cleared automatically when the wave completes, immediately (does not wait for the boss bar's 3s outro), and also on chamber reset via the existing `clearWavesInChamber` cleanup path
  - Folia-safe: entity spawn and removal both run via `scheduler.runAtLocation` on the owning region thread

### Config
- New `spawner-waves.glow-active-spawners: false` — master switch for the glow outline (opt-in)
- New `spawner-waves.glow-color-normal: "#FFFF55"` — hex RGB outline color for normal trial spawners (default: yellow)
- New `spawner-waves.glow-color-ominous: "#A020F0"` — hex RGB outline color for ominous trial spawners (default: purple)

## [1.2.26] - 2026-04-19
### Added
- **Edit default (global) loot tables from the GUI**: Clicking a table in the Loot Tables menu now opens the full editor directly — no need to open each chamber one by one
  - Multi-pool tables route through the pool selector first; legacy single-pool tables open the editor directly
  - Edits persist to `loot.yml` via `LootManager.saveAllToFile()` and apply automatically to every chamber that references the table (i.e. every chamber without a per-chamber override)
  - The full editor feature set works in global mode: weight/amount/rolls adjustments, add-from-hand, toggle enabled, discard draft
- **Spawner-wave boss bar now hides when you leave the area**: Walking past `spawner-waves.remove-distance` (default 32 blocks, hysteresis above `detection-radius`) from a spawner removes the player from that wave's boss bar so it no longer lingers on screen after you exit a chamber
- **Chamber reset clears active wave boss bars**: `ResetManager.resetChamber` now calls `SpawnerWaveManager.clearWavesInChamber` so boss bars for any in-progress wave inside the chamber are force-removed alongside the block/entity cleanup — fixes boss bars persisting after admin-initiated resets

### Changed
- `LootEditorView`, `PoolSelectorView`, and `AmountEditorView` now accept either a `Chamber` *or* a `globalTableName` so the same editor powers both chamber-scoped and global table editing
- `MenuService` gained `openGlobalPoolSelect`, `openGlobalLootEditor`, `openGlobalAmountEditor`, and `saveGlobalDraft`; session restoration via `Session.globalLootEdit` routes the user back to the right flow after reopen

### Config
- New `spawner-waves.remove-distance: 32` — distance at which a player is removed from a boss bar they were previously added to. Must be greater than `detection-radius` to prevent flicker at the boundary.

## [1.2.25] - 2026-04-19
### Added
- **Auto-Discovery of Trial Chambers**: Opt-in system that automatically registers naturally-generated Trial Chambers the first time a player loads one of its chunks
  - New `ChamberDiscoveryListener` hooks `ChunkLoadEvent` and checks `chunk.tileEntities` for `VAULT` or `TRIAL_SPAWNER` — cheap, no block iteration on the hot path
  - New `ChamberDiscoveryManager` runs a BFS flood-fill over chamber structural blocks (tuff brick family, polished/chiseled tuff, copper bulb/grate/chiseled/door/trapdoor incl. all oxidation + waxed variants, heavy core, vaults, trial spawners) to compute a tight bounding box
  - Radius-capped BFS (configurable `max-radius-xz` / `max-radius-y`) prevents runaway scans and cannot merge neighboring chambers (generation places them 544+ blocks apart)
  - Partial-load handling: if BFS hits an unloaded chunk, the seed is re-queued with a retry budget so the AABB grows as adjacent chunks load naturally
  - Per-region debounce (128-block buckets) prevents re-triggering from the same area
  - Auto-name format: `auto_<world>_<centerX>_<centerZ>` (deterministic, collision-safe)
  - Works on both freshly-generated AND existing worlds — `ChunkLoadEvent` fires on every chunk load, regardless of whether the chunk was generated now or pre-existed on disk
  - **Startup sweep**: on plugin enable, iterates every currently-loaded chunk across all Overworld worlds and seeds discovery for any `VAULT`/`TRIAL_SPAWNER` tile-entities found — covers chambers in chunks that were already loaded before the `ChunkLoadEvent` listener was registered (e.g. spawn regions, pre-loaded worlds)
  - Reuses existing `ChamberManager.createChamber` + `scanChamber` to register and populate vaults/spawners
  - Optional auto-snapshot on registration (`discovery.auto-snapshot`, default off because snapshots are expensive)
  - Broadcasts registration to players with new `tcp.discovery.notify` permission
- **New config section `discovery:`**: `enabled` (default `false`, opt-in), `max-radius-xz`, `max-radius-y`, `min-vaults-plus-spawners`, `max-center-y`, `auto-snapshot`, `notify-ops`, `cooldown-seconds`, `pending-retry-seconds`
- **New permission `tcp.discovery.notify`** (default `op`): receive in-game notifications when discovery registers a chamber
- **New message `discovery-registered`** in messages.yml

### Technical Details
- BFS bounds checked against the `generation.max-volume` cap so a malformed run cannot produce an oversized chamber
- Overworld-only (`World.Environment.NORMAL`) — trial chambers do not generate in Nether/End
- Center-Y validation (`max-center-y: 10`, default) rejects above-ground tuff/copper builds that would false-positive
- All block reads scheduled via `plugin.scheduler.runAtLocation` for Folia compatibility
- Listener runs at `EventPriority.MONITOR` and short-circuits if disabled, wrong environment, empty tile-entity list, or location already inside a registered chamber
- Recently-processed region set uses a bounded cleanup pass to avoid unbounded memory growth

## [1.2.24] - 2026-04-15
### Added
- **Custom Plugin Item Support in loot.yml**: Nexo, ItemsAdder, and Oraxen items can now be used as loot drops
  - Use `type: CUSTOM_ITEM` with `plugin: Nexo/ItemsAdder/Oraxen` and `item-id: "..."` fields
  - Resolved at runtime via reflection — no compile-time dependency on any custom item plugin
  - If the plugin is not installed, the item is skipped with a console warning
  - Extra `name:`, `lore:`, and `enchantments:` fields are applied on top of the resolved item
- **`custom-model-data` field for vanilla items**: Set custom model data (resource pack texture override) on any vanilla item in loot.yml
  - Works with all other item fields (enchantments, lore, durability ranges, etc.)

## [1.2.23] - 2026-02-14
### Fixed
- **Vanilla Vaults Broken**: Unregistered/vanilla vaults now work correctly when plugin is installed
  - Root cause: `VaultInteractListener` intercepted ALL vault interactions server-wide, cancelling the event for vaults not in the plugin's database
  - Fix: Early-return check skips plugin logic for vaults outside registered chambers
- **Custom Death Message Not Working**: Death messages set from async thread had no effect (event already processed)
  - Fix: Use synchronous cache lookup (`getCachedChamberAt`) so death message is set while event is still being processed
- **Async Block Data Access**: `saveVault()` accessed `block.blockData.asString` on IO thread (unsafe Bukkit API access)
  - Fix: Block data string is now read on the main thread and passed as a parameter
- **`runBlocking` in Async Thread**: `UndoListener` used `runBlocking` inside `runTaskAsync`, blocking Bukkit's async thread pool
  - Fix: Replaced with `plugin.launchAsync {}` coroutine pattern
- **JDBC Resource Leaks**: `StatisticsManager` had 5 methods with `PreparedStatement`/`ResultSet` not wrapped in `.use{}`
  - Fix: All JDBC resources now use `.use{}` for automatic cleanup
- **`loadingLocks` Memory Leak**: Per-player mutex map in `StatisticsManager` grew without bound
  - Fix: Entries now cleaned up alongside cache invalidation

### Improved
- **Messages Performance**: `getMessage()` now caches the parsed `messages.yml` instead of re-reading and re-parsing the file on every call; cache invalidated on `/tcp reload`
- **Shutdown Reliability**: `PlayerMovementListener`, `PlayerDeathListener`, and `PasteConfirmListener` coroutine scopes now properly cancelled on plugin disable
- **Time Tracking Data Preservation**: Player time-in-chamber data is flushed to database on plugin shutdown (previously up to 5 minutes of data could be lost)
- **Duplicate Command Handlers**: Removed redundant `TCPCommand`/`TCPTabCompleter` creation during async initialization (already registered at startup)

## [1.2.22] - 2026-01-10
### Fixed
- **GUI Teleport Right-Click**: Fixed teleport button in ChamberDetailView ignoring click type
  - Right-click was teleporting to chamber center instead of exit location
  - Root cause: `handleTeleport()` didn't check for left vs right click - always teleported to center
  - Fix: Added `isLeftClick` and `isRightClick` parameters to handler
  - Left click: Teleport to chamber center (as before)
  - Right click: Teleport to exit location (now works correctly)
  - Shows "No exit location set" message if exit not configured

### Technical Details
- `ChamberDetailView.handleTeleport()` now accepts `left: Boolean, right: Boolean` parameters
- Uses `when` expression to differentiate click types
- Existing messages `gui-teleport-to-center`, `gui-teleport-to-exit`, and `gui-no-exit-location` used

## [1.2.21] - 2026-01-10
### Fixed
- **Vault Cooldown Not Working**: Fixed permanent vault cooldown not being enforced after the 5-second spam protection expires
  - Root cause: We were cancelling the `PlayerInteractEvent` to handle loot ourselves, but this prevented vanilla from tracking the player in the vault's `rewarded_players` NBT
  - Our database tracking was supposed to handle cooldowns, but wasn't being respected properly
  - Fix: Now uses Paper's native `Vault` TileState API for cooldown tracking:
    - `hasRewardedPlayer(UUID)` to check if player already opened the vault
    - `addRewardedPlayer(UUID)` to mark player as rewarded after giving loot
    - `update()` to persist the block state changes
  - This is more reliable because it uses Minecraft's built-in tracking that persists with the block
  - Cooldowns automatically reset when the vault block is restored during chamber reset

### Improved
- **SpawnerWaveListener**: Removed reflection-based ominous detection, now uses Paper's native `TrialSpawner.isOminous` property directly
- **VaultInteractListener**: Simplified trial key detection using direct `Material.OMINOUS_TRIAL_KEY` enum instead of string comparison
  - Note: Vault ominous detection still uses block data string parsing as Paper's Vault TileState doesn't have `isOminous` property (unlike TrialSpawner)
- **Vault Reset Commands**: Now properly clear both database tracking AND native Vault `rewarded_players`
  - `/tcp vault reset <chamber> <player>` - Clears specific player from vault cooldown
  - GUI "Reset All Cooldowns" button - Clears all players from vault cooldowns
  - Chamber automatic reset - Clears all vault cooldowns (both DB and native API)
  - Uses `Vault.removeRewardedPlayer(UUID)` and `Vault.update()` to clear native state
  - Fully Folia-compatible using `scheduler.runAtLocation()` for block operations

### Technical Details
- `VaultInteractListener` now imports `org.bukkit.block.Vault` TileState
- Cooldown check: `block.state as? Vault` then `vaultState.hasRewardedPlayer(player.uniqueId)`
- After loot: `vaultState.addRewardedPlayer(player.uniqueId)` then `vaultState.update()` (CRITICAL: must call update!)
- Database `recordOpen` is still called for statistics tracking, but not used for cooldown enforcement
- Both operations run on the region thread (Folia compatible) using `scheduler.runAtLocation()`
- Added `vault-error` message to messages.yml for edge cases where vault state can't be updated
- Key detection: `Material.TRIAL_KEY` and `Material.OMINOUS_TRIAL_KEY` direct enum comparison
- Spawner ominous check: `state.isOminous` property (no reflection needed)

## [1.2.20] - 2026-01-10
### Fixed
- **GUI Icons Misplaced**: Updated InventoryFramework from 0.11.5 to 0.11.6 to fix potential GUI rendering issues
  - Reported: Menu icons appearing in wrong positions
  - May also fix issues with ProtocolLib conflicts
- **Vault Spam-Click Race Condition at 5-Second Boundary**: Fixed race condition where multiple vault opens could occur when spam protection lock expires
  - Root cause: The `delay(5000); remove(lockKey)` in the coroutine's finally block races with new events at exactly 5 seconds
  - At T+5s, the old coroutine removes the lock while new events are checking it, allowing multiple events through
  - Fix: Removed explicit lock removal; rely on timestamp-based expiration check instead
  - Added periodic cleanup (when map size > 100) to prevent memory leaks

### Technical Details
- Updated `com.github.stefvanschie.inventoryframework:IF` dependency from 0.11.5 to 0.11.6
- `VaultInteractListener`: Removed `finally { delay(5000); remove() }` block that caused race condition
- Spam protection lock now uses timestamp expiration only (checked on each event)
- Memory leak prevention: Old entries (>30s) cleaned up when map exceeds 100 entries

## [1.2.19] - 2026-01-09
### Added
- **Plugin Info Command**: `/tcp info` now shows plugin information when used without arguments
  - Displays version, authors, database type, chamber count, platform (Paper/Folia)
  - Shows integration status: WorldEdit/FAWE, WorldGuard, PlaceholderAPI, Vault
  - Shows feature status: Per-Player Loot, Spawner Waves, Spectator Mode, Statistics
  - Requires `tcp.admin` permission
  - `/tcp info <chamber>` still shows chamber info (existing behavior)

### Fixed
- **GUI Item Drag Exploit**: Fixed players being able to drag items inside GUI menus
  - Root cause: `setOnGlobalDrag` was missing from all 17 GUI views
  - Players could drag items by holding click and moving, bypassing click handlers
  - Now all views have both `setOnGlobalClick` and `setOnGlobalDrag` handlers
- **Vault Spam-Click Exploit**: Fixed players being able to spam-click vaults to get multiple loot drops
  - Root cause: The in-memory lock check was inside the async coroutine, not the sync event handler
  - Multiple clicks could launch multiple coroutines before any of them checked/set the lock
  - Fix: Lock check now happens SYNCHRONOUSLY in the event handler before any async work
  - Uses location-based lock key (player + coordinates + vault type) for immediate protection
- **Keys Consumed Without Loot**: Fixed trial keys being consumed even when no loot is generated
  - Now checks if loot table exists BEFORE attempting to open vault
  - If loot generation fails (empty result), key is NOT consumed and vault is NOT marked as opened
  - Player receives error message explaining the configuration issue
  - Common cause: TAB characters in loot.yml (YAML requires spaces, not tabs!)

### Technical Details
- Added `gui.setOnGlobalDrag { it.isCancelled = true }` to all 17 GUI view files
- `VaultInteractListener.onVaultInteract()` now checks/sets the spam protection lock before launching coroutine
- Lock key format changed from `{uuid}:{vaultId}` to `{uuid}:{x}:{y}:{z}:{type}` for sync access (no DB lookup needed)
- Debug logging shows "Vault interaction ignored - already opening (spam protection)" when spam detected
- New messages in messages.yml: `plugin-info-*` for plugin info display

## [1.2.18] - 2026-01-07
### Fixed
- **Wild Spawner Cooldown Not Working**: Fixed cooldown setting having no effect on wild spawners
  - Root cause: Cooldown was set at wave start, but spawner caches cooldown value at that point
  - Fix: Now sets cooldown at **wave completion** (before spawner enters cooldown state)
  - For `cooldown-minutes: 0`: Also clears tracked players/entities for true instant reactivation
- **Copied Spawners Retaining Old Cooldown**: Fixed spawners copied from chambers keeping their baked-in cooldown
  - When cooldown=0, spawner state is force-reset to `waiting_for_players` after key ejection
  - Handles spawners with old NBT data from being copied with WorldEdit/etc.

### Technical Details
- `SpawnerWaveManager.configureWildSpawnerCooldownAtCompletion()` handles the timing
- For cooldown=0: Calls `stopTrackingPlayer()` and `stopTrackingEntity()` so spawner reactivates for same players
- For cooldown=0: Schedules `forceResetSpawnerState()` 2 seconds after wave completion to bypass baked-in cooldown
- Debug logging available with `debug.verbose-logging: true`

## [1.2.17] - 2026-01-07
### Added
- **Wild Spawner Cooldown Configuration**: Control cooldown for trial spawners OUTSIDE registered chambers
  - New config option: `reset.wild-spawner-cooldown-minutes`
  - Values: `-1` = vanilla default (30 min), `0` = no cooldown (instant reactivation), `1-60` = custom minutes
  - Applies server-wide to all unregistered Trial Chamber spawners
  - Useful for servers wanting consistent spawner behavior across the world
  - Boss bar wave tracking now also works for wild spawners (not just registered chambers)

### Technical Details
- `SpawnerWaveListener` tracks spawns from wild spawners and adds them to wave tracking
- Uses `TrialSpawner.cooldownLength` property from Paper API

## [1.2.16] - 2026-01-06
### Fixed
- **Thread Safety Improvements**: Fixed multiple concurrency issues discovered during code review
  - `VaultManager.setLootTableForChamber()`: Now properly async with `suspend` and `Dispatchers.IO`
  - `StatisticsManager.getStats()`: Added per-player mutex to prevent redundant database loads
  - `BlockRestorer.processedBlocks`: Changed to `AtomicInteger` for Folia multi-region safety
  - `SpawnerWaveManager` counters: Changed `mobsKilled`, `mobsSpawned`, `totalMobsExpected` to `AtomicInteger`

- **Memory Leak Fix**: `VaultInteractListener.openingVaults` map now properly cleaned up on plugin disable
  - Added `shutdown()` method to cancel coroutine scope and clear the map
  - Plugin now calls shutdown on listener during `onDisable()`

### Changed
- **Full Message Localization**: All remaining hardcoded messages are now translatable via messages.yml
  - Boss bar messages for spawner wave tracking (normal/ominous titles, progress, completion)
  - Plugin startup message shown when commands are used during initialization
  - Chamber info display format strings (exit location, snapshot status)
  - Generate command usage/help messages

### Technical Details
- `StatisticsManager` uses `kotlinx.coroutines.sync.Mutex` for per-player locking with double-check pattern
- `SpawnerWaveManager.WaveState` now uses `AtomicInteger` for all counters and `@Volatile` for `completed` flag
- `VaultInteractListener` stored as field in main plugin class for proper lifecycle management
- `SpawnerWaveManager` now uses `getMessageComponent()` helper to convert message strings to Adventure Components
- `getMessage()` now skips prefix for keys containing "boss-bar"
- Added 15 new message keys: `spawner-wave-boss-bar-*`, `usage-generate-*`, `info-*`, `plugin-starting-up`

## [1.2.15] - 2026-01-06
### Fixed
- **CRITICAL: Spawner Cooldown Config Not Working**: Fixed `spawner-cooldown-minutes` setting having no effect
    - Root cause: NBTUtil was restoring the old cooldown from snapshot data, overwriting the config value
    - Fix: Removed cooldown restoration from NBTUtil - cooldown is now controlled exclusively by config
    - Added 200ms safety delay between block restoration and spawner reset to ensure proper ordering
    - Now works correctly with all values: 0 (no cooldown), -1 (vanilla default), or custom minutes
    - Debug logging available with `debug.verbose-logging: true` to troubleshoot cooldown issues

### Technical Details
- `NBTUtil.restoreTrialSpawner()` no longer restores `cooldownLength` from snapshot data
- `ResetManager.resetTrialSpawners()` now has comprehensive debug logging for cooldown configuration
- Added verification check after `state.update()` to detect if cooldown was properly applied

## [1.2.14] - 2026-01-06
### Added
- **Disable Automatic Resets**: Set `default-reset-interval: 0` to disable automatic chamber resets
  - Chambers will only reset via manual `/tcp reset <chamber>` command or GUI
  - Per-chamber override available in Chamber Settings GUI with "Disabled" option
  - Useful for event chambers or manually-controlled dungeons

### Technical Details
- `ResetManager.scheduleResetIfNeeded()` now skips scheduling when `resetInterval <= 0`
- Added "Disabled" preset to `ChamberSettingsView` reset interval options

## [1.2.13] - 2026-01-06
### Added
- **Configurable Trial Spawner Cooldown**: Control how long trial spawners stay in cooldown after being completed
  - Global setting: `spawner-cooldown-minutes` in config.yml (default: -1 for vanilla 30 min)
  - Per-chamber override: Set custom cooldown for individual chambers via GUI or database
  - Values: `-1` = vanilla default (30 min), `0` = no cooldown (instant reactivation), `1-60` = custom minutes
  - GUI support in Chamber Settings view with preset options (0, 5, 10, 15, 30 min, or vanilla default)
- **WorldEdit Undo Support for Chamber Resets**: Block restoration now integrates with WorldEdit's undo system
  - When a player initiates a reset (command or GUI), changes are recorded in their WorldEdit undo history
  - Use `//undo` to revert chamber resets initiated by you
  - Automatic resets (scheduled) don't create undo entries (no initiating player)
  - Uses WorldEdit's EditSession API via reflection (soft dependency)

### Changed
- **Complete Message Localization**: All user-facing messages are now translatable via messages.yml
  - Added 50+ new message keys for GUI operations
  - Chamber operations: teleport, reset, exit players, snapshot create/restore
  - Settings operations: reset interval, exit location, loot table, spawner cooldown
  - Loot editor operations: add item, save changes
  - All hardcoded `Component.text()` messages replaced with `plugin.getMessage()`
  - GUI categories: `gui-` prefixed keys for easy organization
  - Placeholders: `{chamber}`, `{count}`, `{seconds}`, `{error}`, `{value}`, `{type}`, `{table}`, `{item}`, `{pool}`, `{setting}`

### Technical Details
- New config option: `spawner-cooldown-minutes` in global section
- New Chamber model property: `spawnerCooldownMinutes: Int?` (null = use global)
- Database migration: Added `spawner_cooldown_minutes` column to chambers table
- `ResetManager.resetTrialSpawners()` now applies cooldown via `TrialSpawner.setCooldownLength()`
- `BlockRestorer.restoreBlocks()` accepts optional `initiatingPlayer` parameter for WorldEdit integration
- WorldEdit integration via reflection: `createWorldEditSession()`, `restoreBlockWithWorldEdit()`, `finalizeWorldEditSession()`
- New messages.yml keys: 35+ GUI action messages with placeholder support

## [1.2.12] - 2025-12-14
### Fixed
- **Advancement granted in spectator mode**: Fixed "Minecraft: Trial(s) Edition" advancement being granted when entering a chamber in spectator or creative mode
  - Now only grants to players in Survival or Adventure mode
- **Duplicate boss bar bug**: Fixed wave progress boss bars duplicating when a new wave starts during the 3-second completion delay
  - Root cause: Old "Wave Complete!" bar remained visible while new wave created another bar
  - Fix: Immediately remove old boss bar when starting a new wave at the same spawner
  - Also added safeguard to prevent adding the same player as a viewer multiple times

### Technical Details
- `PlayerMovementListener` now checks game mode before granting entry advancement
- `SpawnerWaveManager.startWave()` now cleans up completed waves before creating new ones
- `SpawnerWaveManager.addPlayerToBossBar()` now checks if player is already viewing

## [1.2.11] - 2025-12-14
### Fixed
- **CRITICAL: Trial Spawners Not Dropping Keys After Reset**: Fixed trial spawners stuck in cooldown state after chamber reset
  - Root cause: Snapshots captured the `trial_spawner_state` block data (e.g., `cooldown`, `ejecting_reward`)
  - When restored, spawners remained in that state and wouldn't activate for 30 minutes
  - Fix: `BlockRestorer.resetTrialSpawnerState()` now resets spawner state to `waiting_for_players` during restoration
  - Spawners are now immediately ready to activate after chamber reset, regardless of snapshot timing
  - All 6 spawner states are handled: `inactive`, `active`, `waiting_for_reward_ejection`, `ejecting_reward`, `cooldown`
  - **Note**: Existing chambers will be fixed automatically on next reset - no action needed

### Technical Details
- New function: `BlockRestorer.resetTrialSpawnerState()` - Modifies trial spawner block data strings during restoration

## [1.2.10] - 2025-12-10
### Added
- **Vanilla Advancement Granting**: Plugin-created chambers now grant vanilla Trial Chamber advancements
  - **"Minecraft: Trial(s) Edition"** (`adventure/minecraft_trials_edition`) - Granted when entering a plugin-managed chamber
  - **"Under Lock and Key"** (`adventure/under_lock_and_key`) - Granted when opening a normal vault
  - **"Revaulting"** (`adventure/revaulting`) - Granted when opening an ominous vault
  - Preserves the vanilla progression experience for schematic-pasted chambers
  - Folia compatible: Uses `runAtEntity()` for advancement granting in PlayerMovementListener

### Technical Details
- New utility: `AdvancementUtil.kt` with thread-safe advancement granting methods
  - `grantTrialChamberEntry(player)` - For chamber entry
  - `grantVaultUnlock(player)` - For normal vault opening
  - `grantOminousVaultUnlock(player)` - For ominous vault (auto-grants prerequisite)
- `VaultInteractListener` grants vault advancements after successful loot distribution
- `PlayerMovementListener` grants entry advancement when player crosses into chamber bounds
- All advancement operations run on entity thread for Folia compatibility

## [1.2.9] - 2025-12-07
### Changed
- **Update Checker**: Migrated from plain text logging to MiniMessage format for colored console output
  - Update notifications now display with proper colors in console
  - `update.txt` now supports MiniMessage tags (e.g., `<green>`, `<yellow>`, `<gold>`)

## [1.2.8] - 2025-12-04
### Added
- **Complete GUI Overhaul**: 14 new views replacing the old 6-view system
  - **MainMenuView**: New central hub with 6 category buttons (Chambers, Loot Tables, Statistics, Settings, Protection, Help)
  - **ChamberListView**: Paginated chamber list (36 per page) replacing ChambersOverviewView
  - **ChamberDetailView**: Enhanced chamber management hub with quick actions
  - **ChamberSettingsView**: Per-chamber settings (reset interval, exit location, loot overrides)
  - **VaultManagementView**: Vault cooldown management with player lock display
  - **SettingsMenuView**: Settings category hub with config reload
  - **GlobalSettingsView**: Runtime-toggleable settings (13 config options, instant save)
  - **ProtectionMenuView**: Protection toggle switches without YAML editing
  - **StatsMenuView**: Statistics category menu with leaderboard shortcuts
  - **LeaderboardView**: Top 10 players by category with player heads
  - **PlayerStatsView**: Individual player stats with K/D ratio and averages
  - **LootTableListView**: Direct loot table browser
  - **HelpMenuView**: Command reference, permissions, and plugin info
- **Runtime Config Editing**: Toggle 13 config options directly in the GUI
  - Reset settings: clear-ground-items, remove-spawner-mobs, reset-trial-spawners, reset-vault-cooldowns
  - Feature settings: spawner-waves, boss-bar, spectator-mode, statistics tracking
  - Loot settings: apply-luck-effect, per-player-loot
  - Protection settings: All 6 protection toggles
  - Changes save immediately to config.yml
- **Pagination Support**: Handle unlimited chambers with 36 items per page
- **Session Restoration**: Return to previous screens with proper state preservation

### Fixed
- **MySQL Database Compatibility**: Fixed SQL syntax errors when using MySQL database
  - `StatisticsManager.batchAddTimeSpent()` now uses `ON DUPLICATE KEY UPDATE` for MySQL (was SQLite-only `ON CONFLICT`)
  - `StatisticsManager.saveStats()` now uses `ON DUPLICATE KEY UPDATE` for MySQL (was SQLite-only `INSERT OR REPLACE`)
  - Exposed `DatabaseManager.databaseType` property for runtime database type detection
- **Folia Compatibility in ChamberDetailView**: Fixed player ejection not using Folia-safe scheduling
  - `exitPlayers()` now uses `scheduler.runAtEntity()` for each player (required for Folia where players may be in different regions)
  - Added `player.isOnline` check before teleporting to prevent errors with disconnected players
- **Coroutine Scope Leak in Commands**: Fixed orphaned coroutine scope causing resource leaks
  - Removed standalone `commandScope` from `TCPCommand.kt` and `TCPCommandExtensions.kt`
  - Commands now use `plugin.launchAsync {}` which is properly cancelled on plugin disable
  - Ensures all async command operations are tied to plugin lifecycle
- **SpawnerWaveManager Async Thread Safety**: Fixed boss bar cleanup running on wrong thread
  - Changed `runTaskLaterAsync` to `runTaskLater` for boss bar removal
  - `removeBossBar()` calls `server.getPlayer()` which requires main thread access
- **GlobalSettingsView Code Cleanup**: Removed unused parameters from extension function
  - Removed redundant `pane` and `player` parameters from `StaticPane.addItem()` extension
  - Cleaned up all 10 call sites

### Changed
- **MenuService**: Rewritten with 16 screen enums and unified navigation methods
- **Legacy Compatibility**: Old GUI methods deprecated but still functional
- **/tcp menu**: Now opens MainMenuView instead of ChambersOverviewView

### Technical Details
- New helper methods in ChamberManager: `updateResetInterval()`, `updateExitLocation()`
- New helper method in VaultManager: `getVaultLockCount()`
- GUI system uses consistent back/close navigation pattern
- All views use StaticPane and OutlinePane for layout
- `DatabaseManager.databaseType` now publicly accessible for database-specific SQL queries

## [1.2.7] - 2025-12-04
### Added
- **Per-Chamber Loot Tables**: Override global loot tables on a per-chamber basis
  - Set chamber-specific loot tables for normal and/or ominous vaults
  - Priority hierarchy: Chamber override > Vault's stored table > Global default
  - Fully backwards compatible - existing chambers use defaults automatically

### New Commands
- `/tcp loot set <chamber> <normal|ominous> <table>` - Set loot table override for a chamber
- `/tcp loot clear <chamber> [normal|ominous|all]` - Clear loot table override(s)
- `/tcp loot info <chamber>` - Show current loot table settings for a chamber
- `/tcp loot list` - List all available loot tables from loot.yml

### Technical Details
- New database columns: `normal_loot_table` and `ominous_loot_table` in `chambers` table
- Automatic database migration on startup (safe to run multiple times)
- New permission: `tcp.admin.loot` for loot table management
- Full tab completion support for all loot subcommands
- Chamber model extended with `getLootTable(VaultType)` helper method
- ChamberManager provides `getEffectiveLootTable()` for priority resolution

## [1.2.6] - 2025-12-04
### Fixed
- **CRITICAL: Trial Spawners Not Dropping Keys**: Fixed trial spawners not dropping trial keys after chamber resets
  - Root cause: `NBTUtil.kt` had empty implementations for trial spawner capture/restore
  - Trial spawners store `registered_players` (UUIDs of players who've used them)
  - Without clearing this data, spawners "remembered" they were already completed
  - Now properly clears tracked players using Paper API's `TrialSpawner.stopTrackingPlayer()`
  - Spawners will now drop keys (50% chance per player) after mobs are defeated per vanilla behavior
- **NBTUtil Trial Spawner Capture**: Now captures ominous state, cooldown length, and required player range
- **NBTUtil Trial Spawner Restore**: Now clears all tracked players/entities and restores proper state
- **ResetManager Trial Spawner Reset**: Implemented the TODO for resetting trial spawners during chamber reset
  - Scans chamber for all TRIAL_SPAWNER blocks
  - Clears tracked players (the key fix for key drops)
  - Clears tracked entities (spawned mobs)
  - Optionally resets ominous spawners back to normal

### Added
- New config option `reset.reset-trial-spawners: true` to control trial spawner state reset
  - When enabled (default), trial spawners clear their tracked players on chamber reset
  - This allows spawners to be reactivated and drop keys again
  - Documented with explanation of vanilla 50% key drop chance

### Changed
- Config option `reset.reset-ominous-spawners` now properly implemented (was a TODO)
- Trial spawner reset is now a separate step in chamber reset sequence (Step 4)

### Technical Details
- Uses Paper API 1.21+ `TrialSpawner` interface methods:
  - `getTrackedPlayers()` / `stopTrackingPlayer()` - Player tracking
  - `getTrackedEntities()` / `stopTrackingEntity()` - Entity tracking
  - `isOminous()` / `setOminous()` - Ominous state management
- Folia compatible: Uses location-based scheduling for spawner operations
- 10-second timeout for spawner reset to handle larger chambers

## [1.2.5] - 2025-11-30
### Added
- **PlaceholderAPI Integration**: Full support for PlaceholderAPI placeholders
  - Player statistics: `%tcp_vaults_opened%`, `%tcp_vaults_normal%`, `%tcp_vaults_ominous%`
  - Progress tracking: `%tcp_chambers_completed%`, `%tcp_mobs_killed%`, `%tcp_deaths%`
  - Time tracking: `%tcp_time_spent%` (formatted), `%tcp_time_spent_raw%` (seconds)
  - Current state: `%tcp_current_chamber%`, `%tcp_in_chamber%`
  - Leaderboard positions: `%tcp_leaderboard_vaults%`, `%tcp_leaderboard_chambers%`, `%tcp_leaderboard_time%`
  - Top players: `%tcp_top_vaults_1_name%` through `%tcp_top_vaults_10_name%` (and `_value`)
  - Same pattern for `%tcp_top_chambers_X_name%` and `%tcp_top_time_X_name%`
  - Built-in 60-second cache for leaderboard data to reduce database load

- **Trial Spawner Wave System**: Real-time wave progress tracking with boss bar display
  - Boss bar shows wave progress (mobs killed / total mobs)
  - Ominous spawners display purple boss bar, normal shows yellow
  - Automatic mob tracking from trial spawner spawn events
  - Wave completion message with kill count and duration
  - Configurable options: `spawner-waves.enabled`, `spawner-waves.show-boss-bar`
  - Detection radius for adding nearby players to boss bar display
  - Integration with existing statistics system

- **Spectator Mode**: Players can spectate chambers after death
  - Offer spectator mode to players who die in a chamber
  - Chat-based confirmation: type "spectate" to watch, "no" to decline
  - Spectators put in GameMode.SPECTATOR and teleported to chamber center
  - Boundary restriction keeps spectators within chamber bounds (configurable buffer)
  - Exit by typing "exit" in chat or when chamber resets
  - Automatic cleanup on player disconnect or chamber reset
  - New permission: `tcp.spectate` (default: true)
  - Configurable timeout, boundary buffer, and solo spectate option

### Changed
- Plugin now initializes through 12 phases (added Spawner Wave System and Spectator Mode phases)
- Startup logging shows status of new features (PlaceholderAPI, Boss Bar, Death Spectate)

## [1.2.4] - 2025-11-30
### Fixed
- **Tipped Arrow Duration Bug**: Fixed tipped arrows showing "00:00" duration when looted from vaults
  - Root cause: Minimum duration check only triggered when calculated duration was exactly 0
  - If Paper API returned small duration values (1-99 ticks), integer division produced tiny but non-zero values that bypassed the minimum check
  - Now uses `maxOf(calculatedDuration, minDuration)` to always enforce minimum durations
  - Also fixed: `effect-duration: 0` in config now correctly falls back to calculated duration instead of using 0

### Added
- **Full Folia Support**: Complete rewrite of scheduler system for Folia compatibility
  - New `SchedulerAdapter` abstraction layer with Paper and Folia implementations
  - Automatic Folia detection at startup with appropriate scheduler selection
  - Location-based scheduling (`runAtLocation`) for block operations (snapshots, restoration, scanning)
  - Entity-based scheduling (`runAtEntity`) for player operations (teleportation, inventory, messages)
  - Global region scheduling for non-location-specific tasks
  - Added `folia-supported: true` to plugin.yml

### Changed
- **17 files updated** to use the new scheduler abstraction:
  - Core: `TrialChamberPro.kt`, `CoroutineExtensions.kt`
  - Managers: `ResetManager.kt`, `ChamberManager.kt`, `SnapshotManager.kt`, `LootManager.kt`, `PasteConfirmationManager.kt`
  - Listeners: `VaultInteractListener.kt`, `PlayerMovementListener.kt`, `UndoListener.kt`
  - GUI: `MenuService.kt`, `LootTypeSelectView.kt`, `LootEditorView.kt`
  - Utils: `BlockRestorer.kt`, `ParticleVisualizer.kt`, `UpdateChecker.kt`
- Player teleportation during chamber resets now uses entity-specific scheduling for thread safety
- Block restoration batches now schedule to the correct region thread per chunk
- Chamber scanning uses location-based scheduling for proper Bukkit API access

### Technical Details
- `SchedulerAdapter` interface provides unified API for both platforms:
  - `runTask()` / `runTaskAsync()` - Global/async tasks
  - `runTaskLater()` / `runTaskLaterAsync()` - Delayed tasks (now return `ScheduledTask` for cancellation)
  - `runTaskTimer()` / `runTaskTimerAsync()` - Repeating tasks
  - `runAtLocation()` / `runAtLocationLater()` - Region-specific tasks (critical for Folia)
  - `runAtEntity()` / `runAtEntityLater()` - Entity-specific tasks (critical for Folia)
- On Paper: All location/entity methods delegate to main thread
- On Folia: Uses `RegionScheduler`, `EntityScheduler`, `GlobalRegionScheduler`, and `AsyncScheduler`

## [1.2.3] - 2025-11-24
### Added
- **Configurable Potion Effect Durations**: New `effect-duration` field for complete control over potion/tipped arrow effect duration
  - Optional field in loot.yml (measured in ticks, where 20 ticks = 1 second)
  - When not specified, automatically calculates duration using **vanilla Minecraft behavior**
  - **Tipped arrows now use 1/8 of potion duration** (matching vanilla mechanics)
  - Smart multipliers: POTION (1.0×), SPLASH_POTION (0.75×), LINGERING_POTION (0.25×), TIPPED_ARROW (0.125×)
  - Examples: Speed I arrow (22.5s), Slowness I arrow (11.25s), Poison I arrow (5.625s)
  - Works with both `potion-type` (auto-calculated) and `custom-effect-type` (hardcoded fallbacks)
  - Fully documented in loot.yml and loot.yml.md with calculation tables and examples

### Technical Improvements
- Duration calculation now reads base duration from PotionType at runtime
- Automatic scaling ensures consistency with vanilla Minecraft across all potion types
- Custom effect types (BAD_OMEN, etc.) use sensible fallback durations

## [1.2.2] - 2025-11-23
### Fixed
- **CRITICAL:** Fixed ominous bottles creating "uncraftable potion" items instead of actual OMINOUS_BOTTLE items
  - When `ominous-potion: true` was set in loot.yml, the plugin was creating a POTION item with custom effects
  - Now correctly creates Material.OMINOUS_BOTTLE (the 1.21 item type with proper texture and properties)
  - Ominous bottles now display correctly in inventory and match vanilla behavior
  - Custom effects (Bad Omen levels, names, lore) still apply correctly via PotionMeta

## [1.2.1] - 2025-11-22
### Added
- **Ominous Bottles (1.21+ Bad Omen Potions)**: Custom effect type support for special potions
  - New `custom-effect-type` field for potion effects not available as PotionType
  - Full support for BAD_OMEN effect (triggers Ominous Trials)
  - Create ominous bottles with levels III-V (matching vanilla behavior)
  - Duration automatically set to 100 minutes for Bad Omen (matching vanilla)
  - Support for other custom effects: HERO_OF_THE_VILLAGE, LUCK, UNLUCK, GLOWING
  - Works with POTION, SPLASH_POTION, LINGERING_POTION, and TIPPED_ARROW
  - Comprehensive documentation in loot.yml and loot.yml.md
  - Examples for all ominous bottle levels with proper naming and lore

### Fixed
- **CRITICAL:** Fixed duplicate potion effects on tipped arrows and potions with custom levels
  - When using `potion-level`, the code was applying BOTH the base potion effect AND a custom effect
  - Now only applies the custom effect when `potion-level` is specified
  - Example: `potion-type: SLOWNESS, potion-level: 4` now correctly creates Slowness V (not Slowness I + Slowness V)
  - Default potion behavior (without custom level) unchanged
- **Eliminated all deprecation warnings** for Minecraft 1.21.1+ compatibility
  - Migrated from deprecated `AsyncPlayerChatEvent` to modern `AsyncChatEvent` (Paper 1.19+)
    - Now uses Adventure component API with PlainTextComponentSerializer
    - Maintains backward compatibility with confirmation system
  - Added `@OptIn(ExperimentalCoroutinesApi::class)` for suspendCancellableCoroutine usage
  - Replaced deprecated `Sound.valueOf()` with registry access API
    - Uses `Registry.SOUNDS.get()` with NamespacedKey conversion
    - Automatic conversion from enum-style names (BLOCK_VAULT_OPEN_SHUTTER) to namespaced keys
  - Replaced deprecated `PotionEffectType.getByName()` with `Registry.POTION_EFFECT_TYPE.get()`
  - Replaced deprecated `PotionType.effectType` property with `potionEffects.firstOrNull()?.type`
  - Fixed WorldEdit BlockVector3 coordinate deprecations
    - Changed from deprecated `getX()/getY()/getZ()` to modern `x()/y()/z()` methods
    - Compatible with WorldEdit 7.3+ and FastAsyncWorldEdit
- Zero deprecation warnings when building with Kotlin 2.3.0 and Paper API 1.21.1

### Technical Improvements
- All code now uses modern Paper/Bukkit registry access APIs
- Better compatibility with future Minecraft versions
- Cleaner build output with no deprecation noise
- Future-proof API usage following Paper best practices

## [1.2.0] - 2025-11-22
### Added
- **COMMAND Rewards System**: Run console commands when players open vaults
  - Economy rewards (requires Vault plugin): `eco give {player} 1000`
  - Permission grants (LuckPerms): `lp user {player} parent add vip`
  - Experience rewards: `xp add {player} 1000`
  - Item rewards: `give {player} diamond 64`
  - Title/action bar messages and any console command
  - Weight-based probability system (e.g., 25% chance for 1000 coins)
  - Multiple commands per reward execute in sequence
  - Player placeholders: `{player}` (name), `{uuid}` (UUID)
  - Visual feedback with customizable display names
  - Commands run with CONSOLE permissions (OP)
- **Comprehensive COMMAND rewards documentation** in loot.yml
  - 100+ lines of examples and use cases
  - Basic single-pool format examples
  - Advanced multi-pool format examples (separate items and bonuses)
  - Common command examples for economy, permissions, items, XP
  - Jackpot/rare reward examples with multiple commands
  - Full placeholder reference

### Fixed
- Added detailed error message when using incorrect `type: COMMAND` format
  - Shows clear comparison of WRONG vs CORRECT format
  - Directs users to proper `command-rewards` list format
  - Prevents common configuration mistakes

### Documentation
- Added COMMAND rewards section to loot.yml with comprehensive examples
- Documented weight-based probability system for command rewards
- Added examples for economy (Vault), permissions (LuckPerms), and vanilla commands
- Included multi-command reward examples (jackpots, VIP upgrades)

## [1.1.9] - 2025-11-22
### Added
- **Advanced Loot Features**: Vanilla-style loot customization with full 1.21+ support
  - **Tipped Arrows**: Add any potion effect to arrows (POISON, SLOWNESS, etc.) with custom levels
    - Example: `type: TIPPED_ARROW`, `potion-type: POISON`, `potion-level: 1` for Poison II arrows
  - **Potions with Custom Levels**: Create potions beyond vanilla limits
    - Works with POTION, SPLASH_POTION, and LINGERING_POTION types
    - Set `potion-type` and `potion-level` (0 = Level I, 1 = Level II, etc.)
    - Supports level IV+ for ominous potions (1.21+ exclusive feature)
  - **Ominous Potions**: Rare 1.21+ bottles with extreme effect levels
    - Set `ominous-potion: true` for special ominous bottle appearance
    - Perfect for rare ominous vault rewards
  - **Enchantment Randomization**: Dynamic enchantment levels like vanilla
    - `enchantment-ranges`: Apply enchantments with random levels (e.g., `SHARPNESS:1:5` for I-V)
    - `random-enchantment-pool`: Pick ONE random enchantment from a list
    - Combine fixed enchantments, ranges, and random pools on the same item
  - **Variable Durability**: Drop pre-damaged items with random wear
    - Set `durability-min` and `durability-max` for random damage values
    - Great for "used" weapons and tools as loot
- **Debug Mode Startup Banner**: Visual confirmation when debug mode is enabled
  - Displays prominent warning banner in console on server startup
  - Helps verify `debug.verbose-logging: true` is loaded correctly
  - Shows: "DEBUG MODE ENABLED - Verbose logging is active"

### Fixed
- **CRITICAL:** Fixed race condition causing duplicate vault entries
  - Multiple async saveVault() calls could insert duplicates for the same location
  - Changed to atomic UPSERT operation with `INSERT ... ON CONFLICT DO UPDATE`
  - Prevents duplicate vault entries during concurrent scanning operations
- **CRITICAL:** Fixed separate cooldowns for normal vs ominous vaults
  - Changed UNIQUE constraint from `(chamber_id, x, y, z)` to `(chamber_id, x, y, z, type)`
  - Now stores BOTH normal and ominous vault entries at each location for independent cooldown tracking
  - Players can have separate 24h/48h cooldowns for normal and ominous modes at the same vault
  - `VaultManager.getVault()` now requires vault type parameter to fetch correct entry
  - When scanning, creates both NORMAL and OMINOUS entries regardless of current block state
- **CRITICAL:** Enabled SQLite foreign key constraints
  - Added `?foreign_keys=on` to SQLite JDBC URL
  - Fixes CASCADE deletion not working when chambers are deleted
  - Orphaned vault/spawner/player data is now automatically cleaned up
  - No more stale vault entries from deleted chambers
- **CRITICAL:** Fixed ominous vaults giving normal loot after rescanning
  - Root cause: `ChamberManager.saveVault()` was skipping existing vaults instead of updating them
  - Changed logic to UPDATE existing vaults with correct type and loot_table when rescanning
  - Old vaults in database retained stale `lootTable="default"` even for ominous vaults
  - Now properly updates vault type and loot table on every scan
  - **Action Required**: Delete database and rescan all chambers with `/tcp scan <name>`
- Fixed vault counts showing 0 in `/tcp menu` after reload
  - Root cause: `VaultManager.countsCache` was not being cleared on reload
  - Added `VaultManager.clearCache()` method to clear vault counts cache
  - `/tcp reload` now clears both chamber cache and vault counts cache
  - Vault counts are now correctly refreshed after reload
- Added comprehensive debug logging for loot generation
  - Shows requested loot table name and available tables
  - Displays whether table lookup succeeded
  - Logs number of items generated per vault opening
  - Helps diagnose loot table configuration issues

### Changed
- **Database Schema**: Updated vaults table UNIQUE constraint to include vault type
  - Changed from `UNIQUE (chamber_id, x, y, z)` to `UNIQUE (chamber_id, x, y, z, type)`
  - Allows both NORMAL and OMINOUS vaults to coexist at same location
  - **Breaking Change**: Requires deleting existing database
- **Vault Scanning**: Now creates two database entries per vault location
  - One entry for NORMAL type with default loot table
  - One entry for OMINOUS type with ominous-default loot table
  - Enables independent cooldown tracking for each vault type
- **Vault Lookup**: `VaultManager.getVault()` signature changed
  - Added required `type: VaultType` parameter
  - Looks up vault by location AND type for accurate cooldown checking
- Enhanced `LootItem` model with 7 new optional fields for advanced features
- Enhanced `LootManager.parseLootItem()` to parse all new YAML fields
- Enhanced `LootManager.createItemStack()` to apply potions, enchantments, and durability
- Potion durations automatically adjusted based on type:
  - POTION: 3 minutes (3600 ticks)
  - SPLASH_POTION: 2:15 (2700 ticks)
  - LINGERING_POTION: 45 seconds (900 ticks)
  - TIPPED_ARROW: 20 seconds (400 ticks)

### Migration Notes
**IMPORTANT: This version requires deleting your existing database!**

1. Stop your server
2. Delete `plugins/TrialChamberPro/database.db`
3. Update to v1.1.9
4. Start your server (new database with updated schema will be created)
5. Re-register all chambers with `/tcp register` or `/tcp generate`
6. Scan all chambers with `/tcp scan <chamber-name>`
7. Create snapshots with `/tcp snapshot create <chamber-name>`

**Why**: Database schema changed to support separate NORMAL/OMINOUS vault tracking and enable foreign key constraints. Player cooldown data will be lost, but this ensures proper vault behavior going forward.

### Documentation
- Added 170+ lines of examples to `loot.yml` covering all new advanced features
- Added comprehensive documentation for potion types, enchantment syntax, and combinations
- Updated with vanilla-style loot table examples (tipped arrows, ominous potions, etc.)

## [1.1.8] - 2025-11-21
### Fixed
- **CRITICAL:** Fixed ominous vault detection using blockstate string parsing instead of `isOminous` property
  - Changed from `blockData.isOminous` to checking `blockData.asString` for `ominous=true`
  - Ominous vaults now correctly identified during scanning and interaction
  - Ominous vault loot tables now properly applied instead of using normal vault loot
  - Menu now correctly displays ominous vault counts
  - Updated both `ChamberManager.saveVault()` and `VaultInteractListener` to use string-based detection
- Fixed chambers disappearing from `/tcp menu` after `/tcp reload`
  - `ChamberManager.clearCache()` now automatically reloads all chambers from database asynchronously
  - Chambers are immediately available after reload without needing to run `/tcp info`

### Changed
- Debug logging now shows full blockstate string for vault detection: `blockData='minecraft:vault[ominous=true,...]'`

### Migration Notes
- **Action Required:** Rescan existing chambers with `/tcp scan <name>` to update vault types in database
  - Existing vaults may be stored with incorrect types (all as NORMAL)
  - Rescanning will correctly identify ominous vs normal vaults using the new detection method

## [1.1.7] - 2025-11-21
### Added
- **Multi-Pool Loot System**: Vanilla-style loot pools (common/rare/unique) with independent roll ranges
  - Support for up to 5 pools per loot table (configurable via `loot.max-pools-per-table`)
  - Backwards compatible with legacy single-pool format
  - Each pool has its own min/max rolls, weighted items, and guaranteed items
  - Loot tables automatically convert from legacy format when needed
- **GUI Pool Support**: Complete GUI workflow for managing multi-pool loot tables
  - New `PoolSelectorView` displays all pools in a multi-pool table with visual icons
  - Pool icons: Iron Ingot (common), Diamond (rare), Nether Star (unique), Chest (other)
  - `LootTypeSelectView` automatically detects multi-pool vs legacy tables and routes accordingly
  - `LootEditorView` now supports editing specific pools within multi-pool tables
  - Navigation flow: Overview → Loot Type → Pool Selector (if multi-pool) → Editor → Amount Editor
  - Per-pool draft preservation with session-based tracking
- **LUCK Effect Support**: Optional bonus loot rolls based on player LUCK
  - Configurable via `loot.apply-luck-effect: false` (default: disabled)
  - Checks both potion effects (temporary) and item attributes (permanent)
  - Each LUCK level adds +1 bonus roll to each pool
  - Works with potions, beacons, suspicious stew, and custom items with LUCK modifiers
  - Debug logging available with `debug.verbose-logging: true`
- Increased max chamber volume from 500,000 to 750,000 blocks (`generation.max-volume`)

### Fixed
- **CRITICAL:** Fixed ominous vault detection - now properly checks block state (`vault[ominous=true]`)
  - Ominous vaults can now be opened with ominous trial keys
  - Vault scanning correctly identifies ominous vs normal vaults via `BlockData.isOminous`
  - Updated both `VaultInteractListener` and `ChamberManager` to use block state checking

### Changed
- `LootManager` now supports both legacy single-pool and new multi-pool loot table formats
- `MenuService.Session` now tracks `poolName` for pool-specific navigation
- Draft key generation includes pool name to isolate edits per pool
- `AmountEditorView` now accepts `poolName` parameter for proper back navigation

### Internal
- Added `LootPool` data class with min/max rolls and item lists
- Updated `LootTable.isLegacyFormat()` to detect format automatically
- Added `LootTable.getEffectivePools()` to convert legacy tables to pool format on-the-fly
- MenuService now has 6 GUI views: Overview, LootTypeSelect, PoolSelector, LootEditor, AmountEditor
- GUI navigation state machine expanded with `POOL_SELECT` screen

### Documentation
- Updated `loot.yml` with extensive multi-pool examples and migration guide
- Updated `config.yml.md` with LUCK effect documentation and balance warnings

## [1.1.6] - 2025-11-20
### Added
- Added "Locked: X Normal, X Ominous" display to `/tcp menu` chamber tooltips showing per-player locked vault counts
- Added `vault-locked` message for permanent vault locks (distinct from time-based cooldowns)
- Added in-memory lock to prevent vault spam-clicking race condition (5-second window per player-vault)
- Added `getLockedVaultCounts()` function to query locked vaults per player per chamber

### Fixed
- **CRITICAL:** Fixed `Dispatchers.Main` error when opening vaults - now uses Bukkit scheduler instead of non-existent Android Main dispatcher
- **CRITICAL:** Fixed vault statistics never being recorded - `incrementVaultsOpened()` now called when opening vaults
- **CRITICAL:** Fixed database connection pool exhaustion causing 60s timeouts:
  - Increased SQLite connection pool from 1 to 5 (WAL mode supports concurrent readers)
  - Reduced connection timeout from 60s to 30s (fail faster)
  - Added SQLite `busy_timeout=5000` for better lock handling
  - Added leak detection threshold (10s) to identify connection leaks
  - Staggered vault count refreshes in menu to prevent connection stampede
- Fixed UPSERT race condition in `recordOpen()` - now uses atomic SQL `INSERT ... ON CONFLICT DO UPDATE`
- Fixed vault spam-clicking allowing multiple opens before database write completed
- Removed all references to deprecated `/tcp add` command from help, docs, and tab completion

### Changed
- **BREAKING:** Changed default vault cooldown from 24/48 hours to -1 (permanent lock, vanilla behavior)
  - Set `normal-cooldown-hours: -1` (permanent until chamber reset)
  - Set `ominous-cooldown-hours: -1` (permanent until chamber reset)
  - Set `reset-vault-cooldowns: true` by default (required for vanilla behavior)
  - Cooldown can still be customized to time-based (e.g., 24 = 24 hours)
- Updated `canOpenVault()` to interpret negative cooldown as permanent lock
- Vault opening now shows different messages for permanent lock vs time-based cooldown
- Replaced `/tcp add` with `/tcp generate wand` in all documentation and examples

### Internal
- Database schema changes: None (backwards compatible)
- Config changes: Default cooldown values changed from 24/48 to -1
- Permission `tcp.bypass.cooldown` now properly documented as separate from `tcp.admin.*`

### Migration Notes
- Existing vaults opened with old cooldowns will now be permanently locked
- Run `/tcp reset <chamber>` to unlock all vaults in a chamber
- Admins: explicitly deny `tcp.bypass.cooldown` permission if you want to test locking behavior

## [1.1.5] - 2025-10-31
### Added
- Checking for Update from GitHub Releases with a short message, describing what has changed from the previous version.
- Particle Visualizer for the `/tcp paste <name>` command.

### Fixed
- Pasting a schematic with `/tcp paste <name>` now works as expected.`
- Correctly allowing WorldEdit `//undo` and `//redo` commands for pasted schematics.

### Changed
- The `/tcp paste <name>` command now visually represents the boundaries with particles before pasting. The user needs to confirm or cancel the paste in chat.

## [1.1.4] - 2025-10-28
### Added
- FastAsyncWorldEdit (and WorldEdit) soft-depend, as requested, along with FAWE/WE Detection.
- New Schematic Manager can be called with /tcp paste to paste one of the included Trial Chambers or /tcp schematics to view a list of available schematics.
- Without WorldEdit/FAWE, schematic features will be disabled gracefully. 

### Fixed
- Newly generated Trial Chambers appear immediately in `/tcp menu` without requiring a server restart. The Overview now sorts chambers by creation time (newest first), ensuring fresh entries are shown on the first page.
- Cache refresh after updates: setting a chamber's exit location or snapshot file now refreshes the in-memory cache entry so the GUI reflects changes without delays.
- Other minor bugfixes and code cleanup.

### Internal
- `ChamberManager.getCachedChambers()` now returns chambers sorted by `createdAt` descending.
- Minor cache handling refinements when updating chamber fields.

## [1.1.3] - 2025-10-28
### Fixed
- Loot Editor controls render fully on first open (Save bottom-left, Discard bottom-right, Rolls, etc.) — no initial click needed.
- Right-click on an item opens the Amount editor on the first click; no more double-click behavior.
- UI now refreshes instantly after edits (toggle enabled, adjust weight, add from hand, tweak rolls), and the Save button lore reflects unsaved changes immediately.

### Changed
- Replaced the Back arrow with an explicit red Discard button at the bottom-right.
- Edits are applied to a cloned draft; pressing Discard returns without saving and the on-close handler will not persist the clone.

### Internal
- Reordered GUI build so panes are added before being populated and `gui.update()` is called at the end of the initial build to ensure a complete first render.
- Guard save-on-close with `discardRequested` to avoid persisting discarded edits.
- Rebuild controls after actions like Add-from-hand and Rolls adjustments to keep the UI state accurate.

## [1.1.2] - 2025-10-28
### Changed
- Replaced GlobalScope with a structured plugin coroutine scope for chamber resets in GUI. Resets now run on the plugin-managed scope and marshal results back to the main thread for messaging.
- Chambers Overview now uses a lightweight TTL cache for vault counts to avoid blocking the server thread. No more runBlocking; counts refresh asynchronously and are warmed when opening the overview.

### Notes
- The cache defaults to a 30s TTL and returns last-known values (or 0/0 initially) while refreshing in the background.

## [1.1.1] - 2025-10-27
### Added
- Chambers Overview: now shows vault counts (Normal/Ominous), players inside, time until next reset, and time since last reset with friendly formatting (weeks/months) that omits zero components.

### Fixed
- GUI wiring: aligned `LootTypeSelectView` with current APIs (reset now invoked via coroutine; exit location lookup via `Chamber.getExitLocation()`; world spawn fallback). Resolved drag/drop cancellation and improved tooltips.
- Chambers Overview time display: corrected units (ms) and added smarter humanization.

### Changed
- Minor UI text adjustments ("Click to Manage").

## [1.1.0] - 2025-10-27
### Added
- Admin GUI using InventoryFramework, open with `/tcp menu` (permission `tcp.admin.menu`).
  - Chambers overview screen: shows each chamber as a block with details like world, bounds, players inside, and time until reset. Click to manage loot tables.
  - Loot type selection: choose to edit Normal or Ominous loot tables.
  - Loot editor: drag & drop and click variations to adjust items.
    - Left click: +1 amount
    - Right click: -1 amount
    - Shift + Left click: +1 weight (weighted items only)
    - Shift + Right click: -1 weight (weighted items only)
    - Middle click: toggle enabled/disabled
    - Green Save button (bottom-left) persists changes to loot.yml and updates chamber vaults to use the edited table names.
    - Red Cancel button (bottom-right) returns without saving.
  - Session persistence: if the GUI is closed, the last open window and unsaved draft are remembered per admin until `/tcp menu` is opened again.
- Loot items now support an optional `enabled` flag in `loot.yml` and the generator respects it.

### Changed
- Bumped version to 1.1.0 and updated changelog.

### Fixed
- Prevent crash `UninitializedPropertyAccessException: lateinit property menuService has not been initialized` when running `/tcp menu` during startup by guarding all `/tcp` subcommands until the plugin finishes asynchronous initialization and by setting a readiness flag after registrations complete.

## [1.0.9] - 2025-10-26
### Added
- InventoryFramework support is introduced but not used yet.

### Changed
- Release: bump version to 1.0.9 and update changelog.

## [1.0.8] - 2025-10-26
### Added
- Permission: `tcp.admin.generate` to allow registering chambers from coordinates or blocks.

### Changed
- Minor internal adjustments and documentation updates.

## [1.0.7] - 2025-10-25
### Added
- WorldEdit //undo integration for generated chambers with chat confirmation:
  - When you run `//undo` (or `/undo`) after using `/tcp generate`, the plugin intercepts and asks you to type `confirm` in chat to delete the last chamber you generated (or `cancel`).
  - Confirmation window lasts 15 seconds. On confirm, the chamber is deleted and its snapshot is removed. On cancel/timeout, nothing changes.
- Generation anywhere: chambers can be generated even in empty/air regions; snapshots will simply contain fewer non-air blocks.

### Changed
- Internal: Added UndoListener and UndoTracker, and record last-generated chamber per player upon successful generation.

### Fixed
- Friendly in-game message when generating a chamber with an existing name (instead of SQLite UNIQUE constraint error in console). Generation is cancelled and you are told the name is already in use.

## [1.0.6] - 2025-10-25
### Added
- New /tcp generate modes and parsing improvements:
  - Added `wand` mode to generate directly from your current WorldEdit selection: `/tcp generate wand <chamberName>`.
  - Added `blocks` mode to generate a chamber at your current location based on a desired block count: `/tcp generate blocks <amount> [chamberName] [roundingAllowance]`.
    - The region is placed in front of you using your facing direction.
    - Minimum size enforced (31x15x31). Height defaults to 15; width/depth are chosen to approximate the requested blocks.
    - Rounds up within an allowance (default 1000, configurable at `generation.blocks.rounding-allowance`).
  - Enhanced `coords` mode to accept either two separate coordinate triples or the old hyphenated form:
    - `/tcp generate coords <x1,y1,z1> <x2,y2,z2> [world] <name>`
    - `/tcp generate coords <world> <x1,y1,z1> <x2,y2,z2> <name>`
    - Legacy: `/tcp generate coords <x1,y1,z1-x2,y2,z2> [world] <name>`
  - Robust negative coordinate handling (no more issues with hyphens in negatives).
- Tab completion updated for new modes.

### Changed
- Help and documentation updated to reflect new generate modes and syntax.

## [1.0.5] - 2025-10-25
### Added
- New /tcp generate command enhancements:
  - Support for saved WorldEdit variable regions via plugin-managed we-vars.yml.
  - Subcommands: value save <name>, value list, value delete <name>.
  - Generate from named var: /tcp generate value <varName> [chamberName].
  - Coordinate mode: /tcp generate coords <x1,y1,z1-x2,y2,z2> [world] <chamberName>.
- Tab completion for generate subcommands and saved names.

### Changed
- Increased minimum Trial Chamber region size to 31x15x31 based on official structure sizes.
- Updated help messages and documentation.

## [1.0.4] - 2025-10-25
### Fixed
- Resolved SQLite UnsatisfiedLinkError caused by relocating org.sqlite during shading. The sqlite-jdbc driver is now shaded without relocation so its native bindings can load correctly.

### Changed
- Build now produces only the shaded (fat) jar; the thin "plain" jar is no longer built.

## [1.0.3] - 2025-10-25
### Fixed
- Build produced a small (~308 KB) jar. Updated Gradle packaging so the shaded (fat) jar is the primary artifact and the thin jar is classified as "plain". This ensures the distributed jar is >15 MB and contains all dependencies.

## [1.0.2] - 2025-10-25
### Fixed
- Prevented generic "/tcp [args]" usage from appearing for `/tcp`, `/tcp help`, and `/tcp list` by registering the command executor and tab completer immediately during plugin enable.

### Notes
- Left the later, redundant registration in place for now (safe to remove in a future cleanup).

## [1.0.1] - 2025-10-24
### Fixed
- Resolved plugin startup failure `NoClassDefFoundError: kotlinx/coroutines/Dispatchers` by stopping relocation of Kotlin stdlib and kotlinx-coroutines during shading. They are still shaded but keep original package names so Bukkit can load them at bootstrap.

### Internal
- Kept relocations for SQLite and HikariCP to avoid classpath conflicts.

## [1.0.0] - 2025-10-24
### Added
- Initial release of TrialChamberPro with:
  - Snapshot system for chambers
  - Per-player vaults and loot tables
  - Automatic reset scheduler with warnings
  - Protection listeners and optional integrations (WorldGuard, WorldEdit, PlaceholderAPI)
  - Statistics tracking and leaderboards

[1.3.1]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.3.0...v1.3.1
[1.3.0]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.29...v1.3.0
[1.2.29]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.28...v1.2.29
[1.2.28]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.27...v1.2.28
[1.2.27]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.26...v1.2.27
[1.2.26]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.25...v1.2.26
[1.2.25]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.24...v1.2.25
[1.2.24]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.23...v1.2.24
[1.2.23]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.22...v1.2.23
[1.2.22]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.21...v1.2.22
[1.2.21]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.20...v1.2.21
[1.2.20]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.19...v1.2.20
[1.2.19]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.18...v1.2.19
[1.2.18]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.17...v1.2.18
[1.2.17]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.16...v1.2.17
[1.2.16]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.15...v1.2.16
[1.2.15]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.14...v1.2.15
[1.2.14]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.13...v1.2.14
[1.2.13]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.12...v1.2.13
[1.2.12]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.11...v1.2.12
[1.2.11]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.10...v1.2.11
[1.2.10]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.9...v1.2.10
[1.2.9]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.8...v1.2.9
[1.2.8]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.7...v1.2.8
[1.2.7]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.6...v1.2.7
[1.2.6]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.5...v1.2.6
[1.2.5]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.4...v1.2.5
[1.2.4]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.3...v1.2.4
[1.2.3]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.2...v1.2.3
[1.2.2]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.1...v1.2.2
[1.2.1]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.2.0...v1.2.1
[1.2.0]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.1.9...v1.2.0
[1.1.9]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.1.8...v1.1.9
[1.1.8]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.1.7...v1.1.8
[1.1.7]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.1.6...v1.1.7
[1.1.6]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.1.5...v1.1.6
[1.1.5]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.1.4...v1.1.5
[1.1.4]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.1.3...v1.1.4
[1.1.3]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.1.2...v1.1.3
[1.1.2]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.1.1...v1.1.2
[1.1.1]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.0.9...v1.1.0
[1.0.9]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.0.8...v1.0.9
[1.0.8]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.0.7...v1.0.8
[1.0.7]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.0.6...v1.0.7
[1.0.6]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.0.5...v1.0.6
[1.0.5]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.0.4...v1.0.5
[1.0.4]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.0.3...v1.0.4
[1.0.3]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.0.0...v1.0.1