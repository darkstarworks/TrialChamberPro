# 💬 messages.yml Reference

Want your plugin to match your server's vibe? The `messages.yml` file lets you customize every single message players see. Whether you're running a serious roleplay server or a meme-filled chaos realm, you've got full control.

<div data-gb-custom-block data-tag="hint" data-style="info">

**Location:** `plugins/TrialChamberPro/messages.yml`

After making changes, reload with `/tcp reload`

</div>

<div data-gb-custom-block data-tag="hint" data-style="info">

**Translating the admin GUI?** As of v1.3.0 every name, lore line, and button label across all 18 admin GUI views also lives here, under a nested `gui.*` section (~330 keys). For the GUI-specific translation conventions — section layout, shared toggle templates, the `gui.common.*` reuse pattern — see the [Localization](localization.md) guide.

</div>

---

## 🎨 Color Codes & Formatting

All messages support Minecraft color codes using `&`:

### Colors
```yaml
&0 - Black          &8 - Dark Gray
&1 - Dark Blue      &9 - Blue
&2 - Dark Green     &a - Green
&3 - Dark Aqua      &b - Aqua
&4 - Dark Red       &c - Red
&5 - Dark Purple    &d - Light Purple
&6 - Gold           &e - Yellow
&7 - Gray           &f - White
```

### Formatting
```yaml
&l - Bold
&o - Italic
&n - Underline
&m - Strikethrough
&k - Magic/Obfuscated
&r - Reset (removes all formatting)
```

### Examples
```yaml
"&6&lBOLD GOLD TEXT"
"&c&oItalic red text"
"&a✓ &fGreen checkmark, white text"
"&k?????? &r&eMystery text revealed!"
```

<div data-gb-custom-block data-tag="hint" data-style="success">

**Pro tip:** Use `&r` to reset formatting before starting new text. Prevents colors bleeding into placeholders!

</div>

---

## 📋 Message Categories

### General Messages

```yaml
prefix: "&8[&6TCP&8]&r "
reload-success: "&aConfiguration reloaded successfully!"
no-permission: "&cYou don't have permission to use this command."
player-only: "&cThis command can only be used by players."
unknown-command: "&cUnknown subcommand. Use /tcp help for help."
```

#### `prefix`
Prepended to most messages. Keep it short!

**Examples:**
- Minimalist: `"&8[&6TCP&8] "`
- Fancy: `"&8[&6⚔ &eTrialChamber&6 ⚔&8] "`
- Roleplay: `"&7[&5Ancient Chamber&7] "`
- No prefix: `""` (empty string)

---

### Chamber Management

```yaml
chamber-created: "&aChamber &e{chamber}&a created successfully!"
chamber-already-exists: "&cA chamber with that name already exists."
chamber-not-found: "&cChamber &e{chamber}&c not found."
chamber-deleted: "&aChamber &e{chamber}&a deleted successfully."
chamber-list-header: "&6=== Registered Chambers ==="
chamber-list-item: "&e{chamber} &7- &f{world} &7({volume} blocks)"
chamber-list-empty: "&cNo chambers registered yet."
no-selection: "&cYou must make a WorldEdit selection first."
worldedit-not-found: "&cWorldEdit is not installed or enabled."
```

**Placeholders:**
- `{chamber}` - Chamber name
- `{world}` - World name
- `{volume}` - Total blocks in chamber

**Customization ideas:**

Minimalist:
```yaml
chamber-created: "&a✓ Created {chamber}"
chamber-deleted: "&c✗ Deleted {chamber}"
```

Fancy:
```yaml
chamber-created: "&8[&a✓&8] &fChamber &6{chamber} &fhas been &aregistered&f!"
chamber-list-header: "&6&m          &r &6Registered Chambers &m          "
```

Roleplay:
```yaml
chamber-created: "&7The ancient chamber &e{chamber}&7 has been awakened..."
chamber-deleted: "&7The chamber &e{chamber}&7 fades from memory..."
```

---

### Scanning

