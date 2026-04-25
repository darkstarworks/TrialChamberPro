# 🌐 Localization

Every user-visible string in TrialChamberPro lives in `plugins/TrialChamberPro/messages.yml` and can be overridden — including the entire admin GUI as of v1.3.0. This page covers how to translate, the key naming conventions, and the placeholder syntax.

<div data-gb-custom-block data-tag="hint" data-style="info">

**TL;DR**: edit `messages.yml`, run `/tcp reload`, and you're done. The plugin never overwrites this file once it exists, so your translations survive plugin updates.

</div>

## File location and reload

- **Path:** `plugins/TrialChamberPro/messages.yml`
- **Generated on first run** — copy from the bundled defaults, then never touched again.
- **Reload after editing:** `/tcp reload` rereads `config.yml`, `loot.yml`, and `messages.yml` together. No restart required.

## Top-level structure

```yaml
prefix: "&8[&6TCP&8]&r "

# Per-feature message keys (chat output, command help, etc.)
chamber-not-found: "&cChamber '{chamber}' not found."
no-permission: "&cYou don't have permission to do that."

# All admin GUI strings live under a single nested gui.* tree.
gui:
  common:
    back-button: "&e« Back to {destination}"
    close-button: "&cClose"
    # ... shared toggle/lore templates ...

  main-menu:
    title: "TrialChamberPro Admin"
    header-name: "&6&lTrialChamberPro"
    header-lore:
      - "&7Version &f{version}"
      - ""
      - "&eRegistered Chambers: &f{chambers}"
      - "&eLoot Tables: &f{tables}"
    # ... one section per GUI view ...
```

## Color codes

Use the legacy ampersand format (`&a`, `&l`, `&r`, etc.) — it's converted at runtime to Adventure components. Standard codes:

- `&0`–`&9`, `&a`–`&f` — colors
- `&l` bold, `&n` underline, `&o` italic, `&m` strikethrough, `&k` obfuscated
- `&r` reset all formatting

Section-sign (`§`) escapes also work but ampersand is preferred for editability. Hex colors (`&#RRGGBB`) are *not* currently supported — open an issue if you need them.

<div data-gb-custom-block data-tag="hint" data-style="info">

GUI item names default to **non-italic** even though Minecraft normally italicizes renamed items. This is intentional — every `gui.*` key passes through `TextDecoration.ITALIC=false` so admin tools look clean. If you actually want italic, prepend `&o` to the value.

</div>

## Placeholders

Messages can include `{placeholder}` tokens that the plugin substitutes at send time:

```yaml
chamber-created: "&aChamber &f{chamber}&a created with volume {volume}."
```

Supplied at the call site by the plugin code:

```kotlin
plugin.getMessage("chamber-created",
    "chamber" to chamber.name,
    "volume" to chamber.getVolume())
```

The placeholders available for each key are listed inline next to the key in the bundled `messages.yml`. Unknown placeholders are left as literal `{token}` text rather than throwing — useful for spotting typos.

## Multi-line lore

GUI lore (and any other list-typed value) is a YAML list:

```yaml
gui:
  main-menu:
    header-lore:
      - "&7Version &f{version}"
      - ""        # blank line in-game
      - "&eRegistered Chambers: &f{chambers}"
```

Each entry becomes one lore line. Blank entries (`""`) render as visual separators. Placeholders work in every line.

## The `gui.*` namespace

All GUI strings live under a single nested `gui:` tree, organized one sub-section per view:

| Section | Owns |
|---|---|
| `gui.common` | Back/close/prev/next buttons, destination labels, toggle templates |
| `gui.main-menu` | Main hub |
| `gui.chamber-list` / `gui.chamber-detail` / `gui.chamber-settings` | Chamber browse + manage |
| `gui.vault-management` | Vault cooldown management |
| `gui.custom-mob` | Per-chamber mob provider config |
| `gui.loot-table-list` / `gui.pool-selector` / `gui.loot-editor` / `gui.amount-editor` | Loot editing flow |
| `gui.stats-menu` / `gui.leaderboard` / `gui.player-stats` | Statistics views |
| `gui.settings-menu` / `gui.global-settings` / `gui.protection-menu` | Settings hub + toggle screens |
| `gui.help-menu` | In-GUI command reference |

Within a section, key suffixes follow a consistent pattern:

- `<thing>-name` — item display name (single string)
- `<thing>-lore` — item lore (list of strings)
- `<thing>-lore-<state>` — state-dependent lore (e.g. `protection-lore-enabled` / `protection-lore-disabled`)
- `empty-name` / `empty-lore` — placeholder when a list/grid view has zero entries
- `title` — `ChestGui` title

Keys under `gui.*` automatically skip the chat prefix — the GUI doesn't want `[TCP]` prepended to every item name.

## Shared toggle templates

Settings views with on/off toggles all use the same shared templates so translators don't have to retranslate "Enabled / Disabled / Click to toggle" for every setting:

```yaml
gui:
  common:
    toggle-name-enabled: "&a&l{label}"
    toggle-name-disabled: "&c&l{label}"
    toggle-lore-enabled:
      - "&7{description}"
      - ""
      - "&aStatus: Enabled"
      - ""
      - "&eClick to toggle"
    toggle-lore-disabled:
      - "&7{description}"
      - ""
      - "&cStatus: Disabled"
      - ""
      - "&eClick to toggle"
```

Each toggle then provides only its own `<setting>-label` and `<setting>-desc`:

```yaml
gui:
  protection-menu:
    block-break-label: "Prevent Block Break"
    block-break-desc: "Stop players from breaking blocks"
```

Translate the templates once, translate per-toggle labels/descs, and the result composes correctly.

## What's translatable

Everything user-visible:
- ✅ Chat messages, command output, error messages
- ✅ Boss bar text on trial spawner waves
- ✅ Every name, lore line, and button label in the admin GUI
- ✅ `/tcp help` and `/tcp info` output
- ✅ Tab-completer suggestions are not translated (those are internal command tokens, not display text)

Logger output (server console) is intentionally English — those messages are read by server admins and bug reports, not players, and consistency makes triage easier.

## Reset to defaults

The plugin doesn't ship a `/tcp reset-messages` command. To restore the bundled defaults: rename or delete `messages.yml` and run `/tcp reload` — a fresh copy will be written from the JAR.

## Reporting translation issues

If a placeholder is missing, a key is missing from the bundled defaults, or a string can't be sensibly translated due to baked-in word order, [open an issue](https://github.com/darkstarworks/trialchamberpro/issues). Translation contributions are welcome.
