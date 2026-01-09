# 🎲 loot.yml Reference

Time to get creative! The `loot.yml` file is where you customize what players get from vaults. Want to rain diamonds? Give economy rewards? Drop custom items from other plugins? This is your playground.

{% hint style="info" %}
**Location:** `plugins/TrialChamberPro/loot.yml`

After making changes, reload with `/tcp reload`
{% endhint %}

{% hint style="danger" %}
**CRITICAL: Never use TAB characters in loot.yml!**

TAB characters will cause the entire YAML file to fail to load silently. This results in **all loot tables being empty**, causing vaults to give no loot even though keys are consumed (fixed in v1.2.19+).

**Symptoms of TAB character issues:**
- Console shows: `Loot table not found: default (available: )`
- Keys are consumed but no loot is given
- No YAML parsing errors are shown

**How to check:**
1. Open `loot.yml` in a text editor that shows whitespace
2. Look for `→` (TAB) characters instead of spaces
3. Replace all TABs with spaces (2 or 4 spaces per indentation level)

**Prevention:**
- Configure your text editor to use "spaces for tabs"
- Use editors like VS Code, Notepad++, or Sublime Text that can show whitespace
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

## 🎱 Multi-Pool Loot System (NEW!)

**Like vanilla Trial Chambers**, you can now create multiple loot pools that each roll independently! This gives you much finer control over loot distribution.

### Why Use Multiple Pools?

Vanilla Minecraft vaults use **3 separate pools**:
- **Common pool**: Always gives 2-3 basic items (iron, gold, arrows)
- **Rare pool**: Gives 1-2 valuable items (diamonds, enchanted books)
- **Unique pool**: 0-1 chance at special items (enchanted golden apples, heavy core)

**Benefits:**
- ✅ **More predictable loot** - Players always get common items + chance at rare/unique
- ✅ **Better progression** - Separate rare items from common items
- ✅ **Matches vanilla** - Feels like the real Trial Chambers
- ✅ **Flexible design** - Can have pools with `min-rolls: 0` for bonus items

### Multi-Pool Example

```yaml
loot-tables:
  vanilla-style:
    pools:
      # Common pool - always gives 2-3 basic items
      - name: common
        min-rolls: 2
        max-rolls: 3
        weighted-items:
          - type: IRON_INGOT
            amount-min: 3
            amount-max: 7
            weight: 30.0
          - type: GOLD_INGOT
            amount-min: 2
            amount-max: 5
            weight: 25.0
          - type: ARROW
            amount-min: 16
            amount-max: 32
            weight: 20.0

      # Rare pool - gives 1-2 valuable items
      - name: rare
        min-rolls: 1
        max-rolls: 2
        weighted-items:
          - type: DIAMOND
            amount-min: 1
            amount-max: 3
            weight: 15.0
          - type: EMERALD
            amount-min: 3
            amount-max: 8
            weight: 20.0
          - type: GOLDEN_APPLE
            amount-min: 1
            amount-max: 1
            weight: 12.0

      # Unique pool - rare chance at 0-1 special item
      - name: unique
        min-rolls: 0
        max-rolls: 1
        weighted-items:
          - type: ENCHANTED_GOLDEN_APPLE
            amount-min: 1
            amount-max: 1
            weight: 3.0
          - type: NETHERITE_INGOT
            amount-min: 1
            amount-max: 1
            weight: 2.0
```

**How it works:**
1. Player opens vault
2. Common pool rolls 2-3 times → always get basic items
3. Rare pool rolls 1-2 times → get valuable items
4. Unique pool rolls 0-1 times → **might** get special item
5. Total: 3-6 items with good variety!

{% hint style="info" %}
**Legacy Format Still Works!** The old single-pool format (without `pools:`) is fully supported and will keep working. This is purely optional!
{% endhint %}

{% hint style="warning" %}
**GUI Limitation:** The loot editor GUI currently only supports the legacy single-pool format. To use multi-pool, you must edit `loot.yml` directly and reload with `/tcp reload`.
{% endhint %}

