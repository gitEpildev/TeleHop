package com.telehop.velocity.config;

import com.telehop.common.db.DatabaseConfig;
import com.telehop.common.db.RedisConfig;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

public record VelocitySettings(
        DatabaseConfig databaseConfig,
        String hubServer,
        List<String> backends,
        long dedupeWindowMs,
        long requestTimeoutMs,
        boolean multiProxyEnabled,
        boolean globalPlayerList,
        long crossProxyTimeoutMs,
        RedisConfig redisConfig
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

        boolean multiProxy = Boolean.parseBoolean(props.getProperty("multi-proxy.enabled", "false"));
        boolean globalPlayerList = Boolean.parseBoolean(props.getProperty("multi-proxy.global-player-list", "true"));
        long crossProxyTimeoutMs = Long.parseLong(props.getProperty("multi-proxy.cross-proxy-timeout-ms", "15000"));

        String proxyId = props.getProperty("proxy.id", "proxy-1");
        RedisConfig redisConfig = new RedisConfig(
                multiProxy,
                props.getProperty("redis.host", "127.0.0.1"),
                Integer.parseInt(props.getProperty("redis.port", "6379")),
                props.getProperty("redis.password", ""),
                props.getProperty("redis.channel-prefix", "telehop"),
                proxyId
        );

        return new VelocitySettings(
                databaseConfig,
                props.getProperty("servers.hub", "lobby"),
                backends,
                Long.parseLong(props.getProperty("messaging.dedupeWindowMs", "30000")),
                Long.parseLong(props.getProperty("messaging.requestTimeoutMs", "10000")),
                multiProxy,
                globalPlayerList,
                crossProxyTimeoutMs,
                redisConfig
        );
    }
}
