package com.shinobu.rankup.data

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.sql.Connection
import java.sql.ResultSet
import java.util.logging.Level

/**
 * Database manager for ShinobuRankup.
 * Provides HikariCP connection pooling with support for SQLite and MySQL.
 *
 * All database operations are performed asynchronously using Kotlin coroutines
 * with the IO dispatcher to prevent blocking the main server thread.
 *
 * @property plugin The plugin instance for logging and data folder access
 */
class Database(private val plugin: JavaPlugin) {

    private var dataSource: HikariDataSource? = null
    private var databaseType: DatabaseType = DatabaseType.SQLITE

    @Volatile
    private var isConnected: Boolean = false

    /**
     * Initialize the database connection pool.
     *
     * @param config The database configuration section from config.yml
     * @return true if connection was successful
     */
    suspend fun initialize(config: ConfigurationSection): Boolean = withContext(Dispatchers.IO) {
        try {
            val type = config.getString("type", "sqlite")?.lowercase() ?: "sqlite"
            databaseType = DatabaseType.fromString(type)

            val hikariConfig = when (databaseType) {
                DatabaseType.SQLITE -> createSQLiteConfig(config.getConfigurationSection("sqlite"))
                DatabaseType.MYSQL -> createMySQLConfig(config.getConfigurationSection("mysql"))
            }

            dataSource = HikariDataSource(hikariConfig)
            isConnected = true

            // Create tables
            createTables()

            plugin.logger.info("Database connection established (${databaseType.displayName})")
            true
        } catch (e: Exception) {
            plugin.logger.log(Level.SEVERE, "Failed to initialize database", e)
            isConnected = false
            false
        }
    }

    /**
     * Create SQLite HikariCP configuration.
     */
    private fun createSQLiteConfig(config: ConfigurationSection?): HikariConfig {
        val fileName = config?.getString("file") ?: "data/shinobu_rankup.db"
        val dbFile = File(plugin.dataFolder, fileName)

        // Ensure parent directory exists
        dbFile.parentFile?.mkdirs()

        return HikariConfig().apply {
            poolName = "ShinobuRankup-SQLite"
            driverClassName = "org.sqlite.JDBC"
            jdbcUrl = "jdbc:sqlite:${dbFile.absolutePath}"

            // SQLite specific settings
            maximumPoolSize = 1 // SQLite only supports single connection
            minimumIdle = 1
            connectionTimeout = 10000
            idleTimeout = 600000
            maxLifetime = 1800000

            // SQLite performance optimizations
            addDataSourceProperty("journal_mode", "WAL")
            addDataSourceProperty("synchronous", "NORMAL")
            addDataSourceProperty("cache_size", "10000")
            addDataSourceProperty("foreign_keys", "ON")
        }
    }

    /**
     * Create MySQL HikariCP configuration.
     */
    private fun createMySQLConfig(config: ConfigurationSection?): HikariConfig {
        val host = config?.getString("host") ?: "localhost"
        val port = config?.getInt("port") ?: 3306
        val database = config?.getString("database") ?: "shinobu_rankup"
        val username = config?.getString("username") ?: "root"
        val password = config?.getString("password") ?: ""

        val poolConfig = config?.getConfigurationSection("pool")

        return HikariConfig().apply {
            poolName = "ShinobuRankup-MySQL"
            driverClassName = "com.mysql.cj.jdbc.Driver"
            jdbcUrl = "jdbc:mysql://$host:$port/$database?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8"
            this.username = username
            this.password = password

            // Pool settings
            maximumPoolSize = poolConfig?.getInt("size") ?: 10
            minimumIdle = poolConfig?.getInt("min-idle") ?: 2
            connectionTimeout = poolConfig?.getLong("connection-timeout") ?: 10000L
            idleTimeout = poolConfig?.getLong("idle-timeout") ?: 600000L
            maxLifetime = poolConfig?.getLong("max-lifetime") ?: 1800000L

            // MySQL performance optimizations
            val props = config?.getConfigurationSection("properties")
            addDataSourceProperty("cachePrepStmts", props?.getBoolean("cachePrepStmts") ?: true)
            addDataSourceProperty("prepStmtCacheSize", props?.getInt("prepStmtCacheSize") ?: 250)
            addDataSourceProperty("prepStmtCacheSqlLimit", props?.getInt("prepStmtCacheSqlLimit") ?: 2048)
            addDataSourceProperty("useServerPrepStmts", props?.getBoolean("useServerPrepStmts") ?: true)
            addDataSourceProperty("useLocalSessionState", props?.getBoolean("useLocalSessionState") ?: true)
            addDataSourceProperty("rewriteBatchedStatements", props?.getBoolean("rewriteBatchedStatements") ?: true)
            addDataSourceProperty("cacheResultSetMetadata", props?.getBoolean("cacheResultSetMetadata") ?: true)
            addDataSourceProperty("cacheServerConfiguration", props?.getBoolean("cacheServerConfiguration") ?: true)
            addDataSourceProperty("elideSetAutoCommits", props?.getBoolean("elideSetAutoCommits") ?: true)
            addDataSourceProperty("maintainTimeStats", props?.getBoolean("maintainTimeStats") ?: false)
        }
    }

