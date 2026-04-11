package com.telehop.common.db;

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
