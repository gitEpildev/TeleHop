package com.telehop.velocity.config;

import com.telehop.common.config.ConfigYaml;
import com.telehop.common.config.ConfigurationUpgrade;
import com.telehop.common.config.DeploymentConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** Loads proxy configuration as typed YAML and migrates original Java properties without logging secret values. */
public final class VelocityProfiles {
    private record Setting(String property, String file, String key, Object fallback) {}

    private static final List<Setting> SETTINGS = List.of(
            new Setting("mysql.host", "database", "host", "127.0.0.1"),
            new Setting("mysql.port", "database", "port", 3306),
            new Setting("mysql.database", "database", "database", "telehop"),
            new Setting("mysql.username", "database", "username", "telehop"),
            new Setting("mysql.password", "database", "password", ""),
            new Setting("mysql.poolSize", "database", "pool-size", 5),
            new Setting("redis.host", "redis", "host", "127.0.0.1"),
            new Setting("redis.port", "redis", "port", 6379),
            new Setting("redis.password", "redis", "password", ""),
            new Setting("redis.channel-prefix", "redis", "channel-prefix", "telehop"),
            new Setting("redis.ssl", "redis", "ssl", false),
            new Setting("redis.ssl-verify", "redis", "ssl-verify", true),
            new Setting("redis.ssl-ca-cert", "redis", "ssl-ca-cert", ""),
            new Setting("proxy.id", "proxy", "id", ""),
            new Setting("multi-proxy.enabled", "proxy", "multi-proxy-enabled", false),
            new Setting("multi-proxy.global-player-list", "proxy", "global-player-list", true),
            new Setting("multi-proxy.cross-proxy-timeout-ms", "proxy", "cross-proxy-timeout-ms", 15000L),
            new Setting("servers.hub", "servers", "hub", ""),
            new Setting("servers.backends", "servers", "backends", List.of()),
            new Setting("messaging.dedupeWindowMs", "messaging", "dedupe-window-ms", 30000L),
            new Setting("messaging.requestTimeoutMs", "messaging", "request-timeout-ms", 10000L));

    private VelocityProfiles() {}

    /** The first registered server is the first configured connection attempt where available. */
    public static VelocitySettings load(Path data, List<String> registered) throws Exception {
        boolean tracked = ConfigurationUpgrade.tracked(data, "velocity");
        boolean install = !tracked || Files.exists(data.resolve("migrations/work"));
        if (!install) {
            var profile = DeploymentConfig.read(data);
            install = SETTINGS.stream().anyMatch(field -> !Files.isRegularFile(file(data, profile, field.file())));
        }
        if (install) ConfigurationUpgrade.run(data, "velocity", stage -> prepare(stage, registered, !tracked));
        return read(data, registered);
    }

    private static void prepare(Path stage, List<String> registered, boolean first) throws IOException {
        Path old = stage.resolve("config.properties");
        var props = new Properties();
        boolean legacy = first && Files.isRegularFile(old);
        if (legacy) try (var stream = Files.newInputStream(old)) { props.load(stream); }
        Path selector = stage.resolve("config.yml");
        if (Files.notExists(selector)) {
            var mode = bool(props.getProperty("multi-proxy.enabled", "false"), "multi-proxy.enabled")
                    ? DeploymentConfig.Mode.MULTI : DeploymentConfig.Mode.SINGLE;
            Files.writeString(selector, DeploymentConfig.initial(mode, false));
        }
        var deployment = DeploymentConfig.read(stage);
        installFiles(stage, registered, props, deployment, legacy);
        if (first) {
            for (var mode : DeploymentConfig.Mode.values()) {
                var inactive = DeploymentConfig.parse(Map.of("mode", mode.name()));
                if (mode != deployment.mode() && !Files.exists(inactive.profileDirectory(stage))) {
                    installFiles(stage, registered, new Properties(), inactive, false);
                }
            }
        }
        read(stage, registered);
        if (legacy && deployment.backups()) Files.delete(old);
    }

