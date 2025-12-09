package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.KeyType
import io.github.darkstarworks.trialChamberPro.models.VaultType
import io.github.darkstarworks.trialChamberPro.utils.AdvancementUtil
import io.github.darkstarworks.trialChamberPro.utils.MessageUtil
import kotlinx.coroutines.*
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
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

        // Determine vault type from block state string (more reliable than property access)
        val blockStateString = block.blockData.asString
        val vaultType = if (blockStateString.contains("ominous=true", ignoreCase = true)) {
            VaultType.OMINOUS
        } else {
            VaultType.NORMAL
        }

        // Debug logging
        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("Vault interaction: blockData='$blockStateString', detected type=$vaultType")
        }

        // Check if key validation is enabled
        if (plugin.config.getBoolean("trial-keys.validate-key-type", true)) {
            // Validate key type by checking material name
            // Ominous trial keys are a separate item ID: minecraft:ominous_trial_key
            val keyType = when {
                item.type == Material.TRIAL_KEY -> KeyType.NORMAL
                // Check for ominous trial key by material name (not in enum yet)
                item.type.name.equals("OMINOUS_TRIAL_KEY", ignoreCase = true) -> KeyType.OMINOUS
                // Also accept variations in naming
                item.type.name.contains("OMINOUS", ignoreCase = true) &&
                    item.type.name.contains("KEY", ignoreCase = true) -> KeyType.OMINOUS
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

            listenerScope.launch(exceptionHandler) {
                handleVaultOpen(player, block.location, vaultType)
            }
        }
    }

    /**
     * Handles the vault opening logic.
     */
    private suspend fun handleVaultOpen(
        player: org.bukkit.entity.Player,
        location: org.bukkit.Location,
        vaultType: VaultType
    ) {
        // Get vault data for the specific type (normal or ominous)
        val vault = plugin.vaultManager.getVault(location, vaultType)
        if (vault == null) {
            player.sendMessage(plugin.getMessage("vault-not-found"))
            return
        }

        // Create a unique key for this player-vault combination
        val lockKey = "${player.uniqueId}:${vault.id}"

        // Check if this vault is already being opened by this player (prevents spam-click race condition)
        val now = System.currentTimeMillis()
        val existingOperation = openingVaults[lockKey]
        if (existingOperation != null && now - existingOperation < 5000) {
            // Operation still in progress (less than 5 seconds ago)
            return
        }

        // Mark this vault as being opened
        openingVaults[lockKey] = now

        try {
            // Check permission bypass
            if (player.hasPermission("tcp.bypass.cooldown")) {
                openVault(player, vault, vaultType)
                return
            }

            // Check cooldown
            val (canOpen, remainingTime) = plugin.vaultManager.canOpenVault(player.uniqueId, vault)

            if (!canOpen) {
                // Vault is locked
                if (remainingTime == Long.MAX_VALUE) {
                    // Permanent lock (vanilla behavior)
                    player.sendMessage(plugin.getMessage("vault-locked",
                        "type" to vaultType.displayName
                    ))
                } else {
                    // Time-based cooldown
                    val timeString = MessageUtil.formatTime(remainingTime)
                    player.sendMessage(plugin.getMessage("vault-cooldown",
                        "type" to vaultType.displayName,
                        "time" to timeString
                    ))
                }

                // Show cooldown particles
                showCooldownParticles(player, location, vaultType)
                playErrorSound(player)
            } else {
                // Can open the vault
                openVault(player, vault, vaultType)
            }
        } finally {
            // Clean up the lock after 5 seconds to prevent memory leaks
            listenerScope.launch {
                delay(5000)
                openingVaults.remove(lockKey)
            }
        }
    }

    /**
     * Opens a vault for a player.
     * CRITICAL FIX: Handles race condition where player may disconnect during loot generation.
     */
    private suspend fun openVault(
        player: org.bukkit.entity.Player,
        vault: io.github.darkstarworks.trialChamberPro.models.VaultData,
        vaultType: VaultType
    ) {
        // Record the opening (for cooldown tracking)
        plugin.vaultManager.recordOpen(player.uniqueId, vault.id)

        // Update statistics (for leaderboards and /tcp stats)
        plugin.statisticsManager.incrementVaultsOpened(player.uniqueId, vault.type)

        // Resolve the effective loot table (chamber override > vault default)
        val chamber = plugin.chamberManager.getChamberById(vault.chamberId)
        val effectiveLootTable = plugin.chamberManager.getEffectiveLootTable(
            chamber, vaultType, vault.lootTable
        )

        // Debug logging
        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            val chamberOverride = chamber?.getLootTable(vaultType)
            plugin.logger.info("Opening vault ID ${vault.id}: type=${vault.type}, " +
                "vaultDefault='${vault.lootTable}', chamberOverride='$chamberOverride', " +
                "effective='$effectiveLootTable'")
        }

        // Generate loot (async, player might disconnect during this)
        val loot = plugin.lootManager.generateLoot(effectiveLootTable, player)

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
                            player.sendMessage("§eYour inventory was full! Some items were dropped.")
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

                    // Consume the trial key
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
}
