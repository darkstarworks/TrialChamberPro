# ⚙️ config.yml Reference

Your `config.yml` is the control panel for TrialChamberPro. It's where you decide how chambers behave, when they reset, and how much protection you want. Let's break it down section by section.

{% hint style="info" %}
**Location:** `plugins/TrialChamberPro/config.yml`

After making changes, reload with `/tcp reload`
{% endhint %}

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

{% hint style="success" %}
**Stick with SQLite unless** you're running a network with BungeeCord/Velocity and need multiple servers to share chambers.
{% endhint %}

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
```

### `default-reset-interval`
**Default:** `172800` (48 hours)
**Unit:** Seconds

How long before chambers automatically reset. This is the default for ALL chambers unless overridden per-chamber.

**Common values:**
- Daily: `86400`
- Twice daily: `43200`
- Weekly: `604800`

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

{% hint style="warning" %}
**Setting this to false** removes per-player cooldowns. Vaults become first-come-first-served. Usually not what you want for managed chambers!
{% endhint %}

### `normal-cooldown-hours` / `ominous-cooldown-hours`
**Default:** `24` / `48` hours

How long before a player can loot the same vault again. Separate cooldowns for normal and ominous vaults.

**Ideas:**
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
  reset-ominous-spawners: true
  clear-trial-omen-effect: true
  reset-vault-cooldowns: false
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

### `reset-ominous-spawners`
**Default:** `true`

Convert ominous trial spawners back to normal during reset. Set to `false` if you want ominous spawners to stay ominous.

### `clear-trial-omen-effect`
**Default:** `true`

Remove Trial Omen effects from players during reset. Prevents unintended difficulty spikes.

### `reset-vault-cooldowns`
**Default:** `false`

Reset all player vault cooldowns when the chamber resets. Usually `false`—cooldowns are personal and independent of chamber state.

Set to `true` if you want players to always have fresh vaults after each chamber reset.

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

## 🐛 Debug Mode

```yaml
debug: false
```

**Default:** `false`

Spam console with debugging info. Only enable this when troubleshooting issues or reporting bugs.

{% hint style="warning" %}
Debug mode generates TONS of console output. Don't leave it on in production!
{% endhint %}

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

{% hint style="info" %}
**Database changes** require a full restart, not just a reload.
{% endhint %}

---

## 💡 Pro Tips

{% hint style="success" %}
**Start conservative:** Use longer cooldowns and reset intervals initially. It's easier to shorten them later than deal with player complaints about too-frequent changes.
{% endhint %}

{% hint style="info" %}
**Test in dev:** Create a test chamber to experiment with settings. Use `/tcp reset TestChamber` to see how changes affect gameplay.
{% endhint %}

{% hint style="warning" %}
**Backup before tuning:** Changing database settings wrong can corrupt data. Always backup `plugins/TrialChamberPro/` before major config changes.
{% endhint %}

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

{% content-ref url="loot.yml.md" %}
[loot.yml.md](loot.yml.md)
{% endcontent-ref %}

{% content-ref url="messages.yml.md" %}
[messages.yml.md](messages.yml.md)
{% endcontent-ref %}


---

## 🧱 Generation Settings

These settings control constraints when registering or generating chamber regions.

```yaml
generation:
  # Maximum number of blocks allowed when generating/registering a chamber region
  # Keep this manageable for your server hardware
  max-volume: 500000
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
