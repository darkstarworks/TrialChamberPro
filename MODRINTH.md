<img width="723" height="133" alt="TrialChamberPro Banner" src="https://github.com/user-attachments/assets/7be34fce-1bfc-4639-bd34-5fe417e43610" />

# TrialChamberPro

### Recently improved
- **Plug-and-play auto-discovery** — the plugin now finds and registers every natural Trial Chamber by itself. No WorldEdit, no commands.
- **GUI-editable loot tables** — edit your default loot tables from `/tcp menu` and changes apply to every chamber using that table. No YAML editing required.
- **Custom plugin items** — drop Nexo, ItemsAdder, Oraxen, CraftEngine, or MythicCrucible items from vaults, plus `custom-model-data` support for resource pack items.
- **Fully translatable** — every user-facing string lives in `messages.yml`. Localize to any language.
- **Minecraft 26.x support** — use the `-mc26` version (Paper 26.1.2+).
- Better boss bars, fewer bugs, tighter GUIs, and much more.

📘 **Full documentation:** https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation — most questions are answered there, and every section below links to its own detailed page.

---

**The definitive Trial Chamber management plugin for multiplayer servers.**

Transform Minecraft's Trial Chambers from single-use dungeons into renewable, multiplayer-ready content. Automatic resets, per-player loot, custom rewards, griefing protection — and it all works out of the box.

---

## Why TrialChamberPro?

Vanilla Trial Chambers weren't designed for multiplayer. The first player takes everything, vaults stay locked forever, and griefers destroy spawners. **TrialChamberPro fixes all of that.**

| Problem | Solution |
|---------|----------|
| First player gets all loot | Per-player vault system with individual cooldowns |
| No way to reset chambers | Automatic scheduled resets with player warnings |
| Griefers break spawners | Full protection system with optional WorldGuard support |
| Paper trial key bugs | Built-in fixes for known Paper issues |
| No progression tracking | Statistics, leaderboards, and PlaceholderAPI support |
| Setup overhead per chamber | **Auto-discovery** — chambers register themselves |

---

## Two-Line Plug-and-Play Setup

For most servers, the only thing you need to configure is this:

```yaml
# plugins/TrialChamberPro/config.yml
discovery:
  enabled: true        # find natural trial chambers automatically
  auto-snapshot: true  # capture blocks so resets can restore them
```

Restart once. Fly or walk through your world — every natural trial chamber registers itself as its chunks load, with per-player loot, protection, and automatic resets already active. Done.

