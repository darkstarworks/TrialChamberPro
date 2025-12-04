package io.github.darkstarworks.trialChamberPro.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.darkstarworks.trialChamberPro.TrialChamberPro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.configuration.file.FileConfiguration
import java.io.File
import java.sql.Connection
import java.sql.SQLException

/**
 * Manages database connections using HikariCP connection pooling.
 * Supports both SQLite and MySQL databases.
 */
class DatabaseManager(private val plugin: TrialChamberPro) {

    private lateinit var dataSource: HikariDataSource
    private var _databaseType: DatabaseType = DatabaseType.SQLITE

    /** The type of database being used (SQLITE or MYSQL) */
    val databaseType: DatabaseType
        get() = _databaseType

    enum class DatabaseType {
        SQLITE, MYSQL
    }

    /**
     * Initializes the database connection pool and creates tables.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        val config = plugin.config
        _databaseType = try {
            DatabaseType.valueOf(config.getString("database.type", "SQLITE")!!.uppercase())
        } catch (_: IllegalArgumentException) {
            plugin.logger.warning("Invalid database type, defaulting to SQLITE")
            DatabaseType.SQLITE
        }

        dataSource = when (_databaseType) {
            DatabaseType.SQLITE -> createSQLiteDataSource()
            DatabaseType.MYSQL -> createMySQLDataSource(config)
        }

        plugin.logger.info("Database connection pool initialized (${_databaseType.name})")

        // Create tables
        createTables()

        // Run schema migrations for new features
        runMigrations()
    }

    /**
     * Creates a SQLite data source.
     */
    private fun createSQLiteDataSource(): HikariDataSource {
        val dbFile = File(plugin.dataFolder, "database.db")
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}?foreign_keys=on"
            driverClassName = "org.sqlite.JDBC"
            // WAL mode allows multiple concurrent readers (but still only 1 writer)
            // Setting pool size to 5 allows read operations to run concurrently
            maximumPoolSize = 5
            minimumIdle = 1
            connectionTimeout = 30000 // 30 seconds - fail faster if pool is exhausted
            idleTimeout = 300000 // 5 minutes
            maxLifetime = 600000 // 10 minutes
            connectionTestQuery = "SELECT 1"
            poolName = "TrialChamberPro-SQLite"

            // Enable leak detection (helps identify connection leaks during development)
            leakDetectionThreshold = 10000 // 10 seconds

