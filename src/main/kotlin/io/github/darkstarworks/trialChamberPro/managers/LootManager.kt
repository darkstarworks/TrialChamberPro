package io.github.darkstarworks.trialChamberPro.managers

import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import io.github.darkstarworks.trialChamberPro.models.CommandReward
import io.github.darkstarworks.trialChamberPro.models.LootItem
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
     */
    private fun parseLootTable(name: String, section: ConfigurationSection): LootTable {
        val minRolls = section.getInt("min-rolls", 1)
        val maxRolls = section.getInt("max-rolls", 3)

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

    /**
     * Parses a loot item from configuration.
     */
    private fun parseLootItem(data: Map<String, Any>): LootItem? {
        val typeStr = data["type"] as? String ?: return null
        val material = try {
            Material.valueOf(typeStr.uppercase())
        } catch (_: IllegalArgumentException) {
            plugin.logger.warning("Invalid material: $typeStr")
            return null
        }

        val amountMin = (data["amount-min"] as? Number)?.toInt() ?: 1
        val amountMax = (data["amount-max"] as? Number)?.toInt() ?: 1
        val weight = (data["weight"] as? Number)?.toDouble() ?: 1.0

        val name = data["name"] as? String
        @Suppress("UNCHECKED_CAST")
        val lore = data["lore"] as? List<String>

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

        val enabled = (data["enabled"] as? Boolean) ?: true

        return LootItem(
            type = material,
            amountMin = amountMin,
            amountMax = amountMax,
            weight = weight,
            name = name,
            lore = lore,
            enchantments = enchantments.takeIf { it.isNotEmpty() },
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
     *
     * @param tableName The loot table name
     * @param player The player receiving the loot (for placeholders)
     * @return List of generated items
     */
    fun generateLoot(tableName: String, player: Player): List<ItemStack> {
        val lootTable = lootTables[tableName]
        if (lootTable == null) {
            plugin.logger.warning("Loot table not found: $tableName")
            return emptyList()
        }

        val items = mutableListOf<ItemStack>()

        // Add all guaranteed items (respect enabled flag)
        lootTable.guaranteedItems.filter { it.enabled }.forEach { lootItem ->
            items.add(createItemStack(lootItem, player))
        }

        // Roll for weighted items (respect enabled flag)
        val enabledWeighted = lootTable.weightedItems.filter { it.enabled }
        val rolls = Random.nextInt(lootTable.minRolls, lootTable.maxRolls + 1)
        repeat(rolls) {
            // Select a random weighted item
            val selectedItem = selectWeightedItem(enabledWeighted)
            if (selectedItem != null) {
                items.add(createItemStack(selectedItem, player))
            }
        }

        // Process command rewards
        lootTable.commandRewards.forEach { reward ->
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
     */
    private fun createItemStack(lootItem: LootItem, player: Player): ItemStack {
        val amount = Random.nextInt(lootItem.amountMin, lootItem.amountMax + 1)
        val itemStack = ItemStack(lootItem.type, amount)

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
        }

        // Add enchantments
        lootItem.enchantments?.forEach { (enchantment, level) ->
            itemStack.addUnsafeEnchantment(enchantment, level)
        }

        return itemStack
    }

    /**
     * Executes command rewards.
     */
    private fun executeCommandReward(reward: CommandReward, player: Player) {
        reward.commands.forEach { command ->
            val processedCommand = replacePlaceholders(command, player)
            Bukkit.getScheduler().runTask(plugin, Runnable {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand)
            })
        }

        // Notify player
        player.sendMessage(colorize("§aYou received: ${reward.displayName}"))
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
                sec.set("min-rolls", table.minRolls)
                sec.set("max-rolls", table.maxRolls)

                // guaranteed-items
                val guaranteedList = table.guaranteedItems.map { li ->
                    val map = mutableMapOf<String, Any>()
                    map["type"] = li.type.name
                    map["amount-min"] = li.amountMin
                    map["amount-max"] = li.amountMax
                    map["weight"] = li.weight
                    li.name?.let { map["name"] = it }
                    li.lore?.let { map["lore"] = it }
                    li.enchantments?.let { ench ->
                        map["enchantments"] = ench.map { (k, v) -> "${k.key.key.uppercase()}:$v" }
                    }
                    map["enabled"] = li.enabled
                    map
                }
                sec.set("guaranteed-items", guaranteedList)

                // weighted-items
                val weightedList = table.weightedItems.map { li ->
                    val map = mutableMapOf<String, Any>()
                    map["type"] = li.type.name
                    map["amount-min"] = li.amountMin
                    map["amount-max"] = li.amountMax
                    map["weight"] = li.weight
                    li.name?.let { map["name"] = it }
                    li.lore?.let { map["lore"] = it }
                    li.enchantments?.let { ench ->
                        map["enchantments"] = ench.map { (k, v) -> "${k.key.key.uppercase()}:$v" }
                    }
                    map["enabled"] = li.enabled
                    map
                }
                sec.set("weighted-items", weightedList)

                // command-rewards
                if (table.commandRewards.isNotEmpty()) {
                    val rewards = table.commandRewards.map { r ->
                        mapOf(
                            "weight" to r.weight,
                            "commands" to r.commands,
                            "display-name" to r.displayName
                        )
                    }
                    sec.set("command-rewards", rewards)
                }
            }
            config.save(file)
            plugin.logger.info("Saved loot tables to loot.yml (${lootTables.size} tables)")
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save loot.yml: ${e.message}")
            e.printStackTrace()
        }
    }
}
