# ⚙️ config.yml Reference

Your `config.yml` is the control panel for TrialChamberPro. It's where you decide how chambers behave, when they reset, and how much protection you want. Let's break it down section by section.

.
{% hint style="info" %}
**Location:** `plugins/TrialChamberPro/config.yml`

After making changes, reload with `/tcp reload`
{% endhint %}
.

---

## 💾 Database Settings

```yaml
database:
  type: SQLITE # SQLITE or MYSQL
  host: localhost
  port: 3306
  database: trialchamberpro
  username: root
  password: ""
  pool-size: 10
```

### `type`
**Options:** `SQLITE` or `MYSQL`
**Default:** `SQLITE`

SQLite is perfect for single servers—no setup required, everything in one file. MySQL is for networks where multiple servers need to share chamber data.

.
{% hint style="success" %}
**Stick with SQLite unless** you're running a network with BungeeCord/Velocity and need multiple servers to share chambers.
{% endhint %}
.

### MySQL Options (only used when `type: MYSQL`)

| Setting | Description |
|---------|-------------|
| `host` | Database server address |
| `port` | Usually `3306` |
| `database` | Database name |
| `username` | Database user |
| `password` | User's password |
| `pool-size` | Connection pool size (10 is fine for most setups) |

---

## 🌍 Global Chamber Settings

```yaml
global:
  default-reset-interval: 172800
  reset-warning-times: [300, 60, 30]
  teleport-players-on-reset: true
  teleport-location: EXIT_POINT
  async-block-placement: true
  blocks-per-tick: 500
  auto-snapshot-on-register: true
  spawner-cooldown-minutes: -1
```

### `default-reset-interval`
**Default:** `172800` (48 hours)
**Unit:** Seconds

How long before chambers automatically reset. This is the default for ALL chambers unless overridden per-chamber.

**Values:**
- `0` or negative = **Disabled** (no automatic resets)
- Daily: `86400`
- Twice daily: `43200`
- Weekly: `604800`

.
{% hint style="info" %}
**Disable automatic resets:** Set to `0` to disable automatic resets entirely. Chambers will only reset when manually triggered via `/tcp reset <chamber>` or the GUI.
{% endhint %}
.

### `reset-warning-times`
**Default:** `[300, 60, 30]`
**Unit:** Seconds before reset

Players inside a chamber get warnings at these intervals. Default sends warnings at 5 minutes, 1 minute, and 30 seconds before reset.

Remove entries to reduce spam:
```yaml
reset-warning-times: [60] # Only warn 1 minute before
```

### `teleport-players-on-reset`
**Default:** `true`

Kick players out when the chamber resets? If `false`, players stay inside (probably not what you want—spawners respawn, blocks reset, they might suffocate).

### `teleport-location`
**Options:** `EXIT_POINT`, `OUTSIDE_BOUNDS`, `WORLD_SPAWN`
**Default:** `EXIT_POINT`

Where players go when kicked out:
- **EXIT_POINT:** Your `/tcp setexit` location (recommended)
- **OUTSIDE_BOUNDS:** Just outside the chamber boundary
- **WORLD_SPAWN:** Server spawn point

### `async-block-placement`
**Default:** `true`

Place blocks asynchronously during resets? Keeps the server smooth during big chamber resets. Only turn this off if you're having issues.

### `blocks-per-tick`
**Default:** `500`

How many blocks to place per tick during resets. Higher = faster resets but more lag. Lower = slower but smoother.

Adjust based on your server hardware:
- Beefy dedicated server: `1000+`
- Shared hosting: `250-500`

### `auto-snapshot-on-register`
**Default:** `true`

Automatically create a snapshot when registering a new chamber? Super convenient, but uses disk space. You can disable this if you want manual control.

### `spawner-cooldown-minutes`
**Default:** `-1` (vanilla behavior)
**Unit:** Minutes

Control how long trial spawners stay in cooldown after being completed. This affects when spawners can be reactivated after players defeat all mobs.

