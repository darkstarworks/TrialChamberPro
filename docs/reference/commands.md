# 📝 Commands Reference

All commands start with `/tcp` (short for TrialChamberPro). Most require specific permissions—check the [Permissions](permissions.md) page for details.

{% hint style="info" %}
**Aliases:** `/tcp`, `/trialchamberpro`, `/tcpro`

**Tab completion:** Available for all commands! Press `Tab` while typing for suggestions.
{% endhint %}

---

## 🎯 Quick Reference

| Command | Description | Permission |
|---------|-------------|------------|
| `/tcp help` | Show command list | None |
| `/tcp menu` | Open admin GUI | `tcp.admin.menu` |
| `/tcp generate <value|coords|wand|blocks>` | Register chamber from saved var, coords, WE wand, or by block amount | `tcp.admin.generate` |
| `/tcp scan <chamber>` | Scan for vaults/spawners | `tcp.admin.scan` |
| `/tcp setexit <chamber>` | Set exit location | `tcp.admin.create` |
| `/tcp snapshot <action> <chamber>` | Manage snapshots | `tcp.admin.snapshot` |
| `/tcp reset <chamber>` | Force chamber reset | `tcp.admin.reset` |
| `/tcp list` | List all chambers | `tcp.admin` |
| `/tcp info <chamber>` | Show chamber details | `tcp.admin` |
| `/tcp delete <chamber>` | Delete a chamber | `tcp.admin.create` |
| `/tcp vault reset <chamber> <player>` | Reset vault cooldowns | `tcp.admin.vault` |
| `/tcp key give <player> <amount>` | Give trial keys | `tcp.admin.key` |
| `/tcp key check <player>` | Check player's keys | `tcp.admin.key` |
| `/tcp stats [player]` | View statistics | `tcp.stats` / `tcp.admin.stats` |
| `/tcp leaderboard <type>` | View leaderboards | `tcp.stats` |
| `/tcp reload` | Reload configuration | `tcp.admin.reload` |

---

## 📚 Command Details

### `/tcp help`

Shows a list of all available commands.

**Usage:**
```
/tcp help
```

**Permission:** None (everyone can use this)

**Example:**
```
/tcp help
```

{% hint style="info" %}
Only shows commands you have permission to use!
{% endhint %}

---

### `/tcp menu`

Opens the admin GUI for managing all aspects of TrialChamberPro without command line.

**Usage:**
```
/tcp menu
```

**Permission:** `tcp.admin.menu`

**Example:**
```
/tcp menu
```

**GUI Screens (v1.2.8+):**

The admin GUI provides 14 different views organized into categories:

**Main Menu** - Central hub with 6 category buttons:
- **Chambers** - List and manage all registered chambers
- **Loot Tables** - Browse available loot tables
- **Statistics** - View leaderboards and player stats
- **Settings** - Configure plugin settings in real-time
- **Protection** - Toggle protection features
- **Help** - Command reference and permissions

**Chamber Management:**
- **Chamber List** - Paginated list (36 per page) with quick actions
- **Chamber Detail** - Full management hub (loot, vaults, settings, actions)
- **Chamber Settings** - Per-chamber reset interval, exit location, loot overrides
- **Vault Management** - View/reset player vault cooldowns

**Settings:**
- **Global Settings** - Toggle 13 config options without editing YAML
- **Protection Menu** - Enable/disable protection features instantly

**Statistics:**
- **Stats Menu** - Overview with leaderboard shortcuts
- **Leaderboards** - Top 10 players by category
- **Player Stats** - Individual player statistics with K/D ratio

**Key Features:**
- **Runtime Config Editing** - Changes save immediately to config.yml
- **Pagination** - Handle unlimited chambers
- **Navigation** - Consistent back/close buttons throughout
- **Session Restoration** - Return to previous screens automatically

{% hint style="success" %}
**No YAML editing required!** Most configuration can now be done entirely through the GUI.
{% endhint %}

---

### `/tcp generate <value|coords|wand|blocks>`

Registers a chamber using either a saved WorldEdit variable (named region), your current WorldEdit selection, explicit coordinates, or by a desired block amount at your current facing.