```yaml
scan-started: "&aScanning chamber &e{chamber}&a..."
scan-complete: "&aScanning complete! Found &e{vaults}&a vaults, &e{spawners}&a spawners, &e{pots}&a decorated pots."
scan-failed: "&cFailed to scan chamber: {error}"
```

**Placeholders:**
- `{chamber}` - Chamber name
- `{vaults}` - Number of vaults found
- `{spawners}` - Number of trial spawners found
- `{pots}` - Number of decorated pots found
- `{error}` - Error message (on failure)

**Examples:**

Tech-style:
```yaml
scan-started: "&7[SYSTEM] Initiating scan of &f{chamber}&7..."
scan-complete: "&a[SCAN COMPLETE] &7Detected: &f{vaults}x Vaults, {spawners}x Spawners, {pots}x Pots"
```

Adventure-style:
```yaml
scan-started: "&7Exploring the depths of &e{chamber}&7..."
scan-complete: "&aYou've mapped the chamber! Discovered &e{vaults} vaults&a, &e{spawners} monster cages&a, and &e{pots} ancient pots&a!"
```

---

### Snapshots

```yaml
snapshot-creating: "&aCreating snapshot for &e{chamber}&a..."
snapshot-created: "&aSnapshot created successfully! (&e{blocks}&a blocks, &e{size}&a)"
snapshot-restoring: "&aRestoring chamber &e{chamber}&a from snapshot..."
snapshot-restored: "&aChamber restored successfully!"
snapshot-not-found: "&cNo snapshot found for chamber &e{chamber}&c."
snapshot-failed: "&cSnapshot operation failed: {error}"
```

**Placeholders:**
- `{chamber}` - Chamber name
- `{blocks}` - Block count
- `{size}` - File size (e.g., "1.2 MB")
- `{error}` - Error message

**Examples:**

Loading bar vibes:
```yaml
snapshot-creating: "&7[&e▰▰▰▱▱▱&7] Creating snapshot of &f{chamber}&7..."
snapshot-created: "&7[&a▰▰▰▰▰▰&7] &aSnapshot saved! &7({blocks} blocks, {size})"
```

Immersive:
```yaml
snapshot-creating: "&7Capturing the essence of &e{chamber}&7..."
snapshot-created: "&aThe chamber's memory has been preserved. &7({blocks} blocks)"
```

---

### Chamber Resets

```yaml
chamber-reset-warning: "&eWarning: &7{chamber} will reset in &6{time}&7!"
chamber-reset-complete: "&aThe chamber has been reset!"
chamber-resetting: "&aResetting chamber &e{chamber}&a..."
reset-success: "&aChamber &e{chamber}&a reset successfully!"
reset-failed: "&cFailed to reset chamber: {error}"
```

**Placeholders:**
- `{chamber}` - Chamber name
- `{time}` - Time remaining (e.g., "5m 30s")
- `{error}` - Error message

**Examples:**

Urgent/dramatic:
```yaml
chamber-reset-warning: "&c&l⚠ WARNING ⚠ &e{chamber} &cresets in &4&l{time}&c!"
chamber-reset-complete: "&6✦ &fThe chamber has been restored to its former glory!"
```

Calm/informative:
```yaml
chamber-reset-warning: "&7[Notice] &e{chamber}&7 will reset in &f{time}&7. Please finish up!"
chamber-reset-complete: "&aReset complete. The chamber is ready for new challengers."
```

Roleplay/lore:
```yaml
chamber-reset-warning: "&7The magic sustaining &e{chamber}&7 weakens... &6{time}&7 until collapse!"
chamber-reset-complete: "&dThe chamber's ancient power has been renewed!"
```

---

### Vaults

```yaml
vault-opened: "&aYou opened a {type} Vault!"
vault-cooldown: "&cThis {type} Vault is on cooldown for &e{time}&c."
vault-locked: "&cYou have already opened this {type} Vault! It will unlock when the chamber resets."
vault-reset: "&aVault cooldown reset for &e{player}&a."
wrong-key-type: "&cYou need a {required_type} Trial Key to open this vault!"
no-key: "&cYou need a Trial Key to open this vault!"
vault-not-found: "&cNo vault found at this location."
vault-loot-table-missing: "&cVault configuration error! Loot table not found. Please contact an admin."
vault-no-loot-generated: "&cVault is empty! No loot was generated. Please contact an admin."
vault-error: "&cAn error occurred while opening the vault. Please try again."
```

