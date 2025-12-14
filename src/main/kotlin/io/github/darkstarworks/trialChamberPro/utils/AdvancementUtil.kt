package io.github.darkstarworks.trialChamberPro.utils

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player

/**
 * Utility for granting vanilla Minecraft advancements related to Trial Chambers.
 *
 * When chambers are created via plugin schematics (rather than natural generation),
 * vanilla advancement triggers don't fire. This utility manually grants those
 * advancements to preserve the vanilla experience.
 *
 * IMPORTANT: All methods must be called from the main thread (Paper) or
 * the entity's region thread (Folia).
 */
object AdvancementUtil {

    /**
     * Advancement: "Minecraft: Trial(s) Edition"
     * Granted when stepping foot in a Trial Chamber.
     */
    private const val TRIALS_EDITION = "adventure/minecraft_trials_edition"

    /**
     * Advancement: "Under Lock and Key"
     * Granted when unlocking a Vault using a Trial Key.
     */
    private const val UNDER_LOCK_AND_KEY = "adventure/under_lock_and_key"

    /**
     * Advancement: "Revaulting"
     * Granted when unlocking an Ominous Vault with an Ominous Trial Key.
     */
    private const val REVAULTING = "adventure/revaulting"

    /**
     * Grants the "Minecraft: Trial(s) Edition" advancement for entering a Trial Chamber.
     *
     * @param player The player to grant the advancement to
     * @return true if the advancement was granted (or already had it), false if advancement not found
     */
    fun grantTrialChamberEntry(player: Player): Boolean {
        return grantAdvancement(player, TRIALS_EDITION)
    }

    /**
     * Grants the "Under Lock and Key" advancement for opening a normal vault.
     *
     * @param player The player to grant the advancement to
     * @return true if the advancement was granted (or already had it), false if advancement not found
     */
    fun grantVaultUnlock(player: Player): Boolean {
        return grantAdvancement(player, UNDER_LOCK_AND_KEY)
    }

    /**
     * Grants the "Revaulting" advancement for opening an ominous vault.
     * Also grants "Under Lock and Key" as it's a prerequisite.
     *
     * @param player The player to grant the advancement to
     * @return true if the advancement was granted (or already had it), false if advancement not found
     */
    fun grantOminousVaultUnlock(player: Player): Boolean {
        // Grant prerequisite first (Under Lock and Key)
        grantAdvancement(player, UNDER_LOCK_AND_KEY)
        // Then grant Revaulting
        return grantAdvancement(player, REVAULTING)
    }

    /**
     * Grants a specific advancement to a player by awarding all its criteria.
     *
     * @param player The player to grant the advancement to
     * @param advancementKey The advancement resource location (e.g., "adventure/under_lock_and_key")
     * @return true if successful, false if advancement not found
     */
    private fun grantAdvancement(player: Player, advancementKey: String): Boolean {
        val key = NamespacedKey.minecraft(advancementKey)
        val advancement = Bukkit.getAdvancement(key)

        if (advancement == null) {
            Bukkit.getLogger().warning("[TrialChamberPro] Advancement not found: $key")
            return false
        }

        val progress = player.getAdvancementProgress(advancement)

        // Award all remaining criteria
        for (criteria in advancement.criteria) {
            if (!progress.isDone) {
                progress.awardCriteria(criteria)
            }
        }

        return true
    }

    /**
     * Checks if a player already has a specific advancement completed.
     *
     * @param player The player to check
     * @param advancementKey The advancement resource location
     * @return true if completed, false otherwise
     */
    fun hasAdvancement(player: Player, advancementKey: String): Boolean {
        val advancement = Bukkit.getAdvancement(NamespacedKey.minecraft(advancementKey))
            ?: return false
        return player.getAdvancementProgress(advancement).isDone
    }

    /**
     * Checks if a player has the Trial Chamber entry advancement.
     */
    fun hasTrialChamberEntry(player: Player): Boolean {
        return hasAdvancement(player, TRIALS_EDITION)
    }
}
