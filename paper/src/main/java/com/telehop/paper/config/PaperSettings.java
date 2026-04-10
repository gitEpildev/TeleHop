package com.telehop.paper.config;

import com.telehop.common.db.DatabaseConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record PaperSettings(
        String serverName,
        String hubServer,
        Map<String, String> servers,
        DatabaseConfig databaseConfig,
        long dedupeWindowMs,
        long requestTimeoutMs,
        int tpaTimeoutSeconds,
        int tpaCooldownSeconds,
        int tpaDelaySeconds,
        boolean tpaCancelOnMove,
        int rtpCooldownSeconds,
        int rtpDelaySeconds,
        boolean rtpCancelOnMove,
        boolean showCountdown,
        boolean auditEnabled,
        Map<String, Boolean> features,
        String language
) {
    public static PaperSettings from(FileConfiguration cfg) {
        DatabaseConfig db = new DatabaseConfig(
                cfg.getString("mysql.host", "127.0.0.1"),
                cfg.getInt("mysql.port", 3306),
                cfg.getString("mysql.database", "telehop"),
                cfg.getString("mysql.username", "telehop"),
                cfg.getString("mysql.password", ""),
                cfg.getInt("mysql.pool-size", 5)
        );
        Map<String, String> servers = new LinkedHashMap<>();
        ConfigurationSection section = cfg.getConfigurationSection("servers");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                servers.put(key, section.getString(key, key));
            }
        }
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("spawn", cfg.getBoolean("features.spawn", true));
        features.put("rtp", cfg.getBoolean("features.rtp", true));
        features.put("tpa", cfg.getBoolean("features.tpa", true));
        features.put("warps", cfg.getBoolean("features.warps", true));
        features.put("player-warps", cfg.getBoolean("features.player-warps", true));
        features.put("admin-tp", cfg.getBoolean("features.admin-tp", true));
        return new PaperSettings(
                cfg.getString("server-name", "lobby"),
                cfg.getString("hub-server", "lobby"),
                Collections.unmodifiableMap(servers),
                db,
                cfg.getLong("messaging.dedupe-window-ms", 30_000L),
                cfg.getLong("messaging.request-timeout-ms", 10_000L),
                cfg.getInt("tpa.timeout-seconds", 60),
                cfg.getInt("tpa.cooldown-seconds", 10),
                cfg.getInt("tpa.delay-seconds", 0),
                cfg.getBoolean("tpa.cancel-on-move", true),
                cfg.getInt("rtp.cooldown-seconds", 30),
                cfg.getInt("rtp.delay-seconds", 0),
                cfg.getBoolean("rtp.cancel-on-move", true),
                cfg.getBoolean("teleport.show-countdown", true),
                cfg.getBoolean("audit.enabled", false),
                Collections.unmodifiableMap(features),
                cfg.getString("language", "en")
        );
    }

    public boolean isFeatureEnabled(String feature) {
        return features.getOrDefault(feature, true);
    }

    public String resolveServer(String label) {
        return servers.getOrDefault(label, label);
    }
}
