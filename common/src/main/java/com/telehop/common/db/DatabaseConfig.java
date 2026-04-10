package com.telehop.common.db;

public record DatabaseConfig(
        String host,
        int port,
        String database,
        String username,
        String password,
        int poolSize
) {
    public String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database +
                "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }
}
