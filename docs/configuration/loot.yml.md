# 🎲 loot.yml Reference

Time to get creative! The `loot.yml` file is where you customize what players get from vaults. Want to rain diamonds? Give economy rewards? Drop custom items from other plugins? This is your playground.

{% hint style="info" %}
**Location:** `plugins/TrialChamberPro/loot.yml`

After making changes, reload with `/tcp reload`
{% endhint %}

---

## 🎯 Understanding Loot Tables

A **loot table** is a collection of possible rewards. When a player opens a vault, the plugin:

1. Rolls between `min-rolls` and `max-rolls` times
2. Each roll picks ONE item from `weighted-items` based on weight
3. Adds all `guaranteed-items` (these ALWAYS drop)
4. Gives everything to the player

Think of it like rolling dice—higher weight = bigger section of the die.

---

## 📦 Loot Table Structure

```yaml
loot-tables:
  default:  # Normal vaults use this
    min-rolls: 3
    max-rolls: 5
    guaranteed-items: []
    weighted-items:
      - type: DIAMOND
        amount-min: 1
        amount-max: 3
        weight: 10.0
```

### `min-rolls` / `max-rolls`

How many times to randomly pick from `weighted-items`. A random number between min and max is chosen.

**Examples:**
- `min-rolls: 3` + `max-rolls: 5` = Player gets 3-5 random items
- `min-rolls: 1` + `max-rolls: 1` = Player gets exactly 1 item (hardcore mode!)
- `min-rolls: 10` + `max-rolls: 15` = Loot explosion

{% hint style="warning" %}
**Don't go crazy!** More rolls = more items. A vault that drops 20 items might be fun once, but it trivializes progression. Start conservative.
{% endhint %}

---

## ✨ Item Types

### Basic Items

```yaml
- type: DIAMOND
  amount-min: 1
  amount-max: 3
  weight: 10.0
```

**Required fields:**
- `type`: Material name (see [Spigot Material enum](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Material.html))
- `weight`: Probability weight (explained below)

**Optional fields:**
- `amount-min` / `amount-max`: Stack size range (default: 1)

### Items with Custom Names & Lore

```yaml
- type: DIAMOND
  amount-min: 1
  amount-max: 3
  weight: 10.0
  name: "&b&lTrial Diamond"
  lore:
    - "&7Earned from conquering"
    - "&7the Trial Chamber"
    - ""
    - "&6&lRare Drop"
```

Color codes supported! Use `&` for colors:
- `&0-9, a-f` = Colors
- `&l` = Bold, `&o` = Italic, `&n` = Underline
- `&m` = Strikethrough, `&k` = Magic

### Enchanted Items

```yaml
- type: DIAMOND_SWORD
  amount-min: 1
  amount-max: 1
  weight: 5.0
  name: "&c&lTrial Blade"
  enchantments:
    - "SHARPNESS:5"
    - "UNBREAKING:3"
    - "FIRE_ASPECT:2"
```

Enchantments use the format `ENCHANTMENT_NAME:LEVEL`.

See [Spigot Enchantment enum](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/enchantments/Enchantment.html) for names.

### Enchanted Books

```yaml
- type: ENCHANTED_BOOK
  amount-min: 1
  amount-max: 1
  weight: 8.0
  enchantments:
    - "MENDING:1"
```

Pro tip: Give ONLY enchanted books for specific enchants, not enchanted gear. Let players choose what to apply it to!

---

## ⚖️ Understanding Weights

**Weight determines probability.** Higher weight = more likely to be picked.

### Example Breakdown

```yaml
weighted-items:
  - type: DIAMOND          # weight: 10
  - type: EMERALD          # weight: 20
  - type: IRON_INGOT       # weight: 15
  - type: COAL             # weight: 55
```

**Total weight:** 10 + 20 + 15 + 55 = 100

**Probabilities:**
- Diamond: 10/100 = **10%** chance per roll
- Emerald: 20/100 = **20%** chance per roll
- Iron: 15/100 = **15%** chance per roll
- Coal: 55/100 = **55%** chance per roll

{% hint style="info" %}
**Weights don't need to add to 100!** That's just for easy mental math. They're all relative to each other.
{% endhint %}

### Weight Strategies

