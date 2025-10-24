# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project adheres to Semantic Versioning.

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

[1.0.3]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.0.2...v1.0.3
[1.0.2]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.0.1...v1.0.2
[1.0.1]: https://github.com/darkstarworks/TrialChamberPro/compare/v1.0.0...v1.0.1