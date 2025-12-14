package io.github.darkstarworks.trialChamberPro.managers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.Chamber
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages spectator mode for players in Trial Chambers.
 *
 * Features:
 * - Players can spectate after death
 * - Keeps spectators within chamber bounds
 * - Automatic exit when chamber resets or empties
 * - Restores previous game mode on exit
 */
class SpectatorManager(private val plugin: TrialChamberPro) {

    /**
     * Data for a spectating player.
     */
    data class SpectatorData(
        val playerUUID: UUID,
        val chamberId: Int,
        val chamberName: String,
        val previousGameMode: GameMode,
        val previousLocation: Location,
        val startTime: Long = System.currentTimeMillis()
    )

    // Active spectators: UUID -> SpectatorData
    private val spectators = ConcurrentHashMap<UUID, SpectatorData>()

    // Players pending spectator confirmation
    private val pendingSpectate = ConcurrentHashMap<UUID, SpectatorData>()

    /**
     * Offers spectator mode to a player who died in a chamber.
     * Returns true if the offer was made.
     */
    fun offerSpectatorMode(player: Player, chamber: Chamber, deathLocation: Location): Boolean {
        if (!plugin.config.getBoolean("spectator-mode.enabled", true)) {
            return false
        }

        if (!player.hasPermission("tcp.spectate")) {
            return false
        }

        // Don't offer if player is already spectating or has a pending offer
        if (spectators.containsKey(player.uniqueId) || pendingSpectate.containsKey(player.uniqueId)) {
            return false
        }

        // Create pending spectate data
        val data = SpectatorData(
            playerUUID = player.uniqueId,
            chamberId = chamber.id,
            chamberName = chamber.name,
            previousGameMode = player.gameMode,
            previousLocation = deathLocation.clone()
        )

        pendingSpectate[player.uniqueId] = data

        // Send offer message
        player.sendMessage(plugin.getMessage("spectate-offer", "chamber" to chamber.name))
        player.sendMessage(plugin.getMessage("spectate-hint"))

        // Expire the offer after configured time
        val timeoutSeconds = plugin.config.getInt("spectator-mode.offer-timeout", 30)
        plugin.scheduler.runTaskLater(Runnable {
            if (pendingSpectate.remove(player.uniqueId) != null) {
                if (player.isOnline) {
                    player.sendMessage(plugin.getMessage("spectate-offer-expired"))
                }
            }
        }, (timeoutSeconds * 20).toLong())

        return true
    }

    /**
     * Accepts the spectator mode offer.
     */
    fun acceptSpectatorMode(player: Player): Boolean {
        val data = pendingSpectate.remove(player.uniqueId) ?: return false

        // Get the chamber
        val chamber = plugin.chamberManager.getCachedChamberById(data.chamberId)
        if (chamber == null) {
            player.sendMessage(plugin.getMessage("spectate-chamber-not-found"))
            return false
        }

        // Check if there are other players in the chamber
        val playersInChamber = chamber.getPlayersInside(plugin.server).filter { it.uniqueId != player.uniqueId }
        if (playersInChamber.isEmpty() && !plugin.config.getBoolean("spectator-mode.allow-solo-spectate", false)) {
            player.sendMessage(plugin.getMessage("spectate-no-players"))
            return false
        }

        // Store spectator data (before async operation)
        spectators[player.uniqueId] = data

        // Folia-safe: All player operations must run on entity's region thread
        val center = chamber.getCenter()
        val startMessage = plugin.getMessage("spectate-started", "chamber" to chamber.name)
        val exitHintMessage = plugin.getMessage("spectate-exit-hint")

        plugin.scheduler.runAtEntity(player, Runnable {
            if (!player.isOnline) return@Runnable

            // Put player in spectator mode (must be on entity's thread)
            player.gameMode = GameMode.SPECTATOR

            // Teleport to chamber center
            player.teleport(center)

            // Send messages
            player.sendMessage(startMessage)
            player.sendMessage(exitHintMessage)
        })

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[Spectator] ${player.name} is now spectating chamber ${chamber.name}")
        }

