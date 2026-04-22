package io.github.darkstarworks.trialChamberPro.managers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.CommandReward
import io.github.darkstarworks.trialChamberPro.models.LootItem
import io.github.darkstarworks.trialChamberPro.models.LootPool
import io.github.darkstarworks.trialChamberPro.models.LootTable
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.io.File
import kotlin.random.Random

/**
 * Manages loot table parsing and loot generation.
 * Handles weighted random selection, custom items, enchantments, and economy rewards.
 */
class LootManager(private val plugin: TrialChamberPro) {

    private val lootTables = mutableMapOf<String, LootTable>()

    fun getTable(name: String): LootTable? = lootTables[name]


    fun updateTable(table: LootTable) {
        lootTables[table.name] = table
    }

    /**
     * Loads all loot tables from loot.yml.
     */
    fun loadLootTables() {
        lootTables.clear()

        val lootFile = File(plugin.dataFolder, "loot.yml")
        if (!lootFile.exists()) {
            plugin.logger.warning("loot.yml not found, using defaults")
            return
        }

        val config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(lootFile)
        val tablesSection = config.getConfigurationSection("loot-tables") ?: return

        tablesSection.getKeys(false).forEach { tableName ->
            val tableSection = tablesSection.getConfigurationSection(tableName) ?: return@forEach

            try {
                val lootTable = parseLootTable(tableName, tableSection)
                lootTables[tableName] = lootTable
                plugin.logger.info("Loaded loot table: $tableName (${lootTable.weightedItems.size} weighted items)")
            } catch (e: Exception) {
                plugin.logger.severe("Failed to parse loot table $tableName: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Parses a loot table from configuration.
     * Supports both legacy single-pool and new multi-pool formats.
     */
    private fun parseLootTable(name: String, section: ConfigurationSection): LootTable {
        // Check if this is a multi-pool format
        val poolsSection = section.getList("pools")

        if (poolsSection != null && poolsSection.isNotEmpty()) {
            // New multi-pool format
            val pools = mutableListOf<LootPool>()
            val maxPools = plugin.config.getInt("loot.max-pools-per-table", 5)

            poolsSection.take(maxPools).forEach { poolData ->
                if (poolData is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    parseLootPool(poolData as Map<String, Any>)?.let { pools.add(it) }
                }
            }

            return LootTable(name = name, pools = pools)
        } else {
            // Legacy single-pool format (backwards compatible)
            val minRolls = section.getInt("min-rolls", 3)
            val maxRolls = section.getInt("max-rolls", 5)

            // Parse guaranteed items
            val guaranteedItems = mutableListOf<LootItem>()
            section.getList("guaranteed-items")?.forEach { item ->
                if (item is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    parseLootItem(item as Map<String, Any>)?.let { guaranteedItems.add(it) }
                }
            }

            // Parse weighted items
            val weightedItems = mutableListOf<LootItem>()
            section.getList("weighted-items")?.forEach { item ->
                if (item is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    parseLootItem(item as Map<String, Any>)?.let { weightedItems.add(it) }
                }
            }

            // Parse command rewards (optional)
            val commandRewards = mutableListOf<CommandReward>()
            section.getList("command-rewards")?.forEach { item ->
                if (item is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    parseCommandReward(item as Map<String, Any>)?.let { commandRewards.add(it) }
                }
            }

            return LootTable(name, minRolls, maxRolls, guaranteedItems, weightedItems, commandRewards)
        }
    }

    /**
     * Parses a loot pool from configuration.
     */
    private fun parseLootPool(data: Map<String, Any>): LootPool? {
        val poolName = data["name"] as? String ?: return null
        val minRolls = (data["min-rolls"] as? Number)?.toInt() ?: 1
        val maxRolls = (data["max-rolls"] as? Number)?.toInt() ?: 1

        // Parse guaranteed items
        val guaranteedItems = mutableListOf<LootItem>()
        @Suppress("UNCHECKED_CAST")
        (data["guaranteed-items"] as? List<Map<String, Any>>)?.forEach { item ->
            parseLootItem(item)?.let { guaranteedItems.add(it) }
        }

        // Parse weighted items
        val weightedItems = mutableListOf<LootItem>()
        @Suppress("UNCHECKED_CAST")
        (data["weighted-items"] as? List<Map<String, Any>>)?.forEach { item ->
            parseLootItem(item)?.let { weightedItems.add(it) }
        }

        // Parse command rewards
        val commandRewards = mutableListOf<CommandReward>()
        @Suppress("UNCHECKED_CAST")
        (data["command-rewards"] as? List<Map<String, Any>>)?.forEach { item ->
            parseCommandReward(item)?.let { commandRewards.add(it) }
        }

        return LootPool(poolName, minRolls, maxRolls, guaranteedItems, weightedItems, commandRewards)
    }

    /**
     * Parses a loot item from configuration.
     * Supports advanced features: potions, tipped arrows, enchantment ranges, variable durability.
     */
    private fun parseLootItem(data: Map<String, Any>): LootItem? {
        val typeStr = data["type"] as? String ?: return null

        // Check for common mistake: using type: COMMAND instead of command-rewards
        if (typeStr.equals("COMMAND", ignoreCase = true)) {
            plugin.logger.severe("═══════════════════════════════════════════════════════════════════")
            plugin.logger.severe("ERROR: 'type: COMMAND' is NOT valid!")
            plugin.logger.severe("Command rewards use a different format:")
            plugin.logger.severe("")
            plugin.logger.severe("WRONG:")
            plugin.logger.severe("  weighted-items:")
            plugin.logger.severe("    - type: COMMAND  ← Don't use this!")
            plugin.logger.severe("")
            plugin.logger.severe("CORRECT:")
            plugin.logger.severe("  command-rewards:")
            plugin.logger.severe("    - weight: 25.0")
            plugin.logger.severe("      commands:")
            plugin.logger.severe("        - \"eco give {player} 1000\"")
            plugin.logger.severe("      display-name: \"&6+1000 Coins\"")
            plugin.logger.severe("")
            plugin.logger.severe("See loot.yml for full examples!")
            plugin.logger.severe("═══════════════════════════════════════════════════════════════════")
            return null
        }

        // Custom item plugin support — type: CUSTOM_ITEM with plugin + item-id fields
        val customItemPlugin = data["plugin"] as? String
        val customItemId = data["item-id"] as? String

        if (typeStr.equals("CUSTOM_ITEM", ignoreCase = true) && customItemPlugin == null) {
            plugin.logger.warning(
                "type: CUSTOM_ITEM requires 'plugin:' and 'item-id:' fields. Example:\n" +
                "  - type: CUSTOM_ITEM\n" +
                "    plugin: Nexo\n" +
                "    item-id: \"my_item\"\n" +
                "    weight: 5.0"
            )
            return null
        }

        val material = if (typeStr.equals("CUSTOM_ITEM", ignoreCase = true) || customItemPlugin != null) {
            Material.AIR // sentinel — real item resolved at generation time via plugin API
        } else {
            try {
                Material.valueOf(typeStr.uppercase())
            } catch (_: IllegalArgumentException) {
                plugin.logger.warning("Invalid material: $typeStr (if this is a custom item, use 'type: CUSTOM_ITEM' with 'plugin:' and 'item-id:')")
                return null
            }
        }

        val customModelData = (data["custom-model-data"] as? Number)?.toInt()

        val amountMin = (data["amount-min"] as? Number)?.toInt() ?: 1
        val amountMax = (data["amount-max"] as? Number)?.toInt() ?: 1
        val weight = (data["weight"] as? Number)?.toDouble() ?: 1.0

        val name = data["name"] as? String
        @Suppress("UNCHECKED_CAST")
        val lore = data["lore"] as? List<String>

        // Parse fixed enchantments (legacy format: "SHARPNESS:5")
        val enchantments = mutableMapOf<Enchantment, Int>()
        @Suppress("UNCHECKED_CAST")
        (data["enchantments"] as? List<String>)?.forEach { enchStr ->
            val parts = enchStr.split(":")
            if (parts.size == 2) {
                val enchantment = io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)
                    .get(org.bukkit.NamespacedKey.minecraft(parts[0].lowercase()))
                val level = parts[1].toIntOrNull()
                if (enchantment != null && level != null) {
                    enchantments[enchantment] = level
                }
            }
        }

        // Parse enchantment ranges (new format: "SHARPNESS:1:5" for level 1-5)
        val enchantmentRanges = mutableMapOf<Enchantment, io.github.darkstarworks.trialChamberPro.models.EnchantmentRange>()
        @Suppress("UNCHECKED_CAST")
        (data["enchantment-ranges"] as? List<String>)?.forEach { enchStr ->
            val parts = enchStr.split(":")
            if (parts.size == 3) {
                val enchantment = io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)
                    .get(org.bukkit.NamespacedKey.minecraft(parts[0].lowercase()))
                val minLevel = parts[1].toIntOrNull()
                val maxLevel = parts[2].toIntOrNull()
                if (enchantment != null && minLevel != null && maxLevel != null) {
                    enchantmentRanges[enchantment] = io.github.darkstarworks.trialChamberPro.models.EnchantmentRange(enchantment, minLevel, maxLevel)
                }
            }
        }

        // Parse random enchantment pool (pick one random enchantment from this list)
        val randomEnchantmentPool = mutableListOf<io.github.darkstarworks.trialChamberPro.models.EnchantmentRange>()
        @Suppress("UNCHECKED_CAST")
        (data["random-enchantment-pool"] as? List<String>)?.forEach { enchStr ->
            val parts = enchStr.split(":")
            if (parts.size == 3) {
                val enchantment = io.papermc.paper.registry.RegistryAccess.registryAccess()
                    .getRegistry(io.papermc.paper.registry.RegistryKey.ENCHANTMENT)
                    .get(org.bukkit.NamespacedKey.minecraft(parts[0].lowercase()))
                val minLevel = parts[1].toIntOrNull()
                val maxLevel = parts[2].toIntOrNull()
                if (enchantment != null && minLevel != null && maxLevel != null) {
                    randomEnchantmentPool.add(io.github.darkstarworks.trialChamberPro.models.EnchantmentRange(enchantment, minLevel, maxLevel))
                }
            }
        }

        // Parse potion type for potions and tipped arrows
        val potionTypeStr = data["potion-type"] as? String
        val potionType = potionTypeStr?.let {
            try {
                org.bukkit.potion.PotionType.valueOf(it.uppercase())
            } catch (_: IllegalArgumentException) {
                plugin.logger.warning("Invalid potion type: $it")
                null
            }
        }

        val potionLevel = (data["potion-level"] as? Number)?.toInt()
        val customEffectType = data["custom-effect-type"] as? String
        val isOminousPotion = (data["ominous-potion"] as? Boolean) ?: false
        val effectDuration = (data["effect-duration"] as? Number)?.toInt()

        // Parse variable durability
        val durabilityMin = (data["durability-min"] as? Number)?.toInt()
        val durabilityMax = (data["durability-max"] as? Number)?.toInt()

        // Parse goat horn instrument (8 variants: PONDER, SING, SEEK, FEEL, ADMIRE, CALL, YEARN, DREAM)
        val instrument = data["instrument"] as? String

        val enabled = (data["enabled"] as? Boolean) ?: true

        return LootItem(
            type = material,
            amountMin = amountMin,
            amountMax = amountMax,
            weight = weight,
            name = name,
            lore = lore,
            enchantments = enchantments.takeIf { it.isNotEmpty() },
            enchantmentRanges = enchantmentRanges.takeIf { it.isNotEmpty() },
            randomEnchantmentPool = randomEnchantmentPool.takeIf { it.isNotEmpty() },
            potionType = potionType,
            potionLevel = potionLevel,
            customEffectType = customEffectType,
            isOminousPotion = isOminousPotion,
            effectDuration = effectDuration,
            durabilityMin = durabilityMin,
            durabilityMax = durabilityMax,
            instrument = instrument,
            customItemPlugin = customItemPlugin,
            customItemId = customItemId,
            customModelData = customModelData,
            enabled = enabled
        )
    }

    /**
     * Parses a command reward from configuration.
     */
    private fun parseCommandReward(data: Map<String, Any>): CommandReward? {
        val weight = (data["weight"] as? Number)?.toDouble() ?: return null
        @Suppress("UNCHECKED_CAST")
        val commands = data["commands"] as? List<String> ?: return null
        val displayName = data["display-name"] as? String ?: "Command Reward"

        return CommandReward(weight, commands, displayName)
    }

    /**
     * Generates loot from a loot table.
     * Supports both legacy single-pool and new multi-pool formats.
     *
     * @param tableName The loot table name
     * @param player The player receiving the loot (for placeholders)
     * @return List of generated items
     */
    fun generateLoot(tableName: String, player: Player): List<ItemStack> {
        // Debug logging
        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("==== LOOT GENERATION DEBUG ====")
            plugin.logger.info("Requested table: '$tableName'")
            plugin.logger.info("Available tables: ${lootTables.keys.joinToString(", ")}")
            plugin.logger.info("Table exists: ${lootTables.containsKey(tableName)}")
        }

        val lootTable = lootTables[tableName]
        if (lootTable == null) {
            plugin.logger.warning("Loot table not found: $tableName (available: ${lootTables.keys.joinToString(", ")})")
            return emptyList()
        }

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("Using loot table: ${lootTable.name}")
            plugin.logger.info("Pools: ${lootTable.getEffectivePools().size}")
        }

        val items = mutableListOf<ItemStack>()

        // Get all effective pools (handles both legacy and new format)
        val pools = lootTable.getEffectivePools()

        // Generate loot from each pool independently (like vanilla)
        pools.forEach { pool ->
            items.addAll(generateLootFromPool(pool, player))
        }

        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
            plugin.logger.info("Generated ${items.size} items from table '$tableName'")
            plugin.logger.info("================================")
        }

