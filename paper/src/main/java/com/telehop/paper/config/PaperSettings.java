package com.telehop.paper.config;

import com.telehop.common.config.ConfigYaml;
import com.telehop.common.config.DeploymentConfig;
import com.telehop.common.db.DatabaseConfig;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import java.nio.file.Path;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        String language,
        int homeMaxSlots,
        boolean homeConfirmSet,
        String homeGuiTitle,
        Material homeSetBed,
        Material homeEmptyBed,
        Material homeLockedBed,
        int homeGuiRows,
        boolean homeShowLocation,
        String homeWorldOverworld,
        String homeWorldNether,
        String homeWorldEnd,
        Map<String, String> homeServerColors,
        Set<String> homeBlockedServers,
        List<String> homeBlockedServerPrefixes,
        Map<String, TeleportEffect> teleportEffects,
        String respawnWorld,
        int respawnRadius,
        boolean respawnRespectBed,
        boolean respawnRespectAnchor,
        List<String> respawnExcludedWorlds,
        boolean multiProxyEnabled,
        Map<String, RegionConfig> regions,
        boolean networkMessagingEnabled
) {

    public record RegionConfig(List<String> servers, String hub) {}


    public record TeleportEffect(
            boolean particleEnabled, Particle particleType, int particleCount,
            boolean soundEnabled, Sound soundType, float soundVolume, float soundPitch
    ) {}

    /** Load and validate the active profile before replacing any live services. */
    public static PaperSettings load(Path root, DeploymentConfig configuration) {
        File configDir = configuration.profileDirectory(root).toFile();
        FileConfiguration general = loadYaml(configDir, "general.yml");
        FileConfiguration database = PaperProfiles.yaml(root.resolve("config/database.yml"));
        boolean single = configuration.mode() == DeploymentConfig.Mode.SINGLE;
        FileConfiguration messaging = single ? general : loadYaml(configDir, "messaging.yml");
        FileConfiguration featuresFile = loadYaml(configDir, "features.yml");
        FileConfiguration teleportFile = loadYaml(configDir, "teleport.yml");
        FileConfiguration tpaFile = loadYaml(configDir, "tpa.yml");
        FileConfiguration rtpFile = loadYaml(configDir, "rtp.yml");
        FileConfiguration homeFile = loadYaml(configDir, "home.yml");
        FileConfiguration respawnFile = loadYaml(configDir, "respawn.yml");

        DatabaseConfig db = new DatabaseConfig(
                database.getString("mysql.host", "127.0.0.1"),
                database.getInt("mysql.port", 3306),
                database.getString("mysql.database", "telehop"),
                database.getString("mysql.username", "telehop"),
                database.getString("mysql.password", ""),
                database.getInt("mysql.pool-size", 5)
        );

        Map<String, String> servers = new LinkedHashMap<>();
        ConfigurationSection section = general.getConfigurationSection("servers");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                servers.put(key, section.getString(key, key));
            }
        }

        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("hub", featuresFile.getBoolean("features.hub", false));
        features.put("spawn", featuresFile.getBoolean("features.spawn", true));
        features.put("rtp", featuresFile.getBoolean("features.rtp", true));
        features.put("tpa", featuresFile.getBoolean("features.tpa", true));
        features.put("warps", featuresFile.getBoolean("features.warps", true));
        features.put("player-warps", featuresFile.getBoolean("features.player-warps", true));
        features.put("admin-tp", featuresFile.getBoolean("features.admin-tp", true));
        features.put("homes", featuresFile.getBoolean("features.homes", true));
        features.put("back", featuresFile.getBoolean("features.back", true));
        features.put("tpa-toggle", featuresFile.getBoolean("features.tpa-toggle", true));
        features.put("random-respawn", featuresFile.getBoolean("features.random-respawn", true));
        features.put("last-location", featuresFile.getBoolean("features.last-location", true));

        Map<String, TeleportEffect> effects = loadEffects(teleportFile);

        List<String> blockedPrefixes = homeFile.getStringList("homes.blocked-server-prefixes").stream()
                .map(String::toLowerCase).toList();

        boolean multiProxy = configuration.mode() == DeploymentConfig.Mode.MULTI
                || configuration.mode() == DeploymentConfig.Mode.CUSTOM && customMultiProxy(root);
        String regionFile = PaperProfiles.template(configuration.mode(), single ? "general.yml" : "regions.yml");
        Map<String, RegionConfig> regionMap = loadRegions(root, regionFile);
        validate(general, database, featuresFile, messaging, homeFile, tpaFile, rtpFile, respawnFile);

        return new PaperSettings(
                general.getString("server-name"),
                general.getString("hub-server"),
                Collections.unmodifiableMap(servers),
                db,
                messaging.getLong("messaging.dedupe-window-ms", 30_000L),
                messaging.getLong("messaging.request-timeout-ms", 10_000L),
                tpaFile.getInt("tpa.timeout-seconds", 60),
                tpaFile.getInt("tpa.cooldown-seconds", 10),
                tpaFile.getInt("tpa.delay-seconds", 0),
                tpaFile.getBoolean("tpa.cancel-on-move", true),
                rtpFile.getInt("rtp.cooldown-seconds", 30),
                rtpFile.getInt("rtp.delay-seconds", 0),
                rtpFile.getBoolean("rtp.cancel-on-move", true),
                teleportFile.getBoolean("show-countdown", true),
                general.getBoolean("audit.enabled", false),
                Collections.unmodifiableMap(features),
                general.getString("language", "en"),
                Math.clamp(homeFile.getInt("homes.max-slots", 10), 1, 10),
                homeFile.getBoolean("homes.confirm-set", true),
                homeFile.getString("homes.gui-title", "<gradient:red:gold>Your Homes</gradient>"),
                parseMaterial(homeFile.getString("homes.bed-set", "LIME_BED"), Material.LIME_BED),
                parseMaterial(homeFile.getString("homes.bed-empty", "RED_BED"), Material.RED_BED),
                parseMaterial(homeFile.getString("homes.bed-locked", "LIGHT_BLUE_BED"), Material.LIGHT_BLUE_BED),
                homeFile.getInt("homes.gui-rows", 5),
                homeFile.getBoolean("homes.show-location", true),
                homeFile.getString("homes.world-colors.overworld", "<green>Overworld</green>"),
                homeFile.getString("homes.world-colors.nether", "<gradient:red:gold>Nether</gradient>"),
                homeFile.getString("homes.world-colors.the-end", "<gradient:dark_purple:blue>The End</gradient>"),
                Collections.unmodifiableMap(loadServerColors(homeFile)),
                homeFile.getStringList("homes.blocked-servers").stream()
                        .map(String::toLowerCase).collect(Collectors.toUnmodifiableSet()),
                Collections.unmodifiableList(blockedPrefixes),
                Collections.unmodifiableMap(effects),
                respawnFile.getString("random-respawn.world", ""),
                respawnFile.getInt("random-respawn.radius", 5000),
                respawnFile.getBoolean("random-respawn.respect-bed-spawn", true),
                respawnFile.getBoolean("random-respawn.respect-anchor-spawn", true),
                Collections.unmodifiableList(new ArrayList<>(
                        respawnFile.getStringList("random-respawn.excluded-worlds"))),
                multiProxy,
                Collections.unmodifiableMap(regionMap),
                messaging.getBoolean("messaging.network-enabled", true)
        );
    }

    /**
     * Legacy loader for backward compatibility during migration.
     */
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
        features.put("hub", cfg.getBoolean("features.hub", false));
        features.put("spawn", cfg.getBoolean("features.spawn", true));
        features.put("rtp", cfg.getBoolean("features.rtp", true));
        features.put("tpa", cfg.getBoolean("features.tpa", true));
        features.put("warps", cfg.getBoolean("features.warps", true));
        features.put("player-warps", cfg.getBoolean("features.player-warps", true));
        features.put("admin-tp", cfg.getBoolean("features.admin-tp", true));
        features.put("homes", true);
        features.put("back", true);
        features.put("tpa-toggle", true);
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
                cfg.getString("language", "en"),
                5, true, "<gradient:red:gold>Your Homes</gradient>",
                Material.LIME_BED, Material.RED_BED, Material.LIGHT_BLUE_BED, 3,
                true,
                "<green>Overworld</green>",
                "<gradient:red:gold>Nether</gradient>",
                "<gradient:dark_purple:blue>The End</gradient>",
                Map.of(),
                Set.of("lobby"),
                List.of(),
                Map.of(),
                "world",
                5000,
                true,
                true,
                List.of(),
                false,
                Map.of(), true
        );
    }

    public boolean isHomeBlockedOnCurrentServer() {
        return isHomeBlockedOnServer(serverName);
    }

    public boolean isHomeBlockedOnServer(String server) {
        String lower = server.toLowerCase();
        if (homeBlockedServers.contains(lower)) return true;
        return homeBlockedServerPrefixes.stream().anyMatch(lower::startsWith);
    }

    /**
     * Resolves the regional hub for a given server name.
     * If regions are configured and the server belongs to a region, returns that region's hub.
     * Otherwise falls back to {@link #hubServer()}.
     */
    public String getRegionalHub(String currentServer) {
        if (!multiProxyEnabled || regions.isEmpty()) return hubServer;
        String lower = currentServer.toLowerCase();
        for (RegionConfig region : regions.values()) {
            for (String s : region.servers()) {
                if (s.equalsIgnoreCase(lower)) return region.hub();
            }
        }
        return hubServer;
    }

    public boolean isFeatureEnabled(String feature) {
        return features.getOrDefault(feature, true);
    }

    /**
     * Returns true if the given world name matches any entry in the
     * excluded-worlds list. Supports trailing * for prefix matching.
     */
    public boolean isRespawnExcludedWorld(String worldName) {
        if (respawnExcludedWorlds.isEmpty()) return false;
        for (String pattern : respawnExcludedWorlds) {
            if (pattern.endsWith("*")) {
                if (worldName.startsWith(pattern.substring(0, pattern.length() - 1))) return true;
            } else if (pattern.equalsIgnoreCase(worldName)) {
                return true;
            }
        }
        return false;
    }

    public String resolveServer(String label) {
        return servers.getOrDefault(label, label);
    }

    public TeleportEffect effectFor(String type) {
        TeleportEffect effect = teleportEffects.get(type);
        if (effect != null) return effect;
        return teleportEffects.getOrDefault("default",
                new TeleportEffect(true, Particle.PORTAL, 40,
                        true, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f));
    }

    // ── internal helpers ─────────────────────────────────────────────

    private static Map<String, Object> profileDocument(Path root, String file) {
        try { return ConfigYaml.read(root.resolve(file)); }
        catch (IOException ex) { throw new IllegalArgumentException("Cannot read " + file, ex); }
    }

    private static boolean customMultiProxy(Path root) {
        String file = PaperProfiles.template(DeploymentConfig.Mode.CUSTOM, "general.yml");
        var settings = profileDocument(root, file);
        try { return ConfigYaml.flag(settings, "multi-proxy.enabled", false); }
        catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(file + ": multi-proxy.enabled must be true or false");
        }
    }

    private static Map<String, RegionConfig> loadRegions(Path root, String file) {
        Object value = profileDocument(root, file).getOrDefault("regions", Map.of());
        if (!(value instanceof Map<?, ?> regions)) {
            throw new IllegalArgumentException(file + ": regions must be a mapping; use {} for no regional routes");
        }
        Map<String, RegionConfig> routes = new LinkedHashMap<>();
        for (var entry : regions.entrySet()) {
            String key = (String) entry.getKey();
            String field = file + ": regions." + key;
            if (key.isBlank()) throw new IllegalArgumentException(file + ": regions must use non-empty region names");
            if (!(entry.getValue() instanceof Map<?, ?> region)) {
                throw new IllegalArgumentException(field + " must be a mapping with hub and servers");
            }
            if (!(region.get("hub") instanceof String hub) || hub.isBlank()) {
                throw new IllegalArgumentException(field + ".hub must be a non-empty backend name");
            }
            if (!(region.get("servers") instanceof List<?> members) || members.isEmpty()) {
                throw new IllegalArgumentException(field + ".servers must be a non-empty list of backend names");
            }
            List<String> servers = new ArrayList<>();
            for (Object member : members) {
                if (!(member instanceof String server) || server.isBlank()) {
                    throw new IllegalArgumentException(field + ".servers must contain only non-empty backend names");
                }
                servers.add(server);
            }
            if (routes.putIfAbsent(key.toLowerCase(Locale.ROOT), new RegionConfig(List.copyOf(servers), hub)) != null) {
                throw new IllegalArgumentException(field + " duplicates another region name when case is ignored");
            }
        }
        return routes;
    }

    private static FileConfiguration loadYaml(File dir, String name) {
        return PaperProfiles.yaml(dir.toPath().resolve(name));
    }

    private static void validate(FileConfiguration general, FileConfiguration database, FileConfiguration features,
                                 FileConfiguration messaging, FileConfiguration homes, FileConfiguration tpa,
                                 FileConfiguration rtp, FileConfiguration respawn) {
        for (String field : List.of("server-name", "hub-server")) {
            if (!general.isString(field) || general.getString(field, "").isBlank())
                throw new IllegalArgumentException("general.yml: " + field + " must be a non-empty name");
        }
        if (!general.getString("language", "en").matches("[a-zA-Z0-9_-]{1,40}"))
            throw new IllegalArgumentException("general.yml: invalid language identifier");
        if (features.contains("features") && !features.isConfigurationSection("features"))
            throw new IllegalArgumentException("features.yml: features must be a mapping");
        for (String key : features.getKeys(true)) {
            if (!key.equals("features") && key.startsWith("features.") && !features.isBoolean(key))
                throw new IllegalArgumentException("features.yml: " + key + " must be true or false");
        }
        for (String key : List.of("mysql.port", "mysql.pool-size")) range(database, key, 1, 65535);
        for (String key : List.of("messaging.dedupe-window-ms", "messaging.request-timeout-ms")) range(messaging, key, 1, 86400000);
        if (messaging.contains("messaging.network-enabled") && !messaging.isBoolean("messaging.network-enabled"))
            throw new IllegalArgumentException("messaging.network-enabled must be true or false");
        range(homes, "homes.gui-rows", 1, 6);
        range(homes, "homes.max-slots", 1, 54);
        range(tpa, "tpa.timeout-seconds", 1, 86400);
        range(tpa, "tpa.cooldown-seconds", 0, 86400);
        range(tpa, "tpa.delay-seconds", 0, 86400);
        range(rtp, "rtp.cooldown-seconds", 0, 86400);
        range(rtp, "rtp.delay-seconds", 0, 86400);
        range(rtp, "rtp.max-radius", 1, 30000000);
        range(respawn, "random-respawn.radius", 1, 30000000);
    }

    private static void range(FileConfiguration config, String key, int min, int max) {
        if (!config.contains(key)) return;
        Object raw = config.get(key);
        if (!(raw instanceof Integer || raw instanceof Long) || ((Number) raw).longValue() < min
                || ((Number) raw).longValue() > max) throw new IllegalArgumentException(key + " outside " + min + ".." + max);
    }

    private static Map<String, TeleportEffect> loadEffects(FileConfiguration cfg) {
        Map<String, TeleportEffect> map = new LinkedHashMap<>();
        ConfigurationSection effects = cfg.getConfigurationSection("effects");
        if (effects == null) return map;
        for (String key : effects.getKeys(false)) {
            ConfigurationSection sec = effects.getConfigurationSection(key);
            if (sec == null) continue;
            map.put(key, readEffect(sec));
        }
        return map;
    }

    private static TeleportEffect readEffect(ConfigurationSection sec) {
        boolean pEnabled = sec.getBoolean("particle.enabled", true);
        Particle particle = parseParticle(sec.getString("particle.type", "PORTAL"));
        int pCount = sec.getInt("particle.count", 40);
        boolean sEnabled = sec.getBoolean("sound.enabled", true);
        Sound sound = parseSound(sec.getString("sound.type", "ENTITY_ENDERMAN_TELEPORT"));
        float sVolume = (float) sec.getDouble("sound.volume", 1.0);
        float sPitch = (float) sec.getDouble("sound.pitch", 1.0);
        return new TeleportEffect(pEnabled, particle, pCount, sEnabled, sound, sVolume, sPitch);
    }

    private static Map<String, String> loadServerColors(FileConfiguration cfg) {
        Map<String, String> map = new LinkedHashMap<>();
        ConfigurationSection sec = cfg.getConfigurationSection("homes.server-colors");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                map.put(key.toLowerCase(), sec.getString(key, "<white>" + key));
            }
        }
        return map;
    }

    private static Material parseMaterial(String name, Material fallback) {
        try {
            Material material = Material.matchMaterial(name);
            return material == null || !material.isItem() || material.isAir() ? fallback : material;
        } catch (Exception e) { return fallback; }
    }

    private static Particle parseParticle(String name) {
        try { return Particle.valueOf(name.toUpperCase()); }
        catch (Exception e) { return Particle.PORTAL; }
    }

    private static Sound parseSound(String name) {
        // Sound changed from an enum to an interface during 1.21. Both expose the named constants.
        try {
            return (Sound) Sound.class.getField(name.toUpperCase(java.util.Locale.ROOT)).get(null);
        } catch (NoSuchFieldException | IllegalAccessException | ClassCastException ignored) {
            return Sound.ENTITY_ENDERMAN_TELEPORT;
        }
    }
}
