# 🌀 spawner\_presets.yml

**Added in v1.3.1.**

Named templates for `minecraft:trial_spawner` items. Each preset can be handed out via `/tcp give <preset>` to produce a real, placeable trial-spawner block with a custom mob configuration baked in.

<div data-gb-custom-block data-tag="hint" data-style="info">

**What this is for:** server owners who define their own trial-spawner mob pools through a datapack and want a one-command way to deploy preconfigured spawners. No more pasting a 200-character `/give minecraft:trial_spawner[block_entity_data={...}]` string every time.

</div>

---

## 📋 How it works

1. You (or a datapack author) define mob configurations in a Minecraft datapack — see the [Trial Spawner wiki](https://minecraft.wiki/w/Trial_Spawner#Spawner_configuration) for the JSON format.
2. You list named presets in `spawner_presets.yml`, each pointing at one of those datapack configs.
3. `/tcp give <preset>` hands the player a `trial_spawner` item with `block_entity_data` baked in. Placing it produces a working spawner with the configured normal/ominous pools, cooldown, and player range.

The plugin does **not** generate the datapack for you — that's the part Mojang made data-driven, and it's the admin's job. TCP just packages the deployment side.

<div data-gb-custom-block data-tag="hint" data-style="warning">

**Trial spawners only.** The YAML schema has no `material:` field — every preset always produces a `TRIAL_SPAWNER`. This is deliberate. Custom keys, vault crates, and other custom items are out of scope for this file (they belong to the planned premium "Vault Crate" module).

</div>

---

## 🛠️ File structure

```yaml
presets:
  super_zombie:
    normal-config: "namespace:basic_zombie"
    ominous-config: "namespace:basic_zombie_o"
    required-player-range: 14
    target-cooldown-length: 36000
    display-name: "&6Super Zombie Spawner"
    lore:
      - "&7A custom trial spawner."
      - "&7Place to deploy."

  boss_arena:
    normal-config: "namespace:boss_pool"
    required-player-range: 24
    target-cooldown-length: 72000
    total-mobs: 12
    simultaneous-mobs: 4
    spawn-range: 6
    display-name: "&c&lBoss Arena Spawner"
```

---

## 🔧 Field reference

| Field | Type | Default | Description |
|---|---|---|---|
| `normal-config` | string | _(none)_ | Resource location of the datapack-defined spawner config used outside ominous mode. |
| `ominous-config` | string | _(none)_ | Resource location of the datapack-defined spawner config used during ominous mode. |
| `required-player-range` | int | `14` | How close a player must be (blocks) for the spawner to activate. |
| `target-cooldown-length` | int | `36000` | Cooldown in **ticks** after a wave completes (`36000` = 30 min, vanilla default). |
| `total-mobs` | int | _(datapack)_ | Total mobs spawned across the wave. Overrides the datapack value. |
| `simultaneous-mobs` | int | _(datapack)_ | Maximum live mobs at once. Overrides the datapack value. |
| `total-mobs-added-per-player` | float | _(datapack)_ | Extra total mobs per additional participating player. |
| `simultaneous-mobs-added-per-player` | float | _(datapack)_ | Extra concurrent cap per additional player. |
| `ticks-between-spawn` | int | _(datapack)_ | Tick delay between individual spawns. |
| `spawn-range` | int | _(datapack)_ | Radius (blocks) around the spawner where mobs can appear. |
| `display-name` | string | _(none)_ | Item name shown in inventory. Supports `&` colour codes. |
| `lore` | list of strings | _(empty)_ | Item lore lines. Supports `&` colour codes. |

<div data-gb-custom-block data-tag="hint" data-style="info">

At least one of `normal-config` or `ominous-config` must be set, or the preset is skipped at load with a warning. A preset with neither would spawn nothing.

</div>

<div data-gb-custom-block data-tag="hint" data-style="warning">

**`normal-config` and `ominous-config` are resource locations, not inline configs.** They must point at a config defined in a datapack on the server. Inline compound NBT is not supported here. If your datapack isn't installed when a player places the spawner, the spawner will error in the console at activation time — not at `/tcp give`.

</div>

---

## 📝 Using the command

```
/tcp give <preset> [player] [amount]
```

| Argument | Required? | Default |
|---|---|---|
| `<preset>` | yes | — |
| `[player]` | no — must be present if sender is the console | sender |
| `[amount]` | no | `1` |

**Permission:** `tcp.give` (default: op). Included in the `tcp.admin.*` aggregate.

**Examples:**

```
/tcp give super_zombie                    # gives self 1 spawner
/tcp give super_zombie Notch              # gives Notch 1 spawner
/tcp give boss_arena Notch 5              # gives Notch 5 spawners
```

If the player's inventory is full, overflow drops at their feet rather than vanishing.

---

## 🔄 Reloading

After editing `spawner_presets.yml`, run:

```
/tcp reload
```

The preset map is hot-swapped atomically; in-flight `/tcp give` calls are not affected.

---

## 💡 Tips

- **Tab completion** lists every loaded preset id when you press `Tab` after `/tcp give`.
- **Preset ids are case-insensitive** in lookups — `Super_Zombie`, `super_zombie`, and `SUPER_ZOMBIE` all resolve to the same preset.
- **Datapack authoring** is documented at the [Minecraft Wiki — Trial Spawner page](https://minecraft.wiki/w/Trial_Spawner#Spawner_configuration). The relevant data folder is `data/<namespace>/trial_spawner/<config_name>.json`.
- **Validate quickly**: copy your `/give` test command into the preset (just the config strings + numbers) and verify the produced item matches the same NBT in F3+H tooltips.

---

## ⚠️ Limitations

### Custom Mob Providers do NOT apply to preset-spawned spawners

TrialChamberPro's [Custom Mob Providers](custom-mobs.md) (MythicMobs, EliteMobs, EcoMobs, LevelledMobs, InfernalMobs, Citizens) only intercept trial-spawner spawns **inside a registered chamber**. Spawners placed from `/tcp give` exist as standalone blocks in the world — they are not tied to a chamber — so the provider intercept does not run on them.

This means:

- A spawner placed from a preset will spawn whatever entity its datapack config specifies (vanilla mob types only — `minecraft:zombie`, `minecraft:skeleton`, etc., heavily customizable via NBT but always vanilla entity ids).
- You **cannot** spawn MythicMobs / EliteMobs / etc. creatures from a preset-built spawner placed in the wild, because the datapack JSON has no field for non-vanilla entity ids and TCP's provider intercept is chamber-scoped.

**If you need custom-plugin mobs to spawn from a preset spawner**, place the spawner inside a registered chamber and configure that chamber's custom mob provider (`/tcp mobs <chamber> provider <id>`). The chamber's provider will then intercept the preset spawner's wave the same way it intercepts vanilla-generated spawners in that chamber.

<div data-gb-custom-block data-tag="hint" data-style="info">

This is by design for the free tier. If your use case is selling sellable, placeable trial spawners that work with custom-plugin mobs anywhere on a survival map without chamber registration, that's the natural shape of an upcoming premium module — drop a note in the [Issues](https://github.com/darkstarworks/TrialChamberPro/issues) tracker if it'd be useful for your server.

</div>

---

## ❓ Troubleshooting

**"Unknown preset" when running `/tcp give`**
The preset id wasn't loaded. Check the server log on startup or after `/tcp reload` — parse failures and missing-config skips both log a warning.

**Spawner places fine but spawns nothing**
The datapack referenced by `normal-config` / `ominous-config` isn't installed (or the resource location is mistyped). Check the server console when a player approaches the spawner.

**`Failed to build item for preset`**
The SNBT produced from the preset failed Paper's `ItemFactory` parse. Most likely cause: a stray quote or backslash in `normal-config` / `ominous-config`. Use plain `namespace:config_id` strings.

**File not appearing in plugin folder**
`spawner_presets.yml` is created on first plugin load. If it's missing, ensure you ran the server with v1.3.1+ at least once and check write permissions on `plugins/TrialChamberPro/`.