**Placeholders:**
- `{type}` - Vault type: "Normal" or "Ominous"
- `{time}` - Cooldown time remaining
- `{player}` - Player name
- `{required_type}` - Key type needed

**New in v1.2.19:**
- `vault-loot-table-missing` - Shown when the configured loot table doesn't exist (indicates configuration error)
- `vault-no-loot-generated` - Shown when a loot table exists but generates no items (empty weighted-items or bad config)

**New in v1.2.21:**
- `vault-locked` - Shown when a player has already opened a vault (permanent cooldown until reset)
- `vault-error` - Shown when the vault block state can't be updated (rare edge case)

<div data-gb-custom-block data-tag="hint" data-style="info">

**Note:** In v1.2.19+, if error messages appear, **keys are NOT consumed**. The player keeps their key so they can try again once the issue is resolved.

</div>

<div data-gb-custom-block data-tag="hint" data-style="success">

**v1.2.21+:** Vault cooldowns now use Paper's native API. The `vault-locked` message is shown for permanent cooldowns (vanilla behavior). This automatically resets when the chamber is restored from snapshot.

</div>

**Examples:**

Excited/rewarding:
```yaml
vault-opened: "&a&l✓ UNLOCKED! &r&7You opened a {type} Vault!"
vault-cooldown: "&7This {type} Vault has already been looted. Try again in &e{time}&7."
```

Mystical:
```yaml
vault-opened: "&dThe {type} Vault yields its treasures to you..."
vault-cooldown: "&5The vault's magic has not yet regenerated. Return in &d{time}&5."
```

Gameplay-focused:
```yaml
vault-opened: "&6[LOOT] &f{type} Vault opened!"
vault-cooldown: "&e[COOLDOWN] &7{time} remaining"
```

---

### Trial Keys

```yaml
key-given: "&aGave &e{amount}&a {type} Trial Key(s) to &e{player}&a."
key-invalid-amount: "&cInvalid amount. Must be a positive number."
key-check: "&e{player}&a has &e{normal}&a Normal Key(s) and &e{ominous}&a Ominous Key(s)."
```

**Placeholders:**
- `{amount}` - Number of keys
- `{type}` - "Normal" or "Ominous"
- `{player}` - Player name
- `{normal}` - Normal key count
- `{ominous}` - Ominous key count

---

### Statistics

```yaml
stats-header: "&6=== Statistics for {player} ==="
stats-chambers: "&eChambers Completed: &f{count}"
stats-normal-vaults: "&eNormal Vaults Opened: &f{count}"
stats-ominous-vaults: "&eOminous Vaults Opened: &f{count}"
stats-mobs: "&eMobs Killed: &f{count}"
stats-deaths: "&eDeaths: &f{count}"
stats-time: "&eTime Spent: &f{time}"
statistics-disabled: "&cStatistics are disabled in the configuration."
invalid-stat-type: "&cInvalid stat type. Use: chambers, normal, ominous, mobs, or time"
```

**Placeholders:**
- `{player}` - Player name
- `{count}` - Stat count
- `{time}` - Formatted time (e.g., "2h 15m")

**Customization:**

Clean list:
```yaml
stats-header: "&7───── &6Stats: {player} &7─────"
stats-chambers: "&8▸ &7Chambers: &f{count}"
stats-normal-vaults: "&8▸ &7Normal Vaults: &f{count}"
stats-ominous-vaults: "&8▸ &7Ominous Vaults: &f{count}"
```

Game-style:
```yaml
stats-header: "&6╔═══════════════════════╗\n&6║ &fStats: {player}\n&6╚═══════════════════════╝"
stats-chambers: "  &e⚔ Chambers Beaten: &a{count}"
stats-normal-vaults: "  &e🔓 Normal Vaults: &a{count}"
```

