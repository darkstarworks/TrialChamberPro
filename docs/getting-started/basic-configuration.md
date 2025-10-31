# ⚙️ Basic Configuration

You've installed the plugin and created your first chamber. Now let's tune the basics to match your server's playstyle. This is a quick-start guide—for deep dives, check the full [config.yml reference](../configuration/config.yml.md).

{% hint style="info" %}
**Config location:** `plugins/TrialChamberPro/config.yml`

After editing, run `/tcp reload` to apply changes (no restart needed for most settings!)
{% endhint %}

---

## 🎯 The Three Settings You'll Change First

### 1. Reset Interval

**How often should chambers reset?**

```yaml
global:
  default-reset-interval: 172800  # 48 hours in seconds
```

**Default:** 48 hours (2 days)

**Common values:**
- **Daily:** `86400` (24 hours)
- **Twice daily:** `43200` (12 hours)
- **Weekly:** `604800` (7 days)
- **Every 6 hours:** `21600` (for high-activity servers)

**How to choose:**
- **High activity server** (50+ players): 12-24 hours
- **Medium activity** (20-50 players): 24-48 hours
- **Small/casual** (<20 players): 48-168 hours

{% hint style="success" %}
**Start longer, shorten later!** It's easier to speed up resets than deal with player complaints about too-frequent changes.
{% endhint %}

---

### 2. Vault Cooldowns

**How long before a player can loot the same vault again?**

```yaml
vaults:
  normal-cooldown-hours: 24
  ominous-cooldown-hours: 48
```

**Default:** 24 hours (normal), 48 hours (ominous)

**Philosophy:**
- Normal vaults = more common, shorter cooldown
- Ominous vaults = better loot, longer cooldown

**Suggestions:**
- **Match chamber resets:** If chambers reset every 24h, set cooldowns to 24h
- **Independent progression:** Cooldowns longer than resets = players can't farm same vault repeatedly
- **Fast-paced:** 6-12 hours for active servers
- **Hardcore:** 72+ hours for scarce loot

**Example setups:**

Fast-paced server:
```yaml
global:
  default-reset-interval: 43200  # 12 hours
vaults:
  normal-cooldown-hours: 6
  ominous-cooldown-hours: 12
```

Balanced server:
```yaml
global:
  default-reset-interval: 86400  # 24 hours
vaults:
  normal-cooldown-hours: 24
  ominous-cooldown-hours: 48
```

Hardcore server:
```yaml
global:
  default-reset-interval: 604800  # 7 days
vaults:
  normal-cooldown-hours: 168  # 7 days
  ominous-cooldown-hours: 336  # 14 days
```

---

### 3. Protection Settings

**Should players be able to break/place blocks in chambers?**

```yaml
protection:
  enabled: true
  prevent-block-break: true
  prevent-block-place: true
  allow-pvp: true
```

**Recommended settings:**

Survival server (prevent griefing):
```yaml
protection:
  enabled: true
  prevent-block-break: true
  prevent-block-place: true
  allow-pvp: false  # Peaceful farming
```

PvP/competitive server:
```yaml
protection:
  enabled: true
  prevent-block-break: true
  prevent-block-place: true
  allow-pvp: true  # Fight over loot!
```

Creative/build server:
```yaml
protection:
  enabled: false  # Let admins build freely
  # Or keep enabled and use tcp.bypass.protection permission
```

{% hint style="warning" %}
**Don't disable protection on public survival servers!** Players will grief your chambers. Use `tcp.bypass.protection` permission for staff instead.
{% endhint %}

---

## 🎁 Tweaking Loot

Your loot tables live in `loot.yml`, not `config.yml`. Here's a quick example:

```yaml
loot-tables:
  default:
    min-rolls: 3
    max-rolls: 5
```

**What this means:**
- Each vault gives 3-5 random items from the loot pool
- Higher rolls = more items per vault

**Quick adjustments:**

More generous:
```yaml
min-rolls: 5
max-rolls: 8
```

More challenging:
```yaml
min-rolls: 1
max-rolls: 3
```

For detailed loot customization, see the [loot.yml reference](../configuration/loot.yml.md).

---

## ⚡ Performance Tuning

If you have large chambers or many players, tune these:

```yaml
global:
  async-block-placement: true
  blocks-per-tick: 500
```

**`blocks-per-tick`** controls reset speed:
- **Low (250-500):** Slower resets, less lag
- **High (1000+):** Faster resets, possible lag spikes