        return true
    }

    /**
     * Declines the spectator mode offer.
     */
    fun declineSpectatorMode(player: Player): Boolean {
        if (pendingSpectate.remove(player.uniqueId) != null) {
            player.sendMessage(plugin.getMessage("spectate-declined"))
            return true
        }
        return false
    }

    /**
     * Exits spectator mode for a player.
     */
    fun exitSpectatorMode(player: Player, teleportToExit: Boolean = true): Boolean {
        val data = spectators.remove(player.uniqueId) ?: return false

        // Folia-safe: All player operations must run on entity's region thread
        val exitMessage = plugin.getMessage("spectate-exited")
        val exitLocation = if (teleportToExit) {
            val chamber = plugin.chamberManager.getCachedChamberById(data.chamberId)
            chamber?.getExitLocation() ?: data.previousLocation
        } else null

        plugin.scheduler.runAtEntity(player, Runnable {
            if (!player.isOnline) return@Runnable

            // Restore game mode (must be on entity's thread)
            player.gameMode = data.previousGameMode

            // Teleport if requested
            if (exitLocation != null) {
                player.teleport(exitLocation)
            }

            // Send message
            player.sendMessage(exitMessage)
        })

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("[Spectator] ${player.name} exited spectator mode for chamber ${data.chamberName}")
        }

        return true
    }

    /**
     * Checks if a player is spectating.
     */
    fun isSpectating(player: Player): Boolean = spectators.containsKey(player.uniqueId)

    /**
     * Checks if a player is spectating a specific chamber.
     */
    fun isSpectatingChamber(player: Player, chamberId: Int): Boolean {
        return spectators[player.uniqueId]?.chamberId == chamberId
    }

    /**
     * Gets the chamber a player is spectating, if any.
     */
    fun getSpectatingChamber(player: Player): Chamber? {
        val data = spectators[player.uniqueId] ?: return null
        return plugin.chamberManager.getCachedChamberById(data.chamberId)
    }

    /**
     * Checks if a location is within the spectator's allowed bounds.
     */
    fun isWithinSpectatorBounds(player: Player, location: Location): Boolean {
        val data = spectators[player.uniqueId] ?: return true // Not spectating, allow
        val chamber = plugin.chamberManager.getCachedChamberById(data.chamberId) ?: return true

        // Allow some extra space around the chamber for spectators
        val buffer = plugin.config.getInt("spectator-mode.boundary-buffer", 10)
        return location.world?.name == chamber.world &&
               location.blockX >= chamber.minX - buffer &&
               location.blockX <= chamber.maxX + buffer &&
               location.blockY >= chamber.minY - buffer &&
               location.blockY <= chamber.maxY + buffer &&
               location.blockZ >= chamber.minZ - buffer &&
               location.blockZ <= chamber.maxZ + buffer
    }

    /**
     * Gets all spectators for a specific chamber.
     */
    fun getSpectatorsForChamber(chamberId: Int): List<Player> {
        return spectators.entries
            .filter { it.value.chamberId == chamberId }
            .mapNotNull { plugin.server.getPlayer(it.key) }
    }

    /**
     * Removes all spectators from a chamber (e.g., on reset).
     */
    fun exitAllSpectatorsFromChamber(chamberId: Int) {
        val toRemove = spectators.entries.filter { it.value.chamberId == chamberId }

        toRemove.forEach { (uuid, _) ->
            val player = plugin.server.getPlayer(uuid)
            if (player != null) {
                exitSpectatorMode(player, teleportToExit = true)
            } else {
                spectators.remove(uuid)
            }
        }
    }

    /**
     * Cleans up when a player disconnects.
     */
    fun handlePlayerQuit(player: Player) {
        pendingSpectate.remove(player.uniqueId)
        spectators.remove(player.uniqueId)
    }

    /**
     * Checks if a player has a pending spectate offer.
     */
    fun hasPendingOffer(player: Player): Boolean = pendingSpectate.containsKey(player.uniqueId)

    /**
     * Gets all active spectators count.
     */
    fun getActiveSpectatorCount(): Int = spectators.size

    /**
     * Shuts down the spectator manager.
     */
    fun shutdown() {
        // Exit all spectators
        spectators.keys.toList().forEach { uuid ->
            val player = plugin.server.getPlayer(uuid)
            if (player != null) {
                exitSpectatorMode(player, teleportToExit = false)
            }
        }
        spectators.clear()
        pendingSpectate.clear()
    }
}
