# WorldEdit //undo Integration - Implementation Summary

## Problem
The `/tcp paste <schematic>` command was not compatible with WorldEdit's `//undo` command. Pasted schematics could not be undone using standard WorldEdit commands.

## Root Cause
The EditSession created during pasting was not being registered with the player's WorldEdit LocalSession history, so WorldEdit had no record of the operation to undo.

## Solution Implemented

### 1. Modified SchematicManager.pasteSchematic()
**File:** `managers/SchematicManager.kt`

**Key Changes:**
- Added optional `player: Player? = null` parameter
- When player is provided, retrieves their WorldEdit LocalSession
- Calls `localSession.remember(editSession)` after paste completes
- EditSession is added to WorldEdit history BEFORE it closes (inside `use` block)

```kotlin
suspend fun pasteSchematic(
    schematicName: String,
    location: Location,
    player: Player? = null,  // NEW: Optional player for //undo support
    ignoreAir: Boolean = false
): Boolean
```

**Implementation Details:**
```kotlin
// Get player's WorldEdit session if player provided
val localSession = player?.let { p ->
    try {
        val actor = BukkitAdapter.adapt(p)
        WorldEdit.getInstance().sessionManager.get(actor)
    } catch (e: Exception) {
        plugin.logger.warning("Failed to get WorldEdit session...")
        null
    }
}

// Inside EditSession.use { } block, after Operations.complete()
localSession?.remember(editSession)  // Register for //undo
```

### 2. Updated Command Handler
**File:** `commands/TCPCommand.kt`

**Changes in handlePaste():**
- Extracts player from sender: `val player = sender as? Player`
- Passes player to pasteSchematic: `pasteSchematic(schematicName, location, player)`
- Adds helpful message: "Use //undo to undo this paste"

```kotlin
// Extract player for WorldEdit undo integration
val player = sender as? Player

commandScope.launch {
    val success = plugin.schematicManager.pasteSchematic(schematicName, location, player)
    if (success && player != null) {
        sender.sendMessage("§7Use §e//undo §7to undo this paste")
    }
}
```

## How It Works

### WorldEdit Undo System
WorldEdit maintains an undo history per player through the LocalSession:
1. Each player has a LocalSession that tracks their WorldEdit operations
2. When an EditSession completes work, it can be "remembered" in the LocalSession
3. The `//undo` command pops the most recent EditSession from history and reverses it

### Integration Flow
1. **Player executes:** `/tcp paste trial1`
2. **Command handler:** Extracts player reference and passes to SchematicManager
3. **SchematicManager:** 
   - Loads schematic from disk (async IO thread)
   - Switches to main thread for block placement
   - Gets player's WorldEdit LocalSession
   - Creates EditSession and performs paste
   - Calls `localSession.remember(editSession)` to register operation
   - EditSession closes, flushing changes
4. **Player can now:** Execute `//undo` to reverse the paste

## Compatibility

✅ **WorldEdit:** Full compatibility - uses standard LocalSession API
✅ **FastAsyncWorldEdit:** Works with FAWE's LocalSession implementation
✅ **Console/Command Blocks:** No player = no undo history (graceful degradation)
✅ **Existing Code:** Backward compatible - player parameter is optional

## Testing

### Test Case 1: Basic Undo
```
/tcp paste trial1
//undo
```
**Expected:** Schematic is pasted, then completely removed by //undo

### Test Case 2: Multiple Operations
```
/tcp paste trial1
//set stone
/tcp paste trial2
//undo
//undo
//undo
```
**Expected:** Each operation undoes in reverse order (trial2, then stone, then trial1)

### Test Case 3: Console Usage
```
/tcp paste trial1 100 64 200
```
**Expected:** Works normally, no undo history (console has no WorldEdit session)

## Technical Notes

### Critical Timing
- `localSession.remember()` MUST be called AFTER `Operations.complete()`
- `localSession.remember()` MUST be called BEFORE `editSession.close()`
- The `use` block ensures proper ordering

### Error Handling
- If WorldEdit session retrieval fails, paste continues without undo support
- Logged warning: "Failed to get WorldEdit session for {player}"
- Graceful degradation - core functionality unaffected

### Memory Considerations
- WorldEdit automatically manages undo history size
- Default: Last 15 operations per player
- Configurable in WorldEdit config: `history.size`

## Files Modified

1. ✅ **managers/SchematicManager.kt**
   - Added `player` parameter to `pasteSchematic()`
   - Integrated LocalSession.remember() call

2. ✅ **commands/TCPCommand.kt**
   - Updated `handlePaste()` to pass player
   - Added undo hint message

## Benefits

✅ **Standard Workflow:** Aligns with WorldEdit user expectations
✅ **Safety:** Players can undo mistakes immediately
✅ **Flexibility:** Works with all WorldEdit undo features (//undo N, //redo)
✅ **Performance:** Zero overhead when player not provided
✅ **Maintainability:** Uses official WorldEdit API, no hacks

## User Experience

**Before:**
```
/tcp paste trial1
> Successfully pasted...
//undo
> There is nothing to undo
```

**After:**
```
/tcp paste trial1
> Successfully pasted schematic 'trial1' at 100, 64, 200
> Use //undo to undo this paste
//undo
> Undid 1 edit(s)
```