    /**
     * Create required database tables.
     */
    private suspend fun createTables() = withContext(Dispatchers.IO) {
        val autoIncrement = when (databaseType) {
            DatabaseType.SQLITE -> "AUTOINCREMENT"
            DatabaseType.MYSQL -> "AUTO_INCREMENT"
        }

        val timestampType = when (databaseType) {
            DatabaseType.SQLITE -> "TEXT"
            DatabaseType.MYSQL -> "TIMESTAMP"
        }

        val defaultTimestamp = when (databaseType) {
            DatabaseType.SQLITE -> "CURRENT_TIMESTAMP"
            DatabaseType.MYSQL -> "CURRENT_TIMESTAMP"
        }

        // Build CREATE TABLE SQL based on database type
        val createTableSql = if (databaseType == DatabaseType.SQLITE) {
            // SQLite: Simple CREATE TABLE without inline INDEX
            """
                CREATE TABLE IF NOT EXISTS player_data (
                    id INTEGER PRIMARY KEY $autoIncrement,
                    uuid VARCHAR(36) NOT NULL UNIQUE,
                    name VARCHAR(16) NOT NULL,
                    current_rank_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    total_spent DOUBLE NOT NULL DEFAULT 0.0,
                    rankup_count INTEGER NOT NULL DEFAULT 0,
                    first_join $timestampType NOT NULL DEFAULT $defaultTimestamp,
                    last_rankup $timestampType NULL,
                    last_seen $timestampType NOT NULL DEFAULT $defaultTimestamp,
                    metadata TEXT NULL
                )
            """.trimIndent()
        } else {
            // MySQL: CREATE TABLE with inline INDEX
            """
                CREATE TABLE IF NOT EXISTS player_data (
                    id INTEGER PRIMARY KEY $autoIncrement,
                    uuid VARCHAR(36) NOT NULL UNIQUE,
                    name VARCHAR(16) NOT NULL,
                    current_rank_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    total_spent DOUBLE NOT NULL DEFAULT 0.0,
                    rankup_count INTEGER NOT NULL DEFAULT 0,
                    first_join $timestampType NOT NULL DEFAULT $defaultTimestamp,
                    last_rankup $timestampType NULL,
                    last_seen $timestampType NOT NULL DEFAULT $defaultTimestamp,
                    metadata TEXT NULL,
                    INDEX idx_uuid (uuid),
                    INDEX idx_name (name),
                    INDEX idx_rank (current_rank_id),
                    INDEX idx_rankup_count (rankup_count DESC)
                )
            """.trimIndent()
        }

        execute(createTableSql)

        // Create indexes separately for SQLite
        if (databaseType == DatabaseType.SQLITE) {
            execute("CREATE INDEX IF NOT EXISTS idx_uuid ON player_data(uuid)")
            execute("CREATE INDEX IF NOT EXISTS idx_name ON player_data(name)")
            execute("CREATE INDEX IF NOT EXISTS idx_rank ON player_data(current_rank_id)")
            execute("CREATE INDEX IF NOT EXISTS idx_rankup_count ON player_data(rankup_count DESC)")
        }

        plugin.logger.info("Database tables initialized")
    }

