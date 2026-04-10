package com.telehop.velocity.config;

import com.telehop.common.db.DatabaseConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public record VelocitySettings(
        DatabaseConfig databaseConfig,
        String hubServer,
        List<String> backends,
        long dedupeWindowMs,
        long requestTimeoutMs
) {
    public static VelocitySettings from(Properties props) {
        DatabaseConfig databaseConfig = new DatabaseConfig(
                props.getProperty("mysql.host", "127.0.0.1"),
                Integer.parseInt(props.getProperty("mysql.port", "3306")),
                props.getProperty("mysql.database", "telehop"),
                props.getProperty("mysql.username", "telehop"),
                props.getProperty("mysql.password", ""),
                Integer.parseInt(props.getProperty("mysql.poolSize", "5"))
        );
        String backendsRaw = props.getProperty("servers.backends", "lobby");
        List<String> backends = Collections.unmodifiableList(
                Arrays.stream(backendsRaw.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList()
        );
        return new VelocitySettings(
                databaseConfig,
                props.getProperty("servers.hub", "lobby"),
                backends,
                Long.parseLong(props.getProperty("messaging.dedupeWindowMs", "30000")),
                Long.parseLong(props.getProperty("messaging.requestTimeoutMs", "10000"))
        );
    }
}
