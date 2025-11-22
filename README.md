<img width="723" height="133" alt="trialchamberpro-min" src="https://github.com/user-attachments/assets/7be34fce-1bfc-4639-bd34-5fe417e43610" />

> Because Trial Chambers deserve better than being a "one and done" dungeon.

Transform Minecraft 1.21's Trial Chambers from dusty, abandoned structures into thriving endgame content that players actually want to revisit. No more disappointed players finding empty vaults. No more manually resetting chambers. Just renewable, multiplayer-friendly dungeon content that actually works.

## The Problem

Vanilla Trial Chambers? Great for single-player. Terrible for servers.

- **First player gets everything** → Everyone else gets nothing
- **No resets** → Chamber sits there, looted and useless
- **Griefing central** → Players break spawners and steal loot blocks
- **Keys are buggy** → Paper has known issues with trial key mechanics

Yeah, we fixed all of that.

## What This Thing Does

**Automatic Resets** — Set it and forget it. Chambers restore themselves on your schedule (with warnings so nobody gets teleported mid-fight).

**Per-Player Loot** — Everyone gets their own vault rewards. No more racing to loot first. Each player has separate 24h/48h cooldowns for normal and ominous vaults.

**Custom Loot Tables** — Drop whatever you want. Economy rewards? Custom items? Ominous bottles with Bad Omen V? Tipped arrows? Go wild.

**Protection System** — Blocks, spawners, and vaults are protected. Optional WorldGuard integration for extra security.

**Statistics & Leaderboards** — Track completions, deaths, and vault openings. Perfect for competitive servers.

**Admin GUI** — Manage everything with `/tcp menu`. Edit loot tables in-game, view stats, configure chambers without touching YAML.

**WorldEdit Support** — Use your wand to select regions. Or use coordinates. Or paste from schematics. Your choice.

## Key Features

### Core Systems
- **Automatic chamber resets** with configurable intervals
- **Per-player vault cooldowns** (separate for normal/ominous)
- **Custom loot tables** with multi-pool system (common/rare/unique)
- **Griefing protection** (blocks, spawners, containers, mob griefing)
- **Statistics tracking** with leaderboards
- **In-game admin GUI** for zero-config management

### Advanced Loot
- **Tipped arrows** with custom potion effects and levels
- **Custom potions** (including Level IV+ ominous potions)
- **Ominous bottles** (Bad Omen III-V for triggering Ominous Trials)
- **Enchantment randomization** (level ranges + random pools)
- **Variable durability** (pre-damaged items as loot)
- **COMMAND rewards** (economy, permissions, XP, any console command)

### Technical
- **SQLite or MySQL** with connection pooling
- **Async-first** design with Kotlin coroutines
- **Gzip-compressed snapshots** for efficient storage
- **Multi-pool loot tables** matching vanilla Trial Chamber structure
- **WorldEdit/FAWE integration** for schematic support
- **Key management** fixes for Paper bugs

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/tcp menu` | Open admin GUI | `tcp.admin` |
| `/tcp info <chamber>` | View chamber details | `tcp.admin` |
| `/tcp generate wand <name>` | Create chamber from WorldEdit selection | `tcp.admin` |
| `/tcp generate coords <name> <x1> <y1> <z1> <x2> <y2> <z2>` | Create chamber from coordinates | `tcp.admin` |
| `/tcp scan <chamber>` | Scan for vaults in chamber | `tcp.admin` |
| `/tcp snapshot create <chamber>` | Create restoration snapshot | `tcp.admin` |
| `/tcp reset <chamber>` | Force chamber reset | `tcp.admin` |
| `/tcp delete <chamber>` | Delete chamber (with confirmation) | `tcp.admin` |
| `/tcp list` | List all chambers | `tcp.admin` |
| `/tcp reload` | Reload configuration | `tcp.admin` |
| `/tcp stats [player]` | View player statistics | `tcp.stats` |
| `/tcp leaderboard` | View top players | `tcp.stats` |
| `/tcp tp <chamber>` | Teleport to chamber | `tcp.admin` |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `tcp.admin` | Access all admin commands and GUI | `op` |
| `tcp.stats` | View statistics and leaderboards | `true` |
| `tcp.bypass.protection` | Bypass chamber protection (for admins) | `op` |
| `tcp.use` | Basic plugin usage | `true` |

## Configuration

### config.yml Highlights

**Database Settings**
```yaml
database:
  type: sqlite  # or mysql
  # MySQL settings (if using mysql)
  mysql:
    host: localhost
    port: 3306
    database: trialchamberpro
    username: root
    password: changeme
```

**Vault Cooldowns**
```yaml
vaults:
  normal-cooldown-hours: 24    # 24 hours for normal vaults
  ominous-cooldown-hours: 48   # 48 hours for ominous vaults
  show-cooldown-particles: true
  play-sound-on-open: true
```

**Automatic Resets**
```yaml
reset:
  enabled: true
  warning-times: [300, 60, 30, 10]  # Seconds before reset
  teleport-players: true
  # Per-chamber reset intervals (in seconds)
  intervals:
    my_chamber: 86400  # 24 hours
    hard_chamber: 172800  # 48 hours
```

**Protection**
```yaml
protection:
  block-break: true
  block-place: true
  container-access: true
  mob-griefing: true
  worldguard-integration: true
```

**Performance**
```yaml
performance:
  blocks-per-tick: 500  # Balance between speed and TPS
  cache-duration-seconds: 300  # 5 minutes
  time-tracking-interval: 300  # Batch player time updates
```

**Loot System**
```yaml
loot:
  apply-luck-effect: false  # LUCK potion/attribute affects rolls
  max-pools-per-table: 5
```

**Debug**
```yaml
debug:
  verbose-logging: false  # Enable for troubleshooting
```

## Getting Started

1. **Install** — Drop the JAR in your `plugins/` folder
2. **Start server** — Plugin creates default configs
3. **Create a chamber** — Use `/tcp generate wand <name>` with WorldEdit selection
4. **Scan for vaults** — `/tcp scan <name>` finds all trial vaults
5. **Create snapshot** — `/tcp snapshot create <name>` enables automatic resets
6. **Configure loot** — Edit `loot.yml` or use `/tcp menu` GUI
7. **Done!** — Chamber auto-resets on schedule

## Requirements

- **Paper 1.21.1+** (or Folia, Purpur, Pufferfish)
- **Java 21** or newer
- **Optional**: WorldEdit/FAWE for easier chamber creation
- **Optional**: Vault plugin for economy rewards
- **Optional**: LuckPerms for permission rewards

## Support & Links

- **[Full Documentation](https://darkstarworks.gitbook.io/darkstarworks-docs/tcp-documentation)**
- **[Report Issues](https://github.com/darkstarworks/TrialChamberPro/issues)**
- **[Discord Support](https://discord.gg/aWMU2JNXex)**
- **[GitHub](https://github.com/darkstarworks/TrialChamberPro)**

## Fun Fact

Did you know Trial Chambers were designed for single-player? Mojang never expected hundreds of players to visit the same chamber. That's why vault loot is "first come, first served" in vanilla.

We fixed that. You're welcome. 😎

---

**License**: CC-BY-NC-ND 4.0
**Platform**: Paper 1.21.1+
**Language**: Kotlin
**Version**: 1.2.1


## Who's This For?

- **Server Owners** who want engaging, replayable PvE content
- **Admins** tired of manually resetting Trial Chambers
- **Network Operators** running hub servers with minigame-style chambers
- **Players** who love the challenge but hate the "one and done" limitation