**Usage:**
```
/tcp generate value save <varName>
/tcp generate value list
/tcp generate value delete <varName>
/tcp generate value <varName> [chamberName]
/tcp generate coords <x1,y1,z1> <x2,y2,z2> [world] <chamberName>
/tcp generate wand <chamberName>
/tcp generate blocks <amount> [chamberName] [roundingAllowance]
```

**Permission:** `tcp.admin.generate`

**Behavior:**
- **value save**: Saves your current WorldEdit selection to a named variable for later use.
- **value list**: Shows all saved region variables.
- **value delete**: Removes a saved region by name.
- **value <varName> [chamberName]**: Generates a chamber from the saved region. If chamberName is omitted, <varName> is used as the chamber name. If no saved var exists and the sender is a player with a WorldEdit selection, falls back to using the selection.
- **coords**: Generates a chamber from two corners specified as either `<x1,y1,z1> <x2,y2,z2>` or legacy `<x1,y1,z1-x2,y2,z2>`. From console, you must also provide `[world]`.
- **wand**: Generates a chamber from your current WorldEdit selection. Handy shortcut for admins.
- **blocks**: Generates a chamber at your current location and facing, sized to approximately `<amount>` blocks. The plugin enforces a minimum of 31x15x31 and will round up by at most `generation.blocks.rounding-allowance` (default 1000) to form a clean region.

**Notes:**
- Minimum size enforced: 31x15x31 (width x height x depth)
- Maximum volume limited by `generation.max-volume` in config.yml
- WorldEdit must be installed for the `value` and `wand` operations
- Auto-scans for vaults/spawners and creates snapshots based on config settings

---

### `/tcp scan <chamber>`

Scans a chamber to detect vaults, trial spawners, and decorated pots.

**Usage:**
```
/tcp scan <chamber_name>
```

**Permission:** `tcp.admin.scan`

**Arguments:**
- `<chamber_name>` - Name of the chamber to scan

**Examples:**
```
/tcp scan MainChamber
/tcp scan NetherChamber1
```

**What it finds:**
- **Vaults** (normal and ominous)
- **Trial Spawners** (normal and ominous)
- **Decorated Pots**

**Output example:**
```
[TCP] Scanning chamber MainChamber...
[TCP] Scanning complete! Found 8 vaults, 12 spawners, 24 decorated pots.
```

{% hint style="warning" %}
**Re-scanning overwrites previous data!** If you modified your chamber and re-scan, old vault/spawner data is replaced.
{% endhint %}

---

### `/tcp setexit <chamber>`

Sets the exit location for a chamber. Players inside when the chamber resets will teleport here.

**Usage:**
```
/tcp setexit <chamber_name>
```

**Permission:** `tcp.admin.create`

**Requirements:**
- Must be a player (not console)

**Arguments:**
- `<chamber_name>` - Name of the chamber

**Examples:**
```
/tcp setexit MainChamber
```

Stand where you want players to teleport (usually just outside the entrance), then run the command. Your exact position and look direction are saved.

**Tips:**
- Set the exit OUTSIDE the chamber boundaries
- Face the direction you want players to look when teleported
- Test it with `/tcp reset <chamber>` to see where players go

---

### `/tcp snapshot <action> <chamber>`

Manage chamber snapshots (saved states for resets).

**Usage:**
```
/tcp snapshot create <chamber_name>
/tcp snapshot restore <chamber_name>
```

**Permission:** `tcp.admin.snapshot`

**Actions:**

#### `create` - Create Snapshot
Scans the chamber and saves every block to a compressed snapshot file.

**Example:**
```
/tcp snapshot create MainChamber
```

**What happens:**
1. Scans all blocks in chamber boundaries
2. Saves block types, orientations, tile entity data
3. Compresses and stores in `snapshots/<chamber>.dat`

**Time:** 5-30 seconds depending on chamber size

{% hint style="success" %}
**Update snapshots anytime!** Made changes to your chamber? Run `/tcp snapshot create` again to update.
{% endhint %}

#### `restore` - Restore Snapshot
Immediately resets the chamber from its snapshot (same as `/tcp reset`).

**Example:**
```
/tcp snapshot restore MainChamber
```

