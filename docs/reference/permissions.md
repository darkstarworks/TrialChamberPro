# 🔐 Permissions Reference

TrialChamberPro uses a hierarchical permission system. Grant broad access with wildcards or fine-tune with specific permissions.

{% hint style="info" %}
**Permission Plugin Required:** Use LuckPerms, PermissionsEx, or any Bukkit-compatible permissions plugin.

**Default OP Permissions:** All `tcp.admin.*` permissions default to operators. Players get `tcp.stats` and `tcp.leaderboard` by default.
{% endhint %}

---

## 🎯 Quick Setup

### Full Admin Access
```yaml
permissions:
  - tcp.admin.*
```
Grants ALL admin permissions (add, scan, reset, manage keys, etc.)

### Player Access (View Stats Only)
```yaml
permissions:
  - tcp.stats
  - tcp.leaderboard
```
Players can view their own stats and leaderboards. **This is default!**

### Moderator Access (Manage Chambers, No Config)
```yaml
permissions:
  - tcp.admin.reset
  - tcp.admin.key
  - tcp.admin.vault
  - tcp.admin.stats
```
Can reset chambers, manage keys/vaults, view player stats, but can't modify chamber structure or reload config.

---

## 📋 Complete Permission List

### Admin Permissions

#### `tcp.admin.*`
**Description:** All admin permissions (wildcard)
**Default:** Operators only
**Grants access to:**
- All commands listed below
- Complete control over chambers
- Configuration management

**Use this for:** Server owners, head admins

---

#### `tcp.admin.create`
**Description:** Register and delete chambers
**Default:** Operators only
**Allows:**
- `/tcp add <name>` - Register existing chambers for management
- `/tcp delete <chamber>` - Delete chambers
- `/tcp setexit <chamber>` - Set exit locations

**Use this for:** Staff who manage chamber infrastructure

---

#### `tcp.admin.scan`
**Description:** Scan chambers for vaults and spawners
**Default:** Operators only
**Allows:**
- `/tcp scan <chamber>` - Detect vaults/spawners

**Use this for:** Staff who set up chambers (usually paired with `tcp.admin.create`)

---

#### `tcp.admin.snapshot`
**Description:** Manage chamber snapshots
**Default:** Operators only
**Allows:**
- `/tcp snapshot create <chamber>` - Create snapshots
- `/tcp snapshot restore <chamber>` - Restore from snapshots

**Use this for:** Staff who maintain chambers and handle resets

---

#### `tcp.admin.reset`
**Description:** Force chamber resets
**Default:** Operators only
**Allows:**
- `/tcp reset <chamber>` - Immediately reset a chamber

**Use this for:** Moderators who manage events and handle player issues

---

#### `tcp.admin.key`
**Description:** Manage trial keys
**Default:** Operators only
**Allows:**
- `/tcp key give <player> <amount> [type]` - Give keys to players
- `/tcp key check <player>` - Check player's key count

**Use this for:** Staff who run events or compensate players

---

#### `tcp.admin.vault`
**Description:** Manage vault cooldowns
**Default:** Operators only
**Allows:**
- `/tcp vault reset <chamber> <player> [type]` - Reset vault cooldowns

**Use this for:** Staff who handle player support and bug compensation

---

#### `tcp.admin.reload`
**Description:** Reload plugin configuration
**Default:** Operators only
**Allows:**
- `/tcp reload` - Reload config files

**Use this for:** Admins who tune configuration

---

#### `tcp.admin.stats`
**Description:** View other players' statistics
**Default:** Operators only
**Allows:**
- `/tcp stats <player>` - View any player's stats

**Use this for:** Staff who monitor player activity

---

#### `tcp.admin`
**Description:** View chamber information
**Default:** Operators only
**Allows:**
- `/tcp list` - List all chambers
- `/tcp info <chamber>` - View chamber details

**Use this for:** Read-only access to chamber data

{% hint style="info" %}
**Note:** `tcp.admin` is NOT a wildcard. Use `tcp.admin.*` for all admin permissions.
{% endhint %}

---

### Player Permissions

#### `tcp.stats`
**Description:** View own statistics
**Default:** **All players** (true)
**Allows:**
- `/tcp stats` - View your own stats

**Typical usage:** Default permission for all players

---

#### `tcp.leaderboard`
**Description:** View leaderboards
**Default:** **All players** (true)
**Allows:**
- `/tcp leaderboard <type>` - View top players
- `/tcp lb <type>` - Alias
- `/tcp top <type>` - Alias