    private static void installFiles(Path stage, List<String> registered, Properties props,
                                     DeploymentConfig deployment, boolean legacy) throws IOException {
        var documents = new LinkedHashMap<Path, Map<String, Object>>();
        var sections = new LinkedHashMap<String, Map<String, Object>>();
        for (Setting field : SETTINGS) {
            Object value = field.fallback();
            if (legacy && props.containsKey(field.property())) value = convert(props.getProperty(field.property()), field);
            else if (field.property().equals("proxy.id")) value = !legacy ? UUID.randomUUID().toString() : "proxy-1";
            else if (field.property().equals("servers.hub")) value = legacy ? "lobby" : registered.stream().findFirst().orElse("");
            else if (field.property().equals("servers.backends")) value = legacy ? List.of("lobby") : registered;
            else if (field.property().equals("multi-proxy.enabled")) value = deployment.mode() == DeploymentConfig.Mode.MULTI;
            var section = sections.computeIfAbsent(field.file(), ignored -> new LinkedHashMap<>());
            section.put(field.key(), value);
            String key = field.file().equals("database") ? "mysql" : field.file();
            documents.computeIfAbsent(file(stage, deployment, field.file()), ignored -> new LinkedHashMap<>()).put(key, section);
        }
        for (var entry : documents.entrySet()) {
            Path target = entry.getKey();
            Files.createDirectories(target.getParent());
            if (Files.notExists(target)) {
                Files.writeString(target, "# TeleHop proxy settings. Changes require a proxy restart.\n" + ConfigYaml.dump(entry.getValue()));
            }
        }
    }

    private static Path file(Path data, DeploymentConfig config, String name) {
        Path dir = name.equals("database") || name.equals("redis") ? data.resolve("config") : config.profileDirectory(data);
        return dir.resolve(config.mode() == DeploymentConfig.Mode.SINGLE && name.equals("proxy") ? "servers.yml" : name + ".yml");
    }

    private static VelocitySettings read(Path data, List<String> registered) throws IOException {
        var deployment = DeploymentConfig.read(data);
        var files = new LinkedHashMap<String, Map<String, Object>>();
        var props = new Properties();
        for (Setting field : SETTINGS) {
            String file = field.file();
            if (!files.containsKey(file)) files.put(file, ConfigYaml.read(file(data, deployment, file)));
            String key = (file.equals("database") ? "mysql" : file) + "." + field.key();
            Object value = ConfigYaml.get(files.get(file), key, field.fallback());
            if (value instanceof List<?> names && field.fallback() instanceof List) {
                if (names.stream().anyMatch(name -> !(name instanceof String s) || s.isBlank() || s.contains(","))) {
                    throw new IllegalArgumentException(key + " must contain backend names");
                }
                value = String.join(",", names.stream().map(String.class::cast).toList());
            } else if (field.fallback() instanceof Long && (value instanceof Integer || value instanceof Long)) {
                value = ((Number) value).longValue();
            } else if (!field.fallback().getClass().isInstance(value)) {
                throw new IllegalArgumentException(key + " has an invalid value in " + file + ".yml");
            }
            props.setProperty(field.property(), String.valueOf(value));
        }
        if (deployment.mode() != DeploymentConfig.Mode.CUSTOM) props.setProperty("multi-proxy.enabled",
                Boolean.toString(deployment.mode() == DeploymentConfig.Mode.MULTI));
        var settings = VelocitySettings.from(props);
        var known = new HashSet<>(registered.stream().map(s -> s.toLowerCase(Locale.ROOT)).toList());
        var backends = new HashSet<String>();
        for (String name : settings.backends()) {
            if (!known.contains(name.toLowerCase(Locale.ROOT)) || !backends.add(name.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("servers.yml: duplicate or unregistered backend " + name + "; check velocity.toml");
            }
        }
        if (!backends.contains(settings.hubServer().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("servers.yml: hub must be one of the configured and registered backends");
        }
        if (settings.requestTimeoutMs() < 1 || settings.dedupeWindowMs() < 1 || settings.crossProxyTimeoutMs() < 1) {
            throw new IllegalArgumentException("Messaging and cross-proxy timeouts must be positive");
        }
        return settings;
    }

    private static Object convert(String value, Setting field) {
        if (field.fallback() instanceof Boolean) return bool(value, field.property());
        if (field.fallback() instanceof Long) {
            try { return Long.valueOf(value); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("Expected an integer for " + field.property()); }
        }
        if (field.fallback() instanceof Integer) {
            try { return Integer.valueOf(value); }
            catch (NumberFormatException ex) { throw new IllegalArgumentException("Expected an integer for " + field.property()); }
        }
        if (field.fallback() instanceof List) return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return value;
    }

    private static boolean bool(String value, String key) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) throw new IllegalArgumentException("Expected true or false for " + key);
        return Boolean.parseBoolean(value);
    }
}
