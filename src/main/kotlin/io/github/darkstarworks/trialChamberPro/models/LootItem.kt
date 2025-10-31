package io.github.darkstarworks.trialChamberPro.models

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment

/**
 * Represents a loot item configuration from loot.yml.
 */
data class LootItem(
    val type: Material,
    val amountMin: Int,
    val amountMax: Int,
    val weight: Double,
    val name: String? = null,
    val lore: List<String>? = null,
    val enchantments: Map<Enchantment, Int>? = null,
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
 * Represents a loot table configuration.
 */
data class LootTable(
    val name: String,
    val minRolls: Int,
    val maxRolls: Int,
    val guaranteedItems: List<LootItem>,
    val weightedItems: List<LootItem>,
    val commandRewards: List<CommandReward> = emptyList()
)