**Common items:** High weight (20-50)
```yaml
- type: IRON_INGOT
  weight: 30.0
```

**Uncommon items:** Medium weight (5-20)
```yaml
- type: DIAMOND
  weight: 10.0
```

**Rare items:** Low weight (1-5)
```yaml
- type: NETHERITE_INGOT
  weight: 2.0
```

**Super rare items:** Very low weight (0.1-1)
```yaml
- type: ENCHANTED_GOLDEN_APPLE
  weight: 0.5
```

---

## 🎁 Guaranteed Items

Items in `guaranteed-items` ALWAYS drop, regardless of rolls or weight.

```yaml
loot-tables:
  daily-bonus:
    min-rolls: 2
    max-rolls: 4
    guaranteed-items:
      - type: GOLDEN_APPLE
        amount-min: 1
        amount-max: 1
        name: "&6Daily Bonus Apple"
    weighted-items:
      - type: DIAMOND
        weight: 10.0
```

Every player gets 1 Golden Apple + 2-4 random items from weighted-items.

**Use cases:**
- Participation rewards
- Event tokens
- Key fragments (for progression systems)

---

## 🌑 Ominous Vault Loot

The `ominous-default` table is for ominous vaults. Make it WAY better than normal vaults!

```yaml
loot-tables:
  ominous-default:
    min-rolls: 5
    max-rolls: 8
    weighted-items:
      - type: ENCHANTED_GOLDEN_APPLE
        amount-min: 1
        amount-max: 1
        weight: 25.0
        name: "&6&lOminous Reward"

      - type: HEAVY_CORE
        amount-min: 1
        amount-max: 1
        weight: 8.3
        name: "&5&lHeavy Core"
        lore:
          - "&7Combine with Breeze Rod"
          - "&7to create the Mace!"

      - type: NETHERITE_INGOT
        amount-min: 1
        amount-max: 2
        weight: 8.0
```

**Design philosophy:**
- More rolls (5-8 vs 3-5)
- Better items (netherite, enchanted golden apples)
- Unique drops (Heavy Core for Mace crafting)
- Higher amounts (2-4 golden apples vs 1)

---

## 💰 Economy Rewards

Got Vault + an economy plugin? Give money as loot!

```yaml
weighted-items:
  - type: COMMAND
    weight: 15.0
    commands:
      - "eco give {player} 1000"
    display-name: "&a$1,000"
    display-material: GOLD_INGOT  # What the player "sees" in inventory
```

**Placeholders:**
- `{player}`: Player's name
- `{uuid}`: Player's UUID

**Multiple commands:**
```yaml
- type: COMMAND
  weight: 5.0
  commands:
    - "eco give {player} 5000"
    - "lp user {player} permission set special.perk true"
    - "give {player} diamond 10"
  display-name: "&6&lJackpot Package"
  display-material: NETHER_STAR
```

All commands run as console. Perfect for bundles of rewards!

---

## 🎨 Custom Plugin Items

### ItemsAdder, Oraxen, MMOItems, etc.

```yaml
weighted-items:
  - type: CUSTOM_ITEM
    plugin: ItemsAdder
    item-id: "trial_chamber:legendary_sword"
    weight: 2.0

  - type: CUSTOM_ITEM
    plugin: Oraxen
    item-id: "mythic_helmet"
    weight: 3.0
```

TrialChamberPro detects which custom item plugin you're using and fetches the item automatically.

**Supported plugins:**
- ItemsAdder
- Oraxen
- MMOItems
- ExecutableItems
- Nova

{% hint style="warning" %}
**Make sure the item IDs are correct!** If the plugin can't find the item, it'll drop nothing. Test your loot tables after adding custom items.
{% endhint %}

---

## 🎯 Example Loot Tables

### Beginner-Friendly Server

```yaml
loot-tables:
  default:
    min-rolls: 4
    max-rolls: 6
    guaranteed-items:
      - type: GOLDEN_APPLE
        amount-min: 1
        amount-max: 1
    weighted-items:
      - type: IRON_INGOT
        amount-min: 5
        amount-max: 10
        weight: 30.0

      - type: GOLD_INGOT
        amount-min: 3
        amount-max: 8
        weight: 25.0

      - type: DIAMOND
        amount-min: 1
        amount-max: 3
        weight: 15.0

      - type: EMERALD
        amount-min: 2
        amount-max: 5
        weight: 20.0

      - type: ENCHANTED_BOOK
        amount-min: 1
        amount-max: 1
        weight: 10.0
        enchantments:
          - "EFFICIENCY:4"
```