**Typical usage:** Default permission for all players

---

### Bypass Permissions

#### `tcp.bypass.cooldown`
**Description:** Bypass vault cooldowns
**Default:** Operators only
**Effect:**
- Open vaults regardless of personal cooldown
- No waiting period between loot

**Use this for:**
- VIP ranks (be careful with balance!)
- Staff testing chambers
- Special event participants

{% hint style="warning" %}
**Balance Warning:** Giving this to regular players removes cooldown mechanics entirely. Consider carefully!
{% endhint %}

---

#### `tcp.bypass.protection`
**Description:** Bypass chamber protection
**Default:** Operators only
**Effect:**
- Break blocks in protected chambers
- Place blocks in protected chambers
- Access protected containers

**Use this for:**
- Staff building/modifying chambers
- WorldEdit operations inside chambers

{% hint style="danger" %}
**DO NOT give to regular players!** This allows complete modification of protected chambers.
{% endhint %}

---

## 🎭 Permission Groups Examples

### Example 1: Player Rank
```yaml
group: default
permissions:
  - tcp.stats
  - tcp.leaderboard
```

Players can view stats and compete on leaderboards. This is the default!

---

### Example 2: VIP Rank
```yaml
group: vip
permissions:
  - tcp.stats
  - tcp.leaderboard
  - tcp.bypass.cooldown  # Optional: No vault cooldowns
```

VIPs get instant vault access (no cooldowns). **Balance carefully!**

---

### Example 3: Helper/Moderator Rank
```yaml
group: helper
permissions:
  - tcp.admin
  - tcp.admin.stats
  - tcp.admin.key
  - tcp.admin.vault
  - tcp.admin.reset
```

Helpers can:
- View chamber info
- View player stats
- Give keys to players
- Reset vault cooldowns (for bug compensation)
- Force chamber resets

**Cannot:**
- Create/delete chambers
- Modify configuration
- Create snapshots

---

### Example 4: Builder Rank
```yaml
group: builder
permissions:
  - tcp.admin.create
  - tcp.admin.scan
  - tcp.admin.snapshot
  - tcp.admin.reset
  - tcp.bypass.protection
```

Builders can:
- Register and delete chambers
- Scan for vaults/spawners
- Create/restore snapshots
- Build inside protected chambers

**Cannot:**
- Manage keys or vaults
- Reload configuration

---

### Example 5: Admin Rank
```yaml
group: admin
permissions:
  - tcp.admin.*
```

Full access to everything. Simple!

---

## 🔧 LuckPerms Examples

### Grant Full Admin Access
```
/lp user Steve permission set tcp.admin.*
```

### Grant Moderator Access
```
/lp user Alex permission set tcp.admin.reset
/lp user Alex permission set tcp.admin.key
/lp user Alex permission set tcp.admin.vault
/lp user Alex permission set tcp.admin.stats
```

### Create a VIP Group (No Cooldowns)
```
/lp creategroup vip
/lp group vip permission set tcp.bypass.cooldown true
/lp user Bob parent add vip
```

### Create a Builder Group
```
/lp creategroup builder
/lp group builder permission set tcp.admin.create
/lp group builder permission set tcp.admin.scan
/lp group builder permission set tcp.admin.snapshot
/lp group builder permission set tcp.bypass.protection
/lp user Charlie parent add builder
```

### Grant Temporary Admin Access (24 hours)
```
/lp user Diana permission settemp tcp.admin.* true 24h
```

---

## 🌍 Per-World Permissions

Want staff to manage chambers only in certain worlds?

### LuckPerms Example
```
/lp user Steve permission set tcp.admin.create true world=world_nether
/lp user Steve permission set tcp.admin.scan true world=world_nether
```

Steve can only create/scan chambers in the nether.

---

## 🎯 Permission Hierarchy

```
tcp.admin.*
  ├─ tcp.admin.create      (Register, delete, set exit)
  ├─ tcp.admin.scan        (Scan chambers)
  ├─ tcp.admin.snapshot    (Manage snapshots)
  ├─ tcp.admin.reset       (Force resets)
  ├─ tcp.admin.key         (Manage keys)
  ├─ tcp.admin.vault       (Manage vaults)
  ├─ tcp.admin.reload      (Reload config)
  └─ tcp.admin.stats       (View others' stats)

tcp.admin                  (View chambers - NOT a wildcard!)

tcp.stats                  (View own stats)
tcp.leaderboard            (View leaderboards)

tcp.bypass.cooldown        (No vault cooldowns)
tcp.bypass.protection      (Build in protected chambers)
```

