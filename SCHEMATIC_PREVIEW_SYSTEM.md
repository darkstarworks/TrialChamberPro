# Schematic Paste Preview System

## Overview

A visual preview system for schematic pasting, inspired by WorldEditSelectionVisualizer. Players can see exactly where a schematic will be placed before confirming the paste operation.

## Components

### 1. ParticleVisualizer (`utils/ParticleVisualizer.kt`)

Renders bounding boxes using particles to show schematic placement zones.

**Features:**
- Auto-refreshing particle display (updates every 0.5s)
- Per-player visualization tracking
- Configurable particle spacing
- Automatic cleanup when player disconnects

**Key Methods:**
```kotlin
showBox(player, min, max, spacing = 1.0)  // Start showing preview
stopVisualization(player)                  // Stop preview for player
stopAll()                                  // Stop all active previews
```

### 2. PasteConfirmationManager (`managers/PasteConfirmationManager.kt`)

Manages pending paste operations with automatic timeout.

**Features:**
- Stores pending paste requests per player
- 5-minute automatic timeout
- Thread-safe concurrent operations
- Tracks creation time for remaining time calculation

**Key Methods:**
```kotlin
createPending(player, schematic, location)  // Create new pending paste
getPending(player)                          // Get pending paste info
hasPending(player)                          // Check if player has pending
cancelPending(player, silent = false)       // Cancel pending paste
```

### 3. PasteConfirmListener (`listeners/PasteConfirmListener.kt`)

Listens for "confirm" or "cancel" messages in chat from players with pending pastes.

**Features:**
- Intercepts chat messages at LOWEST priority
- Cancels intercepted confirmation messages
- Handles player disconnect cleanup
- Executes paste asynchronously on confirmation

## Workflow

1. **Player runs `/tcp paste <name>`**
   - System loads schematic dimensions
   - Creates pending paste request
   - Shows particle box preview
   - Displays confirmation instructions

2. **Player types "confirm" or "cancel" in chat**
   - "confirm" → Pastes schematic, stops preview
   - "cancel" → Cancels paste, stops preview
   - Message is intercepted and won't appear in chat

3. **Timeout handling**
   - After 5 minutes, pending paste auto-cancels
   - Preview stops automatically
   - Player notified of timeout

4. **On disconnect**
   - All pending pastes cancelled
   - All previews stopped
   - No memory leaks

## Modified Files

### `TCPCommand.kt`
**Changed:** `handlePaste()` method
- Now shows preview first instead of immediate paste
- Requires player to be online (not console)
- Gets schematic dimensions asynchronously
- Creates visualization before paste

### `TrialChamberPro.kt`
**Added:**
- `particleVisualizer` property
- `pasteConfirmationManager` property
- Initialization in `onEnable()`
- Registration of `PasteConfirmListener`
- Cleanup in `onDisable()`

### `messages.yml`
**Added message keys:**
- `paste-loading` - Loading dimensions message
- `paste-preview-shown` - Preview display notification
- `paste-confirm-hint` - Confirmation instructions
- `paste-confirming` - Pasting in progress
- `paste-success` - Successful paste
- `paste-failed` - Paste error
- `paste-cancelled` - User cancelled
- `paste-timeout` - Timeout expiration
- `paste-undo-hint` - WorldEdit undo hint

## Usage Example

```
/tcp paste trial1

# System shows:
[TCP] Loading schematic dimensions...
[TCP] Showing preview for trial1 at 100, 64, 200 (51x23x51)
Type 'confirm' to paste or cancel to abort (expires in 300s)

# Particle box appears showing the placement area

# Player types: confirm

[TCP] Pasting schematic...
[TCP] Successfully pasted trial1 at 100, 64, 200
[TCP] Use //undo to undo this paste
```

## Technical Details

### Particle System
- Uses FLAME particles for visibility
- Draws 12 edges of the bounding box
- Particle spacing of 1.0 blocks (configurable)
- Updates every 10 ticks (0.5 seconds)

### Timeout Mechanism
- Uses Bukkit's `runTaskLater()` with 6000 ticks (300 seconds)
- Cancellable task stored in ConcurrentHashMap
- Auto-cleanup on task execution or manual cancellation

### Thread Safety
- ConcurrentHashMap for thread-safe pending paste storage
- Async paste operation runs on IO dispatcher
- Main thread for WorldEdit operations (required by Paper)

### Memory Management
- Weak cleanup on player quit
- All tasks cancelled on plugin disable
- No lingering particle tasks or pending operations

## Configuration

Currently uses hardcoded values. Future enhancement could add:
```yaml
schematic-paste:
  preview-particle: FLAME
  particle-spacing: 1.0
  refresh-rate: 10  # ticks
  timeout-seconds: 300
```

## Permissions

Uses existing permission: `tcp.admin.generate`

## Integration Notes

- **WorldEdit/FAWE:** Required for schematic operations
- **Paper API:** Uses AsyncChatEvent for confirmation
- **Coroutines:** Async dimension loading and pasting
- **Particle API:** Bukkit 1.21+ particle system

## Future Enhancements

Potential improvements:
1. Different particle colors for different schematic types
2. Configurable timeout duration
3. Preview rotation/offset adjustment commands
4. Multiple schematic preview comparison
5. Paste with rotation/flip options from preview
6. Sound effects on confirmation/cancellation
