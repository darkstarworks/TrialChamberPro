# 🧟 Custom Mob Providers

**Added in v1.3.0.**

Trial Chamber waves normally spawn vanilla mobs (zombies, husks, slimes, etc.) from the trial-spawner pool. With Custom Mob Providers, you can hand that job off to another plugin — MythicMobs, for example — while keeping every TrialChamberPro feature intact: wave counters, boss bars, glow outlines, spawner cooldowns, Trial Key drops, statistics.

<div data-gb-custom-block data-tag="hint" data-style="info">

**How it works:** When a trial spawner fires, vanilla spawns its mob, TCP removes it the same tick, and asks the configured provider to spawn a replacement at the same spot. The vanilla spawner's internal state machine — which tracks participating players and completion — is untouched, so waves still progress normally and vanilla rewards still work.

</div>

---

## 📋 Built-in Providers

| Provider id | Backing plugin | Mob id format | Notes |
|---|---|---|---|
| `vanilla` | _(none)_ | _(n/a)_ | Always available |
| `mythicmobs` | [MythicMobs](https://www.mythiccraft.io/mythicmobs) | mob internal name, e.g. `SkeletalKnight` | Uses `MobManager.spawnMob(name, loc, 1.0)` |
| `elitemobs` | [EliteMobs](https://www.magmaguy.com/) | custom-boss filename, e.g. `the_warden` or `the_warden.yml` | Spawns via `CustomBossEntity.createCustomBossEntity` |
| `ecomobs` | [EcoMobs](https://github.com/Auxilor/EcoMobs) (requires [Eco](https://github.com/Auxilor/eco)) | mob id from `plugins/EcoMobs/mobs/<id>.yml` | Uses `EcoMobs[id].spawn(loc, SpawnReason.CUSTOM)` |
| `levelledmobs` | [LevelledMobs](https://www.spigotmc.org/resources/14498/) | `ENTITY_TYPE[:level]` — e.g. `ZOMBIE`, `ZOMBIE:15`, `HUSK:ominous` | Spawns a vanilla entity then applies a level. `ominous` keyword uses higher tier on ominous waves |
| `infernalmobs` | [InfernalMobs](https://www.spigotmc.org/resources/34710/) | `ENTITY_TYPE` — e.g. `BLAZE`, `WITHER_SKELETON` | Spawns vanilla then calls `makeInfernal(entity, false)` (abilities rolled by plugin) |
| `citizens` | [Citizens](https://www.spigotmc.org/resources/13811/) | numeric NPC id **or** exact NPC name | Clones the template NPC so it isn't moved; stores copy at the spawner |

---

## 🎮 Configuring via GUI

1. `/tcp menu` → **Chambers** → pick a chamber → **Settings**
2. Click the **Custom Mob Provider** button (spawner icon, centre of row 2)
3. **Provider** cycle button — left-click to advance, right-click to go back, shift-click to reset to vanilla
4. **Add normal mob id** / **Add ominous mob id** — click, then type the id in chat (or `cancel`)
5. Click any existing mob-id icon to remove it

Changes save immediately and the view refreshes. If the backing plugin isn't installed, the provider name shows in red and spawns fall back to vanilla at runtime with a warning in the console.

---

## 🛠️ Configuring via Command

```
/tcp mobs providers                          # list registered providers
/tcp mobs list <chamber>                     # show current config
/tcp mobs provider <chamber> <id|vanilla|none>
/tcp mobs add <chamber> <normal|ominous> <mobId>
/tcp mobs remove <chamber> <normal|ominous> <mobId>
```

Example — route the `main_spire` chamber through MythicMobs:

```
/tcp mobs provider main_spire mythicmobs
/tcp mobs add main_spire normal SkeletalKnight
/tcp mobs add main_spire normal VoidHound
/tcp mobs add main_spire ominous VoidLich
```

If the **ominous** list is empty, ominous waves fall back to the **normal** list. If the normal list is also empty while a non-vanilla provider is selected, individual spawns fall back to vanilla for that wave.

---

## 🔑 Trial Key Drops

Vanilla trial spawners drop Trial Keys through their own state machine — that machine only knows about vanilla mobs, so once a wave is driven by a custom provider, TCP takes over key drops itself:

- One `TRIAL_KEY` per unique participating player for a normal wave
- One `OMINOUS_TRIAL_KEY` per unique participating player for an ominous wave
- Dropped at the spawner block with a small upward pop (vanilla style)
- **Owner-locked**: only the original participant can pick up the key during a grace window, then anyone can pick it up. This prevents griefers hovering around active chambers.

<div data-gb-custom-block data-tag="hint" data-style="info">

**Grace window** is configured in `config.yml`:

```yaml
reset:
  spawner-key-drop-owner-grace-seconds: 30   # 0 = owner-locked until despawn
```

</div>

Players with permission `tcp.bypass.droplock` (default: op) can pick up keys regardless of owner.

---

## 🧪 Verifying a Provider Works

1. Set a provider on a chamber and list a single well-known mob id (e.g. `SkeletalKnight` for MythicMobs, from a default pack)
2. Stand in the chamber, enable debug: `/tcp debug on`
3. Hit a trial spawner and watch chat:
   - Vanilla mob flash-spawns and is immediately removed
   - The custom mob appears at the same spot
   - Boss bar counts down as you kill it
4. Complete the wave — Trial Key(s) should drop at the spawner block

If the custom mob never appears and the console shows `Mob provider X could not spawn Y — falling back to vanilla`, double-check:

- The mob id matches exactly (case-insensitive, but must be spelled right)
- The backing plugin is installed and enabled (`/plugins`)
- The provider id on the chamber matches a registered provider (`/tcp mobs providers`)

---

## ⚠️ Known Limitations

- Wave state is not persisted across restarts; an in-progress wave will reset to vanilla behavior if the server restarts mid-fight (matches pre-1.3.0 behavior)
- Provider availability is evaluated at spawn time — enabling/disabling MythicMobs without a restart will not flip registered providers until plugin reload