---

### Leaderboards

```yaml
leaderboard-header: "&6=== Top Players - {stat} ==="
leaderboard-entry: "&e#{rank} &f{player}&7: &a{value}"
leaderboard-empty: "&7No statistics recorded yet."
```

**Placeholders:**
- `{stat}` - Stat type (e.g., "Chambers Completed")
- `{rank}` - Player's rank (1, 2, 3...)
- `{player}` - Player name
- `{value}` - Stat value

**Examples:**

Medal system:
```yaml
leaderboard-header: "&6&l🏆 TOP PLAYERS - {stat} 🏆"
leaderboard-entry: "&7{rank}. &f{player} &8- &e{value}"
```

With actual medals for top 3 (requires creative formatting):
```yaml
# For top 3, manually format with different entries if your plugin supports it
leaderboard-entry: "&e#{rank} &f{player}&7: &a{value}"
# Could be: 🥇 #1, 🥈 #2, 🥉 #3 for top 3
```

---

### Chamber Completion & Entry

```yaml
chamber-completed: "&a✓ You have completed &e{chamber}&a!"
chamber-entered: "&7Entered chamber: &e{chamber}"
chamber-exited: "&7Left chamber"
player-died-in-chamber: "&c{player} died in {chamber}"
```

**Placeholders:**
- `{chamber}` - Chamber name
- `{player}` - Player name

**Examples:**

Achievement-style:
```yaml
chamber-completed: "&6&l✦ ACHIEVEMENT ✦\n&fCompleted &e{chamber}&f!"
chamber-entered: "&7➜ Entering &e{chamber}&7..."
chamber-exited: "&7← Leaving chamber"
```

Subtle:
```yaml
chamber-entered: "&8[&7{chamber}&8]"
chamber-exited: ""
```

Dramatic:
```yaml
chamber-completed: "&d&l⚔ VICTORY! ⚔\n&fYou have conquered &5{chamber}&f!"
player-died-in-chamber: "&c☠ &7{player} fell in &c{chamber}"
```

---

### Protection

```yaml
cannot-break-blocks: "&cYou cannot break blocks in a Trial Chamber!"
cannot-place-blocks: "&cYou cannot place blocks in a Trial Chamber!"
cannot-access-container: "&cYou cannot access containers in a Trial Chamber!"
```

**Customization:**

Strict:
```yaml
cannot-break-blocks: "&4[DENIED] &cBreaking blocks is not allowed here."
```

Funny:
```yaml
cannot-break-blocks: "&cHey! Stop that! This isn't your chamber to break."
cannot-place-blocks: "&cNo block placing in the ancient ruins, thanks."
```

Immersive:
```yaml
cannot-break-blocks: "&7An ancient magic prevents you from damaging the structure."
cannot-place-blocks: "&7The chamber's magic rejects your attempt to alter it."
```

---

### Teleportation

```yaml
teleported-to-chamber: "&aTeleported to chamber &e{chamber}&a."
teleported-to-exit: "&aTeleported to exit of chamber &e{chamber}&a."
```

**Placeholders:**
- `{chamber}` - Chamber name

**Examples:**

Magical:
```yaml
teleported-to-chamber: "&d✦ &fYou've been transported to &e{chamber}&f!"
teleported-to-exit: "&7You've been whisked away to safety..."
```

Simple:
```yaml
teleported-to-chamber: "&7→ {chamber}"
teleported-to-exit: "&7→ Exit"
```

---

### Chamber Info

```yaml
info-header: "&6=== Chamber Info: {chamber} ==="
info-world: "&eWorld: &f{world}"
info-bounds: "&eBounds: &f{minX},{minY},{minZ} to {maxX},{maxY},{maxZ}"
info-volume: "&eVolume: &f{volume} blocks"
info-exit: "&eExit: &f{exit}"
info-reset-interval: "&eReset Interval: &f{interval}"
info-last-reset: "&eLast Reset: &f{time}"
info-vaults: "&eVaults: &f{count}"
info-spawners: "&eSpawners: &f{count}"
info-snapshot: "&eSnapshot: &f{status}"
```