> Why it's opt-in: on **old** worlds that pre-date 1.21, players sometimes build decorative structures out of tuff and copper blocks. The auto-detector could register those as chambers. On fresh worlds there's no risk. [More detail in the docs →](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/configuration/config.yml#auto-discovery-of-natural-trial-chambers)

Prefer manual control? You can still register chambers with WorldEdit (`/tcp generate wand MyChamber`) or by coordinates — see [Your First Chamber](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/getting-started/your-first-chamber).

---

## Features

### Core Systems
- **Auto-Discovery** — natural chambers register themselves on chunk load + startup sweep. [Docs →](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/configuration/config.yml#auto-discovery-of-natural-trial-chambers)
- **Automatic Resets** — chambers restore on schedule with configurable warnings, or set interval to `0` for manual-only.
- **Per-Player Vaults** — everyone gets their own loot with separate cooldowns.
- **Full Protection** — block break/place, container access, mob griefing prevention. WorldGuard-aware.
- **Statistics & Leaderboards** — vaults opened, mobs killed, time spent. [Docs →](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/guides/statistics)
- **Admin GUI** — `/tcp menu` does everything. No YAML editing required.

### Advanced Loot
- **Multi-Pool Tables** — common / rare / unique pools like vanilla, fully configurable. [Docs →](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/configuration/loot.yml)
- **Per-Chamber Overrides** — assign a different loot table to any specific chamber.
- **GUI Editing** *(new in 1.2.26)* — open `/tcp menu` → Loot Tables, click a table, and edit it. Changes save to `loot.yml` instantly.
- **Custom Plugin Items** — drop Nexo, ItemsAdder, or Oraxen items directly from vaults:
  ```yaml
  - type: CUSTOM_ITEM
    plugin: nexo        # or itemsadder / oraxen / craftengine / mythiccrucible
    item-id: mythic_sword
    weight: 5
  ```
- **Custom Model Data** — set `custom-model-data` on any vanilla item for resource-pack integration.
- **Command Rewards** — run any console command as loot (economy deposits, permission grants, XP).
- **Potion & Tipped Arrows** — full attribute support including Bad Omen III–V ominous bottles.
- **LUCK Integration** — optional bonus rolls for players with the LUCK effect.

### Multiplayer Enhancements
- **Spawner Wave Tracking** — boss bar shows wave progress as players fight. Hysteresis-based despawn means the bar disappears when you leave the area *(improved in 1.2.26)*.
- **Spectator Mode** — dead players can watch teammates complete the challenge, bounded to the chamber.
- **PlaceholderAPI** — 20+ placeholders for scoreboards, holograms, tab lists.

### Technical Excellence
- **Folia Native** — full support for regionized multithreading.
- **Paper / Purpur / Pufferfish** — works on all major Paper forks.
- **Async Architecture** — Kotlin coroutines, zero main-thread blocking.
- **Dual Database** — SQLite (default) or MySQL with connection pooling.
- **WorldEdit / FAWE** — optional, used only if you want selection-based chamber creation.

---

## Fully Translatable

Every user-facing string lives in `plugins/TrialChamberPro/messages.yml`. Want your server in French, Chinese, Spanish, or Klingon? Edit one file, `/tcp reload`, done.

Supports `&`-style color codes, `{placeholder}` substitution, and Adventure Components for boss bars.

📘 [Full message reference →](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/configuration/messages.yml)

---

## Quick Start (manual mode)

Prefer not to use auto-discovery? Classic workflow still works:

```
1. Drop the JAR in your plugins folder
2. Start your server
3. Select a Trial Chamber with WorldEdit (//wand)
4. Run: /tcp generate wand MyChamber
5. Run: /tcp snapshot create MyChamber   (enables auto-reset)
6. Done!
```

---

## Commands (at a glance)

| Command | Description |
|---------|-------------|
| `/tcp menu` | Open the admin GUI (does everything) |
| `/tcp generate wand <name>` | Register chamber from WorldEdit selection |
| `/tcp reset <chamber>` | Force immediate reset |
| `/tcp snapshot create <chamber>` | Enable automatic resets |
| `/tcp loot set <chamber> <normal\|ominous> <table>` | Override loot for a chamber |
| `/tcp stats [player]` | View statistics |
| `/tcp leaderboard <type>` | View top players |
| `/tcp reload` | Reload config & loot tables |

📘 [Full command reference →](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/reference/commands)

---

## Permissions (essentials)

| Permission | Description | Default |
|------------|-------------|---------|
| `tcp.admin` | Full admin access | OP |
| `tcp.stats` · `tcp.leaderboard` | View own stats / leaderboards | Everyone |
| `tcp.spectate` | Use spectator mode after death | Everyone |
| `tcp.bypass.cooldown` | Ignore vault cooldowns (careful: removes progression!) | OP |
| `tcp.bypass.protection` | Build in protected chambers | OP |
| `tcp.discovery.notify` | Get notified when auto-discovery registers a chamber | OP |

> **Heads up:** `tcp.bypass.cooldown` is granted to OPs by default. If you're testing cooldowns as an OP, they'll appear broken — either test as a non-OP or explicitly negate the permission.

📘 [Full permissions guide with rank examples →](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/reference/permissions)

---

## Requirements

| Requirement | Version |
|-------------|---------|
| **Minecraft** | 1.21.1+ (use `-mc26` JAR for 26.x) |
| **Server** | Paper, Folia, Purpur, or Pufferfish |
| **Java** | 21+ |

### Optional Dependencies
- **WorldEdit / FastAsyncWorldEdit** — selection-based chamber creation.
- **WorldGuard** — additional region protection.
- **PlaceholderAPI** — scoreboard / hologram placeholders.
- **Vault** — economy command rewards.
- **Nexo / ItemsAdder / Oraxen / CraftEngine / MythicCrucible** — custom items in loot tables.
- **LuckPerms** — permission command rewards.

---

## Essential Configuration

Sensible defaults work out of the box. The three settings most servers actually tweak:

```yaml
global:
  default-reset-interval: 172800   # 48 hours. Use 0 for manual-only resets.

vaults:
  normal-cooldown-hours: -1        # -1 = vanilla (locked until chamber reset)
  ominous-cooldown-hours: -1       # Set a positive number for per-player time cooldown.

discovery:
  enabled: true                    # Auto-register natural chambers. Opt-in.
  auto-snapshot: true              # Allow auto-discovered chambers to restore on reset.
```

📘 [Full config.yml reference →](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/configuration/config.yml) · [loot.yml reference →](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/configuration/loot.yml)

---

## PlaceholderAPI (summary)

20+ placeholders for player stats (`%tcp_vaults_opened%`, `%tcp_mobs_killed%`, `%tcp_time_spent%`), current state (`%tcp_current_chamber%`), and leaderboards (`%tcp_top_vaults_1_name%` through `_10_`). Built-in 60-second cache.

📘 [Full placeholder list →](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation/guides/statistics#placeholderapi)

---

## Support

- 📘 **[Documentation](https://darkstarworks.gitbook.io/darkstarworks-plugins/tcp-documentation)** — setup guides, configuration reference, troubleshooting. **Please check here first!** Most questions are already answered.
- 💬 **[Discord](https://discord.gg/qwYcTpHsNC)** — community support, announcements, feature requests. Not everyone's a reader — that's fine, come chat.
- 🐛 **[GitHub Issues](https://github.com/darkstarworks/TrialChamberPro/issues)** — bug reports.
- ⭐ **[Source Code](https://github.com/darkstarworks/TrialChamberPro)** — open source under CC-BY-NC-ND 4.0.

---

## Target Audience

- **Survival Servers** — renewable endgame content that keeps players engaged.
- **SMP Networks** — fair loot distribution across your playerbase.
- **Minigame Servers** — competitive Trial Chamber runs with leaderboards.
- **Adventure / RP Servers** — protected dungeons with custom rewards.

---

<div align="center">

**Paper 1.21.1+ / 26.1.2+** · **Folia Native** · **Java 21+**

Made with Kotlin by [darkstarworks](https://github.com/darkstarworks)

---

This plugin is free forever and actively maintained.

If you have questions or would like to just say Hi, come [join the Discord](http://discord.gg/qwYcTpHsNC).

Rather stay silent? (Anonymous) donations are also **VERY** welcome: https://ko-fi.com/darkstarworks

</div>
