package com.telehop.paper.config;

import com.telehop.common.config.ConfigYaml;
import com.telehop.common.config.ConfigurationUpgrade;
import com.telehop.common.config.DeploymentConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Installs or upgrades only the selected backend profile. Player storage remains shared. */
public final class PaperProfiles {
    private static final List<String> NETWORK_FILES = List.of("general.yml", "features.yml", "teleport.yml", "tpa.yml",
            "rtp.yml", "home.yml", "spawns.yml", "respawn.yml", "messaging.yml", "regions.yml");

    private PaperProfiles() {}

    private static List<String> files(DeploymentConfig.Mode mode) {
        return mode == DeploymentConfig.Mode.SINGLE ? NETWORK_FILES.subList(0, 8) : NETWORK_FILES;
    }

    public static DeploymentConfig initialise(JavaPlugin plugin) {
        Path data = plugin.getDataFolder().toPath();
        try {
            boolean tracked = ConfigurationUpgrade.tracked(data, "paper");
            boolean needsRepair = !tracked || Files.exists(data.resolve("migrations/work"));
            if (!needsRepair) {
                var selected = DeploymentConfig.read(data);
                Path profile = selected.profileDirectory(data);
                needsRepair = files(selected.mode()).stream().anyMatch(name -> !Files.isRegularFile(profile.resolve(name)))
                        || LanguageFiles.incomplete(plugin, data)
                        || !Files.isRegularFile(data.resolve("config/database.yml")) || !Files.isRegularFile(data.resolve("config/WIKI.md"));
            }
            if (needsRepair) ConfigurationUpgrade.run(data, "paper", stage -> prepare(stage, plugin, !tracked));
            var current = DeploymentConfig.read(data);
            if (needsRepair && current.logMigration()) plugin.getLogger().info("Prepared Paper schema 1. Recovery records are in migrations/.");
            return current;
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot initialise Paper configuration: " + ex.getMessage(), ex);
        }
    }

    static void prepare(Path data, JavaPlugin plugin, boolean first) throws Exception {
        DeploymentConfig config = prepareFiles(data, plugin, first);
        Path active = config.profileDirectory(data);
        var settings = PaperSettings.load(data, config);
        LanguageFiles.complete(plugin, data);
        LanguageFiles.load(data, "en", plugin);
        LanguageFiles.load(data, settings.language(), plugin);
        CommandNames.loadAt(plugin, data, settings, active.toFile(), config.customNames(), config.additionalCommands(),
                settings.isFeatureEnabled("hub"));
        if (first && config.backups()) {
            // Only retire recognised files after validation. The transaction archives their original bytes first.
            for (String file : NETWORK_FILES) Files.deleteIfExists(data.resolve("config").resolve(file));
            Files.deleteIfExists(data.resolve("config/commands.yml"));
        }
    }