**Placeholders:**
- `{chamber}` - Chamber name
- `{world}` - World name
- `{minX}`, `{minY}`, `{minZ}`, `{maxX}`, `{maxY}`, `{maxZ}` - Coordinates
- `{volume}` - Block count
- `{exit}` - Exit location or "Not set"
- `{interval}` - Reset interval (e.g., "48 hours")
- `{time}` - Last reset time
- `{count}` - Count of vaults/spawners
- `{status}` - Snapshot status

---

### Time Formatting

```yaml
time-days: "{days}d"
time-hours: "{hours}h"
time-minutes: "{minutes}m"
time-seconds: "{seconds}s"
time-never: "Never"
time-now: "Just now"
```

**Placeholders:**
- `{days}`, `{hours}`, `{minutes}`, `{seconds}` - Time values

Used for cooldowns, reset timers, etc. Combined like: `"2d 5h 30m"`

**Examples:**

Verbose:
```yaml
time-days: "{days} days"
time-hours: "{hours} hours"
time-minutes: "{minutes} minutes"
time-seconds: "{seconds} seconds"
```

Ultra-minimal:
```yaml
time-days: "{days}d"
time-hours: "{hours}h"
time-minutes: "{minutes}m"
time-seconds: "{seconds}s"
```

---

### System Messages

```yaml
plugin-starting-up: "&eTrialChamberPro is still starting up. Please try again in a moment..."
```

Shown when commands are used before the plugin finishes async initialization.

---

### Info Display Format

```yaml
# Used in /tcp info output
info-exit-location-set: "&a{x}, {y}, {z}"
info-exit-location-not-set: "&cNot set"
info-snapshot-created: "&aCreated"
info-snapshot-not-created: "&cNot created"
```

**Placeholders:**
- `{x}`, `{y}`, `{z}` - Coordinates

These are displayed as values in the chamber info command output.

---

### Spawner Waves

```yaml
spawner-wave-complete: "&a✓ {type} Spawner wave complete! &7Killed &e{killed}&7 mobs in &f{duration}"

# Boss bar messages (no prefix added - displayed on boss bar)
spawner-wave-boss-bar-complete: "Wave Complete!"
spawner-wave-boss-bar-ominous: "Ominous Trial - Wave {wave}"
spawner-wave-boss-bar-normal: "Trial Spawner - Wave {wave}"
spawner-wave-boss-bar-progress: "{type} - {killed}/{total}"
```

**Placeholders:**
- `{type}` - Spawner type: "Trial" or "Ominous"
- `{killed}` - Number of mobs killed
- `{total}` - Total mobs expected
- `{duration}` - Time to complete (e.g., "1m 30s")
- `{wave}` - Wave number

**Examples:**

Celebratory:
```yaml
spawner-wave-complete: "&6🎉 &e{type} Wave Cleared! &7{killed} mobs defeated in {duration}!"
spawner-wave-boss-bar-complete: "✓ Victory!"
```

Competitive:
```yaml
spawner-wave-complete: "&a[WAVE COMPLETE] &7{killed} kills in &f{duration}"
spawner-wave-boss-bar-progress: "{killed}/{total} eliminated"
```

---

### Spectator Mode

```yaml
spectate-offer: "&7You died in &e{chamber}&7. Would you like to spectate?"
spectate-hint: "&7Type &aspectate &7to watch or &cno &7to respawn normally."
spectate-offer-expired: "&7Spectate offer expired."
spectate-started: "&aYou are now spectating &e{chamber}&a!"
spectate-exit-hint: "&7Type &eexit &7to leave spectator mode."
spectate-exited: "&7You have left spectator mode."
spectate-declined: "&7Spectator mode declined."
spectate-boundary-warning: "&cYou cannot leave the chamber while spectating!"
spectate-no-players: "&7No other players are in the chamber to spectate."
spectate-chamber-not-found: "&cThe chamber no longer exists."
```

**Placeholders:**
- `{chamber}` - Chamber name

**Examples:**

