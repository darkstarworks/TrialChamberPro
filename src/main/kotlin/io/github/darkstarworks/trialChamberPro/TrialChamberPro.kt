package io.github.darkstarworks.trialChamberPro

import io.github.darkstarworks.trialChamberPro.commands.TCPCommand
import io.github.darkstarworks.trialChamberPro.commands.TCPTabCompleter
import io.github.darkstarworks.trialChamberPro.database.DatabaseManager
import io.github.darkstarworks.trialChamberPro.gui.MenuService
import io.github.darkstarworks.trialChamberPro.listeners.*
import io.github.darkstarworks.trialChamberPro.managers.*
import io.github.darkstarworks.trialChamberPro.scheduler.SchedulerAdapter
import io.github.darkstarworks.trialChamberPro.utils.UpdateChecker
import kotlinx.coroutines.*
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * Main plugin class for TrialChamberPro.
 * Manages Trial Chambers on multiplayer servers with automatic resets,
 * per-player vault loot, custom loot tables, and region protection.
 */
class TrialChamberPro : JavaPlugin() {

    // Indicates when the plugin finished async initialization and is safe to use
    @Volatile
    var isReady: Boolean = false
        private set

    // Scheduler adapter (Paper/Folia compatible)
    lateinit var scheduler: SchedulerAdapter
        private set

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

    // Menu (GUI) service
    lateinit var menuService: MenuService
        private set

    // Schematic manager
    lateinit var schematicManager: SchematicManager
        private set

    // Particle visualizer for schematic previews
    lateinit var particleVisualizer: io.github.darkstarworks.trialChamberPro.utils.ParticleVisualizer
        private set

    // Paste confirmation manager
    lateinit var pasteConfirmationManager: PasteConfirmationManager
        private set

    // Update checker
    private lateinit var updateChecker: UpdateChecker

    // Coroutine scope for async operations
    private val pluginScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Launch an asynchronous task tied to the plugin lifecycle (cancelled on disable).
     */
    fun launchAsync(block: suspend CoroutineScope.() -> Unit) = pluginScope.launch(Dispatchers.IO) { block() }

    // Snapshots directory
    val snapshotsDir: File by lazy {
        File(dataFolder, "snapshots").apply { mkdirs() }
    }

    override fun onEnable() {
        // ASCII art banner
        logger.info("╔════════════════════════════════════╗")
        logger.info("║   TrialChamberPro v${pluginMeta.version}          ║")
        logger.info("║   Advanced Trial Chamber Manager   ║")
        logger.info("╚════════════════════════════════════╝")

        // Initialize scheduler adapter (Paper/Folia compatible) - must be first!
        scheduler = SchedulerAdapter.create(this)
        if (scheduler.isFolia) {
            logger.info("Folia detected - using regionized scheduling")
        } else {
            logger.info("Paper/Spigot detected - using standard scheduling")
        }

        // Initialize update checker
        updateChecker = UpdateChecker(
            this,
            "darkstarworks/TrialChamberPro",
            "https://raw.githubusercontent.com/darkstarworks/TrialChamberPro/master/src/main/resources/update.txt"
        )
        updateChecker.checkForUpdates()

        // Schedule periodic update checks (every 6 hours = 432000 ticks)
        scheduler.runTaskTimerAsync(Runnable {
            updateChecker.checkForUpdates(notifyConsole = false)
        }, 432000L, 432000L)

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
                menuService = MenuService(this@TrialChamberPro)
                schematicManager = SchematicManager(this@TrialChamberPro)
                schematicManager.initialize()
                particleVisualizer = io.github.darkstarworks.trialChamberPro.utils.ParticleVisualizer(this@TrialChamberPro)
                pasteConfirmationManager = PasteConfirmationManager(this@TrialChamberPro)

                // Load loot tables
                lootManager.loadLootTables()

                // Preload chambers cache for fast, thread-safe lookups in listeners
                chamberManager.preloadCache()

                // Start reset scheduler
                resetManager.startResetScheduler()

                // Register command, tab completer, listeners and log readiness on main thread
                scheduler.runTask(Runnable {
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
                    server.pluginManager.registerEvents(
                        UndoListener(this@TrialChamberPro),
                        this@TrialChamberPro
                    )
                    server.pluginManager.registerEvents(
                        PasteConfirmListener(this@TrialChamberPro),
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
                    logger.info("✓ Phase 9 Schematic System: Ready")
                    logger.info("  - Schematic Manager: Initialized")
                    logger.info("  - Available Schematics: ${schematicManager.listSchematics().size}")
                    logger.info("  - WorldEdit/FAWE: ${if (schematicManager.isAvailable()) "Available" else "Not Found"}")

                    // Mark plugin as fully ready after all sync registrations are done
                    this@TrialChamberPro.isReady = true
                    logger.info("✓ TrialChamberPro is fully initialized and ready!")
                })
            } catch (e: Exception) {
                logger.severe("Failed to initialize plugin: ${e.message}")
                e.printStackTrace()
                scheduler.runTask(Runnable {
                    server.pluginManager.disablePlugin(this@TrialChamberPro)
                })
            }
        }

        // Log debug mode status
        val debugEnabled = config.getBoolean("debug.verbose-logging", false)
        if (debugEnabled) {
            logger.warning("═══════════════════════════════════════")
            logger.warning("   DEBUG MODE ENABLED")
            logger.warning("   Verbose logging is active")
            logger.warning("   Expect detailed console output")
            logger.warning("═══════════════════════════════════════")
        }

        logger.info("TrialChamberPro has been enabled!")
    }

    override fun onDisable() {
        logger.info("Shutting down TrialChamberPro...")

        // Cancel all coroutines
        pluginScope.cancel()

        // Cancel all scheduled tasks
        if (::scheduler.isInitialized) {
            scheduler.cancelAllTasks()
        }

        // Stop reset scheduler
        if (::resetManager.isInitialized) {
            resetManager.shutdown()
        }

        // Clean up pending pastes and visualizations
        if (::pasteConfirmationManager.isInitialized) {
            pasteConfirmationManager.clearAll()
        }
        if (::particleVisualizer.isInitialized) {
            particleVisualizer.stopAll()
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