    static DeploymentConfig prepareFiles(Path data, JavaPlugin plugin, boolean first) throws Exception {
        Path selector = data.resolve("config.yml");
        Path legacy = data.resolve("config");
        Files.createDirectories(legacy);
        Map<String, Object> root = Files.exists(selector) ? ConfigYaml.read(selector) : Map.of();
        boolean selectorFormat = root.containsKey("mode") || root.containsKey("config-version")
                || root.containsKey("platform") || root.containsKey("migration");
        if (Files.exists(data.resolve(".telehop-legacy-migration")) || Files.exists(selector) && !selectorFormat) {
            ConfigMigrator.migrate(data);
        }
        if (!Files.exists(selector)) {
            Map<String, Object> old = Files.exists(legacy.resolve("general.yml")) ? ConfigYaml.read(legacy.resolve("general.yml")) : Map.of();
            var mode = ConfigYaml.flag(old, "multi-proxy.enabled", false) ? DeploymentConfig.Mode.MULTI : DeploymentConfig.Mode.SINGLE;
            boolean custom = false;
            if (Files.exists(legacy.resolve("commands.yml"))) {
                var defaults = ConfigYaml.parse(resource(plugin, "config/commands.yml"), "bundled command defaults");
                custom = ConfigYaml.read(legacy.resolve("commands.yml")).entrySet().stream()
                        .anyMatch(entry -> !entry.getValue().equals(defaults.get(entry.getKey())));
            }
            Files.writeString(selector, DeploymentConfig.initial(mode, custom));
        }
        DeploymentConfig config = DeploymentConfig.read(data);
        Path active = config.profileDirectory(data);
        Files.createDirectories(active);
        for (String name : List.of("database.yml", "WIKI.md")) {
            if (!Files.exists(legacy.resolve(name))) Files.writeString(legacy.resolve(name), resource(plugin, "config/" + name));
        }
        ConfigMigrator.refreshBundledGuide(plugin, data);
        boolean newGeneral = Files.notExists(active.resolve("general.yml")) && (!first || !Files.exists(legacy.resolve("general.yml")));
        for (String name : files(config.mode())) {
            if (Files.exists(active.resolve(name))) continue;
            Path old = legacy.resolve(name);
            if (first && Files.isRegularFile(old)) {
                Files.copy(old, active.resolve(name));
                if (name.equals("rtp.yml")) preserveLegacyRtp(active.resolve(name));
                if (name.equals("respawn.yml")) {
                    var respawn = yaml(active.resolve(name));
                    if (!respawn.contains("random-respawn.world")) {
                        // A missing selector in 3.x meant the literal default server world.
                        respawn.set("random-respawn.world", "world");
                        respawn.save(active.resolve(name).toFile());
                    }
                }
            } else if (!name.equals("messaging.yml") && !name.equals("regions.yml") && !name.equals("features.yml")) {
                Files.writeString(active.resolve(name), resource(plugin, "config/" + name));
            }
        }
        YamlConfiguration general = yaml(active.resolve("general.yml"));
        for (String field : List.of("messaging", "regions")) {
            Path target = config.mode() == DeploymentConfig.Mode.SINGLE ? active.resolve("general.yml") : active.resolve(field + ".yml");
            YamlConfiguration targetYaml = target.equals(active.resolve("general.yml")) ? general
                    : Files.exists(target) ? yaml(target) : new YamlConfiguration();
            boolean changed = false;
            if (!targetYaml.contains(field)) {
                YamlConfiguration inherited = general.contains(field) ? general : new YamlConfiguration();
                if (!inherited.contains(field)) {
                    Path old = legacy.resolve(field + ".yml");
                    inherited.loadFromString(first && Files.isRegularFile(old) ? ConfigYaml.readText(old) : resource(plugin, "config/" + field + ".yml"));
                }
                targetYaml.set(field, inherited.get(field));
                targetYaml.setComments(field, inherited.getComments(field));
                changed = true;
            }
            if (!targetYaml.isConfigurationSection(field)) throw new IllegalArgumentException(target.getFileName() + ": " + field + " must be a mapping");
            if (field.equals("messaging") && !targetYaml.contains("messaging.network-enabled")) {
                targetYaml.set("messaging.network-enabled", true);
                changed = true;
            }
            if (changed) targetYaml.save(target.toFile());
            if (targetYaml != general && general.contains(field)) {
                general.set(field, null);
                general.save(active.resolve("general.yml").toFile());
            }
        }
        if (newGeneral) {
            general.set("server-name", defaultIdentity(plugin));
            general.set("hub-server", defaultIdentity(plugin));
            general.set("multi-proxy.enabled", config.mode() == DeploymentConfig.Mode.MULTI);
            general.save(active.resolve("general.yml").toFile());
        }
        if (!newGeneral && first && Files.isRegularFile(legacy.resolve("general.yml"))
                && (!general.contains("server-name") || !general.contains("hub-server"))) {
            // Capture 3.x implicit defaults once when importing an incomplete legacy file.
            if (!general.contains("server-name")) general.set("server-name", "lobby");
            if (!general.contains("hub-server")) general.set("hub-server", "lobby");
            general.save(active.resolve("general.yml").toFile());
        }
        Path spawn = active.resolve("spawns.yml");
        var routes = SpawnRoutes.decode(Files.readString(spawn));
        String oldKey = SpawnRoutes.path("hub-command/enabled");
        if (routes.contains(oldKey) && !routes.isBoolean(oldKey)) throw new IllegalArgumentException("spawns.yml: hub-command.enabled must be true or false");
        var features = new YamlConfiguration();
        features.loadFromString(resource(plugin, "config/features.yml"));
        features.set("features.hub", routes.getBoolean(oldKey));
        ConfigMigrator.completeSettings(active.resolve("features.yml"), features, true);
        if (routes.contains(oldKey)) {
            routes.set(oldKey, null);
            if (routes.getConfigurationSection("hub-command").getKeys(false).isEmpty()) routes.set("hub-command", null);
            routes.save(spawn.toFile());
        }
        SpawnRoutes.parse(routes);
        if (first) installInactiveDefaults(data, plugin, config);
        return config;
    }