**Values:**
- `-1` = Use vanilla default (30 minutes / 36,000 ticks)
- `0` = No cooldown (spawners reactivate immediately when players approach)
- `1-60` = Custom cooldown in minutes

.
{% hint style="info" %}
**Use Cases:**
- **Quick farming:** Set to `0` for instant reactivation—great for mob farms or high-activity servers
- **Balanced gameplay:** Set to `5-15` minutes for faster resets than vanilla
- **Vanilla experience:** Keep at `-1` for authentic Trial Chamber timing
{% endhint %}
.
{% hint style="success" %}
**Per-Chamber Override:** You can set different cooldowns for individual chambers via the GUI (Chamber Settings → Spawner Cooldown) or database. Per-chamber settings override this global value.
{% endhint %}
.

### `wild-spawner-cooldown-minutes`
**Default:** `-1` (vanilla behavior)
**Unit:** Minutes

Control cooldown for trial spawners **outside** of registered chambers (wild/unregistered Trial Chambers). This is a server-wide setting that affects all spawners not managed by TrialChamberPro.

**Values:**
- `-1` = Use vanilla default (30 minutes)
- `0` = No cooldown (spawners reactivate immediately)
- `1-60` = Custom cooldown in minutes

.
{% hint style="info" %}
**Use Cases:**
- **Server-wide fast farming:** Set to `0` for all wild spawners to reactivate instantly
- **Consistent experience:** Match wild spawner behavior to your chamber settings
- **Vanilla purists:** Keep at `-1` to leave unregistered chambers untouched
{% endhint %}
.
{% hint style="warning" %}
**Bonus Feature:** When this setting is enabled (not -1), spawner wave tracking (boss bars) will also work in wild Trial Chambers, giving players progress feedback even in unregistered chambers!
{% endhint %}
.

---

## 🔒 Vault Settings

```yaml
vaults:
  per-player-loot: true
  normal-cooldown-hours: 24
  ominous-cooldown-hours: 48
  show-cooldown-particles: true
  particles:
    normal-available: VILLAGER_HAPPY
    normal-cooldown: SMOKE_NORMAL
    ominous-available: SOUL_FIRE_FLAME
    ominous-cooldown: SOUL
  play-sound-on-open: true
  sounds:
    normal-open: BLOCK_VAULT_OPEN_SHUTTER
    ominous-open: BLOCK_VAULT_OPEN_SHUTTER
    cooldown: BLOCK_NOTE_BLOCK_BASS
```

### `per-player-loot`
**Default:** `true`

The big one! Each player gets their own loot and cooldowns. If `false`, vaults work like vanilla (one-time use, everyone shares).

.
{% hint style="warning" %}
**Setting this to false** removes per-player cooldowns. Vaults become first-come-first-served. Usually not what you want for managed chambers!
{% endhint %}
.

### `normal-cooldown-hours` / `ominous-cooldown-hours`
**Default:** `-1` (permanent until reset)

How long before a player can loot the same vault again. Separate cooldowns for normal and ominous vaults.

**Values:**
- `-1` = Permanent lock until chamber reset (vanilla behavior, recommended)
- `0` or positive = Time-based cooldown in hours

.
{% hint style="info" %}
**v1.2.21+:** Vault cooldowns now use Paper's native Vault API (`hasRewardedPlayer`/`addRewardedPlayer`) for tracking. This is more reliable because:
- Uses vanilla Minecraft's built-in player tracking
- Cooldowns automatically reset when the chamber is restored from snapshot
- No database sync issues

**Note:** Time-based cooldowns (`0` or positive values) are less reliable with the native API since it doesn't track timestamps. For consistent behavior, use `-1` (permanent) and rely on chamber resets.
{% endhint %}
.

**Ideas:**
- Vanilla behavior: `-1` (permanent until chamber reset)
- Short cooldowns: `1` or `6` hours (for active servers)
- Long cooldowns: `72` or `168` hours (weekly)
- Match chamber resets: `48` hours (synchronized gameplay)