Encouraging:
```yaml
spectate-offer: "&7Tough luck! Want to watch your teammates in &e{chamber}&7?"
spectate-started: "&a👀 Spectating &e{chamber}&a - Cheer them on!"
```

Roleplay:
```yaml
spectate-offer: "&7Your spirit lingers near &e{chamber}&7... Watch from beyond?"
spectate-started: "&d✦ &7Your spirit observes the trial of &e{chamber}&7..."
spectate-exited: "&7Your spirit returns to the mortal plane."
```

---

### GUI Action Messages

```yaml
# Chamber Operations
gui-chamber-world-not-loaded: "&cChamber world not loaded!"
gui-teleport-to-center: "&aTeleported to center of {chamber}"
gui-forcing-reset: "&eForcing reset for '{chamber}'..."
gui-chamber-reset-complete: "&aChamber '{chamber}' has been reset!"
gui-reset-failed: "&cFailed to reset chamber: {error}"
gui-no-players-in-chamber: "&eNo players are currently in this chamber."
gui-players-ejected: "&aEjected {count} player(s) from '{chamber}'!"
gui-reset-scheduled: "&eChamber '{chamber}' will reset in {seconds} seconds."
gui-exit-scheduled: "&ePlayers will be ejected from '{chamber}' in {seconds} seconds."
gui-exit-warning: "&cYou will be ejected from this chamber in {seconds} seconds!"
gui-player-ejected: "&eYou have been ejected from the chamber!"

# Snapshot Operations
gui-no-snapshot-exists: "&cNo snapshot exists for this chamber!"
gui-restoring-snapshot: "&eRestoring snapshot for '{chamber}'..."
gui-snapshot-restored: "&aSnapshot restored!"
gui-restore-failed: "&cFailed to restore: {error}"
gui-creating-snapshot: "&eCreating snapshot for '{chamber}'..."
gui-snapshot-created: "&aSnapshot created!"
gui-snapshot-create-failed: "&cFailed to create snapshot: {error}"

# Chamber Settings
gui-reset-interval-set: "&aReset interval set to {value}"
gui-reset-interval-failed: "&cFailed to update reset interval"
gui-exit-location-set: "&aExit location set to your current position"
gui-exit-location-failed: "&cFailed to set exit location"
gui-no-exit-location: "&cNo exit location set for this chamber"
gui-teleport-to-exit: "&aTeleported to exit location"
gui-spawner-cooldown-set: "&aSpawner cooldown set to {value}"
gui-spawner-cooldown-failed: "&cFailed to update spawner cooldown"
gui-spawner-cooldown-reset: "&aSpawner cooldown reset to global config"

# Loot Table Settings
gui-no-loot-tables: "&cNo loot tables available"
gui-loot-table-set: "&a{type} loot table set to: {table}"
gui-loot-table-failed: "&cFailed to set loot table"
gui-loot-table-cleared: "&a{type} loot table override cleared"
gui-loot-clear-failed: "&cFailed to clear loot table override"

# Settings Toggle
gui-setting-toggled: "&a{setting}: {value}"
gui-setting-enabled: "Enabled"
gui-setting-disabled: "Disabled"

# Loot Editor
gui-hold-item-to-add: "&cHold an item to add!"
gui-item-added-to-loot: "&aAdded {item} to loot table"
gui-loot-changes-saved: "&aLoot table changes saved!"
gui-loot-pool-saved: "&aPool '{pool}' changes saved!"

# Info Messages
gui-loot-table-edit-hint-1: "&eTo edit loot table '{table}':"
gui-loot-table-edit-hint-2: "&71. Go to Chambers and select a chamber"
gui-loot-table-edit-hint-3: "&72. Click Normal/Ominous Loot to edit"
gui-loot-table-edit-hint-4: "&73. Or edit loot.yml directly"
gui-pool-create-hint: "&eTo add a new pool, edit loot.yml directly and /tcp reload"
gui-pool-create-coming-soon: "&7GUI pool creation coming soon!"
```