    /** Persist the old implicit selectors before switching to V4 loaded-world discovery. */
    private static void preserveLegacyRtp(Path file) throws IOException {
        var yaml = yaml(file);
        boolean changed = false;
        if (!yaml.contains("rtp.dimensions")) {
            yaml.set("rtp.dimensions.overworld", "world");
            yaml.set("rtp.dimensions.nether", "world_nether");
            yaml.set("rtp.dimensions.end", "world_the_end");
            if (!yaml.contains("rtp.default-dimension")) yaml.set("rtp.default-dimension", "overworld");
            changed = true;
        }
        if (!yaml.contains("rtp.gui.dimension-items")) {
            var materials = Map.of("overworld", "GRASS_BLOCK", "nether", "NETHERRACK", "end", "END_STONE");
            for (String id : List.of("overworld", "nether", "end")) {
                if (yaml.contains("rtp.dimensions." + id)) {
                    yaml.set("rtp.gui.dimension-items." + id + ".material", materials.get(id));
                    changed = true;
                }
            }
        }
        if (changed) yaml.save(file.toFile());
    }

    private static String defaultIdentity(JavaPlugin plugin) {
        Path plugins = plugin.getDataFolder().toPath().toAbsolutePath().normalize().getParent();
        Path folder = plugins.getParent();
        return folder != null && folder.getFileName() != null
                ? folder.getFileName().toString().replaceAll("[^A-Za-z0-9_-]", "_") : UUID.randomUUID().toString();
    }

    private static void installInactiveDefaults(Path data, JavaPlugin plugin, DeploymentConfig selected) throws IOException {
        for (var mode : DeploymentConfig.Mode.values()) {
            Path profile = data.resolve("config").resolve(mode.name().toLowerCase(java.util.Locale.ROOT));
            if (mode == selected.mode() || Files.exists(profile)) continue;
            Files.createDirectories(profile);
            for (String name : files(mode)) {
                String content = resource(plugin, "config/" + name);
                if (name.equals("general.yml")) {
                    if (mode == DeploymentConfig.Mode.SINGLE) {
                        content += "\n" + resource(plugin, "config/messaging.yml") + "\n" + resource(plugin, "config/regions.yml");
                    }
                    String id = defaultIdentity(plugin);
                    content = content.replace("server-name: \"\"", "server-name: \"" + id + "\"")
                            .replace("hub-server: \"\"", "hub-server: \"" + id + "\"");
                }
                Files.writeString(profile.resolve(name), content);
            }
        }
    }

    static YamlConfiguration yaml(Path path) {
        try {
            String contents = ConfigYaml.readText(path);
            var yaml = new YamlConfiguration();
            yaml.loadFromString(contents);
            return yaml;
        } catch (Exception ex) { throw new IllegalArgumentException("Invalid configuration in " + path); }
    }

    static String resource(JavaPlugin plugin, String name) throws IOException {
        try (var stream = plugin.getResource(name)) {
            if (stream == null) throw new IOException("Missing bundled " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