**When to adjust:**
- **Dedicated server with good CPU:** Increase to 1000+
- **Shared hosting or budget VPS:** Keep at 250-500
- **Massive chambers (50k+ blocks):** Lower to 250 to avoid lag

**Test it:**
```
/tcp reset YourChamber
```
Watch for lag. Adjust `blocks-per-tick` and test again.

---

## 🔔 Reset Warnings

Players inside chambers get warnings before resets:

```yaml
global:
  reset-warning-times: [300, 60, 30]
```

**Default:** Warnings at 5 minutes, 1 minute, 30 seconds before reset

**Adjustments:**

More warnings:
```yaml
reset-warning-times: [600, 300, 120, 60, 30, 10]
# 10 min, 5 min, 2 min, 1 min, 30s, 10s
```

Fewer warnings (less spam):
```yaml
reset-warning-times: [60]
# Only 1 minute warning
```

No warnings (surprise resets):
```yaml
reset-warning-times: []
```

{% hint style="info" %}
**Warnings are in seconds.** `300` = 5 minutes, `60` = 1 minute, `30` = 30 seconds.
{% endhint %}

---

## 🎨 Visual & Sound Settings

Make vaults more noticeable with particles and sounds:

```yaml
vaults:
  show-cooldown-particles: true
  particles:
    normal-available: VILLAGER_HAPPY     # Green sparkles = ready
    normal-cooldown: SMOKE_NORMAL         # Gray smoke = on cooldown
    ominous-available: SOUL_FIRE_FLAME    # Purple flames = ominous ready
    ominous-cooldown: SOUL                # Soul particles = ominous cooldown

  play-sound-on-open: true
  sounds:
    normal-open: BLOCK_VAULT_OPEN_SHUTTER
    ominous-open: BLOCK_VAULT_OPEN_SHUTTER
    cooldown: BLOCK_NOTE_BLOCK_BASS
```

**Want to disable visual feedback?**
```yaml
vaults:
  show-cooldown-particles: false
  play-sound-on-open: false
```

**Want more subtle particles?**
```yaml
particles:
  normal-available: END_ROD            # Subtle white particles
  normal-cooldown: SMOKE_NORMAL
  ominous-available: PORTAL            # Purple portal particles
  ominous-cooldown: ASH
```