        return items
    }

    /**
     * Generates loot from a single pool.
     */
    private fun generateLootFromPool(pool: LootPool, player: Player): List<ItemStack> {
        val items = mutableListOf<ItemStack>()

        // Add all guaranteed items (respect enabled flag)
        pool.guaranteedItems.filter { it.enabled }.forEach { lootItem ->
            items.add(createItemStack(lootItem, player))
        }

        // Calculate number of rolls (with optional LUCK bonus)
        var rolls = Random.nextInt(pool.minRolls, pool.maxRolls + 1)

        // Apply LUCK effect/attribute if enabled
        if (plugin.config.getBoolean("loot.apply-luck-effect", false)) {
            var totalLuckBonus = 0

            // Check for LUCK potion effect (temporary from potions, beacons, etc.)
            val luckEffect = player.getPotionEffect(org.bukkit.potion.PotionEffectType.LUCK)
            if (luckEffect != null) {
                // Each level of LUCK adds +1 bonus roll
                val effectBonus = luckEffect.amplifier + 1
                totalLuckBonus += effectBonus

                if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                    plugin.logger.info("Player ${player.name} has LUCK effect level ${luckEffect.amplifier + 1} (+$effectBonus rolls)")
                }
            }

            // Check for LUCK attribute (permanent from items/equipment)
            // Try to get the LUCK attribute - may not exist in all versions
            val luckAttribute = try {
                player.getAttribute(org.bukkit.attribute.Attribute.LUCK)
            } catch (e: Exception) {
                null
            }
            if (luckAttribute != null) {
                val baseLuck = luckAttribute.baseValue
                val totalLuck = luckAttribute.value
                val attributeLuck = totalLuck - baseLuck // Bonus from items

                if (attributeLuck > 0) {
                    // Each full point of luck attribute adds +1 bonus roll
                    val attributeBonus = attributeLuck.toInt()
                    totalLuckBonus += attributeBonus

                    if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                        plugin.logger.info("Player ${player.name} has +${attributeLuck} LUCK attribute from items (+$attributeBonus rolls)")
                    }
                }
            }

            if (totalLuckBonus > 0) {
                rolls += totalLuckBonus

                if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                    plugin.logger.info("Total LUCK bonus for ${player.name} in pool '${pool.name}': +$totalLuckBonus rolls (total: $rolls)")
                }
            }
        }

        // Roll for weighted items (respect enabled flag)
        val enabledWeighted = pool.weightedItems.filter { it.enabled }
        repeat(rolls) {
            // Select a random weighted item
            val selectedItem = selectWeightedItem(enabledWeighted)
            if (selectedItem != null) {
                items.add(createItemStack(selectedItem, player))
            }
        }

        // Process command rewards
        pool.commandRewards.forEach { reward ->
            if (Random.nextDouble() * 100.0 < reward.weight) {
                executeCommandReward(reward, player)
            }
        }

        return items
    }

    /**
     * Selects a random item based on weights.
     */
    private fun selectWeightedItem(items: List<LootItem>): LootItem? {
        if (items.isEmpty()) return null

        val totalWeight = items.sumOf { it.weight }
        var random = Random.nextDouble() * totalWeight

        items.forEach { item ->
            random -= item.weight
            if (random <= 0) {
                return item
            }
        }

        return items.lastOrNull()
    }

    /**
     * Creates an ItemStack from a LootItem.
     * Applies potions, enchantments, random enchantments, and variable durability.
     */
    private fun createItemStack(lootItem: LootItem, player: Player): ItemStack {
        val amount = Random.nextInt(lootItem.amountMin, lootItem.amountMax + 1)

        // Resolve custom plugin item (Nexo / ItemsAdder / Oraxen / CraftEngine / MythicCrucible)
        if (lootItem.customItemPlugin != null && lootItem.customItemId != null) {
            val resolved = resolveCustomItem(lootItem.customItemPlugin, lootItem.customItemId)
            if (resolved == null) {
                plugin.logger.warning("Skipping unresolvable custom item '${lootItem.customItemId}' (plugin: ${lootItem.customItemPlugin})")
                return ItemStack(Material.AIR, 0)
            }
            val itemStack = resolved.clone().also { it.amount = amount }
            // Apply any name/lore/enchantments specified on top of the custom item base
            itemStack.itemMeta = itemStack.itemMeta?.apply {
                lootItem.name?.let {
                    displayName(net.kyori.adventure.text.Component.text(colorize(replacePlaceholders(it, player))))
                }
                lootItem.lore?.let { loreLines ->
                    lore(loreLines.map { line ->
                        net.kyori.adventure.text.Component.text(colorize(replacePlaceholders(line, player)))
                    })
                }
            }
            lootItem.enchantments?.forEach { (ench, level) -> itemStack.addUnsafeEnchantment(ench, level) }
            lootItem.enchantmentRanges?.values?.forEach { range ->
                itemStack.addUnsafeEnchantment(range.enchantment, Random.nextInt(range.minLevel, range.maxLevel + 1))
            }
            if (!lootItem.randomEnchantmentPool.isNullOrEmpty()) {
                val r = lootItem.randomEnchantmentPool.random()
                itemStack.addUnsafeEnchantment(r.enchantment, Random.nextInt(r.minLevel, r.maxLevel + 1))
            }
            return itemStack
        }

        // Determine actual material type (handle ominous potions)
        val actualMaterial = if (lootItem.isOminousPotion && lootItem.type == Material.POTION) {
            Material.OMINOUS_BOTTLE
        } else {
            lootItem.type
        }

        val itemStack = ItemStack(actualMaterial, amount)

        if (plugin.config.getBoolean("debug.verbose-logging", false) && lootItem.isOminousPotion) {
            plugin.logger.info("Creating OMINOUS_BOTTLE: potionLevel=${lootItem.potionLevel}, customEffectType=${lootItem.customEffectType}, effectDuration=${lootItem.effectDuration}")
            plugin.logger.info("ItemMeta type: ${itemStack.itemMeta?.javaClass?.simpleName}, is PotionMeta: ${itemStack.itemMeta is org.bukkit.inventory.meta.PotionMeta}")
        }

        itemStack.itemMeta = itemStack.itemMeta?.apply {
            // Set custom name
            lootItem.name?.let {
                displayName(net.kyori.adventure.text.Component.text(colorize(replacePlaceholders(it, player))))
            }

            // Set lore
            lootItem.lore?.let { loreLines ->
                lore(loreLines.map { line ->
                    net.kyori.adventure.text.Component.text(colorize(replacePlaceholders(line, player)))
                })
            }

            // Apply custom model data
            lootItem.customModelData?.let { setCustomModelData(it) }

            // Handle OMINOUS_BOTTLE separately (uses OminousBottleMeta, not PotionMeta)
            if (this is org.bukkit.inventory.meta.OminousBottleMeta) {
                // Set Bad Omen amplifier for ominous bottles
                val amplifier = lootItem.potionLevel ?: 0

                if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                    plugin.logger.info("Setting ominous bottle amplifier: $amplifier (Bad Omen ${amplifier + 1})")
                }

                this.amplifier = amplifier
            }
            // Apply potion effects for POTION, SPLASH_POTION, LINGERING_POTION, TIPPED_ARROW
            else if (this is org.bukkit.inventory.meta.PotionMeta) {
                // Handle custom effect types (e.g., BAD_OMEN for ominous bottles)
                if (lootItem.customEffectType != null) {
                    if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                        plugin.logger.info("Processing custom effect type: ${lootItem.customEffectType}, potionLevel from config: ${lootItem.potionLevel}")
                    }

                    val effectType = try {
                        // Use registry access instead of deprecated getByName
                        org.bukkit.Registry.POTION_EFFECT_TYPE.get(
                            org.bukkit.NamespacedKey.minecraft(lootItem.customEffectType.lowercase())
                        )
                    } catch (_: Exception) {
                        null
                    }

                    if (effectType != null) {
                        val duration = lootItem.effectDuration ?: when (lootItem.type) {
                            Material.POTION -> 120000 // 100 minutes for Bad Omen (matches vanilla)
                            Material.SPLASH_POTION -> 2700 // 2:15 for splash
                            Material.LINGERING_POTION -> 900 // 45 seconds for lingering cloud
                            Material.TIPPED_ARROW -> 400 // 20 seconds for arrows
                            else -> 120000
                        }

                        val amplifier = lootItem.potionLevel ?: 0

                        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                            plugin.logger.info("Adding custom effect: type=${lootItem.customEffectType}, duration=$duration ticks (${duration/20}s), amplifier=$amplifier (level ${amplifier + 1})")
                        }

                        addCustomEffect(
                            org.bukkit.potion.PotionEffect(
                                effectType,
                                duration,
                                amplifier,
                                false, // ambient
                                true,  // particles
                                true   // icon
                            ),
                            true // overwrite existing effects
                        )

                        if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                            plugin.logger.info("Applied custom effect ${lootItem.customEffectType} level ${amplifier + 1} to ${lootItem.type}")
                        }
                    } else {
                        plugin.logger.warning("Invalid custom effect type: ${lootItem.customEffectType}")
                    }
                } else if (lootItem.potionType != null) {
                    // Handle standard potion types
                    // Use custom effect when: potion-level is specified OR effect-duration is specified
                    // This ensures effect-duration is not ignored when specified without potion-level
                    val useCustomEffect = lootItem.potionLevel != null ||
                        (lootItem.effectDuration != null && lootItem.effectDuration > 0)

                    if (useCustomEffect) {
                        // Get the effect type from the potion type
                        // Use getPotionEffects() instead of deprecated effectType property
                        val effectType = lootItem.potionType.potionEffects.firstOrNull()?.type

                        if (effectType != null) {
                            // Calculate duration - use explicit value if positive, otherwise calculate
                            val calculatedDuration = if (lootItem.effectDuration != null && lootItem.effectDuration > 0) {
                                lootItem.effectDuration
                            } else {
                                // Get base duration from the potion type (vanilla duration)
                                // Note: Some PotionTypes return 0 duration, so we check for both null and <= 0
                                val rawDuration = lootItem.potionType.potionEffects.firstOrNull()?.duration
                                val baseDuration = if (rawDuration != null && rawDuration > 0) rawDuration else 3600

                                // Apply vanilla duration multipliers based on item type
                                when (lootItem.type) {
                                    Material.POTION -> baseDuration  // 1.0x (base duration)
                                    Material.SPLASH_POTION -> (baseDuration * 0.75).toInt()  // 75% of base
                                    Material.LINGERING_POTION -> (baseDuration * 0.25).toInt()  // 25% of base
                                    Material.TIPPED_ARROW -> (baseDuration / 8)  // 1/8 of base (12.5%)
                                    else -> baseDuration
                                }
                            }

                            // Always enforce minimum duration (fixes 00:00 duration bug)
                            // Vanilla tipped arrows are typically 100-400 ticks (5-20 seconds)
                            val minDuration = when (lootItem.type) {
                                Material.TIPPED_ARROW -> 100  // 5 seconds minimum
                                Material.LINGERING_POTION -> 200  // 10 seconds minimum
                                else -> 600  // 30 seconds minimum for other potions
                            }
                            val duration = maxOf(calculatedDuration, minDuration)

                            // Use specified potion level or default to 0 (Level I)
                            val amplifier = lootItem.potionLevel ?: 0

                            addCustomEffect(
                                org.bukkit.potion.PotionEffect(
                                    effectType,
                                    duration,
                                    amplifier,
                                    false,
                                    true,
                                    true
                                ),
                                true
                            )
                        } else {
                            // effectType is null (e.g., AWKWARD, MUNDANE, THICK potions)
                            // Fall back to base potion type, but log a warning if duration was specified
                            if (lootItem.effectDuration != null && lootItem.effectDuration > 0) {
                                plugin.logger.warning(
                                    "Potion type ${lootItem.potionType} has no effect - effect-duration will be ignored. " +
                                    "Use custom-effect-type for potions without standard effects."
                                )
                            }
                            basePotionType = lootItem.potionType
                        }
                    } else {
                        // No custom level or duration specified - use default base potion type
                        basePotionType = lootItem.potionType
                    }
                }
            }

            // Apply variable durability (for tools, armor, etc.)
            if (lootItem.durabilityMin != null && lootItem.durabilityMax != null) {
                if (this is org.bukkit.inventory.meta.Damageable) {
                    val maxDurability = lootItem.type.maxDurability
                    if (maxDurability > 0) {
                        val damageValue = Random.nextInt(lootItem.durabilityMin, lootItem.durabilityMax + 1)
                        damage = damageValue.coerceIn(0, maxDurability.toInt())
                    }
                }
            }

            // Apply goat horn instrument (8 variants)
            if (lootItem.instrument != null && lootItem.type == Material.GOAT_HORN) {
                if (this is org.bukkit.inventory.meta.MusicInstrumentMeta) {
                    // Build the full instrument name (e.g., PONDER -> PONDER_GOAT_HORN)
                    val instrumentName = if (lootItem.instrument.endsWith("_GOAT_HORN", ignoreCase = true)) {
                        lootItem.instrument.uppercase()
                    } else {
                        "${lootItem.instrument.uppercase()}_GOAT_HORN"
                    }

                    try {
                        // Get the MusicInstrument using reflection to support both field access patterns
                        val instrumentField = org.bukkit.MusicInstrument::class.java.getField(instrumentName)
                        val instrument = instrumentField.get(null) as? org.bukkit.MusicInstrument
                        if (instrument != null) {
                            setInstrument(instrument)
                            if (plugin.config.getBoolean("debug.verbose-logging", false)) {
                                plugin.logger.info("Applied goat horn instrument: $instrumentName")
                            }
                        } else {
                            plugin.logger.warning("Invalid goat horn instrument: $instrumentName (field exists but value is null)")
                        }
                    } catch (e: NoSuchFieldException) {
                        plugin.logger.warning("Invalid goat horn instrument: ${lootItem.instrument}. " +
                            "Valid options: PONDER, SING, SEEK, FEEL, ADMIRE, CALL, YEARN, DREAM")
                    } catch (e: Exception) {
                        plugin.logger.warning("Failed to set goat horn instrument: ${e.message}")
                    }
                }
            }
        }

        // Add fixed enchantments (legacy format)
        lootItem.enchantments?.forEach { (enchantment, level) ->
            itemStack.addUnsafeEnchantment(enchantment, level)
        }

        // Add enchantment ranges (random level within range)
        lootItem.enchantmentRanges?.values?.forEach { range ->
            val randomLevel = Random.nextInt(range.minLevel, range.maxLevel + 1)
            itemStack.addUnsafeEnchantment(range.enchantment, randomLevel)
        }

        // Pick one random enchantment from pool
        if (!lootItem.randomEnchantmentPool.isNullOrEmpty()) {
            val randomEnch = lootItem.randomEnchantmentPool.random()
            val randomLevel = Random.nextInt(randomEnch.minLevel, randomEnch.maxLevel + 1)
            itemStack.addUnsafeEnchantment(randomEnch.enchantment, randomLevel)
        }

        return itemStack
    }

    /**
     * Resolves a custom item from a supported plugin (Nexo, ItemsAdder, Oraxen, CraftEngine, MythicCrucible).
     * Uses reflection so no compile-time dependency on any of these plugins is needed.
     *
     * Note: MythicCrucible is an addon of MythicMobs and registers its items into the Mythic item manager,
     * so the gate check uses "MythicMobs" rather than "MythicCrucible".
     */
    private fun resolveCustomItem(pluginName: String, itemId: String): ItemStack? {
        val gatePluginName = when (pluginName.lowercase()) {
            "mythiccrucible", "crucible" -> "MythicMobs"
            else -> pluginName
        }
        if (!org.bukkit.Bukkit.getPluginManager().isPluginEnabled(gatePluginName)) {
            plugin.logger.warning("Custom item plugin '$gatePluginName' is not enabled — cannot resolve item '$itemId'")
            return null
        }
        return when (pluginName.lowercase()) {
            "nexo" -> resolveNexoItem(itemId)
            "itemsadder" -> resolveItemsAdderItem(itemId)
            "oraxen" -> resolveOraxenItem(itemId)
            "craftengine" -> resolveCraftEngineItem(itemId)
            "mythiccrucible", "crucible" -> resolveMythicCrucibleItem(itemId)
            else -> {
                plugin.logger.warning(
                    "Unsupported custom item plugin: '$pluginName'. " +
                    "Supported: Nexo, ItemsAdder, Oraxen, CraftEngine, MythicCrucible"
                )
                null
            }
        }
    }

    /** Resolves a Nexo item via NexoItems.itemFromId(id).build() */
    private fun resolveNexoItem(itemId: String): ItemStack? = try {
        val cls = Class.forName("com.nexomc.nexo.api.NexoItems")
        val wrapper = cls.getMethod("itemFromId", String::class.java).invoke(null, itemId)
        if (wrapper == null) {
            plugin.logger.warning("Nexo item not found: '$itemId'")
            null
        } else {
            wrapper.javaClass.getMethod("build").invoke(wrapper) as? ItemStack
        }
    } catch (e: Exception) {
        plugin.logger.warning("Failed to resolve Nexo item '$itemId': ${e.message}")
        null
    }

    /** Resolves an ItemsAdder item via CustomStack.getInstance(id).itemStack */
    private fun resolveItemsAdderItem(itemId: String): ItemStack? = try {
        val cls = Class.forName("dev.lone.itemsadder.api.CustomStack")
        val instance = cls.getMethod("getInstance", String::class.java).invoke(null, itemId)
        if (instance == null) {
            plugin.logger.warning("ItemsAdder item not found: '$itemId'")
            null
        } else {
            instance.javaClass.getMethod("getItemStack").invoke(instance) as? ItemStack
        }
    } catch (e: Exception) {
        plugin.logger.warning("Failed to resolve ItemsAdder item '$itemId': ${e.message}")
        null
    }

    /** Resolves an Oraxen item via OraxenItems.getItemById(id).build() */
    private fun resolveOraxenItem(itemId: String): ItemStack? = try {
        val cls = Class.forName("io.th0rgal.oraxen.api.OraxenItems")
        val builder = cls.getMethod("getItemById", String::class.java).invoke(null, itemId)
        if (builder == null) {
            plugin.logger.warning("Oraxen item not found: '$itemId'")
            null
        } else {
            builder.javaClass.getMethod("build").invoke(builder) as? ItemStack
        }
    } catch (e: Exception) {
        plugin.logger.warning("Failed to resolve Oraxen item '$itemId': ${e.message}")
        null
    }

    /**
     * Resolves a CraftEngine item via CraftEngineItems.byId(Key) → buildItemStack().
     *
     * Notes on the CraftEngine API (verified against Xiao-MoMi/craft-engine main):
     * - `CraftEngineItems.byId` takes a `net.momirealms.craftengine.core.util.Key`, not a String.
     *   We build the Key via its public `Key.from(String)` factory (accepts `"namespace:value"` or bare
     *   value, defaulting to the `minecraft` namespace — CraftEngine's own items use the `craftengine`
     *   namespace, so pack items should be referenced as e.g. `"my_pack:item_name"`).
     * - `CustomItem<ItemStack>` exposes a no-arg `buildItemStack()` which returns the Bukkit ItemStack
     *   directly. Player-context overloads exist but take CraftEngine's own `Player` abstraction, not
     *   Bukkit's — intentionally skipped here; static vault loot doesn't need player-context placeholders.
     */
    private fun resolveCraftEngineItem(itemId: String): ItemStack? = try {
        val keyCls = Class.forName("net.momirealms.craftengine.core.util.Key")
        val key = keyCls.getMethod("from", String::class.java).invoke(null, itemId)
        val itemsCls = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems")
        val customItem = itemsCls.getMethod("byId", keyCls).invoke(null, key)
        if (customItem == null) {
            plugin.logger.warning("CraftEngine item not found: '$itemId'")
            null
        } else {
            customItem.javaClass.getMethod("buildItemStack").invoke(customItem) as? ItemStack
        }
    } catch (e: Exception) {
        plugin.logger.warning("Failed to resolve CraftEngine item '$itemId': ${e.message}")
        null
    }

    /**
     * Resolves a MythicCrucible item via the MythicMobs API chain:
     *   MythicBukkit.inst().getItemManager().getItem(String) -> Optional<MythicItem>
     *     -> MythicItem.generateItemStack(int) -> AbstractItemStack (BukkitItemStack at runtime)
     *     -> BukkitItemStack.build() -> org.bukkit.inventory.ItemStack
     *
     * MythicCrucible is an addon of MythicMobs — Crucible items are registered into the Mythic item
     * manager, so we query through the MythicBukkit API rather than a Crucible-specific entry point.
     * Amount is always generated as 1; the outer caller scales to the configured amount range.
     */
    private fun resolveMythicCrucibleItem(itemId: String): ItemStack? = try {
        val mythicBukkitCls = Class.forName("io.lumine.mythic.bukkit.MythicBukkit")
        val instance = mythicBukkitCls.getMethod("inst").invoke(null)
        val itemManager = instance.javaClass.getMethod("getItemManager").invoke(instance)
        val optional = itemManager.javaClass.getMethod("getItem", String::class.java).invoke(itemManager, itemId) as java.util.Optional<*>
        val mythicItem = optional.orElse(null)
        if (mythicItem == null) {
            plugin.logger.warning("MythicCrucible item not found: '$itemId' (is the item defined in a Mythic/Crucible item file?)")
            null
        } else {
            // generateItemStack(int) -> AbstractItemStack; on Bukkit it's BukkitItemStack which has build() -> ItemStack
            val abstractStack = mythicItem.javaClass.getMethod("generateItemStack", Int::class.javaPrimitiveType).invoke(mythicItem, 1)
            if (abstractStack == null) {
                plugin.logger.warning("MythicCrucible.generateItemStack returned null for '$itemId'")
                null
            } else {
                abstractStack.javaClass.getMethod("build").invoke(abstractStack) as? ItemStack
            }
        }
    } catch (e: Exception) {
        plugin.logger.warning("Failed to resolve MythicCrucible item '$itemId': ${e.message}")
        null
    }

    /**
     * Executes command rewards.
     * Folia compatible: Uses global scheduler for console command execution.
     */
    private fun executeCommandReward(reward: CommandReward, player: Player) {
        reward.commands.forEach { command ->
            val processedCommand = replacePlaceholders(command, player)
            plugin.scheduler.runTask(Runnable {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand)
            })
        }

        // Notify player
        player.sendMessage(plugin.getMessage("loot-received", "item" to reward.displayName))
    }

    /**
     * Replaces placeholders in strings.
     */
    private fun replacePlaceholders(text: String, player: Player): String {
        return text
            .replace("{player}", player.name)
            .replace("{uuid}", player.uniqueId.toString())
    }

    /**
     * Colorizes text with & color codes.
     */
    private fun colorize(text: String): String {
        return text.replace('&', '§')
    }

    /**
     * Gets all loaded loot table names.
     */
    fun getLootTableNames(): Set<String> = lootTables.keys


    fun saveAllToFile() {
        try {
            val file = File(plugin.dataFolder, "loot.yml")
            val config = org.bukkit.configuration.file.YamlConfiguration()
            val root = config.createSection("loot-tables")
            lootTables.values.forEach { table ->
                val sec = root.createSection(table.name)

                if (table.isLegacyFormat()) {
                    // Save as legacy single-pool format
                    sec.set("min-rolls", table.minRolls)
                    sec.set("max-rolls", table.maxRolls)

                    // guaranteed-items
                    val guaranteedList = table.guaranteedItems.map { li -> serializeLootItem(li) }
                    if (guaranteedList.isNotEmpty()) {
                        sec.set("guaranteed-items", guaranteedList)
                    }

                    // weighted-items
                    val weightedList = table.weightedItems.map { li -> serializeLootItem(li) }
                    sec.set("weighted-items", weightedList)

                    // command-rewards
                    if (table.commandRewards.isNotEmpty()) {
                        val rewards = table.commandRewards.map { r -> serializeCommandReward(r) }
                        sec.set("command-rewards", rewards)
                    }
                } else {
                    // Save as new multi-pool format
                    val poolsList = table.pools.map { pool ->
                        val poolMap = mutableMapOf<String, Any>()
                        poolMap["name"] = pool.name
                        poolMap["min-rolls"] = pool.minRolls
                        poolMap["max-rolls"] = pool.maxRolls

                        if (pool.guaranteedItems.isNotEmpty()) {
                            poolMap["guaranteed-items"] = pool.guaranteedItems.map { serializeLootItem(it) }
                        }

                        if (pool.weightedItems.isNotEmpty()) {
                            poolMap["weighted-items"] = pool.weightedItems.map { serializeLootItem(it) }
                        }

                        if (pool.commandRewards.isNotEmpty()) {
                            poolMap["command-rewards"] = pool.commandRewards.map { serializeCommandReward(it) }
                        }

                        poolMap
                    }
                    sec.set("pools", poolsList)
                }
            }
            config.save(file)
            plugin.logger.info("Saved loot tables to loot.yml (${lootTables.size} tables)")
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save loot.yml: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Serializes a LootItem to a map for saving.
     */
    private fun serializeLootItem(li: LootItem): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        if (li.customItemPlugin != null) {
            map["type"] = "CUSTOM_ITEM"
            map["plugin"] = li.customItemPlugin
            li.customItemId?.let { map["item-id"] = it }
        } else {
            map["type"] = li.type.name
        }
        map["amount-min"] = li.amountMin
        map["amount-max"] = li.amountMax
        map["weight"] = li.weight
        li.name?.let { map["name"] = it }
        li.lore?.let { map["lore"] = it }
        li.customModelData?.let { map["custom-model-data"] = it }
        li.enchantments?.let { ench ->
            map["enchantments"] = ench.map { (k, v) -> "${k.key.key.uppercase()}:$v" }
        }
        map["enabled"] = li.enabled
        return map
    }

    /**
     * Serializes a CommandReward to a map for saving.
     */
    private fun serializeCommandReward(r: CommandReward): Map<String, Any> {
        return mapOf(
            "weight" to r.weight,
            "commands" to r.commands,
            "display-name" to r.displayName
        )
    }
}
