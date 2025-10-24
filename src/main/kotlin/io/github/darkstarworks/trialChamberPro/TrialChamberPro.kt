package io.github.darkstarworks.trialChamberPro

import io.github.darkstarworks.trialChamberPro.commands.TCPCommand
import io.github.darkstarworks.trialChamberPro.commands.TCPTabCompleter
import io.github.darkstarworks.trialChamberPro.database.DatabaseManager
import io.github.darkstarworks.trialChamberPro.listeners.PlayerDeathListener
import io.github.darkstarworks.trialChamberPro.listeners.PlayerMovementListener
import io.github.darkstarworks.trialChamberPro.listeners.ProtectionListener
import io.github.darkstarworks.trialChamberPro.listeners.VaultInteractListener
import io.github.darkstarworks.trialChamberPro.managers.ChamberManager
import io.github.darkstarworks.trialChamberPro.managers.LootManager
import io.github.darkstarworks.trialChamberPro.managers.ResetManager
import io.github.darkstarworks.trialChamberPro.managers.SnapshotManager
import io.github.darkstarworks.trialChamberPro.managers.StatisticsManager
import io.github.darkstarworks.trialChamberPro.managers.VaultManager
import kotlinx.coroutines.*
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * Main plugin class for TrialChamberPro.
 * Manages Trial Chambers on multiplayer servers with automatic resets,
 * per-player vault loot, custom loot tables, and region protection.
 */
class TrialChamberPro : JavaPlugin() {

    // Database manager
    lateinit var databaseManager: DatabaseManager
        private set

    // Snapshot manager
    lateinit var snapshotManager: SnapshotManager
        private set

    // Chamber manager
    lateinit var chamberManager: ChamberManager
        private set

    // Vault manager
    lateinit var vaultManager: VaultManager
        private set

    // Loot manager
    lateinit var lootManager: LootManager
        private set

    // Reset manager
    lateinit var resetManager: ResetManager
        private set

    // Statistics manager
    lateinit var statisticsManager: StatisticsManager
        private set

    // Coroutine scope for async operations
    private val pluginScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Snapshots directory
    val snapshotsDir: File by lazy {
        File(dataFolder, "snapshots").apply { mkdirs() }
    }