See [Spigot's Particle enum](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Particle.html) for all options.

---

## 📊 Statistics Settings

Track player activity and leaderboards:

```yaml
statistics:
  enabled: true
  track-time-spent: true
  leaderboard-update-interval: 3600
  top-players-count: 10
```

**Disable statistics** (saves minimal database overhead):
```yaml
statistics:
  enabled: false
```

**Show more players on leaderboards:**
```yaml
top-players-count: 25
```

**Update leaderboards more frequently:**
```yaml
leaderboard-update-interval: 1800  # 30 minutes instead of 1 hour
```

---

## 🗄️ Database Settings

**SQLite (default, recommended for single servers):**
```yaml
database:
  type: SQLITE
```

No other settings needed! Everything is stored in `data.db`.

**MySQL (for networks with multiple servers):**
```yaml
database:
  type: MYSQL
  host: localhost
  port: 3306
  database: trialchamberpro
  username: your_username
  password: your_password
  pool-size: 10
```

{% hint style="warning" %}
**Database changes require a full restart,** not just `/tcp reload`!
{% endhint %}

**When to use MySQL:**
- Running BungeeCord/Velocity with multiple servers
- Need shared chamber data across servers
- Want external database access

**Otherwise, stick with SQLite.** It's faster and simpler for single-server setups.

---

## 🎮 Quick Config Templates

### Casual Survival Server

```yaml
global:
  default-reset-interval: 86400  # Daily
  reset-warning-times: [300, 60]
  teleport-players-on-reset: true

vaults:
  per-player-loot: true
  normal-cooldown-hours: 6
  ominous-cooldown-hours: 12
  show-cooldown-particles: true

protection:
  enabled: true
  prevent-block-break: true
  prevent-block-place: true
  allow-pvp: false

statistics:
  enabled: true
```

**Philosophy:** Generous cooldowns, peaceful gameplay, daily resets.

---

### Competitive/PvP Server

```yaml
global:
  default-reset-interval: 43200  # Twice daily
  reset-warning-times: [120, 60, 30, 10]
  teleport-players-on-reset: true

vaults:
  per-player-loot: true
  normal-cooldown-hours: 12
  ominous-cooldown-hours: 24
  show-cooldown-particles: true

protection:
  enabled: true
  prevent-block-break: true
  prevent-block-place: true
  allow-pvp: true

statistics:
  enabled: true
  track-time-spent: true
```

**Philosophy:** Frequent resets, PvP enabled, competitive stat tracking.

---

### High-Activity Server

```yaml
global:
  default-reset-interval: 21600  # Every 6 hours
  reset-warning-times: [60, 30]
  teleport-players-on-reset: true
  blocks-per-tick: 1000

vaults:
  per-player-loot: true
  normal-cooldown-hours: 3
  ominous-cooldown-hours: 6
  show-cooldown-particles: true

protection:
  enabled: true
  prevent-block-break: true
  prevent-block-place: true
  allow-pvp: false

performance:
  cache-duration-seconds: 600
```

**Philosophy:** Fast resets, short cooldowns, optimized for many players.

---

### Roleplay/Immersive Server

```yaml
global:
  default-reset-interval: 604800  # Weekly
  reset-warning-times: [300, 60]
  teleport-players-on-reset: true

vaults:
  per-player-loot: true
  normal-cooldown-hours: 168  # Weekly
  ominous-cooldown-hours: 336  # Bi-weekly
  show-cooldown-particles: false  # Less immersion-breaking
  play-sound-on-open: false

protection:
  enabled: true
  prevent-block-break: true
  prevent-block-place: true
  allow-pvp: false

statistics:
  enabled: false  # Focus on roleplay, not stats
```

**Philosophy:** Scarce resources, minimal UI feedback, long cooldowns for meaningful loot.

---

## 🔄 Applying Your Changes

1. **Edit** `plugins/TrialChamberPro/config.yml`
2. **Save** the file
3. **Reload:** `/tcp reload`

Most settings apply immediately. Exceptions:
- **Database settings** require a full restart
- **Chamber-specific intervals** affect new resets, not current timers

{% hint style="success" %}
**Backup before major changes!** Copy your `plugins/TrialChamberPro/` folder before experimenting with config.
{% endhint %}

---

## ❓ Common Questions

**"Do I need to restart after editing config?"**
Usually no—`/tcp reload` works for most settings. Only database changes require a restart.

**"Can different chambers have different reset intervals?"**
Not via config.yml (yet). All chambers use `default-reset-interval`. Per-chamber intervals are planned for future versions.

**"What happens to existing cooldowns if I change vault cooldown hours?"**
Existing cooldowns aren't retroactively changed. New cooldowns apply to future vault interactions.

**"Can I disable per-player loot?"**
Yes, set `vaults.per-player-loot: false`. Vaults become first-come-first-served like vanilla (not recommended for multiplayer).

**"How do I make chambers reset faster without lag?"**
Increase `blocks-per-tick` gradually (500 → 750 → 1000) and test for lag. Also ensure `async-block-placement: true`.

---

## 🎯 Next Steps

Now that you've configured the basics, dive deeper:

{% content-ref url="../configuration/config.yml.md" %}
[config.yml.md](../configuration/config.yml.md)
{% endcontent-ref %}

Complete reference for every config option.

{% content-ref url="../configuration/loot.yml.md" %}
[loot.yml.md](../configuration/loot.yml.md)
{% endcontent-ref %}

Customize what players get from vaults.

{% content-ref url="../configuration/messages.yml.md" %}
[messages.yml.md](../configuration/messages.yml.md)
{% endcontent-ref %}

Change all player-facing messages to match your server's style.

{% content-ref url="../guides/automatic-resets.md" %}
[automatic-resets.md](../guides/automatic-resets.md)
{% endcontent-ref %}

Deep dive into how resets work and advanced scheduling.

---

## 💡 Pro Tips

{% hint style="success" %}
**Test in a dev environment first!** Create a test chamber and experiment with settings before applying to production.
{% endhint %}

{% hint style="info" %}
**Start conservative with cooldowns and intervals.** You can always make them shorter based on player feedback. Going the opposite direction causes complaints!
{% endhint %}

{% hint style="warning" %}
**Watch your server TPS during resets.** If TPS drops significantly, lower `blocks-per-tick` or increase `reset-warning-times` to spread resets out.
{% endhint %}

{% hint style="success" %}
**Join the discussion!** Check [GitHub Issues](https://github.com/darkstarworks/TrialChamberPro/issues) for community config tips and common setups.
{% endhint %}

Happy configuring! ⚙️