    /**
     * Execute a SQL statement without returning results.
     *
     * @param sql The SQL statement to execute
     * @param params The parameters to bind to the statement
     */
    suspend fun execute(sql: String, vararg params: Any?) = withContext(Dispatchers.IO) {
        getConnection()?.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { index, param ->
                    stmt.setObject(index + 1, param)
                }
                stmt.executeUpdate()
            }
        }
    }

    /**
     * Execute a SQL query and map results.
     *
     * @param sql The SQL query to execute
     * @param params The parameters to bind to the query
     * @param mapper Function to map ResultSet rows to objects
     * @return List of mapped objects
     */
    suspend fun <T> query(
        sql: String,
        vararg params: Any?,
        mapper: (ResultSet) -> T
    ): List<T> = withContext(Dispatchers.IO) {
        val results = mutableListOf<T>()

        getConnection()?.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { index, param ->
                    stmt.setObject(index + 1, param)
                }
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        results.add(mapper(rs))
                    }
                }
            }
        }

        results
    }

    /**
     * Execute a SQL query and return a single result.
     *
     * @param sql The SQL query to execute
     * @param params The parameters to bind to the query
     * @param mapper Function to map ResultSet row to object
     * @return The mapped object or null if no results
     */
    suspend fun <T> queryOne(
        sql: String,
        vararg params: Any?,
        mapper: (ResultSet) -> T
    ): T? = withContext(Dispatchers.IO) {
        getConnection()?.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                params.forEachIndexed { index, param ->
                    stmt.setObject(index + 1, param)
                }
                stmt.executeQuery().use { rs ->
                    if (rs.next()) mapper(rs) else null
                }
            }
        }
    }

    /**
     * Execute a batch of SQL statements in a transaction.
     *
     * @param statements List of SQL statements with their parameters
     * @return true if all statements executed successfully
     */
    suspend fun executeBatch(statements: List<Pair<String, Array<out Any?>>>): Boolean =
        withContext(Dispatchers.IO) {
            getConnection()?.use { conn ->
                val originalAutoCommit = conn.autoCommit
                try {
                    conn.autoCommit = false

                    statements.forEach { (sql, params) ->
                        conn.prepareStatement(sql).use { stmt ->
                            params.forEachIndexed { index, param ->
                                stmt.setObject(index + 1, param)
                            }
                            stmt.executeUpdate()
                        }
                    }

                    conn.commit()
                    true
                } catch (e: Exception) {
                    conn.rollback()
                    plugin.logger.log(Level.WARNING, "Batch execution failed, rolled back", e)
                    false
                } finally {
                    conn.autoCommit = originalAutoCommit
                }
            } ?: false
        }

    /**
     * Execute an operation within a transaction.
     *
     * @param block The operation to execute
     * @return The result of the operation
     */
    suspend fun <T> transaction(block: suspend (Connection) -> T): T? = withContext(Dispatchers.IO) {
        getConnection()?.use { conn ->
            val originalAutoCommit = conn.autoCommit
            try {
                conn.autoCommit = false
                val result = block(conn)
                conn.commit()
                result
            } catch (e: Exception) {
                conn.rollback()
                plugin.logger.log(Level.WARNING, "Transaction failed, rolled back", e)
                null
            } finally {
                conn.autoCommit = originalAutoCommit
            }
        }
    }

    /**
     * Get a connection from the pool.
     *
     * @return A database connection or null if unavailable
     */
    fun getConnection(): Connection? {
        return try {
            dataSource?.connection
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Failed to get database connection", e)
            null
        }
    }

    /**
     * Check if the database is connected.
     */
    fun isConnected(): Boolean = isConnected && dataSource != null && !dataSource!!.isClosed

    /**
     * Get the database type.
     */
    fun getType(): DatabaseType = databaseType

    /**
     * Get pool statistics for monitoring.
     */
    fun getPoolStats(): PoolStats? {
        val pool = dataSource?.hikariPoolMXBean ?: return null
        return PoolStats(
            activeConnections = pool.activeConnections,
            idleConnections = pool.idleConnections,
            totalConnections = pool.totalConnections,
            threadsAwaitingConnection = pool.threadsAwaitingConnection
        )
    }

    /**
     * Shutdown the database connection pool.
     */
    suspend fun shutdown() = withContext(Dispatchers.IO) {
        try {
            dataSource?.close()
            isConnected = false
            plugin.logger.info("Database connection pool closed")
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Error closing database connection", e)
        }
    }

    /**
     * Database type enumeration.
     */
    enum class DatabaseType(val displayName: String) {
        SQLITE("SQLite"),
        MYSQL("MySQL/MariaDB");

        companion object {
            fun fromString(value: String): DatabaseType {
                return when (value.lowercase()) {
                    "mysql", "mariadb" -> MYSQL
                    else -> SQLITE
                }
            }
        }
    }

    /**
     * Pool statistics data class.
     */
    data class PoolStats(
        val activeConnections: Int,
        val idleConnections: Int,
        val totalConnections: Int,
        val threadsAwaitingConnection: Int
    )
}