### Config Option

Control the maximum number of pools in `config.yml`:

```yaml
loot:
  max-pools-per-table: 5  # Default: 5
```

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

## 🔮 Advanced Loot Features (NEW!)

**Version 1.1.9+** brings vanilla-style loot customization with full Minecraft 1.21+ support! Create dynamic, randomized loot just like vanilla Trial Chambers.

### 🏹 Tipped Arrows

Add potion effects to arrows with custom amplifier levels!

```yaml
- type: TIPPED_ARROW
  amount-min: 8
  amount-max: 16
  weight: 15.0
  potion-type: POISON    # Any PotionType
  potion-level: 1        # 0 = Level I, 1 = Level II, etc.
  name: "&aPoison Arrows"
```

**Available potion types:**
`SPEED`, `SLOWNESS`, `STRENGTH`, `INSTANT_HEAL`, `INSTANT_DAMAGE`, `JUMP_BOOST`, `REGENERATION`, `RESISTANCE`, `FIRE_RESISTANCE`, `WATER_BREATHING`, `INVISIBILITY`, `NIGHT_VISION`, `WEAKNESS`, `POISON`, `WITHER`, `TURTLE_MASTER`, `SLOW_FALLING`

**Examples:**
```yaml
# Poison II arrows (great for combat)
- type: TIPPED_ARROW
  amount-min: 12
  amount-max: 24
  weight: 15.0
  potion-type: POISON
  potion-level: 1

# Slowness IV arrows (for PvP)
- type: TIPPED_ARROW
  amount-min: 8
  amount-max: 16
  weight: 10.0
  potion-type: SLOWNESS
  potion-level: 3

# Long-lasting poison arrows with custom duration
- type: TIPPED_ARROW
  amount-min: 4
  amount-max: 8
  weight: 12.0
  potion-type: POISON
  potion-level: 1
  effect-duration: 1200    # 60 seconds (1200 ticks). Default: 400 ticks (20 seconds)
  name: "&2Long-Lasting Poison Arrow"
```

**Custom Effect Duration:**

The `effect-duration` field lets you override how long potion effects last. When **not specified**, durations are **automatically calculated** from the potion type using vanilla Minecraft multipliers:

| Item Type | Auto-Calculated Duration | Multiplier |
|-----------|-------------------------|-----------|
| POTION | Base duration from potion type | 1.0× (100%) |
| SPLASH_POTION | 75% of base potion | 0.75× |
| LINGERING_POTION | 25% of base potion | 0.25× |
| TIPPED_ARROW | **1/8 of base potion** | 0.125× (12.5%) |

**Examples with auto-calculation:**
- **Speed I Potion** (3:00 base) → **Tipped Arrow** = 22.5s (3:00 ÷ 8)
- **Slowness I Potion** (1:30 base) → **Tipped Arrow** = 11.25s (1:30 ÷ 8)
- **Regeneration I Potion** (0:45 base) → **Tipped Arrow** = 5.625s (0:45 ÷ 8)
- **Poison I Potion** (0:45 base) → **Tipped Arrow** = 5.625s (0:45 ÷ 8)

**Manual override:** Specify `effect-duration: <ticks>` to use a custom duration instead of auto-calculation (20 ticks = 1 second)

### 🧪 Potions with Custom Levels

Create potions with any effect level—perfect for ominous vault rewards!

```yaml
- type: POTION
  amount-min: 1
  amount-max: 2
  weight: 12.0
  potion-type: STRENGTH
  potion-level: 1        # Strength II
  name: "&cStrength Potion II"
```

**Works with:**
- `POTION` - Drinkable potions (3 minute duration)
- `SPLASH_POTION` - Throwable (2:15 duration)
- `LINGERING_POTION` - Creates cloud (45 second cloud)

