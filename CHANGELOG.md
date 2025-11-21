# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

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