### `show-cooldown-particles`
**Default:** `true`

Show particles above vaults to indicate status? Super helpful for players to see what's available.

### `particles`
Visual effects shown above vaults:
- **normal-available:** Green sparkles = ready to open
- **normal-cooldown:** Gray smoke = on cooldown
- **ominous-available:** Soul flames = ominous vault ready
- **ominous-cooldown:** Soul particles = ominous on cooldown

See [Spigot's Particle enum](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Particle.html) for all options.

### `play-sound-on-open` / `sounds`
**Default:** `true`

Play sounds when vaults open or when someone tries to open during cooldown.

Change sounds if you want custom feedback. See [Spigot's Sound enum](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Sound.html).

---

## 🛡️ Protection Settings

```yaml
protection:
  enabled: true
  prevent-block-break: true
  prevent-block-place: true
  prevent-container-access: false
  allow-pvp: true
  prevent-mob-griefing: true
  worldguard-integration: true
```

### `enabled`
**Default:** `true`

Master toggle for chamber protection. Turn this off to rely entirely on WorldGuard or other plugins.

### `prevent-block-break` / `prevent-block-place`
**Default:** `true`

Stop players from breaking or placing blocks inside chambers. Prevents griefing and preserves your carefully-designed structures.

### `prevent-container-access`
**Default:** `false`

Block access to chests, barrels, hoppers, etc. Usually `false` because you WANT players opening decorated pots and chests for loot.

Set to `true` if you have admin chests inside chambers that shouldn't be touched.

### `allow-pvp`
**Default:** `true`

Allow PvP inside chambers? Great for competitive chambers where teams fight over loot. Set to `false` for peaceful farming.

### `prevent-mob-griefing`
**Default:** `true`

Stop mobs from breaking blocks (creeper explosions, endermen picking up blocks, etc.). Highly recommended unless you want chaos.

### `worldguard-integration`
**Default:** `true`

If WorldGuard is installed, respect its regions? Usually `true` for compatibility. TrialChamberPro's protection layers on top of WorldGuard's.

---

## 🔑 Trial Key Settings

```yaml
trial-keys:
  fix-paper-bugs: true
  prevent-duplication: true
  max-stack-size: 16
  validate-key-type: true
```

### `fix-paper-bugs`
**Default:** `true`

Paper has some bugs with trial keys. This applies fixes. Keep it `true` unless it causes issues.

### `prevent-duplication`
**Default:** `true`

Basic dupe prevention for trial keys. For industrial-grade protection, use [DupeTrace](https://modrinth.com/plugin/dupetrace).

### `max-stack-size`
**Default:** `16`

How high trial keys can stack. Vanilla is 1, but that's annoying. 16 is a nice balance.

### `validate-key-type`
**Default:** `true`

Enforce that normal keys only open normal vaults, ominous keys only open ominous vaults. Recommended to prevent exploits.

---

## 🔄 Reset Settings

```yaml
reset:
  clear-ground-items: true
  remove-spawner-mobs: true
  remove-non-chamber-mobs: false
  reset-trial-spawners: true
  reset-ominous-spawners: true
  clear-trial-omen-effect: true
  reset-vault-cooldowns: true
```

### `clear-ground-items`
**Default:** `true`

Delete dropped items on reset? Prevents loot/trash buildup. Usually `true` for cleanliness.

### `remove-spawner-mobs`
**Default:** `true`

Kill mobs spawned by trial spawners during reset. Keeps chambers clean.

### `remove-non-chamber-mobs`
**Default:** `false`

Kill ALL mobs in the chamber, even those not from spawners (like player pets). Usually `false` to avoid accidents.

### `reset-trial-spawners`
**Default:** `true`

**CRITICAL for trial key drops!** Reset trial spawner state when the chamber resets. This clears the spawner's internal tracking of which players have completed it.

.
{% hint style="warning" %}
**Why this matters:** Trial spawners store which players have "completed" them. Without clearing this data, spawners won't spawn mobs or drop keys for returning players. This setting ensures spawners work like vanilla after each reset.
{% endhint %}
.

**Vanilla behavior when enabled:**
- Spawners forget which players completed them
- Players can reactivate spawners after chamber reset
- Spawners drop trial keys (50% chance per player) when all mobs are defeated

Set to `false` if you want spawners to permanently remember who completed them (not recommended).

### `reset-ominous-spawners`
**Default:** `true`

Convert ominous trial spawners back to normal during reset. Set to `false` if you want ominous spawners to stay ominous.

### `clear-trial-omen-effect`
**Default:** `true`

Remove Trial Omen effects from players during reset. Prevents unintended difficulty spikes.

### `reset-vault-cooldowns`
**Default:** `true`

Reset all player vault cooldowns when the chamber resets. This is the vanilla behavior—vaults reset when the chamber resets.

Set to `false` if you want personal cooldowns independent of chamber state (players must wait their individual cooldown time even after chamber resets).

---

## ⚡ Performance Settings

```yaml
performance:
  cache-chamber-lookups: true
  cache-duration-seconds: 300
  async-database-operations: true
  time-tracking-interval: 300
  use-folialib: true
```

### `cache-chamber-lookups`
**Default:** `true`

Cache which chamber a block belongs to. Huge performance boost. Only disable for debugging.

### `cache-duration-seconds`
**Default:** `300` (5 minutes)

How long to cache lookups. Higher = better performance, but changes take longer to propagate.

### `async-database-operations`
**Default:** `true`

Run database queries asynchronously. Critical for performance—don't turn this off.

### `time-tracking-interval`
**Default:** `300` (5 minutes)

How often to save "time spent in chamber" stats to the database. More frequent = more accurate stats but more database writes.

### `use-folialib`
**Default:** `true`

Enable Folia compatibility. If you're on Folia, this MUST be `true`. On Paper/Purpur, it doesn't hurt to leave it `true`.

---

## 📊 Statistics Settings

```yaml
statistics:
  enabled: true
  track-time-spent: true
  leaderboard-update-interval: 3600
  top-players-count: 10
```

### `enabled`
**Default:** `true`

Track player statistics (vaults opened, chambers completed, time spent)? Required for leaderboards.

### `track-time-spent`
**Default:** `true`

Track how long players spend inside chambers. Disable if you don't care about time-based stats.

### `leaderboard-update-interval`
**Default:** `3600` (1 hour)

How often to recalculate leaderboards. Lower = more up-to-date but more database load.

### `top-players-count`
**Default:** `10`

How many players to show on leaderboards. Increase to 25 or 50 if you want bigger boards.

---

## 🎁 Loot Settings

```yaml
loot:
  apply-luck-effect: false
```

### `apply-luck-effect`
**Default:** `false`

Enable LUCK to influence loot generation. When enabled, players receive bonus loot rolls based on their LUCK sources.

**How it works:**
- Checks **both** potion effects AND item attributes:
  - **Potion Effect**: Temporary LUCK from potions, beacons, suspicious stew
  - **Attribute**: Permanent LUCK from armor/items with luck modifiers
- Each point of LUCK adds +1 bonus roll to each loot pool
- Applies to weighted items only (guaranteed items are always given)
- Works with both normal and ominous vaults

**Example:**
If a loot table has `min-rolls: 3` and `max-rolls: 5`:
- No LUCK: Player gets 3-5 items
- LUCK I potion: Player gets 4-6 items (+1 roll)
- LUCK II potion: Player gets 5-7 items (+2 rolls)
- LUCK I potion + 2 luck from items: Player gets 6-8 items (+3 rolls)

**Sources of LUCK:**
- Potion of Luck (temporary)
- Beacons with Luck effect
- Suspicious Stew made with dandelions
- Custom items/armor with `Attribute.GENERIC_LUCK` modifiers

.
{% hint style="info" %}
**Tip:** This is great for rewarding players who bring LUCK potions to chambers, or for special events where you want to boost loot!
{% endhint %}
.
{% hint style="warning" %}
**Balance Warning:** LUCK can significantly increase loot output. Test with your loot tables to ensure it doesn't break your economy.
{% endhint %}
.

---

## 🎯 Spawner Wave System

```yaml
spawner-waves:
  enabled: true
  show-boss-bar: true
  detection-radius: 20
  award-stats: true
  completion-message: true
```

### `enabled`
**Default:** `true`

Enable wave progress tracking for trial spawners. Shows boss bar and sends completion messages.

### `show-boss-bar`
**Default:** `true`

Display a boss bar showing wave progress (mobs killed / total mobs) to nearby players. Ominous spawners show purple, normal spawners show yellow.

### `detection-radius`
**Default:** `20` blocks

How far from a trial spawner players can be and still see the boss bar / be considered participants.

### `award-stats`
**Default:** `true`

Track mob kills from waves in player statistics. Used for leaderboards.

### `completion-message`
**Default:** `true`

Send a chat message when a wave is complete showing kill count and duration.

.
{% hint style="info" %}
**How it works:** When mobs spawn from a trial spawner, the plugin tracks them. As players kill mobs, the boss bar updates. When all mobs are dead, players get a completion message.
{% endhint %}
.

---

## 👀 Spectator Mode

```yaml
spectator-mode:
  enabled: true
  offer-timeout: 30
  restrict-to-chamber: true
  boundary-buffer: 10
  allow-solo-spectate: false
```

### `enabled`
**Default:** `true`

Enable spectator mode. When players die in a chamber, they're offered the chance to spectate teammates.

### `offer-timeout`
**Default:** `30` seconds

How long the spectate offer lasts before expiring. Players type "spectate" or "no" in chat to respond.

### `restrict-to-chamber`
**Default:** `true`

Keep spectators within chamber bounds. They can fly around but not leave the area.

### `boundary-buffer`
**Default:** `10` blocks

Extra space outside the chamber boundary where spectators can still fly. Allows viewing from slightly outside.

### `allow-solo-spectate`
**Default:** `false`

Allow spectating empty chambers (when no other players are inside). Usually `false`—spectating is for watching teammates.

.
{% hint style="info" %}
**Spectator Mode Flow:**
1. Player dies in chamber
2. After respawn, offered to spectate (if other players are inside)
3. Type "spectate" to accept → GameMode.SPECTATOR, teleport to center
4. Type "exit" to leave → restored to previous game mode, teleported to exit
{% endhint %}
.

---

## 🐛 Debug Mode

```yaml
debug:
  verbose-logging: false
```

### `verbose-logging`
**Default:** `false`

Enable detailed logging for debugging. Logs include:
- Vault type detection (normal vs ominous)
- Key validation checks
- LUCK effect calculations
- Block state information
- Vault saving and updates
- Loot roll calculations
- Spawner wave tracking (mob spawns, deaths, wave completion)
- **Spawner cooldown configuration** (v1.2.15+): Shows config values, old→new cooldown for each spawner, and verification that changes were applied

**Version 1.1.9+:** When enabled, displays a prominent startup banner on server start:
```
═══════════════════════════════════════
   DEBUG MODE ENABLED
   Verbose logging is active
   Expect detailed console output
═══════════════════════════════════════
```

This helps verify that debug mode is actually loaded from your config file.

.
{% hint style="info" %}
**Troubleshooting:** If you don't see the startup banner after enabling debug mode:
1. Make sure you're editing the config in `plugins/TrialChamberPro/config.yml` (not the default in the plugin jar)
2. Fully restart your server (not just `/tcp reload`)
3. Check for YAML syntax errors in your config file
{% endhint %}
.
{% hint style="warning" %}
Debug mode generates TONS of console output. Don't leave it on in production!
{% endhint %}

.
---

## 🎯 Quick Configs for Common Setups

### Casual Survival Server
```yaml
global:
  default-reset-interval: 86400  # Daily resets
vaults:
  normal-cooldown-hours: 6  # Generous cooldowns
  ominous-cooldown-hours: 12
protection:
  allow-pvp: false  # No PvP
```

### Competitive/PvP Server
```yaml
global:
  default-reset-interval: 43200  # Twice daily
vaults:
  normal-cooldown-hours: 12
  ominous-cooldown-hours: 24
protection:
  allow-pvp: true
  prevent-block-place: true
```

### High-Activity Server
```yaml
global:
  default-reset-interval: 21600  # Every 6 hours
  blocks-per-tick: 1000  # Fast resets
vaults:
  normal-cooldown-hours: 3
  ominous-cooldown-hours: 6
performance:
  cache-duration-seconds: 600  # Longer cache
```

### Roleplay/Lore Server
```yaml
global:
  default-reset-interval: 604800  # Weekly resets
vaults:
  normal-cooldown-hours: 168  # Once per week
  show-cooldown-particles: false  # Less immersion-breaking
  play-sound-on-open: false
```

---

## 🔄 Applying Changes

After editing `config.yml`:

```
/tcp reload
```

Most settings apply immediately. Chamber-specific settings (like `default-reset-interval`) only affect new resets, not chambers mid-cycle.

.
{% hint style="info" %}
**Database changes** require a full restart, not just a reload.
{% endhint %}

.
---

## 💡 Pro Tips

.
{% hint style="success" %}
**Start conservative:** Use longer cooldowns and reset intervals initially. It's easier to shorten them later than deal with player complaints about too-frequent changes.
{% endhint %}
.
{% hint style="info" %}
**Test in dev:** Create a test chamber to experiment with settings. Use `/tcp reset TestChamber` to see how changes affect gameplay.
{% endhint %}
.
{% hint style="warning" %}
**Backup before tuning:** Changing database settings wrong can corrupt data. Always backup `plugins/TrialChamberPro/` before major config changes.
{% endhint %}
.

---

## ❓ Common Questions

**"Can different chambers have different reset intervals?"**
Yes! The `default-reset-interval` is just the default. You can override per-chamber using database edits or (in future versions) per-chamber configs.

**"What happens if I change cooldowns while players have active cooldowns?"**
Existing cooldowns aren't retroactively changed. New cooldowns apply to future vault interactions.

**"Can I disable statistics for performance?"**
Yes, set `statistics.enabled: false`. You'll lose leaderboards, but save a tiny bit of database overhead.

**"Should I use MySQL or SQLite?"**
SQLite unless you're running multiple servers that need shared data. SQLite is faster for single-server setups.

---

Need help with something specific? Check out the other configuration pages!
.
{% content-ref url="loot.yml.md" %}
[loot.yml.md](loot.yml.md)
{% endcontent-ref %}
.
{% content-ref url="messages.yml.md" %}
[messages.yml.md](messages.yml.md)
{% endcontent-ref %}
.

---

## 🧱 Generation Settings

These settings control constraints when registering or generating chamber regions.

```yaml
generation:
  # Maximum number of blocks allowed when generating/registering a chamber region
  # Keep this manageable for your server hardware
  max-volume: 750000
  blocks:
    # When using /tcp generate blocks <amount>, we may need to round up to reach
    # minimum viable dimensions (31x15x31). This is the maximum number of extra
    # blocks allowed beyond the requested amount.
    rounding-allowance: 1000
```

Notes:
- The plugin enforces a hard minimum region size of 31x15x31.
- The `blocks` generator places the region in front of the player based on their facing.
- If your requested amount is below minimum, it will be rounded up to that minimum.
- If your requested amount is slightly above minimum or not factorizable cleanly, the plugin rounds up to form a solid box within the rounding allowance.