**Placeholders:**
- `{chamber}` - Chamber name
- `{count}` - Number of players
- `{seconds}` - Time in seconds
- `{error}` - Error message
- `{value}` - Setting value (e.g., "5 minutes", "Enabled")
- `{type}` - Vault/loot type: "Normal" or "Ominous"
- `{table}` - Loot table name
- `{item}` - Item type name
- `{pool}` - Pool name
- `{setting}` - Setting name

**Customization:**

Tech-style:
```yaml
gui-forcing-reset: "&7[SYSTEM] Initiating reset of &f{chamber}&7..."
gui-chamber-reset-complete: "&a[COMPLETE] &7Chamber &f{chamber}&7 restored"
gui-reset-scheduled: "&7[SCHEDULED] &f{chamber}&7 reset in &e{seconds}s"
```

Immersive:
```yaml
gui-forcing-reset: "&7The ancient magic begins to stir in &e{chamber}&7..."
gui-chamber-reset-complete: "&d✦ &7The chamber &e{chamber}&7 has been renewed!"
gui-player-ejected: "&7An unseen force gently pushes you from the chamber..."
```

---

### Help Messages

```yaml
help-header: "&6=== TrialChamberPro Commands ==="
help-scan: "&e/tcp scan <chamber> &7- Scan for vaults/spawners"
help-generate: "&e/tcp generate <mode> ... &7- Register chamber from various sources"
# ... (all help entries)
```

Customize the entire help menu to match your server's command style!

**Examples:**

Compact:
```yaml
help-header: "&6Commands:"
help-scan: "&e/tcp scan <chamber> &8▸ &7Scan for blocks"
help-generate: "&e/tcp generate <mode> &8▸ &7Register chamber"
```

Detailed:
```yaml
help-header: "&6╔════════════════════════════╗\n&6║ &fTrialChamberPro Commands\n&6╚════════════════════════════╝"
help-generate: "&e/tcp generate wand <name>\n  &7Registers a chamber from your WorldEdit selection"
```

---

## 🎯 Complete Theme Examples

### Minimalist Theme

```yaml
prefix: "&8[&6TCP&8] "
chamber-created: "&a✓ Created {chamber}"
chamber-deleted: "&c✗ Deleted {chamber}"
vault-opened: "&a{type} vault unlocked"
vault-cooldown: "&7Cooldown: &e{time}"
chamber-reset-warning: "&e{chamber} &7resets in {time}"
chamber-completed: "&a✓ {chamber} complete"
stats-header: "&7─── &6{player} &7───"
```

**Vibe:** Clean, no-nonsense, modern.

---

### Fantasy/Roleplay Theme

```yaml
prefix: "&7[&5Ancient Trials&7] "
chamber-created: "&7The chamber &e{chamber}&7 awakens from its slumber..."
chamber-deleted: "&7The memory of &e{chamber}&7 fades into legend..."
vault-opened: "&dThe {type} Vault bestows its treasures upon you!"
vault-cooldown: "&5The vault's magic has not yet recovered. Return in &d{time}&5."
chamber-reset-warning: "&7The magic of &e{chamber}&7 wanes... &6{time}&7 until renewal!"
chamber-reset-complete: "&d✦ The chamber's ancient power has been restored! ✦"
chamber-completed: "&5⚔ &fYou have conquered &d{chamber}&f! ⚔"
stats-header: "&5╔════════════════════════╗\n&5║ &fLegend of {player}\n&5╚════════════════════════╝"
```

**Vibe:** Immersive, magical, story-driven.

---

### Competitive/PvP Theme

```yaml
prefix: "&c[&4PvP&c] "
chamber-created: "&c[NEW ARENA] &f{chamber} &cregistered"
vault-opened: "&a[LOOTED] &f{type} Vault"
vault-cooldown: "&e[ON COOLDOWN] &7{time} remaining"
chamber-reset-warning: "&4⚠ ARENA RESET: &c{time}"
chamber-completed: "&6[VICTORY] &e{chamber} &6conquered"
player-died-in-chamber: "&c☠ {player} &7was slain in &c{chamber}"
stats-header: "&4╔════════════════════╗\n&4║ &c{player} Stats\n&4╚════════════════════╝"
leaderboard-header: "&4&l⚔ TOP FIGHTERS - {stat} ⚔"
```

