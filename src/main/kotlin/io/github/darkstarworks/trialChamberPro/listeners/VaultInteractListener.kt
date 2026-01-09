package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.KeyType
import io.github.darkstarworks.trialChamberPro.models.VaultType
import io.github.darkstarworks.trialChamberPro.utils.AdvancementUtil
import io.github.darkstarworks.trialChamberPro.utils.MessageUtil
import kotlinx.coroutines.*
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Vault
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

/**
 * Listens for vault interactions and handles per-player loot system.
 * Validates trial keys, checks cooldowns, and provides visual feedback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VaultInteractListener(private val plugin: TrialChamberPro) : Listener {

    private val listenerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        plugin.logger.severe("Exception in vault listener: ${exception.message}")
        exception.printStackTrace()
    }

    // Track vaults currently being opened to prevent spam-clicking race condition
    private val openingVaults = java.util.concurrent.ConcurrentHashMap<String, Long>()

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onVaultInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        val block = event.clickedBlock ?: return
        val player = event.player
        val item = player.inventory.itemInMainHand

        // Check if it's a vault and determine type from block state
        if (block.type != Material.VAULT) {
            return
        }

        // Determine vault type from block data
        // Note: Paper's Vault TileState doesn't have isOminous property (unlike TrialSpawner)
        // so we must use block data string parsing
        val blockDataString = block.blockData.asString
        val vaultType = if (blockDataString.contains("ominous=true", ignoreCase = true)) {
            VaultType.OMINOUS
        } else {
            VaultType.NORMAL
        }

        // Debug logging
        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("Vault interaction: blockData='$blockDataString', detected type=$vaultType")
        }

        // Check if key validation is enabled
        if (plugin.config.getBoolean("trial-keys.validate-key-type", true)) {
            // Validate key type using direct Material enum
            val keyType = when (item.type) {
                Material.TRIAL_KEY -> KeyType.NORMAL
                Material.OMINOUS_TRIAL_KEY -> KeyType.OMINOUS
                else -> null
            }

            // Debug logging
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.info("Key check: item=${item.type.name}, keyType=$keyType")
            }

            if (keyType == null) {
                event.isCancelled = true
                player.sendMessage(plugin.getMessage("no-key"))
                playErrorSound(player)
                return
            }

            // Check if key matches vault type
            val requiredKeyType = when (vaultType) {
                VaultType.NORMAL -> KeyType.NORMAL
                VaultType.OMINOUS -> KeyType.OMINOUS
            }

            if (keyType != requiredKeyType) {
                event.isCancelled = true
                val requiredTypeName = if (vaultType == VaultType.OMINOUS) "Ominous" else "Normal"
                player.sendMessage(plugin.getMessage("wrong-key-type", "required_type" to requiredTypeName))
                playErrorSound(player)
                return
            }
        }

        // Handle per-player vault system
        if (plugin.config.getBoolean("vaults.per-player-loot", true)) {
            event.isCancelled = true // We'll handle the vault opening ourselves

            // CRITICAL: Check the lock SYNCHRONOUSLY before launching async work
            // This prevents the spam-click race condition where multiple coroutines could
            // be launched before any of them checks the lock
            val lockKey = "${player.uniqueId}:${block.location.blockX}:${block.location.blockY}:${block.location.blockZ}:${vaultType.name}"
            val now = System.currentTimeMillis()

            // Periodically clean up old entries to prevent memory leaks (entries older than 30 seconds)
            if (openingVaults.size > 100) {
                openingVaults.entries.removeIf { now - it.value > 30000 }
            }

            val existingOperation = openingVaults[lockKey]

            if (existingOperation != null && now - existingOperation < 5000) {
                // Operation still in progress (less than 5 seconds ago), ignore this click
                if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                    plugin.logger.info("Vault interaction ignored - already opening (spam protection)")
                }
                return
            }

            // Set the lock SYNCHRONOUSLY before launching async work
            openingVaults[lockKey] = now

            // Launch coroutine WITHOUT explicit lock removal
            // The timestamp check above handles expiration - removing explicitly causes
            // a race condition at the 5-second boundary where the remove() races with
            // new events checking the lock
            listenerScope.launch(exceptionHandler) {
                handleVaultOpen(player, block.location, vaultType)
            }
        }
    }

    /**
     * Handles the vault opening logic.
     * NOTE: Spam-click protection is done SYNCHRONOUSLY in the event handler before this is called.
     *
     * Uses Paper's native Vault TileState API for cooldown tracking (hasRewardedPlayer/addRewardedPlayer).
     * This is more reliable than database tracking because:
     * 1. It uses vanilla Minecraft's built-in tracking (rewarded_players NBT)
     * 2. It persists automatically with the block state
     * 3. It resets automatically when the vault block is restored during chamber reset
     */
    private suspend fun handleVaultOpen(
        player: org.bukkit.entity.Player,
        location: org.bukkit.Location,
        vaultType: VaultType
    ) {
        // Get vault data for the specific type (normal or ominous) - needed for loot table resolution
        val vaultData = plugin.vaultManager.getVault(location, vaultType)
        if (vaultData == null) {
            player.sendMessage(plugin.getMessage("vault-not-found"))
            return
        }

        // Check permission bypass - skip cooldown check entirely
        if (player.hasPermission("tcp.bypass.cooldown")) {
            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                plugin.logger.info("[Vault API] Player ${player.name} has tcp.bypass.cooldown permission - SKIPPING cooldown check!")
            }
            openVault(player, vaultData, vaultType, location)
            return
        }

        // Use Paper's native Vault API for cooldown checking
        // This MUST be done on the main thread (or region thread for Folia)
        val hasAlreadyRewarded = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { continuation ->
            plugin.scheduler.runAtLocation(location, Runnable {
                try {
                    val block = location.block
                    val vaultState = block.state as? Vault

                    if (vaultState == null) {
                        plugin.logger.warning("Block at $location is not a Vault TileState!")
                        continuation.resume(false) {}
                        return@Runnable
                    }

                    val hasRewarded = vaultState.hasRewardedPlayer(player.uniqueId)

                    if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                        plugin.logger.info("[Vault API] hasRewardedPlayer(${player.name}): $hasRewarded")
                    }

                    continuation.resume(hasRewarded) {}
                } catch (e: Exception) {
                    plugin.logger.severe("Error checking vault reward status: ${e.message}")
                    e.printStackTrace()
                    continuation.resume(false) {} // Allow opening on error
                }
            })
        }

        if (hasAlreadyRewarded) {
            // Vault is locked for this player (they already got loot)
            // Check config to determine behavior (permanent vs time-based)
            val cooldownHours = when (vaultType) {
                VaultType.NORMAL -> plugin.config.getLong("vaults.normal-cooldown-hours", -1)
                VaultType.OMINOUS -> plugin.config.getLong("vaults.ominous-cooldown-hours", -1)
            }

            if (cooldownHours < 0) {
                // Permanent lock (vanilla behavior) - player must wait for chamber reset
                player.sendMessage(plugin.getMessage("vault-locked",
                    "type" to vaultType.displayName
                ))
            } else {
                // Time-based cooldown configured, but Vault API doesn't track time
                // For now, treat as permanent until chamber reset (vanilla behavior)
                // Future enhancement: Could use database for time-based cooldowns
                player.sendMessage(plugin.getMessage("vault-locked",
                    "type" to vaultType.displayName
                ))
            }

            // Show cooldown particles
            showCooldownParticles(player, location, vaultType)
            playErrorSound(player)
        } else {
            // Can open the vault - player hasn't been rewarded yet
            openVault(player, vaultData, vaultType, location)
        }
    }

    /**
     * Opens a vault for a player.
     * CRITICAL FIX: Handles race condition where player may disconnect during loot generation.
     * CRITICAL FIX: Does NOT consume key if no loot was generated (missing loot table).
     *
     * Uses Paper's native Vault API to mark player as rewarded (addRewardedPlayer).
     */
    private suspend fun openVault(
        player: org.bukkit.entity.Player,
        vaultData: io.github.darkstarworks.trialChamberPro.models.VaultData,
        vaultType: VaultType,
        location: Location
    ) {
        // Resolve the effective loot table (chamber override > vault default)
        val chamber = plugin.chamberManager.getChamberById(vaultData.chamberId)
        val effectiveLootTable = plugin.chamberManager.getEffectiveLootTable(
            chamber, vaultType, vaultData.lootTable
        )

        // Debug logging
        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            val chamberOverride = chamber?.getLootTable(vaultType)
            plugin.logger.info("Opening vault ID ${vaultData.id}: type=${vaultData.type}, " +
                "vaultDefault='${vaultData.lootTable}', chamberOverride='$chamberOverride', " +
                "effective='$effectiveLootTable'")
        }

        // Check if the loot table exists BEFORE generating loot
        val tableExists = plugin.lootManager.getTable(effectiveLootTable) != null
        if (!tableExists) {
            plugin.logger.warning("Loot table '$effectiveLootTable' not found! Key will NOT be consumed.")
            player.sendMessage(plugin.getMessage("vault-loot-table-missing"))
            return  // Don't consume key, don't mark vault as opened
        }

        // Generate loot (async, player might disconnect during this)
        val loot = plugin.lootManager.generateLoot(effectiveLootTable, player)

        // CRITICAL: If no loot was generated, don't consume the key or mark the vault
        if (loot.isEmpty()) {
            plugin.logger.warning("No loot generated from table '$effectiveLootTable'! Key will NOT be consumed.")
            player.sendMessage(plugin.getMessage("vault-no-loot-generated"))
            return  // Don't consume key, don't mark vault as opened
        }

        // Mark player as rewarded using Paper's native Vault API
        // This MUST be done on the region thread (before giving items)
        val rewardMarked = kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { continuation ->
            plugin.scheduler.runAtLocation(location, Runnable {
                try {
                    val block = location.block
                    val vaultState = block.state as? Vault

                    if (vaultState == null) {
                        plugin.logger.warning("Cannot mark reward - block at $location is not a Vault!")
                        continuation.resume(false) {}
                        return@Runnable
                    }

                    // Add player to rewarded list
                    vaultState.addRewardedPlayer(player.uniqueId)

                    // CRITICAL: Must call update() to persist the block state changes
                    vaultState.update()

                    if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                        plugin.logger.info("[Vault API] addRewardedPlayer(${player.name}) - marked and persisted")
                    }

                    continuation.resume(true) {}
                } catch (e: Exception) {
                    plugin.logger.severe("Error marking vault reward: ${e.message}")
                    e.printStackTrace()
                    continuation.resume(false) {}
                }
            })
        }

        if (!rewardMarked) {
            plugin.logger.warning("Failed to mark player as rewarded! Key will NOT be consumed.")
            player.sendMessage(plugin.getMessage("vault-error"))
            return
        }

        // Also record in database for statistics tracking (but not for cooldown - Vault API handles that)
        plugin.vaultManager.recordOpen(player.uniqueId, vaultData.id)

        // Update statistics (for leaderboards and /tcp stats)
        plugin.statisticsManager.incrementVaultsOpened(player.uniqueId, vaultData.type)

        // MUST switch to entity's region thread for player access (Folia compatible)
        // Use scheduler adapter instead of Dispatchers.Main (which doesn't exist in server environment)
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            plugin.scheduler.runAtEntity(player, Runnable {
                try {
                    // Verify player still online
                    if (!player.isOnline) {
                        plugin.logger.info("Player ${player.name} disconnected during vault open")
                        continuation.resume(Unit) {}
                        return@Runnable
                    }

                    // Give items to player
                    val leftover = player.inventory.addItem(*loot.toTypedArray())

                    // Drop leftover items if inventory is full
                    if (leftover.isNotEmpty()) {
                        // Double-check still online
                        if (player.isOnline) {
                            leftover.values.forEach { item ->
                                player.world.dropItemNaturally(player.location, item)
                            }
                            player.sendMessage(plugin.getMessage("inventory-full"))
                        }
                    }

                    player.sendMessage(plugin.getMessage("vault-opened", "type" to vaultType.displayName))

                    // Grant the appropriate advancement
                    when (vaultType) {
                        VaultType.NORMAL -> AdvancementUtil.grantVaultUnlock(player)
                        VaultType.OMINOUS -> AdvancementUtil.grantOminousVaultUnlock(player)
                    }

                    // Play success sound and particles
                    playSuccessSound(player, player.location)
                    showSuccessParticles(player, player.location, vaultType)

                    // Consume the trial key - ONLY if we got this far (loot was generated and given)
                    val item = player.inventory.itemInMainHand
                    if (item.amount > 1) {
                        item.amount -= 1
                    } else {
                        player.inventory.setItemInMainHand(null)
                    }

                    continuation.resume(Unit) {}
                } catch (e: Exception) {
                    plugin.logger.severe("Error in vault open: ${e.message}")
                    e.printStackTrace()
                    continuation.resumeWith(Result.failure(e))
                }
            })
        }
    }

    /**
     * Shows cooldown particles around the vault.
     */
    private fun showCooldownParticles(player: org.bukkit.entity.Player, location: org.bukkit.Location, vaultType: VaultType) {
        if (!plugin.config.getBoolean("vaults.show-cooldown-particles", true)) return

        val particleType = when (vaultType) {
            VaultType.NORMAL -> {
                val name = plugin.config.getString("vaults.particles.normal-cooldown", "SMOKE_NORMAL")!!
                try {
                    Particle.valueOf(name)
                } catch (_: IllegalArgumentException) {
                    Particle.SMOKE
                }
            }
            VaultType.OMINOUS -> {
                val name = plugin.config.getString("vaults.particles.ominous-cooldown", "SOUL")!!
                try {
                    Particle.valueOf(name)
                } catch (_: IllegalArgumentException) {
                    Particle.SOUL
                }
            }
        }

        player.spawnParticle(
            particleType,
            location.clone().add(0.5, 1.0, 0.5),
            20,
            0.3, 0.5, 0.3,
            0.01
        )
    }

    /**
     * Shows success particles around the vault.
     */
    private fun showSuccessParticles(player: org.bukkit.entity.Player, location: org.bukkit.Location, vaultType: VaultType) {
        if (!plugin.config.getBoolean("vaults.show-cooldown-particles", true)) return

        val particleType = when (vaultType) {
            VaultType.NORMAL -> {
                val name = plugin.config.getString("vaults.particles.normal-available", "VILLAGER_HAPPY")!!
                try {
                    Particle.valueOf(name)
                } catch (_: IllegalArgumentException) {
                    Particle.HAPPY_VILLAGER
                }
            }
            VaultType.OMINOUS -> {
                val name = plugin.config.getString("vaults.particles.ominous-available", "SOUL_FIRE_FLAME")!!
                try {
                    Particle.valueOf(name)
                } catch (_: IllegalArgumentException) {
                    Particle.SOUL_FIRE_FLAME
                }
            }
        }

        player.spawnParticle(
            particleType,
            location.clone().add(0.5, 1.0, 0.5),
            30,
            0.3, 0.5, 0.3,
            0.02
        )
    }

    /**
     * Plays success sound to the player.
     */
    private fun playSuccessSound(player: org.bukkit.entity.Player, location: org.bukkit.Location) {
        if (!plugin.config.getBoolean("vaults.play-sound-on-open", true)) return

        val soundName = plugin.config.getString("vaults.sounds.normal-open", "BLOCK_VAULT_OPEN_SHUTTER")!!
        val sound = resolveSound(soundName) ?: return
        player.playSound(location, sound, 1.0f, 1.0f)
    }

    /**
     * Plays error sound to the player.
     */
    private fun playErrorSound(player: org.bukkit.entity.Player) {
        if (!plugin.config.getBoolean("vaults.play-sound-on-open", true)) return

        val soundName = plugin.config.getString("vaults.sounds.cooldown", "BLOCK_NOTE_BLOCK_BASS")!!
        val sound = resolveSound(soundName) ?: return
        player.playSound(player.location, sound, 1.0f, 0.5f)
    }

    /**
     * Resolves a sound from either enum name (BLOCK_VAULT_OPEN_SHUTTER) or namespaced key (block.vault.open_shutter).
     * Properly handles compound names like NOTE_BLOCK that should stay as note_block.
     */
    private fun resolveSound(soundName: String): org.bukkit.Sound? {
        return try {
            // First try: direct enum lookup (works if user specifies BLOCK_VAULT_OPEN_SHUTTER)
            org.bukkit.Sound.valueOf(soundName.uppercase())
        } catch (_: Exception) {
            try {
                // Second try: if already a namespaced key format (block.vault.open_shutter)
                val key = if (soundName.contains('.')) {
                    soundName.lowercase()
                } else {
                    // Convert enum-style to namespaced: BLOCK_VAULT_OPEN_SHUTTER -> block.vault.open_shutter
                    // Split by underscore, then intelligently join:
                    // - Use dots between major categories (BLOCK, ENTITY, ITEM, etc.)
                    // - Keep underscores within names (NOTE_BLOCK -> note_block)
                    convertEnumToNamespacedKey(soundName)
                }
                org.bukkit.Registry.SOUNDS.get(org.bukkit.NamespacedKey.minecraft(key))
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Converts Bukkit Sound enum name to Minecraft namespaced key.
     * Examples:
     * - BLOCK_VAULT_OPEN_SHUTTER -> block.vault.open_shutter
     * - BLOCK_NOTE_BLOCK_BASS -> block.note_block.bass
     * - ENTITY_PLAYER_HURT -> entity.player.hurt
     */
    private fun convertEnumToNamespacedKey(enumName: String): String {
        val lower = enumName.lowercase()

        // Known compound words that should keep their underscores
        val compoundWords = listOf(
            "note_block", "trial_spawner", "ender_dragon", "wither_skeleton",
            "elder_guardian", "iron_golem", "snow_golem", "magma_cube",
            "slime_block", "honey_block", "soul_sand", "soul_soil",
            "copper_bulb", "copper_door", "copper_trapdoor", "copper_grate",
            "heavy_core", "wind_charge", "trial_key", "ominous_trial_key",
            "cave_vines", "glow_lichen", "pointed_dripstone", "sculk_sensor",
            "sculk_shrieker", "sculk_catalyst", "big_dripleaf", "small_dripleaf",
            "mangrove_roots", "chorus_flower", "chorus_plant", "end_portal",
            "end_gateway", "nether_portal", "dragon_egg", "turtle_egg",
            "frog_spawn", "sniffer_egg", "decorated_pot", "enchanting_table",
            "brewing_stand", "ender_chest", "shulker_box", "respawn_anchor",
            "lodestone_compass", "item_frame", "armor_stand", "end_crystal",
            "minecart_inside", "chest_locked", "wet_grass"
        )

        // Replace underscores with dots, but preserve compound words
        var result = lower
        for (compound in compoundWords) {
            val placeholder = compound.replace('_', '\u0000')
            result = result.replace(compound, placeholder)
        }
        result = result.replace('_', '.')
        result = result.replace('\u0000', '_')

        return result
    }

    /**
     * Cleans up resources when the plugin is disabled.
     * Cancels the coroutine scope and clears the opening vaults map to prevent memory leaks.
     */
    fun shutdown() {
        listenerScope.cancel()
        openingVaults.clear()
    }
}
