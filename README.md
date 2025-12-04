<img width="723" height="133" alt="TrialChamberPro Banner" src="https://github.com/user-attachments/assets/7be34fce-1bfc-4639-bd34-5fe417e43610" />

# TrialChamberPro

**The definitive Trial Chamber management plugin for multiplayer servers.**

Transform Minecraft 1.21's Trial Chambers from single-use dungeons into renewable, multiplayer-ready content.<br>
Automatic resets, per-player loot, custom rewards, griefing protection & many more features.

---

## Why TrialChamberPro?

Vanilla Trial Chambers weren't designed for multiplayer.<br>
The first player takes everything, vaults stay locked forever, and griefers destroy spawners. **TrialChamberPro fixes all of this.**

| Problem | Solution |
|---------|----------|
| First player gets all loot | Per-player vault system with individual cooldowns |
| No way to reset chambers | Automatic scheduled resets with player warnings |
| Griefers break spawners | Full protection system with WorldGuard support |
| Paper trial key bugs | Built-in fixes for known Paper issues |
| No progression tracking | Statistics, leaderboards, and PlaceholderAPI support |

---

## Features

### Core Systems
- **Automatic Resets** — Chambers restore on schedule with configurable warnings
- **Per-Player Vaults** — Everyone gets their own loot with separate cooldowns
- **Full Protection** — Block break/place, container access, mob griefing prevention
- **Statistics & Leaderboards** — Track vaults opened, mobs killed, time spent
- **Admin GUI** — Complete management with 14 views—configure everything without YAML editing

### Advanced Loot System
- **Multi-Pool Tables** — Common, rare, and unique pools like vanilla
- **Custom Items** — Tipped arrows, enchanted gear, ominous bottles (Bad Omen III-V)
- **Command Rewards** — Economy deposits, permission grants, XP, custom commands
- **LUCK Integration** — Potion effects and attributes affect loot rolls

### Multiplayer Enhancements
- **Spawner Wave Tracking** — Boss bar shows wave progress as players fight
- **Spectator Mode** — Dead players can watch teammates complete the challenge
- **PlaceholderAPI** — 20+ placeholders for scoreboards, holograms, and more

### Admin GUI (New in 1.2.8)
- **Runtime Config Editing** — Toggle settings in-game without editing YAML files
- **Chamber Management** — Per-chamber reset intervals, exit locations, loot overrides
- **Vault Cooldowns** — View and reset player cooldowns for any vault
- **Protection Toggles** — Enable/disable protection features instantly
- **Statistics Dashboard** — View leaderboards and player stats in-game

### Technical Excellence
- **Folia Native** — Full support for regionized multithreading
- **Paper/Purpur/Pufferfish** — Works on all major Paper forks
- **Async Architecture** — Kotlin coroutines for zero main-thread blocking
- **Dual Database** — SQLite (default) or MySQL with connection pooling
- **WorldEdit/FAWE** — Create chambers from selections or schematics

---

## Quick Start

```
1. Drop the JAR in your plugins folder
2. Start your server
3. Select a Trial Chamber with WorldEdit (//wand)
4. Run: /tcp generate wand MyChamber
5. Run: /tcp scan MyChamber
6. Run: /tcp snapshot create MyChamber
7. Done! Your chamber now auto-resets and has per-player loot.
```

---

## Commands

| Command | Description |
|---------|-------------|
| `/tcp menu` | Open the admin GUI |
| `/tcp generate wand <name>` | Register chamber from WorldEdit selection |
| `/tcp scan <chamber>` | Detect vaults and spawners |
| `/tcp snapshot create <chamber>` | Enable automatic resets |
| `/tcp reset <chamber>` | Force immediate reset |
| `/tcp stats [player]` | View statistics |
| `/tcp leaderboard <type>` | View top players |

<details>
<summary>View all commands</summary>

| Command | Description | Permission |
|---------|-------------|------------|
| `/tcp menu` | Open admin GUI | `tcp.admin` |
| `/tcp generate wand <name>` | Create from WorldEdit selection | `tcp.admin` |
| `/tcp generate coords <name> <coords>` | Create from coordinates | `tcp.admin` |
| `/tcp scan <chamber>` | Scan for vaults/spawners | `tcp.admin` |
| `/tcp snapshot create <chamber>` | Create restoration snapshot | `tcp.admin` |
| `/tcp snapshot restore <chamber>` | Restore from snapshot | `tcp.admin` |
| `/tcp reset <chamber>` | Force chamber reset | `tcp.admin` |
| `/tcp delete <chamber>` | Delete chamber | `tcp.admin` |
| `/tcp setexit <chamber>` | Set exit location | `tcp.admin` |
| `/tcp list` | List all chambers | `tcp.admin` |
| `/tcp info <chamber>` | View chamber details | `tcp.admin` |
| `/tcp tp <chamber>` | Teleport to chamber | `tcp.admin` |
| `/tcp stats [player]` | View statistics | `tcp.stats` |
| `/tcp leaderboard <type>` | View leaderboards | `tcp.stats` |
| `/tcp reload` | Reload configuration | `tcp.admin` |