Generous amounts, guaranteed golden apple, decent diamond rate. Great for keeping new players engaged!

---

### Hardcore/Competitive Server

```yaml
loot-tables:
  default:
    min-rolls: 1
    max-rolls: 2
    guaranteed-items: []
    weighted-items:
      - type: IRON_INGOT
        amount-min: 1
        amount-max: 3
        weight: 50.0

      - type: GOLD_INGOT
        amount-min: 1
        amount-max: 2
        weight: 30.0

      - type: DIAMOND
        amount-min: 1
        amount-max: 1
        weight: 5.0

      - type: EMERALD
        amount-min: 1
        amount-max: 2
        weight: 10.0

      - type: ENCHANTED_BOOK
        amount-min: 1
        amount-max: 1
        weight: 5.0
        enchantments:
          - "SHARPNESS:3"
```

Stingy! Only 1-2 items, low amounts, rare diamonds. Makes every vault opening feel earned.

---

### Economy-Focused Server

```yaml
loot-tables:
  default:
    min-rolls: 3
    max-rolls: 5
    weighted-items:
      # Small payout - common
      - type: COMMAND
        weight: 40.0
        commands:
          - "eco give {player} 500"
        display-name: "&a$500"
        display-material: GOLD_NUGGET

      # Medium payout - uncommon
      - type: COMMAND
        weight: 20.0
        commands:
          - "eco give {player} 2000"
        display-name: "&a$2,000"
        display-material: GOLD_INGOT

      # Large payout - rare
      - type: COMMAND
        weight: 5.0
        commands:
          - "eco give {player} 10000"
        display-name: "&6$10,000"
        display-material: GOLD_BLOCK

      # Still drop some physical items
      - type: DIAMOND
        amount-min: 1
        amount-max: 2
        weight: 10.0

      - type: ENCHANTED_BOOK
        amount-min: 1
        amount-max: 1
        weight: 5.0
        enchantments:
          - "MENDING:1"
```

Perfect for servers where money is the primary progression system.

---

### Custom Items + Vanilla Mix

```yaml
loot-tables:
  default:
    min-rolls: 3
    max-rolls: 5
    weighted-items:
      # Custom items from ItemsAdder
      - type: CUSTOM_ITEM
        plugin: ItemsAdder
        item-id: "trial_chamber:trial_token"
        weight: 25.0

      - type: CUSTOM_ITEM
        plugin: ItemsAdder
        item-id: "trial_chamber:rare_gem"
        weight: 10.0

      # Vanilla items as filler
      - type: DIAMOND
        amount-min: 1
        amount-max: 3
        weight: 15.0

      - type: EMERALD_BLOCK
        amount-min: 1
        amount-max: 1
        weight: 8.0

      # Commands for bonus perks
      - type: COMMAND
        weight: 5.0
        commands:
          - "lp user {player} permission set chamber.bonus true 3d"
        display-name: "&d3-Day Bonus Perk"
        display-material: NETHER_STAR
```

Best of all worlds—custom items, vanilla loot, and special perks.

---

## 🔧 Per-Chamber Loot Tables

Want different chambers to drop different loot? You can create multiple tables!

```yaml
loot-tables:
  # Main chamber - standard loot
  default:
    min-rolls: 3
    max-rolls: 5
    weighted-items:
      - type: DIAMOND
        weight: 10.0

  # Nether-themed chamber - fire-focused loot
  nether-chamber:
    min-rolls: 4
    max-rolls: 6
    weighted-items:
      - type: BLAZE_ROD
        amount-min: 3
        amount-max: 6
        weight: 25.0

      - type: FIRE_CHARGE
        amount-min: 5
        amount-max: 10
        weight: 30.0

      - type: NETHERITE_SCRAP
        amount-min: 1
        amount-max: 2
        weight: 10.0

      - type: ENCHANTED_BOOK
        weight: 15.0
        enchantments:
          - "FIRE_PROTECTION:4"

  # Ocean-themed chamber - water-focused loot
  ocean-chamber:
    min-rolls: 3
    max-rolls: 5
    weighted-items:
      - type: PRISMARINE_CRYSTALS
        amount-min: 5
        amount-max: 15
        weight: 30.0

      - type: HEART_OF_THE_SEA
        amount-min: 1
        amount-max: 1
        weight: 5.0

      - type: TRIDENT
        weight: 3.0
        enchantments:
          - "RIPTIDE:3"
```