**Examples:**
```yaml
# Healing II splash potion
- type: SPLASH_POTION
  amount-min: 2
  amount-max: 4
  weight: 10.0
  potion-type: HEALING
  potion-level: 1

# Regeneration III lingering potion
- type: LINGERING_POTION
  amount-min: 1
  amount-max: 2
  weight: 8.0
  potion-type: REGENERATION
  potion-level: 2
```

### 🌑 Ominous Potions (1.21+ Exclusive)

**NEW IN 1.21!** Ominous potions are special bottles with extreme effect levels—only available from Trial Chambers in vanilla!

```yaml
- type: POTION
  amount-min: 1
  amount-max: 1
  weight: 2.0
  potion-type: STRENGTH
  potion-level: 3          # Strength IV!
  ominous-potion: true     # Makes it an ominous bottle
  name: "&5&lOminous Strength"
  lore:
    - "&7A powerful ominous concoction"
    - "&7Only found in Trial Chambers"
```

**Perfect for ominous vault loot!** These are the rarest potions in Minecraft.

**Popular ominous potions:**
```yaml
# Ominous Strength IV (vanilla: only from ominous vaults)
- type: POTION
  potion-type: STRENGTH
  potion-level: 3
  ominous-potion: true
  weight: 3.0

# Ominous Regeneration IV
- type: POTION
  potion-type: REGENERATION
  potion-level: 3
  ominous-potion: true
  weight: 2.0

# Ominous Speed IV
- type: POTION
  potion-type: SPEED
  potion-level: 3
  ominous-potion: true
  weight: 2.5
```

{% hint style="info" %}
**Ominous Potion Note:** The `ominous-potion: true` flag is cosmetic in most 1.21 implementations. The real power is the high `potion-level` (3+ = Level IV+)!
{% endhint %}

### 🔮 Ominous Bottles (Bad Omen Effect)

**NEW IN 1.21!** Ominous Bottles are special potions that give the **Bad Omen** effect, which triggers **Ominous Trials** when entering a Trial Chamber. In vanilla Minecraft, these bottles are only found in ominous vaults and come in levels **III-V** (never I or II).

#### What Makes Them Special?

- Give **Bad Omen** effect (not available as a regular PotionType)
- Found exclusively in ominous vaults
- Randomly determined level: **III, IV, or V** only
- Used to trigger Ominous Trials for better loot

#### Creating Ominous Bottles

Use the `custom-effect-type` field to specify custom potion effects like `BAD_OMEN`:

```yaml
# Ominous Bottle III (most common)
- type: POTION
  amount-min: 1
  amount-max: 1
  weight: 8.0
  custom-effect-type: BAD_OMEN  # Use custom effect type, not potion-type
  potion-level: 2               # 0=I, 1=II, 2=III, 3=IV, 4=V
  name: "&5&lOminous Bottle III"
  lore:
    - "&7Drink to receive Bad Omen III"
    - "&7Triggers an Ominous Trial"

# Ominous Bottle IV (less common)
- type: POTION
  amount-min: 1
  amount-max: 1
  weight: 5.0
  custom-effect-type: BAD_OMEN
  potion-level: 3               # Bad Omen IV
  name: "&5&lOminous Bottle IV"
  lore:
    - "&7Drink to receive Bad Omen IV"
    - "&7A more challenging Ominous Trial!"

# Ominous Bottle V (very rare!)
- type: POTION
  amount-min: 1
  amount-max: 1
  weight: 2.0
  custom-effect-type: BAD_OMEN
  potion-level: 4               # Bad Omen V (maximum)
  effect-duration: 240000       # Optional: 200 minutes (default is 100 minutes)
  name: "&5&lOminous Bottle V"
  lore:
    - "&7Drink to receive Bad Omen V"
    - "&7The ultimate Ominous Trial challenge!"
```

#### Key Differences: `custom-effect-type` vs `potion-type`