</details>

---

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `tcp.admin` | Full admin access | OP |
| `tcp.stats` | View own statistics | Everyone |
| `tcp.leaderboard` | View leaderboards | Everyone |
| `tcp.spectate` | Use spectator mode after death | Everyone |
| `tcp.bypass.cooldown` | Ignore vault cooldowns | OP |
| `tcp.bypass.protection` | Build in protected chambers | OP |

---

## PlaceholderAPI

<details>
<summary>View all placeholders</summary>

**Player Statistics**
- `%tcp_vaults_opened%` — Total vaults opened
- `%tcp_vaults_normal%` — Normal vaults opened
- `%tcp_vaults_ominous%` — Ominous vaults opened
- `%tcp_chambers_completed%` — Chambers completed
- `%tcp_mobs_killed%` — Mobs killed in chambers
- `%tcp_deaths%` — Deaths in chambers
- `%tcp_time_spent%` — Time spent (formatted)
- `%tcp_time_spent_raw%` — Time spent (seconds)

**Current State**
- `%tcp_current_chamber%` — Chamber player is in
- `%tcp_in_chamber%` — Whether player is in a chamber

**Leaderboards**
- `%tcp_leaderboard_vaults%` — Player's vault rank
- `%tcp_leaderboard_chambers%` — Player's chamber rank
- `%tcp_leaderboard_time%` — Player's time rank
- `%tcp_top_vaults_1_name%` — #1 player by vaults
- `%tcp_top_vaults_1_value%` — #1 player's vault count
- *(1-10 available for vaults, chambers, and time)*

</details>

---

## Requirements

| Requirement | Version |
|-------------|---------|
| **Minecraft** | 1.21.1+ |
| **Server** | Paper, Folia, Purpur, or Pufferfish |
| **Java** | 21+ |

### Optional Dependencies
- **WorldEdit** or **FastAsyncWorldEdit** — Easier chamber creation
- **WorldGuard** — Additional region protection
- **PlaceholderAPI** — Scoreboard/hologram placeholders
- **Vault** — Economy command rewards
- **LuckPerms** — Permission command rewards

---

## Configuration

All configuration is optional—sensible defaults work out of the box.

<details>
<summary>config.yml highlights</summary>

```yaml
# Vault cooldowns
vaults:
  normal-cooldown-hours: -1    # -1 = until reset (vanilla behavior)
  ominous-cooldown-hours: -1
  per-player-loot: true

# Automatic resets
global:
  default-reset-interval: 172800  # 48 hours
  reset-warning-times: [300, 60, 30]
  teleport-players-on-reset: true

# Protection
protection:
  prevent-block-break: true
  prevent-block-place: true
  prevent-mob-griefing: true
  worldguard-integration: true

# New in 1.2.5
spawner-waves:
  enabled: true
  show-boss-bar: true

spectator-mode:
  enabled: true
  offer-timeout: 30
```

</details>

<details>
<summary>Example loot table</summary>

```yaml
default:
  pools:
    common:
      min-rolls: 2
      max-rolls: 4
      items:
        - material: ARROW
          amount: 8-16
          weight: 100
        - material: TIPPED_ARROW
          potion-type: SLOWNESS
          amount: 4-8
          weight: 50
    rare:
      min-rolls: 1
      max-rolls: 2
      guaranteed: true
      items:
        - material: DIAMOND
          amount: 1-3
          weight: 30
        - material: EMERALD
          amount: 2-5
          weight: 70
    unique:
      min-rolls: 0
      max-rolls: 1
      items:
        - material: OMINOUS_BOTTLE
          custom-effect-type: BAD_OMEN
          potion-level: 3
          weight: 10
        - type: COMMAND
          command: "eco give {player} 500"
          weight: 20
```

</details>

---

## Support

- **[Documentation](https://darkstarworks.gitbook.io/darkstarworks-docs/tcp-documentation)** — Full setup guides and reference
- **[Discord](https://discord.gg/aWMU2JNXex)** — Community support and announcements
- **[GitHub Issues](https://github.com/darkstarworks/TrialChamberPro/issues)** — Bug reports and feature requests
- **[Source Code](https://github.com/darkstarworks/TrialChamberPro)** — Open source under CC-BY-NC-ND 4.0

---

## Target Audience

- **Survival Servers** — Renewable endgame content that keeps players engaged
- **SMP Networks** — Fair loot distribution across your playerbase
- **Minigame Servers** — Competitive Trial Chamber runs with leaderboards
- **Adventure Servers** — Protected dungeons with custom rewards

---

<div align="center">

**Paper 1.21.1+** · **Folia Native** · **Java 21+**

Made with Kotlin by [darkstarworks](https://github.com/darkstarworks)

</div>
