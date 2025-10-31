# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

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