| Field | Purpose | Examples |
|-------|---------|----------|
| `potion-type` | Standard Minecraft potion effects | `SPEED`, `STRENGTH`, `POISON`, `HEALING` |
| `custom-effect-type` | Special/custom potion effect types | `BAD_OMEN`, `HERO_OF_THE_VILLAGE`, `GLOWING`, `LUCK` |

**When to use which:**
- **Use `potion-type`** for normal potions (Strength, Speed, Healing, etc.)
- **Use `custom-effect-type`** for special effects not available as PotionType (Bad Omen, Hero of the Village, etc.)

**Note:** The `effect-duration` field works with both `potion-type` and `custom-effect-type`! Use it to customize how long any potion effect lasts.

{% hint style="warning" %}
**Important:** In vanilla Minecraft, ominous bottles only come in levels **III, IV, and V**—never I or II. Match vanilla behavior by using `potion-level: 2` (III), `potion-level: 3` (IV), or `potion-level: 4` (V).
{% endhint %}

#### Other Custom Effect Types

You can use `custom-effect-type` for other special effects:

```yaml
# Hero of the Village (from raid victory)
- type: POTION
  custom-effect-type: HERO_OF_THE_VILLAGE
  potion-level: 1  # Hero of the Village II
  weight: 1.0
  name: "&aVillage Hero Potion"

# Luck (affects loot tables)
- type: POTION
  custom-effect-type: LUCK
  potion-level: 2  # Luck III
  weight: 3.0
  name: "&2Fortune's Favor"

# Glowing (outline effect)
- type: SPLASH_POTION
  custom-effect-type: GLOWING
  potion-level: 0  # Glowing I
  weight: 5.0
  name: "&eGlowing Splash Potion"
```

### ✨ Enchantment Randomization

Add dynamic enchantments with random levels—just like vanilla treasure loot!

#### Fixed Level Ranges

Apply enchantments with random levels within a range:

```yaml
- type: DIAMOND_SWORD
  amount-min: 1
  amount-max: 1
  weight: 10.0
  enchantment-ranges:
    - "SHARPNESS:1:5"    # Random Sharpness I to V
    - "LOOTING:1:3"      # Random Looting I to III
  name: "&bRandom Enchanted Sword"
```

**Format:** `ENCHANTMENT:MIN_LEVEL:MAX_LEVEL`

**Examples:**
```yaml
# Pickaxe with random Efficiency and Fortune
- type: DIAMOND_PICKAXE
  amount-min: 1
  amount-max: 1
  weight: 8.0
  enchantment-ranges:
    - "EFFICIENCY:3:5"    # Efficiency III-V
    - "FORTUNE:1:3"       # Fortune I-III

# Bow with random Power level
- type: BOW
  amount-min: 1
  amount-max: 1
  weight: 12.0
  enchantment-ranges:
    - "POWER:3:5"         # Power III-V
    - "UNBREAKING:2:3"    # Unbreaking II-III
```

#### Random Enchantment Pool

Pick **ONE** random enchantment from a pool—great for variety!

```yaml
- type: ENCHANTED_BOOK
  amount-min: 1
  amount-max: 1
  weight: 15.0
  random-enchantment-pool:    # Will pick ONE of these
    - "SHARPNESS:3:5"         # Either Sharpness III-V
    - "PROTECTION:2:4"        # OR Protection II-IV
    - "UNBREAKING:2:3"        # OR Unbreaking II-III
    - "EFFICIENCY:3:5"        # OR Efficiency III-V
    - "MENDING:1:1"           # OR Mending I
```

**Perfect for enchanted books!** Each player gets a different random enchantment.

**Examples:**
```yaml
# Armor with ONE random protection type
- type: DIAMOND_CHESTPLATE
  amount-min: 1
  amount-max: 1
  weight: 5.0
  random-enchantment-pool:
    - "PROTECTION:3:4"
    - "BLAST_PROTECTION:3:4"
    - "PROJECTILE_PROTECTION:3:4"
    - "FIRE_PROTECTION:3:4"

# Book with one random utility enchantment
- type: ENCHANTED_BOOK
  weight: 10.0
  random-enchantment-pool:
    - "MENDING:1:1"
    - "SILK_TOUCH:1:1"
    - "FORTUNE:2:3"
    - "LOOTING:2:3"
```

