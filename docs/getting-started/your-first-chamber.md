# 🎯 Your First Chamber

Time to turn that dusty Trial Chamber into a repeatable endgame experience! This guide walks you through registering and configuring your first chamber.

## 🗺️ Step 1: Find a Trial Chamber

First things first—you need a Trial Chamber to manage. Got one already? Great! Need to find one?

```
/locate structure trial_chambers
```

Teleport to it and explore until you find the main vault room. You'll want to register the entire chamber structure, not just one room.

{% hint style="info" %}
**Pro tip:** Trial Chambers can be MASSIVE. Some span 100+ blocks in each direction. Bring blocks to mark corners!
{% endhint %}

## 📐 Step 2: Select the Chamber

TrialChamberPro needs to know which blocks belong to your chamber. The easiest way? **WorldEdit**.

### Using WorldEdit (Recommended)

1. Grab your WorldEdit wand: `/wand` or `/tool wand`
2. Left-click one corner of the chamber (lowest point)
3. Right-click the opposite corner (highest point)

Your selection should encompass the entire chamber—all rooms, hallways, spawners, and vaults.

{% hint style="warning" %}
**Important:** Make sure your selection includes a bit of padding around the structure. If spawners or vaults are right on the edge, they might not get detected in the scan.
{% endhint %}

### Without WorldEdit

Don't have WorldEdit? You can still do it manually (but seriously, get WorldEdit):

```
/tcp generate coords <minX,minY,minZ> <maxX,maxY,maxZ> <name>
```

Example:
```
/tcp generate coords -150,-20,400 -50,40,500 MainChamber
```

That's painful. Get WorldEdit. Moving on!

## ✅ Step 3: Register the Chamber

With your selection made, register the existing Trial Chamber with the plugin:

```
/tcp generate wand MyChamber
```

Replace `MyChamber` with whatever name you want. Keep it simple—no spaces!

You'll see:
```
[TCP] Chamber MyChamber created successfully!
[TCP] Next step: Run /tcp scan MyChamber to detect vaults and spawners
```

{% hint style="success" %}
**Naming conventions:** Use clear names like `MainChamber`, `NetherPortalTC`, or `SpawnChamber1`. You'll thank yourself later when managing multiple chambers.
{% endhint %}

{% hint style="info" %}
**Note:** `/tcp generate wand` registers **existing** Trial Chambers from your WorldEdit selection for management. It doesn't create or modify blocks—it just tells the plugin to start managing the selected region.
{% endhint %}

## 🔍 Step 4: Scan for Vaults and Spawners

Now we tell the plugin to find all the vaults, spawners, and decorated pots:

```
/tcp scan MyChamber
```

This takes a few seconds. The plugin is searching every block in your selection for:

- **Vaults** (normal and ominous)
- **Trial Spawners** (normal and ominous)
- **Decorated Pots**

You'll get a summary:
```
[TCP] Scanning chamber MyChamber...
[TCP] Scanning complete! Found 8 vaults, 12 spawners, 24 decorated pots.
```

{% hint style="info" %}
**What if it finds 0 vaults?** Either your selection didn't include the vaults, or you're in a decorative Trial Chamber variant without vaults. Expand your selection and scan again!
{% endhint %}

## 📸 Step 5: Create a Snapshot

Here's where the magic happens. TrialChamberPro needs a "snapshot" of your chamber in its pristine state:

```
/tcp snapshot create MyChamber
```

The plugin will:
1. Scan every block in the chamber
2. Save block types, orientations, and tile entity data
3. Compress it all into a file
4. Store it in `snapshots/MyChamber.dat`

This might take 5-30 seconds depending on chamber size. You'll see:
```
[TCP] Creating snapshot for MyChamber...
[TCP] Snapshot created successfully! (12,847 blocks, 1.2 MB)
```

{% hint style="warning" %}
**Keep it pristine!** Create snapshots when your chamber is in perfect condition. This is what the chamber will reset back to.
{% endhint %}