Then use `/tcp setloot <chamber> <table>` to assign the table to a specific chamber!

```
/tcp setloot NetherChamber nether-chamber
/tcp setloot OceanChamber ocean-chamber
```

---

## 💡 Design Tips

### Balance Philosophy

**Don't make chambers replace mining/gameplay**
Chambers should supplement progression, not replace it. If vaults drop 64 diamonds, why would players mine?

**Reward effort appropriately**
Chambers require keys and combat. Make rewards better than what players could get from 5 minutes of mining, but not game-breaking.

**Progression over time**
Start conservative. It's easier to buff loot after launch than nerf it (players hate nerfs).

### Weight Tuning

**Test your tables!** Open 20-30 vaults and see if the loot feels right. Use `/tcp reset` to quickly test.

**Check the math:**
```
Common items: 40-60% chance
Uncommon items: 15-30% chance
Rare items: 5-15% chance
Super rare items: <5% chance
```

**Adjust based on rolls:**
- More rolls = lower individual weights
- Fewer rolls = higher individual weights

---

## 🎲 Advanced: Conditional Loot (Future Feature)

In future versions, you'll be able to add conditions:

```yaml
# Example of planned future feature
weighted-items:
  - type: DIAMOND
    weight: 10.0
    conditions:
      - "permission:vip.bonus"  # Only VIPs get this
      - "world:world_nether"    # Only in nether world
```

Not available yet, but coming soon!

---

## 🔄 Testing Your Loot

### Quick Test Cycle

1. Edit `loot.yml`
2. Run `/tcp reload`
3. Run `/tcp reset TestChamber` (forces instant reset)
4. Open vaults
5. Check if loot feels right
6. Repeat!

### Statistical Testing

Open 50 vaults, record what you get, calculate actual drop rates. Does it match your intended design?

**Example:**
- Wanted diamonds at 10% per roll
- Got diamonds in 8/50 vaults with 3 rolls each = 8/150 rolls = 5.3%
- Weights might be off! Double-check total weight calculations

---

## ❓ Common Questions

**"Can I use NBT data for custom items?"**
Not directly. Use custom item plugins (ItemsAdder, Oraxen) which handle NBT internally.

**"Can different players get different loot from the same vault?"**
Yes! With `per-player-loot: true` in config.yml, each player has their own loot roll.

**"What happens if I typo a material name?"**
The plugin logs an error and skips that item. Check console after reloading.

**"Can I make loot tables that call other loot tables?"**
Not currently, but you can work around it with multiple chambers assigned different tables.

**"How do I remove an item from vanilla loot?"**
You can't remove vanilla loot directly—TrialChamberPro *replaces* vault loot entirely. Just don't include unwanted items in your weighted-items!

---

## 🎯 What's Next?

Now that you've mastered loot configuration, check out:

{% content-ref url="messages.yml.md" %}
[messages.yml.md](messages.yml.md)
{% endcontent-ref %}

Customize all player-facing messages to match your server's style.

{% content-ref url="../guides/custom-loot.md" %}
[custom-loot.md](../guides/custom-loot.md)
{% endcontent-ref %}

Hands-on guide to creating themed loot tables with examples.

---

## 💎 Pro Tips

{% hint style="success" %}
**Create loot tiers:** Make tables named `tier1`, `tier2`, `tier3` with progressively better loot. Assign them to chambers in different regions of your world!
{% endhint %}

{% hint style="info" %}
**Seasonal events:** Copy your `loot.yml`, modify it for the event (Halloween, Christmas), swap it in, reload. Easy seasonal loot!
{% endhint %}

{% hint style="warning" %}
**Backup before experimenting!** Copy `loot.yml` before making drastic changes. Easy to restore if you mess up the YAML formatting.
{% endhint %}

Happy looting! ⚔️