#### Combining Enchantment Features

You can mix fixed enchantments, ranges, and random pools!

```yaml
- type: DIAMOND_SWORD
  amount-min: 1
  amount-max: 1
  weight: 5.0
  name: "&6&lLegendary Blade"
  lore:
    - "&7Forged in ancient trials"
  enchantments:
    - "UNBREAKING:3"          # ALWAYS Unbreaking III
  enchantment-ranges:
    - "SHARPNESS:4:5"         # PLUS random Sharpness IV-V
  random-enchantment-pool:    # PLUS one random bonus
    - "LOOTING:2:3"
    - "SWEEPING_EDGE:2:3"
    - "FIRE_ASPECT:1:2"
```

### 🔨 Variable Durability

Drop pre-damaged items with random wear—makes loot feel "used" and realistic!

```yaml
- type: DIAMOND_SWORD
  amount-min: 1
  amount-max: 1
  weight: 10.0
  enchantments:
    - "SHARPNESS:4"
  durability-min: 200        # Minimum damage value
  durability-max: 800        # Maximum damage value
  name: "&bUsed Diamond Sword"
  lore:
    - "&7Found in a Trial Chamber"
    - "&7Slightly worn but still powerful!"
```

**How it works:**
- `durability-min` and `durability-max` are **damage values** (higher = more damaged)
- Diamond sword max durability: 1561 (so 200-800 damage = 60-50% durability remaining)
- Perfect for "treasure" items that feel discovered, not crafted

**Examples:**
```yaml
# Heavily worn pickaxe (still useful)
- type: NETHERITE_PICKAXE
  amount-min: 1
  amount-max: 1
  weight: 3.0
  enchantment-ranges:
    - "EFFICIENCY:4:5"
    - "FORTUNE:2:3"
  durability-min: 500
  durability-max: 1500
  name: "&5Veteran's Pickaxe"
  lore:
    - "&7Seen many adventures"

# Lightly damaged armor (great find)
- type: DIAMOND_HELMET
  weight: 8.0
  enchantments:
    - "PROTECTION:3"
  durability-min: 50
  durability-max: 200
  name: "&bScratched Helmet"
```

{% hint style="warning" %}
**Durability values are damage amounts!** Higher values = more damaged. Check the max durability for each material to calibrate your ranges.
{% endhint %}

### 🎨 Combining Everything

You can mix **all** advanced features on a single item!

```yaml
- type: DIAMOND_SWORD
  amount-min: 1
  amount-max: 1
  weight: 2.0
  name: "&5&lUltimate Trial Weapon"
  lore:
    - "&7Found in the deepest chamber"
    - "&7Radiates ancient power"
  enchantments:
    - "UNBREAKING:3"          # Fixed: always Unbreaking III
  enchantment-ranges:
    - "SHARPNESS:4:5"         # Random Sharpness IV-V
  random-enchantment-pool:
    - "LOOTING:2:3"           # One random bonus enchantment
    - "SWEEPING_EDGE:2:3"
    - "FIRE_ASPECT:1:2"
  durability-min: 100         # Pre-damaged (battle-worn)
  durability-max: 300
```

This creates an incredible loot item with:
- Custom name and lore
- Always has Unbreaking III
- Random Sharpness IV or V
- ONE random bonus enchantment (Looting/Sweeping/Fire Aspect)
- Random damage (100-300), making it feel "discovered"

**Perfect for ominous vault jackpots!**

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

**"My loot tables aren't loading / vaults give no loot!"**
Most likely caused by TAB characters in your YAML file. Check the console for `Loot table not found: default (available: )` - if the available list is empty, your entire loot.yml failed to parse. Open the file in an editor that shows whitespace and replace all TABs with spaces. See the warning at the top of this page for more details.

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

