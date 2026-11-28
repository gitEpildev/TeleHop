package com.telehop.paper.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Migrates the original Paper configuration while preserving the input and any existing split files.
 */
public final class ConfigMigrator {
    private static final String STAGING = ".telehop-legacy-migration";
    private static final List<String> SPLIT_FILES = List.of(
            "database.yml", "features.yml", "teleport.yml", "tpa.yml", "rtp.yml", "home.yml", "general.yml");

    private static final String GUIDE = "config/WIKI.md";
    // Historical distribution bytes identify our guide even on installations with no ownership metadata.
    private static final Set<String> PREVIOUS_GUIDES = Set.of(
            "9679fb61a9be9545a492d8628d539e98bffb9222a9aa47a0dd9f04c2bf1ac79e",
            "6d50bacbeb38ceed9d430caec9d58d3de1ef324249de9ca08d5c20f4c75b5b1b",
            "5f203d5fcf734268cfc24d21ff36a453fbac0435aca2a33f0d7a82d6132cf2b9",
            "58f6cf2b28d7993168fa222a2906f866db0b24c0eee4f8e60f7d5e05910c589b",
            "d4afdc564020fee21cbfc214b4f62811a8123b098a84ab62af11a6012f03be3c",
            "e96a547168b639674009857df9f6e5d8fbf6c65b7aeaad8f94d6dc2f694418dd",
            "5fdc26662df27b460104aa19ec979edefbae71e45bb183318258934edfb060f8",
            "b4b68c1bb38d16c14af5766b829f8827656e5791353c089ee890708ccf014395",
            "4da481a533217850aa850846b2870e0c1c01775770ebc8693ecf7927c5708378",
            "e525fb181e8282bf181ebf9a04e7d038f836e3ce0d9dce36e40d9fe5c37f552f",
            "04105a0db82ceccc5a10ee6d01ff88635111bca1dd602cf71e089e7cd16eead6",
            "dcd7fc019e6e460881197172fb458d87d4cfc3de48bd60b10f4c2333dda4aadc");

    private ConfigMigrator() {}

    /** A staging directory marks an interrupted migration, even after general.yml was published. */
    public static boolean needsMigration(JavaPlugin plugin) {
        Path root = plugin.getDataFolder().toPath();
        if (Files.exists(root.resolve(STAGING))) return true;
        if (!Files.exists(root.resolve("config.yml")) || Files.isRegularFile(root.resolve("config/general.yml"))) return false;
        try { return PaperProfiles.operationalDirectory(root).equals(root.resolve("config")); }
        catch (IllegalArgumentException ex) { return true; } // The caller must correct the invalid configuration before it can be classified.
        catch (IOException ex) { throw new IllegalArgumentException("Cannot inspect config.yml for migration", ex); }
    }

    /** Publishes validated YAML before archiving the legacy source. A failed attempt can run again at startup. */
    public static void migrate(JavaPlugin plugin) throws IOException, InvalidConfigurationException {
        migrate(plugin.getDataFolder().toPath());
    }

    static void migrate(Path root) throws IOException, InvalidConfigurationException {
        Path legacy = root.resolve("config.yml");
        Path stage = root.resolve(STAGING);
        Path config = root.resolve("config");
        if (Files.notExists(legacy)) {
            // Only the empty staging directory can remain after the final archive move.
            if (!Files.isDirectory(stage) || !Files.isRegularFile(root.resolve("config.yml.old"))
                    || !Files.isRegularFile(root.resolve("storage.yml"))
                    || !SPLIT_FILES.stream().allMatch(name -> Files.isRegularFile(config.resolve(name)))) {
                throw new IOException("Incomplete legacy migration: restore " + legacy + " to resume");
            }
            Files.delete(stage);
            return;
        }

        var old = readYaml(legacy);
        if (PaperProfiles.isSelector(old.getValues(false))) {
            com.telehop.common.config.DeploymentConfig.read(root);
            if (Files.exists(stage)) throw new IOException("Legacy staging and an active profile selector both exist. Review " + stage);
            return;
        }
        Files.createDirectories(stage);
        writeGeneral(old, stage.resolve("general.yml").toFile());
        writeDatabase(old, stage.resolve("database.yml").toFile());
        writeFeatures(old, stage.resolve("features.yml").toFile());
        writeTeleport(old, stage.resolve("teleport.yml").toFile());
        writeTpa(old, stage.resolve("tpa.yml").toFile());
        writeRtp(old, stage.resolve("rtp.yml").toFile());
        writeHome(old, stage.resolve("home.yml").toFile());
        StorageManager.legacySpawn(old).save(stage.resolve("storage.yml").toFile());

        Files.createDirectories(config);
        for (String name : SPLIT_FILES) {
            publish(stage.resolve(name), config.resolve(name));
        }
        publish(stage.resolve("storage.yml"), root.resolve("storage.yml"));

        Path backup = root.resolve("config.yml.old");
        for (int suffix = 1; Files.exists(backup); suffix++) {
            backup = root.resolve("config.yml.old." + suffix);
        }
        Files.move(legacy, backup);
        Files.delete(stage);

    }

