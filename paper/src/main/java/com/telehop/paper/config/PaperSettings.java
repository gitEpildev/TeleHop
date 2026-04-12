package com.telehop.paper.config;

import com.telehop.common.db.DatabaseConfig;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
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
        Map<String, TeleportEffect> teleportEffects,
        String respawnWorld,
        int respawnRadius,
        boolean respawnRespectBed,
        boolean respawnRespectAnchor
) {

    public record TeleportEffect(
            boolean particleEnabled, Particle particleType, int particleCount,
            boolean soundEnabled, Sound soundType, float soundVolume, float soundPitch
    ) {}

    /**
     * Loads settings from split config files in {@code plugins/TeleHop/config/}.
     * Falls back to defaults if a file is missing.
     */
    public static PaperSettings load(JavaPlugin plugin) {
        File configDir = new File(plugin.getDataFolder(), "config");
        FileConfiguration general = loadYaml(configDir, "general.yml");
        FileConfiguration database = loadYaml(configDir, "database.yml");
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

        Map<String, TeleportEffect> effects = loadEffects(teleportFile);

        return new PaperSettings(
                general.getString("server-name", "lobby"),
                general.getString("hub-server", "lobby"),
                Collections.unmodifiableMap(servers),
                db,
                general.getLong("messaging.dedupe-window-ms", 30_000L),
                general.getLong("messaging.request-timeout-ms", 10_000L),
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
                homeFile.getInt("homes.max-slots", 5),
                homeFile.getBoolean("homes.confirm-set", true),
                homeFile.getString("homes.gui-title", "<gradient:red:gold>Your Homes</gradient>"),
                parseMaterial(homeFile.getString("homes.bed-set", "LIME_BED"), Material.LIME_BED),
                parseMaterial(homeFile.getString("homes.bed-empty", "RED_BED"), Material.RED_BED),
                parseMaterial(homeFile.getString("homes.bed-locked", "LIGHT_BLUE_BED"), Material.LIGHT_BLUE_BED),
                homeFile.getInt("homes.gui-rows", 3),
                homeFile.getBoolean("homes.show-location", true),
                homeFile.getString("homes.world-colors.overworld", "<green>Overworld</green>"),
                homeFile.getString("homes.world-colors.nether", "<gradient:red:gold>Nether</gradient>"),
                homeFile.getString("homes.world-colors.the-end", "<gradient:dark_purple:blue>The End</gradient>"),
                Collections.unmodifiableMap(loadServerColors(homeFile)),
                homeFile.getStringList("homes.blocked-servers").stream()
                        .map(String::toLowerCase).collect(Collectors.toUnmodifiableSet()),
                Collections.unmodifiableMap(effects),
                respawnFile.getString("random-respawn.world", "world"),
                respawnFile.getInt("random-respawn.radius", 5000),
                respawnFile.getBoolean("random-respawn.respect-bed-spawn", true),
                respawnFile.getBoolean("random-respawn.respect-anchor-spawn", true)
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
                Map.of(),
                "world",
                5000,
                true,
                true
        );
    }

    public boolean isHomeBlockedOnCurrentServer() {
        return homeBlockedServers.contains(serverName.toLowerCase());
    }

    public boolean isFeatureEnabled(String feature) {
        return features.getOrDefault(feature, true);
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

    private static FileConfiguration loadYaml(File dir, String name) {
        File file = new File(dir, name);
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        }
        return new YamlConfiguration();
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
            Material m = Material.matchMaterial(name.toUpperCase());
            return m != null ? m : fallback;
        } catch (Exception e) { return fallback; }
    }

    private static Particle parseParticle(String name) {
        try { return Particle.valueOf(name.toUpperCase()); }
        catch (Exception e) { return Particle.PORTAL; }
    }

    private static Sound parseSound(String name) {
        try { return Sound.valueOf(name.toUpperCase()); }
        catch (Exception e) { return Sound.ENTITY_ENDERMAN_TELEPORT; }
    }
}
