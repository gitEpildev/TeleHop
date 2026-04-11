package com.telehop.common.db;

/**
 * Immutable configuration for a MySQL/MariaDB connection.
 * Validated on construction — blank hosts, invalid ports, or zero pool sizes
 * throw {@link IllegalArgumentException}.
 *
 * @param host     database hostname or IP
 * @param port     database port (1–65535)
 * @param database schema name
 * @param username database user
 * @param password database password
 * @param poolSize HikariCP maximum pool size (≥ 1)
 */
public record DatabaseConfig(
        String host,
        int port,
        String database,
        String username,
        String password,
        int poolSize
) {
    public DatabaseConfig {
        if (host == null || host.isBlank())
            throw new IllegalArgumentException("Database host must not be blank");
        if (port < 1 || port > 65535)
            throw new IllegalArgumentException("Database port must be 1-65535, got " + port);
        if (database == null || database.isBlank())
            throw new IllegalArgumentException("Database name must not be blank");
        if (poolSize < 1)
            throw new IllegalArgumentException("Database pool size must be >= 1, got " + poolSize);
    }

    public String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" +
                "&useUnicode=true&characterEncoding=UTF-8";
    }
}
