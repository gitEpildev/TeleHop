package com.telehop.common.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
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

    public DatabaseManager(DatabaseConfig config) {
        this(config, null);
    }

    public DatabaseManager(DatabaseConfig config, java.util.logging.Logger logger) {
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

        this.dataSource = new HikariDataSource(hikariConfig);
        this.dbExecutor = Executors.newFixedThreadPool(config.poolSize(), r -> {
            Thread t = new Thread(r, "TeleHop-DB-" + THREAD_ID.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
        this.circuitBreaker = new DatabaseCircuitBreaker(5, 30_000L, logger);
    }

    public void initSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            for (String statement : SqlSchema.statements()) {
                try (Statement st = connection.createStatement()) {
                    st.execute(statement);
                }
            }
            for (String migration : SqlSchema.migrations()) {
                try (Statement st = connection.createStatement()) {
                    st.execute(migration);
                } catch (SQLException ignored) {
                    // migration already applied or column doesn't exist — safe to skip
                }
            }
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
        return circuitBreaker.execute(() -> CompletableFuture.supplyAsync(supplier, dbExecutor));
    }

    /**
     * Submits an async fire-and-forget database operation through the circuit breaker.
     */
    public CompletableFuture<Void> runAsync(Runnable runnable) {
        return circuitBreaker.executeRun(() -> CompletableFuture.runAsync(runnable, dbExecutor));
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