---

## 💡 Common Setups

### Survival Server
```yaml
default:
  - tcp.stats
  - tcp.leaderboard

vip:
  - tcp.stats
  - tcp.leaderboard
  # No bypass perms for balance

moderator:
  - tcp.admin.reset
  - tcp.admin.key
  - tcp.admin.vault
  - tcp.admin.stats

admin:
  - tcp.admin.*
```

**Philosophy:** Keep VIPs balanced, give mods tools to help players, admins control everything.

---

### Creative/Building Server
```yaml
default:
  - tcp.stats
  - tcp.leaderboard

builder:
  - tcp.admin.create
  - tcp.admin.scan
  - tcp.admin.snapshot
  - tcp.bypass.protection

admin:
  - tcp.admin.*
```

**Philosophy:** Let builders register chambers freely, admins handle config/keys.

---

### Event/Minigame Server
```yaml
default:
  - tcp.stats
  - tcp.leaderboard

event-participant:
  - tcp.bypass.cooldown  # Fast loot for events

event-host:
  - tcp.admin.reset
  - tcp.admin.key
  - tcp.admin.vault

admin:
  - tcp.admin.*
```

**Philosophy:** Event participants get faster loot, hosts can reset chambers and give rewards.

---

## ❓ FAQ

**"Do I need `tcp.admin` for `tcp.admin.create`?"**
No! Specific permissions (like `tcp.admin.create`) work independently. `tcp.admin` only grants access to `/tcp list` and `/tcp info`.

**"What's the difference between `tcp.admin` and `tcp.admin.*`?"**
- `tcp.admin` = View chambers (`/tcp list`, `/tcp info`)
- `tcp.admin.*` = ALL admin permissions (wildcard)

**"Can I use negative permissions to remove specific access?"**
Yes! With LuckPerms or similar plugins:
```
/lp user Steve permission set tcp.admin.* true
/lp user Steve permission set tcp.admin.reload false
```
Steve gets all admin perms EXCEPT reload.

**"Does `tcp.bypass.cooldown` work retroactively?"**
Yes! If a player has this permission, they can open vaults immediately regardless of existing cooldowns.

**"Can I give permissions temporarily?"**
Yes, with LuckPerms:
```
/lp user Steve permission settemp tcp.admin.* true 1d
```

**"Do these permissions work with permission inheritance?"**
Yes! If a group has `tcp.admin.*`, all members inherit all child permissions automatically.

---

## 🔗 Related Pages

{% content-ref url="commands.md" %}
[commands.md](commands.md)
{% endcontent-ref %}

See which commands require which permissions.

{% content-ref url="../configuration/config.yml.md" %}
[config.yml.md](../configuration/config.yml.md)
{% endcontent-ref %}

Protection settings and their interaction with bypass permissions.

---

## 🛡️ Security Best Practices

{% hint style="success" %}
**Use the least privilege principle:** Only grant permissions users actually need. Don't give `tcp.admin.*` to everyone.
{% endhint %}

{% hint style="warning" %}
**Be careful with bypass permissions:** `tcp.bypass.cooldown` removes progression. `tcp.bypass.protection` allows chamber destruction.
{% endhint %}

{% hint style="info" %}
**Separate builder and admin permissions:** Builders need `tcp.admin.create` + `tcp.bypass.protection`. They don't need `tcp.admin.reload` or key management.
{% endhint %}

{% hint style="danger" %}
**Never give regular players `tcp.bypass.protection`** unless you want them modifying chambers freely!
{% endhint %}

---

## 🎉 Quick Permission Cheat Sheet

| Role | Permissions | Why |
|------|-------------|-----|
| **Player** | `tcp.stats`, `tcp.leaderboard` | View stats, compete |
| **VIP** | Same + `tcp.bypass.cooldown` | Faster loot (optional) |
| **Helper** | `tcp.admin.key`, `tcp.admin.vault`, `tcp.admin.reset` | Help players, manage events |
| **Mod** | Helper + `tcp.admin.stats` | Monitor players |
| **Builder** | `tcp.admin.create`, `tcp.admin.scan`, `tcp.admin.snapshot`, `tcp.bypass.protection` | Register chambers |
| **Admin** | `tcp.admin.*` | Full control |

Use this as a starting point and adjust to your server's needs!
