package com.telehop.common.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class DatabaseManager {
    private final HikariDataSource dataSource;
    private final ExecutorService dbExecutor;

    public DatabaseManager(DatabaseConfig config) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("MySQL JDBC driver not found in plugin classpath", e);
        }
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.jdbcUrl());
        hikariConfig.setUsername(config.username());
        hikariConfig.setPassword(config.password());
        hikariConfig.setMaximumPoolSize(config.poolSize());
        hikariConfig.setMinimumIdle(Math.max(2, config.poolSize() / 2));
        hikariConfig.setConnectionTimeout(10_000);
        hikariConfig.setIdleTimeout(600_000);
        hikariConfig.setMaxLifetime(1_800_000);
        this.dataSource = new HikariDataSource(hikariConfig);
        this.dbExecutor = Executors.newFixedThreadPool(Math.max(4, config.poolSize()));
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

    public <T> java.util.concurrent.CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return java.util.concurrent.CompletableFuture.supplyAsync(supplier, dbExecutor);
    }

    public java.util.concurrent.CompletableFuture<Void> runAsync(Runnable runnable) {
        return java.util.concurrent.CompletableFuture.runAsync(runnable, dbExecutor);
    }

    public void shutdown() {
        dbExecutor.shutdownNow();
        dataSource.close();
    }
}