## 💰 COMMAND Rewards (Economy & Permissions)

Want to give players **money**, **permissions**, **experience**, or run **any console command** when they open vaults? COMMAND rewards let you do exactly that!

### How It Works

Command rewards run console commands with a **probability** (weight-based). They execute alongside regular item drops.

{% hint style="info" %}
**Key Point:** Command rewards go in a separate `command-rewards` list, **NOT** in `weighted-items`!
{% endhint %}

### Basic Example

```yaml
loot-tables:
  default:
    min-rolls: 3
    max-rolls: 5
    weighted-items:
      - type: DIAMOND
        amount-min: 1
        amount-max: 3
        weight: 10.0

    # Command rewards (separate list!)
    command-rewards:
      - weight: 25.0              # 25% chance
        commands:
          - "eco give {player} 1000"
        display-name: "&6+1000 Coins"

      - weight: 10.0              # 10% chance
        commands:
          - "lp user {player} permission set special.vault.bonus true"
        display-name: "&5Special Permission Unlocked!"
```

When a player opens a vault:
1. They get 3-5 random items (from weighted-items)
2. 25% chance to receive 1000 coins
3. 10% chance to get a special permission
4. Player sees message: "&6+1000 Coins" or "&5Special Permission Unlocked!"

### Multi-Pool with Bonuses

Separate item drops from bonus rewards using pools:

```yaml
loot-tables:
  premium-vault:
    pools:
      # Regular items pool
      - name: items
        min-rolls: 3
        max-rolls: 5
        weighted-items:
          - type: DIAMOND
            amount-min: 5
            amount-max: 10
            weight: 10.0
          - type: NETHERITE_INGOT
            amount-min: 1
            amount-max: 3
            weight: 5.0

      # Bonus rewards pool (guaranteed 1 roll)
      - name: bonuses
        min-rolls: 1
        max-rolls: 1
        command-rewards:
          - weight: 50.0
            commands:
              - "eco give {player} 500"
            display-name: "&6+500 Coins"

          - weight: 30.0
            commands:
              - "eco give {player} 1000"
            display-name: "&6+1000 Coins"

          - weight: 15.0
            commands:
              - "eco give {player} 5000"
              - "give {player} nether_star 1"
            display-name: "&e&lJACKPOT! &6+5000 Coins"

          - weight: 5.0
            commands:
              - "lp user {player} parent add vip"
              - "eco give {player} 10000"
              - "give {player} elytra 1"
            display-name: "&5&lULTRA RARE! &dVIP Rank!"
```

Player opens vault:
- Gets 3-5 premium items (diamonds, netherite)
- Gets exactly 1 bonus (weighted probability):
  - 50% chance: 500 coins
  - 30% chance: 1000 coins
  - 15% chance: 5000 coins + nether star
  - 5% chance: VIP rank + 10000 coins + elytra

### Common Examples

#### Economy Rewards (Vault Plugin)
```yaml
command-rewards:
  # Give money
  - weight: 30.0
    commands:
      - "eco give {player} 1000"
    display-name: "&6+1000 Coins"

  # Take money (punishment vault!)
  - weight: 5.0
    commands:
      - "eco take {player} 500"
    display-name: "&c-500 Coins (Cursed Vault!)"
```

#### Permissions (LuckPerms)
```yaml
command-rewards:
  # Grant permission
  - weight: 15.0
    commands:
      - "lp user {player} permission set special.perk true"
    display-name: "&dSpecial Perk Unlocked!"

  # Add to group
  - weight: 5.0
    commands:
      - "lp user {player} parent add vip"
    display-name: "&5&lVIP RANK UNLOCKED!"

  # Temporary permission (1 hour)
  - weight: 20.0
    commands:
      - "lp user {player} permission settemp special.bonus.1h true 1h"
    display-name: "&a1-Hour Bonus Active!"
```

