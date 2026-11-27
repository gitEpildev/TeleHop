package com.telehop.common.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Manages the HikariCP connection pool and provides an async executor
 * for all database operations. A {@link DatabaseCircuitBreaker} protects
 * against cascading failures when MySQL is unreachable.
 *
 * <p>Obtain connections via {@link #dataSource()} for schema init, or
 * use {@link #supplyAsync(Supplier)} / {@link #runAsync(Runnable)} for
 * circuit-breaker-protected async work.</p>
 */
public class DatabaseManager {
    private static final AtomicInteger THREAD_ID = new AtomicInteger(1);

    private final HikariDataSource dataSource;
    private final ExecutorService dbExecutor;
    private final DatabaseCircuitBreaker circuitBreaker;
    private final CompletableFuture<Void> schemaReady;

    public DatabaseManager(DatabaseConfig config) {
        this(config, null);
    }

    public DatabaseManager(DatabaseConfig config, java.util.logging.Logger logger) {
        this(config, logger, false);
    }

    private DatabaseManager(DatabaseConfig config, java.util.logging.Logger logger, boolean backgroundSchema) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("MySQL JDBC driver not found in plugin classpath", e);
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("TeleHop");
        hikariConfig.setJdbcUrl(config.jdbcUrl());
        hikariConfig.setUsername(config.username());
        hikariConfig.setPassword(config.password());
        hikariConfig.setAutoCommit(true);

        hikariConfig.setMaximumPoolSize(config.poolSize());
        hikariConfig.setMinimumIdle(Math.max(1, config.poolSize() / 4));

        hikariConfig.setConnectionTimeout(10_000);
        hikariConfig.setIdleTimeout(300_000);
        hikariConfig.setMaxLifetime(1_740_000);

        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.setLeakDetectionThreshold(60_000);

        // The game thread may construct this pool. Its first JDBC connection belongs to the worker.
        if (backgroundSchema) hikariConfig.setInitializationFailTimeout(-1);
        this.dataSource = new HikariDataSource(hikariConfig);
        this.dbExecutor = Executors.newFixedThreadPool(config.poolSize(), r -> {
            Thread t = new Thread(r, "TeleHop-DB-" + THREAD_ID.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
        this.circuitBreaker = new DatabaseCircuitBreaker(5, 30_000L, logger);
        schemaReady = backgroundSchema ? circuitBreaker.executeRun(() -> CompletableFuture.runAsync(() -> {
            try {
                initSchema();
            } catch (SQLException failure) {
                throw new java.util.concurrent.CompletionException(failure);
            }
        }, dbExecutor)) : CompletableFuture.completedFuture(null);
    }

    /** Start the pool and schema off the game thread. Repository work waits for the same schema result. */
    public static DatabaseManager start(DatabaseConfig config, java.util.logging.Logger logger) {
        return new DatabaseManager(config, logger, true);
    }

    public CompletableFuture<Void> schemaReady() {
        return schemaReady.copy();
    }

    /** Opens a pool with the required schema, releasing it if the upgrade fails. */
    public static DatabaseManager open(DatabaseConfig config) throws SQLException {
        DatabaseManager manager = new DatabaseManager(config);
        try {
            manager.initSchema();
            return manager;
        } catch (SQLException | RuntimeException e) {
            manager.shutdown();
            throw e;
        }
    }

    public void initSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            SqlSchema.initialise(connection);
        }
    }

    public DataSource dataSource() {
        return dataSource;
    }

    /**
     * Submits an async database read/write through the circuit breaker.
     * If the database has too many consecutive failures, calls fail fast
     * with {@link DatabaseCircuitBreaker.CircuitOpenException}.
     */
    public <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return schemaReady.thenCompose(ready -> circuitBreaker.execute(() -> CompletableFuture.supplyAsync(supplier, dbExecutor)));
    }

    /**
     * Submits an async fire-and-forget database operation through the circuit breaker.
     */
    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return schemaReady.thenCompose(ready -> circuitBreaker.executeRun(() -> CompletableFuture.runAsync(runnable, dbExecutor)));
    }

    /** Returns the circuit breaker for monitoring or testing. */
    public DatabaseCircuitBreaker circuitBreaker() {
        return circuitBreaker;
    }

    public void shutdown() {
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            dbExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        dataSource.close();
    }
}
