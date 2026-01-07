# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

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
- CLAUDE.md updated with multi-pool architecture and GUI navigation flow

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