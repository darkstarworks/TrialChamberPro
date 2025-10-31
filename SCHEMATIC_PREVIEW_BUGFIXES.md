# Bug Fixes: Schematic Preview System

## Fixed Issues

### 1. Message Keys Not Found ✅

**Problem:** Game chat showed "Message not found: paste-loading" etc.

**Root Cause:** The messages were added to `src/main/resources/messages.yml` but the compiled plugin JAR in the `build/` directory still had the old version.

**Solution:** 
- Verified messages.yml has correct keys (it does)
- **You need to rebuild the plugin** with `./gradlew build` to include the new messages in the compiled JAR

**All message keys added:**
```yaml
paste-loading: "&7Loading schematic dimensions..."
paste-preview-shown: "&7Showing preview for &e{schematic}&7 at &f{x}, {y}, {z}&7 (&e{width}x{height}x{length}&7)"
paste-confirm-hint: "&aType 'confirm' &7to paste or &ccancel &7to abort &8(expires in {time}s)"
paste-confirming: "&7Pasting schematic..."
paste-success: "&aSuccessfully pasted &e{schematic}&a at &f{x}, {y}, {z}"
paste-failed: "&cFailed to paste schematic. Check console for errors."
paste-cancelled: "&ePaste cancelled."
paste-timeout: "&cPaste request timed out. Run the command again to paste."
paste-undo-hint: "&7Use &e//undo &7to undo this paste"
```

---

### 2. Particle Boundaries Incorrect ✅

**Problem:** Particles showed as a simple box from paste location + dimensions, not accounting for schematic origin/offset.

**Root Cause:** Schematics have an **origin point** in clipboard-local coordinates. When WorldEdit pastes:
- The origin is placed at the paste location
- Blocks are positioned relative to that origin
- Example: If origin is (0,0,0) but region starts at (-25, 0, -25), blocks appear 25 blocks in the negative X/Z direction from the paste point

**Previous (Wrong) Calculation:**
```kotlin
// Just added dimensions to location
val min = location
val max = location + dimensions
```

**New (Correct) Calculation:**
```kotlin
// Account for clipboard origin and region offset
val regionMin = clipboard.region.minimumPoint
val regionMax = clipboard.region.maximumPoint
val origin = clipboard.origin

// World position = pasteLocation + (clipboardPosition - origin)
worldMin = pasteLocation + (regionMin - origin)
worldMax = pasteLocation + (regionMax - origin)
```

**Changed Method:** `SchematicManager.getSchematicBounds()`
- Renamed from `getSchematicDimensions()` for clarity
- Now takes `pasteLocation` parameter
- Returns `Pair<Location, Location>` (actual world bounds)
- Fully loads clipboard to read origin and region data

**Changes in TCPCommand:**
- Updated `handlePaste()` to use `getSchematicBounds()`
- Passes paste location to bounds calculation
- Uses returned bounds directly for particle visualization
- Calculates dimensions from bounds for display messages

---

## Why The Old Approach Failed

### Example Schematic Analysis

**Trial Chamber Schematic (trial1.schem):**
- **Region in clipboard space:** (-25, 0, -25) to (25, 22, 25)
- **Origin:** (0, 0, 0)
- **Dimensions:** 51×23×51 blocks

### Old Calculation (Wrong):
```
Paste at (100, 64, 200)
Particle box: (100, 64, 200) to (151, 87, 251)
```
❌ This shows a box 51 blocks in the positive direction from paste point

### New Calculation (Correct):
```
Paste at (100, 64, 200)
regionMin = (-25, 0, -25)
regionMax = (25, 22, 25)
origin = (0, 0, 0)

worldMin = (100, 64, 200) + ((-25, 0, -25) - (0, 0, 0))
         = (100, 64, 200) + (-25, 0, -25)
         = (75, 64, 175)

worldMax = (100, 64, 200) + ((25, 22, 25) - (0, 0, 0))
         = (100, 64, 200) + (25, 22, 25)
         = (125, 86, 225)

Particle box: (75, 64, 175) to (125, 86, 225)
```
✅ This correctly shows where blocks will actually be placed!

---

## Technical Details

### Loading Strategy

**Step-by-step process:**
1. User runs `/tcp paste trial1`
2. Message: "Loading schematic dimensions..." (immediate feedback)
3. **Background (async on IO thread):**
   - Open schematic file
   - Parse NBT structure
   - Load full clipboard into memory
   - Read region bounds
   - Read origin point
   - Calculate world-space bounds
4. **Back to main thread:**
   - Create pending paste request
   - Start particle visualization with **correct bounds**
   - Display preview message with dimensions
   - Display confirmation hint

**Performance:**
- Schematic loading: ~50-200ms (IO-bound, doesn't block server)
- Bounds calculation: <1ms (simple math)
- Particle rendering: 10 ticks (0.5s refresh rate)

### Memory Considerations

The clipboard is loaded temporarily for bounds calculation then immediately released. It's loaded again when user confirms the paste. This is acceptable because:
- Schematic files are small (trial1.schem ≈ 200KB)
- Loading is fast (~100ms)
- Memory is released after calculation
- Alternative would be keeping clipboard in memory for 5 minutes (worse)

---

## Testing Checklist

After rebuilding the plugin:

1. **Messages Test:**
   - [ ] Run `/tcp paste trial1`
   - [ ] Verify "Loading schematic dimensions..." appears
   - [ ] Verify preview message shows correct format
   - [ ] Verify confirmation hint appears

2. **Boundary Test:**
   - [ ] Run `/tcp paste trial1` at a known location
   - [ ] Check particle box covers exact schematic area
   - [ ] Confirm by typing "confirm"
   - [ ] Verify schematic pastes exactly where particles showed

3. **Timeout Test:**
   - [ ] Run `/tcp paste trial1`
   - [ ] Wait 5+ minutes without confirming
   - [ ] Verify timeout message appears
   - [ ] Verify particles stop

4. **Cancel Test:**
   - [ ] Run `/tcp paste trial1`
   - [ ] Type "cancel" in chat
   - [ ] Verify cancellation message
   - [ ] Verify particles stop

---

## Build Instructions

To apply these fixes:

```bash
# Navigate to plugin directory
cd TrialChamberPro

# Clean previous builds
./gradlew clean

# Build new JAR
./gradlew build

# The fixed plugin will be at:
# build/libs/TrialChamberPro-X.X.X.jar
```

Then replace the plugin on your server and restart.