Useful for testing or forcing manual resets.

---

### `/tcp reset <chamber>`

Forces an immediate chamber reset.

**Usage:**
```
/tcp reset <chamber_name>
```

**Permission:** `tcp.admin.reset`

**Arguments:**
- `<chamber_name>` - Name of the chamber to reset

**Examples:**
```
/tcp reset MainChamber
/tcp reset NetherChamber1
```

**What it does:**
1. Teleports all players inside to the exit location
2. Restores all blocks from snapshot
3. Resets vault states
4. Removes spawned mobs (configurable)
5. Clears ground items (configurable)

**Use cases:**
- Testing chamber functionality
- Manual reset for events
- Fixing a broken chamber

{% hint style="info" %}
**Doesn't affect player vault cooldowns** unless `reset-vault-cooldowns: true` in config.yml.
{% endhint %}

---

### `/tcp list`

Lists all registered chambers.

**Usage:**
```
/tcp list
```

**Permission:** `tcp.admin`

**Example output:**
```
[TCP] === Registered Chambers ===
[TCP] MainChamber - world (12,847 blocks)
[TCP] NetherChamber1 - world_nether (8,521 blocks)
[TCP] OceanChamber - world (15,392 blocks)
```

Shows chamber name, world, and total block count.

---

### `/tcp info <chamber>`

Shows detailed information about a chamber.

**Usage:**
```
/tcp info <chamber_name>
```

**Permission:** `tcp.admin`

**Arguments:**
- `<chamber_name>` - Name of the chamber

**Examples:**
```
/tcp info MainChamber
```

**Example output:**
```
[TCP] === Chamber Info: MainChamber ===
[TCP] World: world
[TCP] Bounds: -150,-20,400 to -50,40,500
[TCP] Volume: 12,847 blocks
[TCP] Exit: -145, 65, 395
[TCP] Reset Interval: 48 hours
[TCP] Last Reset: 2 hours ago
[TCP] Snapshot: Created
```

**Info shown:**
- Chamber name and world
- Boundary coordinates
- Total block volume
- Exit location (or "Not set")
- Reset interval
- Last reset time
- Snapshot status

---

### `/tcp delete <chamber>`

Permanently deletes a chamber and all associated data.

**Usage:**
```
/tcp delete <chamber_name>
```

**Permission:** `tcp.admin.create`

**Arguments:**
- `<chamber_name>` - Name of the chamber to delete

**Examples:**
```
/tcp delete OldChamber
/tcp delete TestChamber
```

{% hint style="danger" %}
**⚠️ PERMANENT ACTION!** This deletes:
- Chamber boundaries and settings
- All vault data
- All spawner data
- Player vault cooldowns for this chamber
- The snapshot file is NOT deleted (manual cleanup required)

**Cannot be undone!**
{% endhint %}

---

### `/tcp vault reset <chamber> <player> [type]`

Resets a player's vault cooldowns for a specific chamber.

**Usage:**
```
/tcp vault reset <chamber_name> <player_name> [normal|ominous]
```

**Permission:** `tcp.admin.vault`

**Arguments:**
- `<chamber_name>` - Chamber name
- `<player_name>` - Player name (can be offline)
- `[type]` - Optional: `normal` or `ominous` (resets all if not specified)

**Examples:**
```
/tcp vault reset MainChamber Steve
/tcp vault reset MainChamber Alex normal
/tcp vault reset OceanChamber Bob ominous
```

**What it does:**
- Resets cooldowns for ALL vaults in the chamber
- Player can immediately loot vaults again
- Filters by vault type if specified

**Use cases:**
- Compensate for server issues/bugs
- Event rewards ("free vault access!")
- Testing vault mechanics

{% hint style="info" %}
**Per-vault cooldowns:** This resets cooldowns for every vault in the chamber individually, not just one vault.
{% endhint %}

---

### `/tcp key give <player> <amount> [type]`

Gives trial keys to a player.

**Usage:**
```
/tcp key give <player_name> <amount> [normal|ominous]
```

**Permission:** `tcp.admin.key`

**Arguments:**
- `<player_name>` - Player to give keys to (must be online)
- `<amount>` - Number of keys (positive integer)
- `[type]` - Optional: `normal` or `ominous` (default: `normal`)