    override fun onEnable() {
        // ASCII art banner
        logger.info("╔════════════════════════════════════╗")
        logger.info("║   TrialChamberPro v${pluginMeta.version}      ║")
        logger.info("║   Advanced Trial Chamber Manager  ║")
        logger.info("╚════════════════════════════════════╝")

        // Save default config files
        saveDefaultConfig()
        saveResource("messages.yml", false)
        saveResource("loot.yml", false)

        // Register command executor and tab completer immediately to avoid early "/tcp [args]" usage messages
        val tcpCommand = TCPCommand(this)
        val tabCompleter = TCPTabCompleter(this)
        getCommand("tcp")?.setExecutor(tcpCommand)
        getCommand("tcp")?.tabCompleter = tabCompleter

        // Initialize database asynchronously
        pluginScope.launch {
            try {
                databaseManager = DatabaseManager(this@TrialChamberPro)
                databaseManager.initialize()

                // Test connection
                if (databaseManager.testConnection()) {
                    logger.info("Database connection test successful")
                } else {
                    logger.severe("Database connection test failed")
                }

                // Initialize managers
                snapshotManager = SnapshotManager(this@TrialChamberPro)
                chamberManager = ChamberManager(this@TrialChamberPro)
                vaultManager = VaultManager(this@TrialChamberPro)
                lootManager = LootManager(this@TrialChamberPro)
                resetManager = ResetManager(this@TrialChamberPro)
                statisticsManager = StatisticsManager(this@TrialChamberPro)

                // Load loot tables
                lootManager.loadLootTables()

                // Preload chambers cache for fast, thread-safe lookups in listeners
                chamberManager.preloadCache()

                // Start reset scheduler
                resetManager.startResetScheduler()

                // Register command, tab completer, listeners and log readiness on main thread
                org.bukkit.Bukkit.getScheduler().runTask(this@TrialChamberPro, Runnable {
                    // Register command executor and tab completer
                    val tcpCommand = TCPCommand(this@TrialChamberPro)
                    val tabCompleter = TCPTabCompleter(this@TrialChamberPro)
                    getCommand("tcp")?.setExecutor(tcpCommand)
                    getCommand("tcp")?.tabCompleter = tabCompleter

                    // Register listeners
                    server.pluginManager.registerEvents(
                        VaultInteractListener(this@TrialChamberPro),
                        this@TrialChamberPro
                    )
                    server.pluginManager.registerEvents(
                        ProtectionListener(this@TrialChamberPro),
                        this@TrialChamberPro
                    )
                    server.pluginManager.registerEvents(
                        PlayerMovementListener(this@TrialChamberPro),
                        this@TrialChamberPro
                    )
                    server.pluginManager.registerEvents(
                        PlayerDeathListener(this@TrialChamberPro),
                        this@TrialChamberPro
                    )

                    logger.info("✓ Phase 1 Foundation: Initialized successfully")
                    logger.info("  - Database: Connected")
                    logger.info("  - Configuration: Loaded")
                    logger.info("  - Data Models: Ready")
                    logger.info("✓ Phase 2 Snapshot System: Ready")
                    logger.info("  - Snapshot Manager: Initialized")
                    logger.info("  - Block Restorer: Available")
                    logger.info("  - Compression: Enabled (Gzip)")
                    logger.info("✓ Phase 3 Chamber Registration: Ready")
                    logger.info("  - Chamber Manager: Initialized")
                    logger.info("  - Commands: Registered (/tcp)")
                    logger.info("  - WorldEdit: ${if (server.pluginManager.getPlugin("WorldEdit") != null) "Available" else "Not found"}")
                    logger.info("✓ Phase 4 Per-Player Vault System: Ready")
                    logger.info("  - Vault Manager: Initialized")
                    logger.info("  - Vault Listener: Registered")
                    logger.info("  - Key Validation: ${if (config.getBoolean("trial-keys.validate-key-type")) "Enabled" else "Disabled"}")
                    logger.info("  - Cooldowns: Normal=${config.getLong("vaults.normal-cooldown-hours")}h, Ominous=${config.getLong("vaults.ominous-cooldown-hours")}h")
                    logger.info("✓ Phase 5 Loot Generation System: Ready")
                    logger.info("  - Loot Manager: Initialized")
                    logger.info("  - Loot Tables: ${lootManager.getLootTableNames().size} loaded")
                    logger.info("  - Weighted Selection: Enabled")
                    logger.info("  - Custom Items: Supported")
                    logger.info("✓ Phase 6 Automatic Reset System: Ready")
                    logger.info("  - Reset Manager: Initialized")
                    logger.info("  - Reset Scheduler: Running")
                    logger.info("  - Warnings: ${config.getIntegerList("global.reset-warning-times").size} configured")
                    logger.info("  - Player Teleport: ${if (config.getBoolean("global.teleport-players-on-reset")) "Enabled" else "Disabled"}")
                    logger.info("✓ Phase 7 Protection System: Ready")
                    logger.info("  - Protection Listener: Registered")
                    logger.info("  - Block Protection: ${if (config.getBoolean("protection.prevent-block-break")) "Enabled" else "Disabled"}")
                    logger.info("  - Container Protection: ${if (config.getBoolean("protection.prevent-container-access")) "Enabled" else "Disabled"}")
                    logger.info("  - Mob Griefing: ${if (config.getBoolean("protection.prevent-mob-griefing")) "Prevented" else "Allowed"}")
                    logger.info("  - WorldGuard: ${if (config.getBoolean("protection.worldguard-integration") && server.pluginManager.getPlugin("WorldGuard") != null) "Integrated" else "Disabled"}")
                    logger.info("✓ Phase 8 Statistics & Leaderboards: Ready")
                    logger.info("  - Statistics Manager: Initialized")
                    logger.info("  - Movement Listener: Registered")
                    logger.info("  - Death Listener: Registered")
                    logger.info("  - Stats Tracking: ${if (config.getBoolean("statistics.enabled")) "Enabled" else "Disabled"}")
                    logger.info("  - Time Tracking: ${if (config.getBoolean("statistics.track-time-spent")) "Enabled" else "Disabled"}")
                    logger.info("  - Leaderboards: Top ${config.getInt("statistics.top-players-count", 10)} players")
                })
            } catch (e: Exception) {
                logger.severe("Failed to initialize plugin: ${e.message}")
                e.printStackTrace()
                org.bukkit.Bukkit.getScheduler().runTask(this@TrialChamberPro, Runnable {
                    server.pluginManager.disablePlugin(this@TrialChamberPro)
                })
            }
        }

        logger.info("TrialChamberPro has been enabled!")
    }

    override fun onDisable() {
        logger.info("Shutting down TrialChamberPro...")

        // Cancel all coroutines
        pluginScope.cancel()

        // Stop reset scheduler
        if (::resetManager.isInitialized) {
            resetManager.shutdown()
        }

        // Close database connections
        if (::databaseManager.isInitialized) {
            databaseManager.close()
        }

        logger.info("TrialChamberPro has been disabled!")
    }

    /**
     * Reloads the plugin configuration.
     */
    fun reloadPluginConfig() {
        reloadConfig()
        if (::lootManager.isInitialized) {
            lootManager.loadLootTables()
        }
        logger.info("Configuration reloaded")
    }

    /**
     * Gets a message from messages.yml with optional placeholders.
     */
    fun getMessage(key: String, vararg replacements: Pair<String, Any?>): String {
        val messagesFile = File(dataFolder, "messages.yml")
        val messages = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(messagesFile)

        val prefix = messages.getString("prefix", "&8[&6TCP&8]&r ")
        var message = messages.getString(key, "&cMessage not found: $key")!!

        // Replace placeholders
        replacements.forEach { (placeholder, value) ->
            message = message.replace("{$placeholder}", value?.toString() ?: "null")
        }

        // Add prefix if not a list item or header
        val shouldAddPrefix = !key.contains("list-item") &&
                              !key.contains("header") &&
                              !key.contains("help-")

        val finalMessage = if (shouldAddPrefix) "$prefix$message" else message

        // Convert color codes
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacyAmpersand()
            .deserialize(finalMessage)
            .let { net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(it) }
    }
}
