# ❓ Troubleshooting

Most problems fall into one of the buckets below. Scan headings first — if you recognise the symptom, the fix is usually a few lines down.

If nothing here matches, jump to [Reporting Bugs](#reporting-bugs) or ask in [Discord](https://discord.gg/qwYcTpHsNC).

---

## Vault cooldowns don't work — players open the same vault instantly

**The likely cause:** you're testing as an OP.

Operators have **every** permission by default, including `tcp.bypass.cooldown`. That permission intentionally skips cooldown checks so staff can test chambers. It's working as designed — but it's confusing the first time you hit it.

**Fix one of three ways:**

1. Test with a non-OP account. Cooldowns will apply normally.
2. Explicitly negate the permission on your OP user:
   ```
   /lp user <yourname> permission set tcp.bypass.cooldown false
   ```
3. Temporarily deop yourself: `/deop <yourname>`, test, re-op with `/op <yourname>`.

**To confirm this is your issue:** set `debug.verbose-logging: true` in `config.yml`, `/tcp reload`, then open a vault. If the log shows `[Vault API] Player X has tcp.bypass.cooldown permission - SKIPPING cooldown check!` — that's it.

---

## "Loot table not found" / vault opens but no loot drops

**The likely cause:** TAB characters in your `loot.yml`.

YAML is strict about indentation. Mixing tabs and spaces — or using tabs at all — breaks the parser silently. The entire file fails to load, which is why `/tcp loot set` reports "Available tables:" as empty and vaults generate nothing.

**Fix:**

1. Open `plugins/TrialChamberPro/loot.yml` in a real text editor (VS Code, Notepad++, Sublime).
2. Enable "Show whitespace" / "View invisible characters" so you can see tabs.
3. Replace every tab with 2 spaces (or use your editor's "Convert Indentation to Spaces" command).
4. `/tcp reload`.
5. `/tcp menu` → Loot Tables — your tables should be listed.

Same rule applies to `config.yml` and `messages.yml`. If any of the three go quiet after an edit, tabs are the first suspect.

---

## Do I need WorldEdit? Does this work without manually registering every chamber?

**No, you don't need WorldEdit — and yes, it works automatically.**

Enable auto-discovery and the plugin finds every natural chamber by itself:

```yaml
discovery:
  enabled: true
  auto-snapshot: true    # so resets can restore blocks
```

Restart once. Walk or fly around your world — chambers register themselves as their chunks load. Pre-loaded chunks (spawn region on server start, worlds that were already loaded) are picked up by a one-time startup sweep.

Full details: [Auto-Discovery config →](configuration/config.yml.md#auto-discovery-of-natural-trial-chambers)

If you prefer manual control (e.g. you want custom names or only specific chambers to use the plugin), the classic WorldEdit workflow still works: `/tcp generate wand MyChamber`. See [Your First Chamber](getting-started/your-first-chamber.md).

---

## Chamber resets don't restore broken blocks

**The likely cause:** the chamber has no snapshot.

Resets clear entities, restart spawners, and clear vault cooldowns — but to rebuild blocks they need a **snapshot** taken while the chamber was intact.

**Fix for manually-registered chambers:**

```
/tcp snapshot create <chamber>
```

Take this **before** players start breaking things. You can also take a fresh snapshot any time the chamber is in a known-good state and use it as the new baseline.

**Fix for auto-discovered chambers:**

Set `discovery.auto-snapshot: true` in `config.yml` and `/tcp reload`. New chambers discovered from that point forward will snapshot on registration. For chambers already auto-discovered without a snapshot, create one manually with `/tcp snapshot create <chamber>` (use `/tcp list` to find the auto-generated name).

---

## Boss bars don't go away when I leave a chamber

**Fixed in 1.2.26.** Update to the latest version.

If you're already on 1.2.26+ and still seeing this, check `spawner-waves.remove-distance` in `config.yml` — default is 32. Players outside this range get removed from the bar. If you've lowered it below `detection-radius`, the hysteresis breaks.

---

## Server lags when chambers reset or snapshot

Snapshot and restore operations scale with chamber size. A 100×50×100 chamber is 500,000 blocks — even streaming to disk, that's work.

**Tune these in `config.yml`:**

```yaml
global:
  async-block-placement: true     # Must be true. Non-negotiable.
  blocks-per-tick: 500            # Lower this to 100-200 on low-spec servers.

performance:
  async-database-operations: true
  cache-duration-seconds: 300
```

Also: don't reset multiple large chambers at the same clock minute. Stagger their reset intervals (one at `172800`, another at `172900`, etc.) so they don't overlap.

**If you're on Folia,** confirm `performance.use-folialib: true`. The plugin detects Folia automatically, but this flag is required.

---

## Cooldowns work for some players but not others

Usually a permission inheritance problem. Check:

1. **Does the affected player / group have `tcp.bypass.cooldown`?** Often picked up via a default permission pack or a copy-pasted permission group.
   ```
   /lp user <player> permission check tcp.bypass.cooldown
   ```
2. **Is the player in creative or spectator?** Creative players bypass cooldowns regardless of permissions (vanilla vault behaviour).
3. **Did you recently clear vault data in the database?** If you wiped `player_vault_data` but not the native `rewarded_players` on the vault block (v1.2.21+ stores both), the native block state still remembers them. Use `/tcp vault reset <chamber> <player>` — it clears both.

---

## MySQL connection errors on startup

Usually a credentials or host issue. Full error text tells you which:

| Error contains | Meaning | Fix |
|----------------|---------|-----|
| `Access denied for user` | Wrong username or password | Verify `database.username` / `password` in config.yml |
| `Unknown database` | Database doesn't exist | `CREATE DATABASE trialchamberpro;` on your MySQL server |
| `Connection refused` | Host unreachable | Check `database.host` and `port`; is MySQL running? |
| `timeout after 30000ms` | Connection pool exhausted | Increase `database.pool-size` from 10 to 20 |

If you're not actually using MySQL and the plugin is still trying to connect to it, check `database.type: SQLITE` (case-sensitive).

---

## Auto-discovery registered something that isn't a chamber

On worlds that existed before 1.21, players sometimes build structures out of tuff bricks or copper blocks. The auto-detector's structural predicate can match these.

**Short-term fix:** delete the false registration.

```
/tcp list                  # find the auto_world_X_Z name
/tcp delete <name>
```

**Long-term fix:** tighten the detection thresholds in `config.yml`:

```yaml
discovery:
  min-vaults-plus-spawners: 3    # Up from 2 — chambers almost always have ≥3
  max-center-y: 5                # Down from 10 — natural chambers gen quite deep
```

Then bump `discovery.cooldown-seconds` higher if the same false region keeps re-triggering.

If you're on an old world and the false-positive rate is high, it may be easier to leave `discovery.enabled: false` and register chambers manually — the trade-off is up to you.

---

## Nexo / ItemsAdder / Oraxen items don't drop

The `CUSTOM_ITEM` loot type uses reflection, so it's safe if the custom-item plugin isn't installed — but that also means the item silently skips if anything's off. Check:

1. **The plugin is installed and loaded.** `/plugins` should show it green.
2. **The item ID is correct and lowercase where required.** Each plugin has its own case rules — match exactly how their docs write it.
3. **The `plugin:` field is spelled correctly:** `nexo`, `itemsadder`, or `oraxen`. Case-insensitive, but typos are silent failures.
4. **`debug.verbose-logging: true`** will log the attempted resolution — watch the console when a vault opens.

Example that works:

```yaml
- type: CUSTOM_ITEM
  plugin: oraxen
  item-id: amethyst_blade
  weight: 5
```

---

## I changed messages.yml but nothing changed in-game

1. Did you run `/tcp reload`? Config and message edits require a reload (or a restart).
2. Is the key you edited the one actually being displayed? Some messages look similar. Search `messages.yml` for the exact text you see in-game.
3. TAB characters? (See [Loot table not found](#loot-table-not-found--vault-opens-but-no-loot-drops) above — same rule.)
4. Are you sure it's not a boss bar? Boss bar messages are in `messages.yml` under keys containing `boss-bar` — they use MiniMessage tags, different from regular color codes.

Full messages reference: [messages.yml →](configuration/messages.yml.md)

---

## Performance Tips

General-purpose tuning guidance.

- **`blocks-per-tick`** is the single most important knob. Lower on low-spec hardware, raise on beefy servers with headroom. Default 500 is conservative.
- **`async-block-placement: true`** should never be turned off.
- **Cache durations** — `cache-duration-seconds: 300` (5 min) is fine for most servers. Bump to 600+ if you have hundreds of registered chambers and rare modifications.
- **MySQL** outperforms SQLite past ~50 concurrent players. Below that, SQLite is simpler and plenty fast.
- **Snapshot files** live in `plugins/TrialChamberPro/snapshots/`. They're gzip-compressed but a 500k-block chamber can still be 20+ MB. Monitor disk if you have many large chambers.
- **Skip discovery on world pregen.** If you're running Chunky to pre-generate your world, temporarily set `discovery.enabled: false`, run the pregen, then re-enable. Discovery + chunk-load storm adds up.

---

## Reporting Bugs

If you've hit something not covered here, a good bug report includes:

1. **Plugin version** (`/tcp info` shows it).
2. **Server type and version** (Paper 1.21.4, Folia 26.1.2, etc.).
3. **Steps to reproduce** — exact commands, exact actions.
4. **What you expected** vs **what actually happened**.
5. **Relevant log excerpt** — run with `debug.verbose-logging: true` to get detailed output, then paste the lines around the error.
6. **Your config.yml and loot.yml** (redact MySQL credentials first!) if they're relevant.

File at [GitHub Issues](https://github.com/darkstarworks/TrialChamberPro/issues) or post in the `#support` channel on [Discord](https://discord.gg/qwYcTpHsNC).

"Can't reproduce" is a real answer — sometimes a bug depends on state that's hard to observe. If we ask for more detail, it's because we're trying to track it down, not brushing it off.