**Examples:**
```
/tcp key give Steve 5
/tcp key give Alex 10 normal
/tcp key give Bob 3 ominous
```

**Use cases:**
- Rewards for events/competitions
- Compensation for bugs
- Sell keys in-game shop (via command blocks or other plugins)
- Testing vault mechanics

{% hint style="warning" %}
**Player must be online!** Offline players can't receive items. The command will fail if the player isn't online.
{% endhint %}

---

### `/tcp key check <player>`

Checks how many trial keys a player has.

**Usage:**
```
/tcp key check <player_name>
```

**Permission:** `tcp.admin.key`

**Arguments:**
- `<player_name>` - Player to check (must be online)

**Examples:**
```
/tcp key check Steve
```

**Example output:**
```
[TCP] Steve has 5 Normal Key(s) and 2 Ominous Key(s).
```

Counts ALL keys in the player's inventory (all slots combined).

---

### `/tcp stats [player]`

View player statistics for Trial Chamber activity.

**Usage:**
```
/tcp stats
/tcp stats <player_name>
```

**Permission:**
- `tcp.stats` - View own stats
- `tcp.admin.stats` - View other players' stats

**Arguments:**
- `[player_name]` - Optional: Player to view stats for (requires admin permission)

**Examples:**
```
/tcp stats
/tcp stats Steve
```

**Example output:**
```
[TCP] === Statistics for Steve ===
[TCP] Chambers Completed: 12
[TCP] Normal Vaults Opened: 45
[TCP] Ominous Vaults Opened: 18
[TCP] Mobs Killed: 324
[TCP] Deaths: 7
[TCP] Time Spent: 5h 32m
```

{% hint style="info" %}
**Requires statistics to be enabled** in config.yml (`statistics.enabled: true`)
{% endhint %}

**Tracked stats:**
- **Chambers Completed** - How many times player completed a chamber
- **Normal Vaults Opened** - Total normal vaults looted
- **Ominous Vaults Opened** - Total ominous vaults looted
- **Mobs Killed** - Mobs killed inside managed chambers
- **Deaths** - Deaths inside managed chambers
- **Time Spent** - Total time spent inside chambers

---

### `/tcp leaderboard <type>`

View top players for a specific statistic.

**Usage:**
```
/tcp leaderboard <type>
/tcp lb <type>
/tcp top <type>
```

**Permission:** `tcp.stats`

**Arguments:**
- `<type>` - Stat type to display

**Stat types:**
- `chambers` or `completions` - Chambers completed
- `normal` or `normalvaults` - Normal vaults opened
- `ominous` or `ominousvaults` - Ominous vaults opened
- `mobs` or `kills` - Mobs killed
- `time` or `playtime` - Time spent in chambers

**Examples:**
```
/tcp leaderboard chambers
/tcp lb normal
/tcp top time
```

**Example output:**
```
[TCP] === Top Players - Chambers Completed ===
[TCP] #1 Steve: 47
[TCP] #2 Alex: 42
[TCP] #3 Bob: 38
[TCP] #4 Charlie: 35
[TCP] #5 Diana: 31
```

**Configuration:**
- Number of players shown: `statistics.top-players-count` in config.yml (default: 10)
- Update frequency: `statistics.leaderboard-update-interval` in config.yml (default: 1 hour)

{% hint style="info" %}
**Leaderboards are cached** to prevent database lag. They update on the interval specified in config, not in real-time.
{% endhint %}

---

### `/tcp reload`

Reloads the plugin configuration without restarting the server.

**Usage:**
```
/tcp reload
```

**Permission:** `tcp.admin.reload`

**Example:**
```
/tcp reload
```

**What gets reloaded:**
- `config.yml` settings
- `loot.yml` loot tables
- `messages.yml` messages
- Chamber lookup cache is cleared

**What DOESN'T reload:**
- Database connections (requires full restart)
- Existing chamber data in memory
- Active reset timers (they continue with old intervals until next reset)

{% hint style="warning" %}
**Database changes require restart!** If you changed database settings in config.yml, you MUST restart the server, not just reload.
{% endhint %}

---