            // SQLite optimizations for better concurrent access
            addDataSourceProperty("journal_mode", "WAL") // Write-Ahead Logging for better concurrency
            addDataSourceProperty("synchronous", "NORMAL") // Balance between safety and speed
            addDataSourceProperty("busy_timeout", "5000") // Wait up to 5s for locks instead of failing immediately
        }
        return HikariDataSource(config)
    }

    /**
     * Creates a MySQL data source.
     */
    private fun createMySQLDataSource(config: FileConfiguration): HikariDataSource {
        val host = config.getString("database.host", "localhost")!!
        val port = config.getInt("database.port", 3306)
        val database = config.getString("database.database", "trialchamberpro")!!
        val username = config.getString("database.username", "root")!!
        val password = config.getString("database.password", "")!!
        val poolSize = config.getInt("database.pool-size", 10)

        val hikariConfig = HikariConfig().apply {
            jdbcUrl = "jdbc:mysql://$host:$port/$database?useSSL=false&serverTimezone=UTC"
            driverClassName = "com.mysql.cj.jdbc.Driver"
            this.username = username
            this.password = password
            maximumPoolSize = poolSize
            connectionTestQuery = "SELECT 1"
            poolName = "TrialChamberPro-MySQL"

            // Performance optimizations
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("useServerPrepStmts", "true")
        }

        return HikariDataSource(hikariConfig)
    }

    /**
     * Creates all database tables if they don't exist.
     */
    private suspend fun createTables() = withContext(Dispatchers.IO) {
        connection.use { conn ->
            conn.createStatement().use { stmt ->
                // Chambers table
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS chambers (
                        id ${if (databaseType == DatabaseType.SQLITE) "INTEGER PRIMARY KEY AUTOINCREMENT" else "INT AUTO_INCREMENT PRIMARY KEY"},
                        name VARCHAR(64) UNIQUE NOT NULL,
                        world VARCHAR(64) NOT NULL,
                        min_x INT NOT NULL,
                        min_y INT NOT NULL,
                        min_z INT NOT NULL,
                        max_x INT NOT NULL,
                        max_y INT NOT NULL,
                        max_z INT NOT NULL,
                        exit_x DOUBLE,
                        exit_y DOUBLE,
                        exit_z DOUBLE,
                        exit_yaw FLOAT,
                        exit_pitch FLOAT,
                        snapshot_file VARCHAR(255),
                        reset_interval BIGINT NOT NULL,
                        last_reset BIGINT,
                        created_at BIGINT NOT NULL
                    )
                    """.trimIndent()
                )

                // Vaults table
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS vaults (
                        id ${if (databaseType == DatabaseType.SQLITE) "INTEGER PRIMARY KEY AUTOINCREMENT" else "INT AUTO_INCREMENT PRIMARY KEY"},
                        chamber_id INT NOT NULL,
                        x INT NOT NULL,
                        y INT NOT NULL,
                        z INT NOT NULL,
                        type VARCHAR(16) DEFAULT 'NORMAL',
                        loot_table VARCHAR(64) NOT NULL,
                        FOREIGN KEY (chamber_id) REFERENCES chambers(id) ON DELETE CASCADE,
                        UNIQUE (chamber_id, x, y, z, type)
                    )
                    """.trimIndent()
                )

                // Spawners table
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS spawners (
                        id ${if (databaseType == DatabaseType.SQLITE) "INTEGER PRIMARY KEY AUTOINCREMENT" else "INT AUTO_INCREMENT PRIMARY KEY"},
                        chamber_id INT NOT NULL,
                        x INT NOT NULL,
                        y INT NOT NULL,
                        z INT NOT NULL,
                        type VARCHAR(16) DEFAULT 'NORMAL',
                        FOREIGN KEY (chamber_id) REFERENCES chambers(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                // Player vaults table
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS player_vaults (
                        player_uuid VARCHAR(36) NOT NULL,
                        vault_id INT NOT NULL,
                        last_opened BIGINT NOT NULL,
                        times_opened INT DEFAULT 0,
                        PRIMARY KEY (player_uuid, vault_id),
                        FOREIGN KEY (vault_id) REFERENCES vaults(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                // Player statistics table
                stmt.execute(
                    """
                    CREATE TABLE IF NOT EXISTS player_stats (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        chambers_completed INT DEFAULT 0,
                        normal_vaults_opened INT DEFAULT 0,
                        ominous_vaults_opened INT DEFAULT 0,
                        mobs_killed INT DEFAULT 0,
                        deaths INT DEFAULT 0,
                        time_spent BIGINT DEFAULT 0,
                        last_updated BIGINT NOT NULL
                    )
                    """.trimIndent()
                )

                // Create indexes for performance
                try {
                    // Use IF NOT EXISTS where supported; wrap in try/catch for MySQL which may not support it on older versions
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_vaults_chamber ON vaults(chamber_id)")
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_vaults_type ON vaults(type)")
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_vaults_player ON player_vaults(player_uuid)")
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_spawners_chamber ON spawners(chamber_id)")
                } catch (e: SQLException) {
                    // Fallback for databases that don't support IF NOT EXISTS
                    try { stmt.execute("CREATE INDEX idx_vaults_chamber ON vaults(chamber_id)") } catch (_: SQLException) {}
                    try { stmt.execute("CREATE INDEX idx_vaults_type ON vaults(type)") } catch (_: SQLException) {}
                    try { stmt.execute("CREATE INDEX idx_player_vaults_player ON player_vaults(player_uuid)") } catch (_: SQLException) {}
                    try { stmt.execute("CREATE INDEX idx_spawners_chamber ON spawners(chamber_id)") } catch (_: SQLException) {}
                }
            }
        }
        plugin.logger.info("Database tables created/verified successfully")
    }

    /**
     * Runs schema migrations for new features.
     * Each migration is idempotent (safe to run multiple times).
     */
    private suspend fun runMigrations() = withContext(Dispatchers.IO) {
        connection.use { conn ->
            conn.createStatement().use { stmt ->
                // v1.2.7: Per-chamber loot tables
                listOf(
                    "ALTER TABLE chambers ADD COLUMN normal_loot_table VARCHAR(64)",
                    "ALTER TABLE chambers ADD COLUMN ominous_loot_table VARCHAR(64)"
                ).forEach { sql ->
                    try {
                        stmt.execute(sql)
                        plugin.logger.info("Migration executed: $sql")
                    } catch (_: SQLException) {
                        // Column already exists - this is expected on subsequent runs
                    }
                }
            }
        }
    }

    /**
     * Gets a connection from the pool.
     */
    val connection: Connection
        get() = dataSource.connection

    /**
     * Executes a database operation with proper connection handling and error logging.
     * HIGH PRIORITY FIX: Prevents connection leaks from exceptions before .use block.
     *
     * @param block The database operation to execute with the connection
     * @return The result of the database operation
     * @throws SQLException if the database operation fails
     */
    suspend fun <T> withConnection(block: (Connection) -> T): T =
        withContext(Dispatchers.IO) {
            try {
                connection.use { conn -> block(conn) }
            } catch (e: SQLException) {
                plugin.logger.severe("Database operation failed: ${e.message}")
                throw e
            }
        }

    /**
     * Closes the database connection pool.
     */
    fun close() {
        if (::dataSource.isInitialized && !dataSource.isClosed) {
            dataSource.close()
            plugin.logger.info("Database connection pool closed")
        }
    }

    /**
     * Tests if the database connection is valid.
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT 1").use { rs ->
                        rs.next()
                    }
                }
            }
            true
        } catch (e: SQLException) {
            plugin.logger.severe("Database connection test failed: ${e.message}")
            false
        }
    }
}