    /** Add missing switches and labels to older split files, retaining the owner's explicit choices. */
    public static void completeCommandSettings(JavaPlugin plugin, boolean legacyHub)
            throws IOException, InvalidConfigurationException {
        for (String name : List.of("config/features.yml", "config/commands.yml")) {
            boolean features = name.equals("config/features.yml");
            String template = features ? PaperProfiles.template(com.telehop.common.config.DeploymentConfig.Mode.SINGLE, "features.yml")
                    : CommandNames.LEGACY_TEMPLATE;
            var resource = plugin.getResource(template);
            if (resource == null) throw new IOException("Missing bundled " + template);
            var defaults = new YamlConfiguration();
            try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
                defaults.load(reader);
            }
            if (features) defaults.set("features.hub", legacyHub);
            Path target = plugin.getDataFolder().toPath().resolve(name);
            completeSettings(target, defaults, features);
        }
    }

    /** A missing guide or an unchanged previously shipped edition can be replaced safely. */
    static boolean needsGuideUpdate(Path data) throws IOException {
        Path guide = data.resolve(GUIDE);
        if (Files.notExists(guide, LinkOption.NOFOLLOW_LINKS)) return true;
        if (!Files.isRegularFile(guide, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Expected a regular file at " + guide);
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = new DigestInputStream(Files.newInputStream(guide), digest)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            return PREVIOUS_GUIDES.contains(HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static void refreshBundledGuide(JavaPlugin plugin, Path data) throws IOException {
        if (!needsGuideUpdate(data)) return;
        Path guide = data.resolve(GUIDE);
        try (var bundled = plugin.getResource(GUIDE)) {
            if (bundled == null) throw new IOException("Missing bundled " + GUIDE);
            Path temporary = Files.createTempFile(guide.getParent(), ".telehop-guide-", ".tmp");
            try {
                Files.copy(bundled, temporary, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temporary, guide, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
    }

    static void completeSettings(Path target, YamlConfiguration defaults, boolean features)
            throws IOException, InvalidConfigurationException {
        boolean exists = Files.exists(target);
        var yaml = exists ? readYaml(target) : defaults;
        if (features && yaml.contains("features") && !yaml.isConfigurationSection("features")) {
            throw new InvalidConfigurationException(target + ": features must be a mapping");
        }
        if (features && yaml.contains("features.hub") && !yaml.isBoolean("features.hub")) {
            throw new InvalidConfigurationException(target + ": features.hub must be true or false");
        }
        boolean changed = !exists;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key) || yaml.contains(key)) continue;
            yaml.set(key, defaults.get(key));
            yaml.setComments(key, defaults.getComments(key));
            changed = true;
        }
        if (!features && yaml.getComments("hub").contains("Registered only when enabled in spawns.yml.")) {
            var comments = new java.util.ArrayList<>(yaml.getComments("hub"));
            comments.set(comments.indexOf("Registered only when enabled in spawns.yml."),
                    "Enable features.hub in features.yml and restart Paper to register these names.");
            yaml.setComments("hub", comments);
            changed = true;
        }
        if (!changed) return;
        // Publish the entire file in one move so an interrupted upgrade can resume safely.
        Path temporary = Files.createTempFile(target.getParent(), ".telehop-settings-", ".tmp");
        try {
            yaml.save(temporary.toFile());
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void publish(Path staged, Path target) throws IOException, InvalidConfigurationException {
        if (Files.exists(target)) {
            // Existing settings may have been customised or published before an interruption.
            readYaml(target);
            Files.delete(staged);
        } else {
            Files.move(staged, target);
        }
    }

    private static YamlConfiguration readYaml(Path path) throws IOException, InvalidConfigurationException {
        var yaml = new YamlConfiguration();
        try {
            com.telehop.common.config.ConfigYaml.read(path);
            yaml.load(path.toFile());
        } catch (IllegalArgumentException | InvalidConfigurationException ex) {
            // Parser messages can echo lines containing database credentials.
            throw new InvalidConfigurationException("Invalid YAML in " + path);
        }
        return yaml;
    }

    private static void writeGeneral(FileConfiguration old, File target) throws IOException {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("language", old.getString("language", "en"));
        cfg.set("server-name", old.getString("server-name", "lobby"));
        cfg.set("hub-server", old.getString("hub-server", "lobby"));

        ConfigurationSection servers = old.getConfigurationSection("servers");
        if (servers != null) {
            for (String key : servers.getKeys(false)) {
                cfg.set("servers." + key, servers.getString(key));
            }
        } else {
            cfg.set("servers.lobby", "lobby");
        }

        cfg.set("messaging.dedupe-window-ms", old.getLong("messaging.dedupe-window-ms", 30000));
        cfg.set("messaging.request-timeout-ms", old.getLong("messaging.request-timeout-ms", 10000));
        cfg.set("audit.enabled", old.getBoolean("audit.enabled", false));
        for (String group : List.of("regions", "multi-proxy", "audit", "messaging")) {
            if (old.contains(group)) cfg.set(group, old.get(group));
        }
        cfg.save(target);
    }

    private static void writeDatabase(FileConfiguration old, File target) throws IOException {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("mysql.host", old.getString("mysql.host", "127.0.0.1"));
        cfg.set("mysql.port", old.getInt("mysql.port", 3306));
        cfg.set("mysql.database", old.getString("mysql.database", "telehop"));
        cfg.set("mysql.username", old.getString("mysql.username", "telehop"));
        cfg.set("mysql.password", old.getString("mysql.password", "change-me"));
        cfg.set("mysql.pool-size", old.getInt("mysql.pool-size", 5));
        cfg.save(target);
    }

    private static void writeFeatures(FileConfiguration old, File target) throws IOException {
        YamlConfiguration cfg = new YamlConfiguration();
        // Bring forward the operator's existing switches; the split template fills in missing ones.
        ConfigurationSection features = old.getConfigurationSection("features");
        if (features != null) cfg.set("features", features.getValues(false));
        cfg.save(target);
    }

    private static void writeTeleport(FileConfiguration old, File target) throws IOException {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("show-countdown", old.getBoolean("teleport.show-countdown", true));
        if (old.contains("teleport.effects")) cfg.set("effects", old.get("teleport.effects"));
        else if (old.contains("effects")) cfg.set("effects", old.get("effects"));
        cfg.save(target);
    }

    private static void writeTpa(FileConfiguration old, File target) throws IOException {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("tpa.timeout-seconds", old.getInt("tpa.timeout-seconds", 60));
        cfg.set("tpa.cooldown-seconds", old.getInt("tpa.cooldown-seconds", 10));
        cfg.set("tpa.delay-seconds", old.getInt("tpa.delay-seconds", 0));
        cfg.set("tpa.cancel-on-move", old.getBoolean("tpa.cancel-on-move", true));
        cfg.save(target);
    }

    private static void writeRtp(FileConfiguration old, File target) throws IOException {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("rtp.cooldown-seconds", old.getInt("rtp.cooldown-seconds", 30));
        cfg.set("rtp.delay-seconds", old.getInt("rtp.delay-seconds", 0));
        cfg.set("rtp.cancel-on-move", old.getBoolean("rtp.cancel-on-move", true));
        cfg.set("rtp.max-radius", old.getInt("rtp.max-radius", 25000));

        ConfigurationSection regions = old.getConfigurationSection("rtp.regions");
        if (regions != null) {
            for (String key : regions.getKeys(false)) {
                ConfigurationSection region = regions.getConfigurationSection(key);
                if (region == null) continue;
                String prefix = "rtp.regions." + key;
                cfg.set(prefix + ".world", region.getString("world", "world"));
                cfg.set(prefix + ".radius", region.getInt("radius", 25000));
                ConfigurationSection gui = region.getConfigurationSection("gui");
                if (gui != null) {
                    cfg.set(prefix + ".gui.material", gui.getString("material", "GRASS_BLOCK"));
                    cfg.set(prefix + ".gui.name", gui.getString("name"));
                    cfg.set(prefix + ".gui.lore", gui.getStringList("lore"));
                }
            }
        }

        if (old.contains("rtp.dimensions")) {
            cfg.set("rtp.dimensions", old.get("rtp.dimensions"));
        } else {
            cfg.set("rtp.dimensions.overworld", "world");
            cfg.set("rtp.dimensions.nether", "world_nether");
            cfg.set("rtp.dimensions.end", "world_the_end");
        }

        cfg.set("rtp.gui.region-menu.title", old.getString("rtp.gui.region-menu.title", "<dark_purple>Select Region</dark_purple>"));
        cfg.set("rtp.gui.region-menu.rows", old.getInt("rtp.gui.region-menu.rows", 3));
        cfg.set("rtp.gui.dimension-menu.title", old.getString("rtp.gui.dimension-menu.title", "<gold>Select Dimension</gold>"));
        cfg.set("rtp.gui.dimension-menu.rows", old.getInt("rtp.gui.dimension-menu.rows", 3));
        // Earlier formats accepted arbitrary region and GUI keys. Carry those values into the new profile.
        ConfigurationSection custom = old.getConfigurationSection("rtp");
        if (custom != null) {
            for (String key : custom.getKeys(true)) {
                if (!custom.isConfigurationSection(key)) cfg.set("rtp." + key, custom.get(key));
            }
        }
        cfg.save(target);
    }

    private static void writeHome(FileConfiguration old, File target) throws IOException {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("homes.max-slots", 5);
        cfg.set("homes.confirm-set", true);
        cfg.set("homes.gui-title", "<gradient:red:gold>Your Homes</gradient>");
        if (old.isConfigurationSection("homes")) cfg.set("homes", old.get("homes"));
        cfg.save(target);
    }
}
