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

    // Spawner wave manager
    lateinit var spawnerWaveManager: SpawnerWaveManager
        private set

    // Spectator manager
    lateinit var spectatorManager: SpectatorManager
        private set

    // Chamber auto-discovery manager
    lateinit var chamberDiscoveryManager: ChamberDiscoveryManager
        private set

    // Custom mob provider registry (v1.3.0) — always contains VanillaMobProvider;
    // additional providers (MythicMobs, ...) are registered after soft-deps are up.
    lateinit var trialMobProviderRegistry: io.github.darkstarworks.trialChamberPro.providers.TrialMobProviderRegistry
        private set

    // Spawner preset manager (v1.3.1) — backs `/tcp give <preset>`.
    lateinit var spawnerPresetManager: SpawnerPresetManager
        private set

    // Module registry (v1.3.3) — lifecycle hub for premium add-on plugins
    // and third-party integrations implementing TCPModule.
    lateinit var moduleRegistry: io.github.darkstarworks.trialChamberPro.api.TCPModuleRegistry
        private set

    // Vault interaction listener (stored for proper shutdown)
    private lateinit var vaultInteractListener: VaultInteractListener

    // Listeners with coroutine scopes (stored for proper shutdown)
    private lateinit var playerMovementListener: PlayerMovementListener
    private lateinit var playerDeathListener: PlayerDeathListener
    private lateinit var pasteConfirmListener: PasteConfirmListener

    // Update checker
    private lateinit var updateChecker: UpdateChecker

    // Cached messages configuration (invalidated on reload)
    @Volatile
    private var cachedMessages: org.bukkit.configuration.file.YamlConfiguration? = null

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

        // v1.3.3: Module registry instantiated synchronously so that external
        // plugins can call `plugin.moduleRegistry.register(myModule)` from
        // their own onEnable, even while TCP's async startup is still in
        // flight. Modules registered early are queued and loaded by
        // `loadAllPending()` once TCP reaches isReady = true.
        moduleRegistry = io.github.darkstarworks.trialChamberPro.api.TCPModuleRegistry(this)
        server.pluginManager.registerEvents(moduleRegistry, this)

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

        // v1.3.0: sanity-check numeric config values; clamp with warnings rather than hard-fail
        io.github.darkstarworks.trialChamberPro.config.ConfigValidator.validate(this)

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
                spawnerWaveManager = SpawnerWaveManager(this@TrialChamberPro)
                spectatorManager = SpectatorManager(this@TrialChamberPro)
                chamberDiscoveryManager = ChamberDiscoveryManager(this@TrialChamberPro)

                // v1.3.0: Trial mob provider registry. Vanilla is registered by the registry's
                // init block; soft-depended providers are registered below once we know their
                // backing plugins are enabled (which is guaranteed by the time this runs because
                // plugin.yml `softdepend` controls load order).
                trialMobProviderRegistry = io.github.darkstarworks.trialChamberPro.providers.TrialMobProviderRegistry()
                if (server.pluginManager.getPlugin("MythicMobs") != null) {
                    trialMobProviderRegistry.register(
                        io.github.darkstarworks.trialChamberPro.providers.MythicMobsProvider(this@TrialChamberPro)
                    )
                    logger.info("Registered TrialMobProvider: MythicMobs")
                }
                if (server.pluginManager.getPlugin("EliteMobs") != null) {
                    trialMobProviderRegistry.register(
                        io.github.darkstarworks.trialChamberPro.providers.EliteMobsProvider(this@TrialChamberPro)
                    )
                    logger.info("Registered TrialMobProvider: EliteMobs")
                }
                if (server.pluginManager.getPlugin("EcoMobs") != null) {
                    trialMobProviderRegistry.register(
                        io.github.darkstarworks.trialChamberPro.providers.EcoMobsProvider(this@TrialChamberPro)
                    )
                    logger.info("Registered TrialMobProvider: EcoMobs")
                }
                if (server.pluginManager.getPlugin("LevelledMobs") != null) {
                    trialMobProviderRegistry.register(
                        io.github.darkstarworks.trialChamberPro.providers.LevelledMobsProvider(this@TrialChamberPro)
                    )
                    logger.info("Registered TrialMobProvider: LevelledMobs")
                }
                if (server.pluginManager.getPlugin("InfernalMobs") != null) {
                    trialMobProviderRegistry.register(
                        io.github.darkstarworks.trialChamberPro.providers.InfernalMobsProvider(this@TrialChamberPro)
                    )
                    logger.info("Registered TrialMobProvider: InfernalMobs")
                }
                if (server.pluginManager.getPlugin("Citizens") != null) {
                    trialMobProviderRegistry.register(
                        io.github.darkstarworks.trialChamberPro.providers.CitizensProvider(this@TrialChamberPro)
                    )
                    logger.info("Registered TrialMobProvider: Citizens")
                }

                // Load loot tables
                lootManager.loadLootTables()

                // v1.3.1: Spawner presets (backs `/tcp give <preset>`)
                spawnerPresetManager = SpawnerPresetManager(this@TrialChamberPro)
                spawnerPresetManager.load()

                // Preload chambers cache for fast, thread-safe lookups in listeners
                chamberManager.preloadCache()

                // Start reset scheduler
                resetManager.startResetScheduler()

                // Register listeners and log readiness on main thread
                scheduler.runTask(Runnable {
                    // Register listeners
                    vaultInteractListener = VaultInteractListener(this@TrialChamberPro)
                    server.pluginManager.registerEvents(
                        vaultInteractListener,
                        this@TrialChamberPro
                    )
                    server.pluginManager.registerEvents(
                        ProtectionListener(this@TrialChamberPro),
                        this@TrialChamberPro
                    )
                    playerMovementListener = PlayerMovementListener(this@TrialChamberPro)
                    server.pluginManager.registerEvents(
                        playerMovementListener,
                        this@TrialChamberPro
                    )
                    playerDeathListener = PlayerDeathListener(this@TrialChamberPro)
                    server.pluginManager.registerEvents(
                        playerDeathListener,
                        this@TrialChamberPro
                    )
                    server.pluginManager.registerEvents(
                        UndoListener(this@TrialChamberPro),
                        this@TrialChamberPro
                    )
                    pasteConfirmListener = PasteConfirmListener(this@TrialChamberPro)
                    server.pluginManager.registerEvents(
                        pasteConfirmListener,
                        this@TrialChamberPro
                    )
                    server.pluginManager.registerEvents(
                        SpawnerWaveListener(this@TrialChamberPro),
                        this@TrialChamberPro
                    )
                    server.pluginManager.registerEvents(
                        SpectatorListener(this@TrialChamberPro),
                        this@TrialChamberPro
                    )
                    server.pluginManager.registerEvents(
                        ChamberDiscoveryListener(this@TrialChamberPro),
                        this@TrialChamberPro
                    )
                    io.github.darkstarworks.trialChamberPro.listeners.VaultDropOwnerListener.init(this@TrialChamberPro)
                    server.pluginManager.registerEvents(
                        io.github.darkstarworks.trialChamberPro.listeners.VaultDropOwnerListener(this@TrialChamberPro),
                        this@TrialChamberPro
                    )
                    // v1.3.0: spawner-key drop owner lock (sibling of vault drop listener)
                    io.github.darkstarworks.trialChamberPro.listeners.SpawnerKeyDropOwnerListener.init(this@TrialChamberPro)
                    server.pluginManager.registerEvents(
                        io.github.darkstarworks.trialChamberPro.listeners.SpawnerKeyDropOwnerListener(this@TrialChamberPro),
                        this@TrialChamberPro
                    )
                    // v1.3.0: one-shot chat input collector for CustomMobProviderView
                    server.pluginManager.registerEvents(
                        io.github.darkstarworks.trialChamberPro.listeners.MobIdInputListener(this@TrialChamberPro),
                        this@TrialChamberPro
                    )
                    // v1.3.0: drop GUI session cache entries on player quit
                    server.pluginManager.registerEvents(
                        io.github.darkstarworks.trialChamberPro.listeners.MenuSessionCleanupListener(this@TrialChamberPro),
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

                    // Register PlaceholderAPI expansion if available
                    val placeholderAPIStatus = if (server.pluginManager.getPlugin("PlaceholderAPI") != null) {
                        try {
                            io.github.darkstarworks.trialChamberPro.integrations.PlaceholderAPIExpansion(this@TrialChamberPro).register()
                            "Registered"
                        } catch (e: Exception) {
                            logger.warning("Failed to register PlaceholderAPI expansion: ${e.message}")
                            "Failed"
                        }
                    } else {
                        "Not Found"
                    }
                    logger.info("✓ Phase 10 Integrations: Ready")
                    logger.info("  - PlaceholderAPI: $placeholderAPIStatus")
                    logger.info("✓ Phase 11 Spawner Wave System: Ready")
                    logger.info("  - Wave Manager: Initialized")
                    logger.info("  - Wave Listener: Registered")
                    logger.info("  - Boss Bar: ${if (config.getBoolean("spawner-waves.show-boss-bar", true)) "Enabled" else "Disabled"}")
                    logger.info("✓ Phase 12 Spectator Mode: Ready")
                    logger.info("  - Spectator Manager: Initialized")
                    logger.info("  - Spectator Listener: Registered")
                    logger.info("  - Death Spectate: ${if (config.getBoolean("spectator-mode.enabled", true)) "Enabled" else "Disabled"}")

                    // Mark plugin as fully ready after all sync registrations are done
                    this@TrialChamberPro.isReady = true
                    logger.info("✓ TrialChamberPro is fully initialized and ready!")

                    // v1.3.3: load any premium / third-party modules whose
                    // backing plugins registered with the module registry
                    // before TCP became ready. New registrations after this
                    // point load immediately.
                    moduleRegistry.loadAllPending()

                    // Sweep already-loaded chunks for chambers that existed before the
                    // ChunkLoadEvent listener was registered (spawn regions, pre-loaded worlds).
                    chamberDiscoveryManager.runStartupSweep()
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

        // v1.3.3: unload modules FIRST so they can still touch TCP
        // managers / database during their onUnload before everything
        // tears down. Reverse-registration-order is handled inside.
        if (::moduleRegistry.isInitialized) {
            moduleRegistry.shutdownAll()
        }

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
        if (::spawnerWaveManager.isInitialized) {
            spawnerWaveManager.shutdown()
        }
        if (::spectatorManager.isInitialized) {
            spectatorManager.shutdown()
        }
        if (::vaultInteractListener.isInitialized) {
            vaultInteractListener.shutdown()
        }
        if (::playerMovementListener.isInitialized) {
            playerMovementListener.shutdown()
        }
        if (::playerDeathListener.isInitialized) {
            playerDeathListener.shutdown()
        }
        if (::pasteConfirmListener.isInitialized) {
            pasteConfirmListener.shutdown()
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
        io.github.darkstarworks.trialChamberPro.config.ConfigValidator.validate(this)
        cachedMessages = null // Force reload on next getMessage() call
        if (::lootManager.isInitialized) {
            lootManager.loadLootTables()
        }
        if (::spawnerPresetManager.isInitialized) {
            spawnerPresetManager.load()
        }
        logger.info("Configuration reloaded")
    }

    /**
     * Gets a message from messages.yml with optional placeholders.
     */
    private fun loadedMessages(): org.bukkit.configuration.file.YamlConfiguration {
        return cachedMessages ?: run {
            val loaded = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(File(dataFolder, "messages.yml"))
            cachedMessages = loaded
            loaded
        }
    }

    /**
     * Gets a list-valued message from messages.yml (used for GUI lore and
     * multi-line help entries). Added in v1.3.0.
     *
     * Accepts either a YAML list (preferred) or a single string; returns empty
     * list if the key is absent. Runs `{placeholder}` substitution on every
     * line but does NOT add the chat prefix or convert color codes — callers
     * decide what to do with the raw strings.
     */
    fun getMessageList(key: String, vararg replacements: Pair<String, Any?>): List<String> {
        val messages = loadedMessages()
        val list = messages.getStringList(key)
        val source: List<String> = if (list.isEmpty()) {
            messages.getString(key)?.let { listOf(it) } ?: return emptyList()
        } else list

        return source.map { line ->
            var out = line
            replacements.forEach { (p, v) -> out = out.replace("{$p}", v?.toString() ?: "null") }
            out
        }
    }

    /**
     * Gets a GUI item name (Component) from messages.yml. Unlike [getMessage]
     * this never prepends the chat prefix and disables Minecraft's default
     * italic styling on item names. Added in v1.3.0.
     */
    fun getGuiText(
        key: String,
        vararg replacements: Pair<String, Any?>
    ): net.kyori.adventure.text.Component {
        val messages = loadedMessages()
        var raw = messages.getString(key, "<missing: $key>")!!
        replacements.forEach { (p, v) -> raw = raw.replace("{$p}", v?.toString() ?: "null") }
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacyAmpersand()
            .deserialize(raw)
            .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
    }

    /**
     * Gets a GUI item lore (list of Components) from messages.yml. The key
     * must point at a YAML list; each line is independently color-parsed and
     * has italics disabled. Empty lines render as [Component.empty].
     * Added in v1.3.0.
     */
    fun getGuiLore(
        key: String,
        vararg replacements: Pair<String, Any?>
    ): List<net.kyori.adventure.text.Component> {
        return getMessageList(key, *replacements).map { line ->
            if (line.isBlank()) net.kyori.adventure.text.Component.empty()
            else net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand()
                .deserialize(line)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
        }
    }

    fun getMessage(key: String, vararg replacements: Pair<String, Any?>): String {
        val messages = loadedMessages()

        val prefix = messages.getString("prefix", "&8[&6TCP&8]&r ")
        var message = messages.getString(key, "&cMessage not found: $key")!!

        // Replace placeholders
        replacements.forEach { (placeholder, value) ->
            message = message.replace("{$placeholder}", value?.toString() ?: "null")
        }

        // Add prefix if not a list item, header, help text, or boss bar
        val shouldAddPrefix = !key.contains("list-item") &&
                !key.contains("header") &&
                !key.contains("help-") &&
                !key.contains("boss-bar") &&
                !key.startsWith("gui.")

        val finalMessage = if (shouldAddPrefix) "$prefix$message" else message

        // Convert color codes
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
            .legacyAmpersand()
            .deserialize(finalMessage)
            .let { net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(it) }
    }
}