**Vibe:** Intense, competitive, action-focused.

---

### Casual/Friendly Theme

```yaml
prefix: "&b[Trial] "
chamber-created: "&aWoohoo! Chamber &e{chamber}&a is ready to go!"
vault-opened: "&a🎉 You got loot from a {type} vault!"
vault-cooldown: "&eOops! This vault is still cooling down. Try again in &6{time}&e!"
chamber-reset-warning: "&eHeads up! &b{chamber}&e is resetting in &6{time}&e. Wrap it up!"
chamber-completed: "&a✨ Nice job! You completed &e{chamber}&a!"
stats-header: "&b=== How's {player} doing? ==="
cannot-break-blocks: "&cHey now! No breaking stuff in here 😊"
```

**Vibe:** Friendly, approachable, lighthearted.

---

## 🌍 Multi-Language Support

Want to support multiple languages? You can create separate message files!

1. Copy `messages.yml` to `messages_es.yml` (Spanish), `messages_fr.yml` (French), etc.
2. Translate all messages
3. Use a language switcher plugin to change which file is loaded per-player

<div data-gb-custom-block data-tag="hint" data-style="info">

**Note:** TrialChamberPro doesn't have built-in per-player language switching (yet), but you can manually swap files and reload for server-wide language changes.

</div>

---

## 💡 Pro Tips

<div data-gb-custom-block data-tag="hint" data-style="success">

**Test your messages!** Trigger each message in-game to see how it looks in chat. Some look great in the config but ugly in-game.

</div>

<div data-gb-custom-block data-tag="hint" data-style="warning">

**Don't overdo colors.** Too many colors = eyesore. Stick to 2-3 main colors for consistency.

</div>

<div data-gb-custom-block data-tag="hint" data-style="info">

**Use prefixes wisely.** If you have a very long prefix, consider shortening it or removing it entirely for certain messages (like warnings).

</div>

---

## 🎨 Color Palette Ideas

### Earthy/Natural
- Primary: `&a` (green), `&e` (yellow)
- Accent: `&6` (gold), `&7` (gray)

### Mystical/Magical
- Primary: `&d` (light purple), `&5` (dark purple)
- Accent: `&b` (aqua), `&f` (white)

### Tech/Modern
- Primary: `&b` (cyan), `&f` (white)
- Accent: `&7` (gray), `&8` (dark gray)

### Fire/Danger
- Primary: `&c` (red), `&6` (gold)
- Accent: `&4` (dark red), `&e` (yellow)

---

## 🔄 Applying Changes

After editing `messages.yml`:

```
/tcp reload
```

All messages update immediately. No restart needed!

---

## ❓ Common Questions

**"Can I remove the prefix from all messages?"**
Yes! Set `prefix: ""` (empty string).

**"What if I want different prefixes for different message types?"**
You'll need to manually add prefixes to individual messages instead of using the global prefix.

**"Can I use hex color codes?"**
Not in the current version, but it's planned for a future update!

**"What if I mess up the YAML formatting?"**
The plugin will fail to load messages and log errors. Always backup before editing!

**"Can I add custom messages for my own plugins?"**
TrialChamberPro only uses the messages defined here. For custom messages, you'd need to modify the plugin or request new placeholder support.

---

## 🎯 What's Next?

You've completed the configuration trilogy! 🎉

Check out the guides for hands-on usage:

<div data-gb-custom-block data-tag="content-ref" data-url="../guides/custom-loot.md">

[custom-loot.md](../guides/custom-loot.md)

</div>

<div data-gb-custom-block data-tag="content-ref" data-url="../guides/automatic-resets.md">

[automatic-resets.md](../guides/automatic-resets.md)

</div>

<div data-gb-custom-block data-tag="content-ref" data-url="../reference/commands.md">

[commands.md](../reference/commands.md)

</div>

Now go make those messages uniquely yours! ✨