## 🎮 Common Command Sequences

### Registering an Existing Chamber (Full Process)

```bash
# 1. Select chamber with WorldEdit
/wand
# (left-click + right-click corners)

# 2. Register chamber
/tcp generate wand MyChamber

# 3. Scan is automatic, but you can re-scan if needed
/tcp scan MyChamber

# 4. Set exit location (stand outside chamber)
/tcp setexit MyChamber

# 5. Snapshot is automatic, but you can update it
/tcp snapshot create MyChamber

# 6. Check chamber info
/tcp info MyChamber

# 7. Test the reset
/tcp reset MyChamber
```

---

### Managing Player Issues

```bash
# Player accidentally used all keys
/tcp key give Steve 5 normal

# Player's vaults stuck on cooldown (bug)
/tcp vault reset MainChamber Steve

# Check player's statistics
/tcp stats Steve

# Check player's key inventory
/tcp key check Steve
```

---

### Event Setup

```bash
# Give all online players keys for event
/tcp key give Player1 10
/tcp key give Player2 10
/tcp key give Player3 10

# After event, check leaderboards
/tcp leaderboard chambers
/tcp leaderboard time

# Reset chamber immediately for next group
/tcp reset EventChamber
```

---

### Maintenance Tasks

```bash
# List all chambers
/tcp list

# Check each chamber's status
/tcp info MainChamber
/tcp info NetherChamber

# Update snapshots after building changes
/tcp snapshot create MainChamber
/tcp snapshot create NetherChamber

# Reload config after edits
/tcp reload
```

---

## 💡 Pro Tips

{% hint style="success" %}
**Use tab completion!** Press `Tab` while typing commands to autocomplete chamber names, player names, and arguments.
{% endhint %}

{% hint style="info" %}
**Aliases:** All leaderboard commands work with `/tcp lb` and `/tcp top` for quick access.
{% endhint %}

{% hint style="warning" %}
**Chamber names are case-sensitive** in some commands. Use tab completion to ensure correct capitalization.
{% endhint %}

{% hint style="info" %}
**Offline player support:** Most commands work with offline players (like `/tcp vault reset`), but `/tcp key give` requires the player to be online.
{% endhint %}

---

## 🔧 Command Permissions

For a complete list of all permissions (including per-command permissions), see the [Permissions](permissions.md) page.

**Quick permission groups:**

**Full Admin:**
```yaml
tcp.admin
tcp.admin.*
```

**Statistics Access:**
```yaml
tcp.stats
tcp.admin.stats
```

**Read-only Access:**
```yaml
tcp.admin       # Can view chambers
tcp.stats       # Can view own stats
```

---

## ❓ Troubleshooting

**"Unknown subcommand"**
- Check spelling (use tab completion!)
- Verify you have permission for that command
- Run `/tcp help` to see available commands

**"You don't have permission to use this command"**
- Check with your server admin for permissions
- See [Permissions](permissions.md) for the full list

**"Chamber not found"**
- Use `/tcp list` to see all chambers
- Chamber names are case-sensitive
- Use tab completion to avoid typos

**"No WorldEdit selection found"**
- Make sure WorldEdit is installed
- Use `/wand` and select two corners
- Your selection must have volume (not flat)

**"Player not found or not online"**
- Player must be online for `/tcp key give` and `/tcp key check`
- For offline players, use `/tcp vault reset` (works offline)

**"Snapshot operation failed"**
- Check console for detailed error
- Ensure disk space is available
- Verify file permissions on `plugins/TrialChamberPro/snapshots/` folder

---

## 🎯 Related Pages

{% content-ref url="permissions.md" %}
[permissions.md](permissions.md)
{% endcontent-ref %}

Complete permission nodes for all commands and features.

{% content-ref url="../configuration/config.yml.md" %}
[config.yml.md](../configuration/config.yml.md)
{% endcontent-ref %}

Settings that affect command behavior (auto-scan, auto-snapshot, etc.)

{% content-ref url="../getting-started/your-first-chamber.md" %}
[your-first-chamber.md](../getting-started/your-first-chamber.md)
{% endcontent-ref %}

Step-by-step guide using these commands to set up your first chamber.
