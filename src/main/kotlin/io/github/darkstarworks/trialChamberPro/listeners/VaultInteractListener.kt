package io.github.darkstarworks.trialChamberPro.listeners

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.KeyType
import io.github.darkstarworks.trialChamberPro.models.VaultType
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
class VaultInteractListener(private val plugin: TrialChamberPro) : Listener {

    private val listenerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        plugin.logger.severe("Exception in vault listener: ${exception.message}")
        exception.printStackTrace()
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onVaultInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return

        val block = event.clickedBlock ?: return
        val player = event.player
        val item = player.inventory.itemInMainHand

        // Check if it's a vault
        val vaultType = when (block.type) {
            Material.VAULT -> VaultType.NORMAL
            else -> {
                // Check by name for ominous vault
                if (block.type.name == "OMINOUS_VAULT") {
                    VaultType.OMINOUS
                } else {
                    return
                }
            }
        }

        // Check if key validation is enabled
        if (plugin.config.getBoolean("trial-keys.validate-key-type", true)) {
            // Validate key type
            val keyType = when (item.type) {
                Material.TRIAL_KEY -> KeyType.NORMAL
                else -> {
                    if (item.type.name == "OMINOUS_TRIAL_KEY") {
                        KeyType.OMINOUS
                    } else {
                        null
                    }
                }
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
        // Get vault data
        val vault = plugin.vaultManager.getVault(location)
        if (vault == null) {
            player.sendMessage(plugin.getMessage("vault-not-found"))
            return
        }

        // Check permission bypass
        if (player.hasPermission("tcp.bypass.cooldown")) {
            openVault(player, vault, vaultType)
            return
        }

        // Check cooldown
        val (canOpen, remainingTime) = plugin.vaultManager.canOpenVault(player.uniqueId, vault)

        if (!canOpen) {
            // Vault is on cooldown
            val timeString = MessageUtil.formatTime(remainingTime)
            player.sendMessage(plugin.getMessage("vault-cooldown",
                "type" to vaultType.displayName,
                "time" to timeString
            ))

            // Show cooldown particles
            showCooldownParticles(player, location, vaultType)
            playErrorSound(player)
        } else {
            // Can open the vault
            openVault(player, vault, vaultType)
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
        // Record the opening
        plugin.vaultManager.recordOpen(player.uniqueId, vault.id)

        // Generate loot (async, player might disconnect during this)
        val loot = plugin.lootManager.generateLoot(vault.lootTable, player)

        // MUST switch to main thread for player access
        withContext(Dispatchers.Main) {
            // Verify player still online
            if (!player.isOnline) {
                plugin.logger.info("Player ${player.name} disconnected during vault open")
                return@withContext
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
        try {
            val sound = Sound.valueOf(soundName)
            player.playSound(location, sound, 1.0f, 1.0f)
        } catch (_: IllegalArgumentException) {
            // Sound not found, silently fail
        }
    }

    /**
     * Plays error sound to the player.
     */
    private fun playErrorSound(player: org.bukkit.entity.Player) {
        if (!plugin.config.getBoolean("vaults.play-sound-on-open", true)) return

        val soundName = plugin.config.getString("vaults.sounds.cooldown", "BLOCK_NOTE_BLOCK_BASS")!!
        try {
            val sound = Sound.valueOf(soundName)
            player.playSound(player.location, sound, 1.0f, 0.5f)
        } catch (_: IllegalArgumentException) {
            // Sound not found, silently fail
        }
    }
}