#### Experience & Levels
```yaml
command-rewards:
  # Give XP
  - weight: 25.0
    commands:
      - "xp add {player} 1000"
    display-name: "&a+1000 XP"

  # Give levels
  - weight: 10.0
    commands:
      - "xp add {player} 10 levels"
    display-name: "&a+10 Levels"
```

#### Items (Vanilla)
```yaml
command-rewards:
  # Give items
  - weight: 20.0
    commands:
      - "give {player} diamond 64"
    display-name: "&b+64 Diamonds"

  # Give multiple items
  - weight: 10.0
    commands:
      - "give {player} elytra 1"
      - "give {player} firework_rocket 64"
    display-name: "&5Flight Kit!"
```

#### Titles & Messages
```yaml
command-rewards:
  - weight: 5.0
    commands:
      - "title {player} title {\"text\":\"JACKPOT!\",\"color\":\"gold\",\"bold\":true}"
      - "title {player} subtitle {\"text\":\"You won the grand prize!\",\"color\":\"yellow\"}"
      - "playsound minecraft:ui.toast.challenge_complete master {player}"
    display-name: "&6&l★ JACKPOT ★"
```

#### Combined Rewards
```yaml
command-rewards:
  # Ultimate reward package
  - weight: 1.0                 # 1% chance - very rare!
    commands:
      - "lp user {player} parent add vip"
      - "eco give {player} 50000"
      - "give {player} elytra 1"
      - "give {player} netherite_ingot 16"
      - "xp add {player} 100 levels"
      - "title {player} title {\"text\":\"LEGENDARY REWARD!\",\"color\":\"gold\",\"bold\":true}"
    display-name: "&6&l⚡ LEGENDARY REWARD PACKAGE ⚡"
```

### Available Placeholders

- `{player}` - Player's name
- `{uuid}` - Player's UUID

Example:
```yaml
commands:
  - "eco give {player} 1000"        # Becomes: eco give Steve 1000
  - "lp user {player} parent add vip"  # Becomes: lp user Steve parent add vip
```

### Important Notes

{% hint style="warning" %}
**Commands run as CONSOLE** with OP permissions. Be careful with what commands you allow!
{% endhint %}

{% hint style="success" %}
**Weight = Probability**. Higher weight = more likely. A `weight: 50.0` is twice as likely as `weight: 25.0`.
{% endhint %}

{% hint style="info" %}
**Multiple commands execute in order**. Great for jackpot rewards that give items + money + permissions!
{% endhint %}

{% hint style="danger" %}
**Don't use `type: COMMAND` in weighted-items!** That's incorrect. Use the `command-rewards` list instead.
{% endhint %}

### Required Plugins

Command rewards work with **any** plugin that uses console commands:

- **Economy**: [Vault](https://www.spigotmc.org/resources/vault.34315/) (+ economy plugin like EssentialsX)
- **Permissions**: [LuckPerms](https://luckperms.net/)
- **Custom Items**: ItemsAdder, Oraxen, MMOItems (use their give commands)
- **Vanilla**: No plugins needed for vanilla commands (give, xp, title, etc.)

### Troubleshooting

**"Commands aren't running!"**
- Check console for errors when vault opens
- Verify command syntax is correct (test in console manually)
- Ensure required plugins (Vault, LuckPerms) are installed

**"Getting 'type: COMMAND is not valid' error"**
- You're using the wrong format! Don't put commands in `weighted-items`
- Use `command-rewards` list instead (see examples above)

**"Player gets no message"**
- Make sure `display-name` is set
- Check if commands are actually executing (console logs)
- Weight might be too low (increase for testing)

**"Economy commands don't work"**
- Vault plugin required for `eco` commands
- Need an economy plugin (EssentialsX, CMI, etc.) alongside Vault
- Test command manually in console: `/eco give PlayerName 1000`

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
