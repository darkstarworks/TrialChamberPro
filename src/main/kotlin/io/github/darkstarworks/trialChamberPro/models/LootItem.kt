package io.github.darkstarworks.trialChamberPro.models

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.potion.PotionType

/**
 * Represents an enchantment with a level range for randomization.
 */
data class EnchantmentRange(
    val enchantment: Enchantment,
    val minLevel: Int,
    val maxLevel: Int
)

/**
 * Represents a loot item configuration from loot.yml.
 * Supports advanced features like tipped arrows, potions with levels,
 * enchantment randomization, and variable durability.
 */
data class LootItem(
    val type: Material,
    val amountMin: Int,
    val amountMax: Int,
    val weight: Double,
    val name: String? = null,
    val lore: List<String>? = null,

    // Fixed enchantments (legacy format, still supported)
    val enchantments: Map<Enchantment, Int>? = null,

    // Enchantment randomization (new features)
    val enchantmentRanges: Map<Enchantment, EnchantmentRange>? = null, // e.g., SHARPNESS with level 1-5
    val randomEnchantmentPool: List<EnchantmentRange>? = null, // Pick one random enchantment from this pool

    // Potion/Tipped Arrow support
    val potionType: PotionType? = null, // For potions and tipped arrows
    val potionLevel: Int? = null, // Potion effect amplifier (0 = level I, 1 = level II, etc.)
    val customEffectType: String? = null, // For custom effects like BAD_OMEN (ominous bottles)
    val isOminousPotion: Boolean = false, // For ominous potions (1.21+ feature)

    // Variable durability
    val durabilityMin: Int? = null, // Minimum durability (as damage value)
    val durabilityMax: Int? = null, // Maximum durability (as damage value)

    val enabled: Boolean = true
)

/**
 * Represents a command-based reward.
 */
data class CommandReward(
    val weight: Double,
    val commands: List<String>,
    val displayName: String
)

/**
 * Represents a loot pool within a loot table.
 * Each pool rolls independently (like vanilla's common/rare/unique).
 */
data class LootPool(
    val name: String,
    val minRolls: Int,
    val maxRolls: Int,
    val guaranteedItems: List<LootItem> = emptyList(),
    val weightedItems: List<LootItem> = emptyList(),
    val commandRewards: List<CommandReward> = emptyList()
)

/**
 * Represents a loot table configuration.
 * Can contain multiple pools (like vanilla: common, rare, unique).
 *
 * For backwards compatibility, supports both:
 * - Legacy single-pool format (minRolls, maxRolls, guaranteedItems, weightedItems)
 * - New multi-pool format (pools list)
 */
data class LootTable(
    val name: String,
    // Legacy format (backwards compatible)
    val minRolls: Int = 3,
    val maxRolls: Int = 5,
    val guaranteedItems: List<LootItem> = emptyList(),
    val weightedItems: List<LootItem> = emptyList(),
    val commandRewards: List<CommandReward> = emptyList(),
    // New multi-pool format
    val pools: List<LootPool> = emptyList()
) {
    /**
     * Returns true if this table uses the legacy single-pool format.
     */
    fun isLegacyFormat(): Boolean = pools.isEmpty()

    /**
     * Gets all effective pools (converts legacy format to single pool if needed).
     */
    fun getEffectivePools(): List<LootPool> {
        return if (isLegacyFormat()) {
            // Convert legacy format to single pool
            listOf(
                LootPool(
                    name = "main",
                    minRolls = minRolls,
                    maxRolls = maxRolls,
                    guaranteedItems = guaranteedItems,
                    weightedItems = weightedItems,
                    commandRewards = commandRewards
                )
            )
        } else {
            pools
        }
    }
}