## 🚪 Step 6: Set an Exit Point

When the chamber resets, players inside need somewhere to go. Stand where you want them to teleport and run:

```
/tcp setexit MyChamber
```

This saves your exact position (including look direction). Usually, you'll want this just outside the entrance.

{% hint style="info" %}
**No exit set?** If you don't set one, players will teleport to the world spawn instead. Not the worst thing, but a dedicated exit is cleaner.
{% endhint %}

## 🎉 Step 7: Test It Out!

Your chamber is now managed! Let's verify everything works:

### Check Chamber Info
```
/tcp info MyChamber
```

You should see:
- Chamber bounds
- Number of vaults and spawners
- Snapshot status
- Reset interval
- Last reset time

### Test the Reset
```
/tcp reset MyChamber
```

This forces an immediate reset. You should:
- Get teleported to the exit point
- See the chamber fully restored
- Notice all vaults are locked again

{% hint style="success" %}
**Perfect!** Your chamber is now on autopilot. It will automatically reset based on the interval in your config (default: 48 hours).
{% endhint %}

## ⚙️ Step 8: Configure Reset Schedule (Optional)

By default, chambers reset every 48 hours. Want to change that for this specific chamber?

Edit `plugins/TrialChamberPro/config.yml`:

```yaml
# This is the default for all chambers
global:
  default-reset-interval: 172800  # 48 hours in seconds
```

Or set it per-chamber in the database (advanced users only).

**Common intervals:**
- Daily: `86400` (24 hours)
- Twice daily: `43200` (12 hours)
- Weekly: `604800` (7 days)
- Custom: Use an [online converter](https://www.timecalculator.net/) to get seconds

## 📚 Quick Reference

Here's everything in one place:

```bash
# 1. Select chamber with WorldEdit
/wand

# 2. Register chamber
/tcp generate wand MyChamber

# 3. Scan for vaults/spawners
/tcp scan MyChamber

# 4. Create snapshot
/tcp snapshot create MyChamber

# 5. Set exit point
/tcp setexit MyChamber

# 6. Check info
/tcp info MyChamber

# 7. Test reset (optional)
/tcp reset MyChamber
```

## 🎯 What's Next?

You've got a working chamber! Now let's make it awesome:

{% content-ref url="../guides/custom-loot.md" %}
[custom-loot.md](../guides/custom-loot.md)
{% endcontent-ref %}

Learn how to replace boring vanilla loot with custom rewards, economy integration, and more!

{% content-ref url="../guides/automatic-resets.md" %}
[automatic-resets.md](../guides/automatic-resets.md)
{% endcontent-ref %}

Configure reset schedules, warnings, and player handling.

{% content-ref url="../guides/per-player-vaults.md" %}
[per-player-vaults.md](../guides/per-player-vaults.md)
{% endcontent-ref %}

Understand how per-player loot works and configure cooldowns.

---

## 💡 Pro Tips

{% hint style="info" %}
**Multiple chambers?** Just repeat the process! Each chamber is independent with its own settings, loot tables, and reset schedules.
{% endhint %}

{% hint style="info" %}
**Update the snapshot:** Made changes to your chamber? Run `/tcp snapshot create MyChamber` again to update it. The old snapshot is overwritten.
{% endhint %}

{% hint style="warning" %}
**Don't delete snapshots manually!** The `.dat` files in `snapshots/` are critical. If you delete them, you can't reset that chamber anymore.
{% endhint %}

## ❓ Common Issues

**"No WorldEdit selection found"**
You didn't make a selection. Use `/wand` and select both corners first.

**"Scan found 0 vaults"**
Your selection doesn't include the vaults, or they're not actually vaults (check with F3).

**"Snapshot creation failed"**
Usually a disk space or permission issue. Check console logs for details.

**"Players aren't teleporting on reset"**
Make sure you set an exit point with `/tcp setexit MyChamber`.

Still stuck? Check the [full troubleshooting guide](../troubleshooting.md)!
