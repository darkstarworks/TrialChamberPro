# TrialChamberPro

Transform Minecraft's Trial Chambers from single-use dungeons into renewable, multiplayer-ready content. Automatic resets, per-player vault loot, custom rewards, griefing protection — and it works on every natural chamber in your world without any setup per-chamber.

---

## The problem it solves

Vanilla Trial Chambers weren't designed for multiplayer. The first player takes everything, vaults stay locked forever, and griefers can destroy spawners. On a server with more than one player, chambers become single-use content almost immediately.

TrialChamberPro fixes all of that: every player gets their own loot roll, chambers reset on a schedule, spawners are protected, and progression is tracked. The plugin can find and manage natural chambers automatically — no WorldEdit, no commands per chamber.

---

## What you can do

* **Automatic resets** — chambers restore on schedule, with warnings before the reset fires.
* **Per-player vaults** — every player gets their own loot roll with their own cooldown.
* **Full protection** — block break / place, container access, mob griefing, WorldGuard-aware.
* **Statistics & leaderboards** — track vaults opened, mobs killed, chambers completed, time spent.
* **Custom loot** — multi-pool tables, command rewards, potions, tipped arrows, custom plugin items (Nexo / ItemsAdder / Oraxen), resource-pack items via `custom-model-data`.
* **Auto-discovery** — opt-in; the plugin finds and registers every natural chamber on its own.
* **Admin GUI** — `/tcp menu` handles everything. No YAML editing required.
* **Spawner wave tracking** — boss bar shows progress as players fight.
* **Spectator mode** — dead players can watch teammates finish the chamber.
* **PlaceholderAPI** — 20+ placeholders for scoreboards, holograms, tab lists.
* **Full translation support** — every user-facing string lives in `messages.yml`.

---

## Requirements

* **Minecraft 1.21.1+** (use the `-mc26` build for Minecraft 26.x)
* **Paper, Folia, Purpur, or Pufferfish**
* **Java 21+**
* *Optional:* WorldEdit / FAWE, WorldGuard, PlaceholderAPI, Vault, LuckPerms, Nexo / ItemsAdder / Oraxen

---

## Where to go next

{% content-ref url="getting-started/installation.md" %}
[installation.md](getting-started/installation.md)
{% endcontent-ref %}

Install the JAR and get the server running. Two minutes.

{% content-ref url="getting-started/your-first-chamber.md" %}
[your-first-chamber.md](getting-started/your-first-chamber.md)
{% endcontent-ref %}

Manually register and configure a chamber. Recommended if you want fine control over specific chambers.

{% content-ref url="getting-started/basic-configuration.md" %}
[basic-configuration.md](getting-started/basic-configuration.md)
{% endcontent-ref %}

Walk through the config settings most servers actually tweak.

{% content-ref url="configuration/config.yml.md" %}
[config.yml.md](configuration/config.yml.md)
{% endcontent-ref %}

Full `config.yml` reference. Includes the auto-discovery plug-and-play setup.

{% content-ref url="configuration/loot.yml.md" %}
[loot.yml.md](configuration/loot.yml.md)
{% endcontent-ref %}

Everything about loot tables — pools, custom items, command rewards.

{% content-ref url="troubleshooting.md" %}
[troubleshooting.md](troubleshooting.md)
{% endcontent-ref %}

Something not working? Most issues have a known cause. Check here first.

---

## Support

* **[GitHub Issues](https://github.com/darkstarworks/TrialChamberPro/issues)** — bug reports, feature requests
* **[Discord](https://discord.gg/qwYcTpHsNC)** — community support, announcements
* **[Modrinth](https://modrinth.com/plugin/trialchamberpro)** — downloads and release notes

Open source under CC-BY-NC-ND 4.0. Made with Kotlin by [darkstarworks](https://github.com/darkstarworks).
