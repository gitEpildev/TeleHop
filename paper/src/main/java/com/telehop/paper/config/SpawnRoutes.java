package com.telehop.paper.config;

import com.telehop.common.model.SpawnEntry;
import com.telehop.common.model.SpawnPoint;
import com.telehop.common.model.SpawnRoute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.nio.file.Files;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

/** Immutable routes for one backend. World keys can contain full stops. */
public record SpawnRoutes(boolean enabled, boolean legacyHubCommand, SpawnRoute fallback, Map<String, SpawnRoute> worlds,
                          Map<String, SpawnPoint> destinations) {
    public SpawnRoutes {
        worlds = Map.copyOf(worlds);
        destinations = Map.copyOf(destinations);
    }

    public Map<String, SpawnRoute> allRoutes() {
        Map<String, SpawnRoute> result = new HashMap<>(worlds);
        if (fallback != null) result.put("", fallback);
        return Map.copyOf(result);
    }

    public static SpawnRoutes load(File folder) {
        return parse(read(folder));
    }

    public static SpawnRoutes loadProfile(java.nio.file.Path profile) {
        try { return parse(decode(Files.readString(profile.resolve("spawns.yml")))); }
        catch (Exception ex) { throw new IllegalArgumentException("Invalid " + profile.resolve("spawns.yml")); }
    }

    static YamlConfiguration read(File folder) {
        try { return decode(Files.readString(new File(folder, "config/spawns.yml").toPath())); }
        catch (Exception ex) { throw new IllegalArgumentException("Cannot load config/spawns.yml: " + ex.getMessage(), ex); }
    }

    static YamlConfiguration decode(String text) throws org.bukkit.configuration.InvalidConfigurationException {
        com.telehop.common.config.ConfigYaml.parse(text, "spawns.yml");
        var yaml = new YamlConfiguration();
        yaml.options().pathSeparator('\u001f');
        yaml.loadFromString(text);
        return yaml;
    }

    static String path(String segments) { return segments.replace('/', '\u001f'); }

    private static void keys(ConfigurationSection section, Set<String> allowed) {
        for (String key : section.getKeys(false)) {
            if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown spawn setting: " + key);
        }
    }

    static SpawnRoutes parse(YamlConfiguration yaml) {
        keys(yaml, Set.of("routing", "hub-command", "destinations"));
        for (String section : java.util.List.of("routing", "hub-command", "destinations")) {
            if (yaml.contains(section) && !yaml.isConfigurationSection(section)) {
                throw new IllegalArgumentException("Expected a mapping for " + section);
            }
        }
        if (yaml.isConfigurationSection("routing")) keys(yaml.getConfigurationSection("routing"), Set.of("enabled", "default", "worlds"));
        if (yaml.isConfigurationSection("hub-command")) keys(yaml.getConfigurationSection("hub-command"), Set.of("enabled"));
        for (String flag : java.util.List.of("routing/enabled", "hub-command/enabled")) {
            if (yaml.contains(path(flag)) && !yaml.isBoolean(path(flag))) throw new IllegalArgumentException("Invalid flag: " + flag);
        }
        if (yaml.contains(path("routing/worlds")) && !yaml.isConfigurationSection(path("routing/worlds"))) {
            throw new IllegalArgumentException("Expected a mapping for routing/worlds");
        }
        Map<String, SpawnRoute> worlds = new HashMap<>();
        var section = yaml.getConfigurationSection(path("routing/worlds"));
        if (section != null) {
            for (String name : section.getKeys(false)) {
                if (name.isBlank() || name.length() > 255) throw new IllegalArgumentException("Invalid source world");
                worlds.put(name, target(section, name, false));
            }
        }
        Map<String, SpawnPoint> points = new HashMap<>();
        if (yaml.contains("destinations") && !yaml.isConfigurationSection("destinations")) {
            throw new IllegalArgumentException("Expected a mapping for destinations");
        }
        var definitions = yaml.getConfigurationSection("destinations");
        if (definitions != null) {
            for (String key : definitions.getKeys(false)) {
                String name = SpawnEntry.normalise(key);
                var location = definitions.getConfigurationSection(key);
                if (location == null || !location.isString("world")) throw new IllegalArgumentException("Invalid world for " + name);
                keys(location, Set.of("world", "x", "y", "z", "yaw", "pitch", "access"));
                for (String field : java.util.List.of("x", "y", "z", "yaw", "pitch")) {
                    if (!(location.get(field) instanceof Number)) throw new IllegalArgumentException("Invalid " + field + " for " + name);
                }
                String access = location.getString("access");
                if (!"public".equals(access) && !"restricted".equals(access)) throw new IllegalArgumentException("Invalid access for " + name);
                var point = new SpawnPoint(location.getString("world"), location.getDouble("x"), location.getDouble("y"),
                        location.getDouble("z"), (float) location.getDouble("yaw"), (float) location.getDouble("pitch"), "restricted".equals(access));
                if (points.put(name, point) != null) throw new IllegalArgumentException("Duplicate spawn name: " + name);
            }
        }
        return new SpawnRoutes(yaml.getBoolean(path("routing/enabled")), yaml.getBoolean(path("hub-command/enabled")),
                target(yaml, path("routing/default"), true), worlds, points);
    }

    private static SpawnRoute target(ConfigurationSection parent, String key, boolean optional) {
        var section = parent.getConfigurationSection(key);
        if (optional && (!parent.contains(key) || section != null && section.getKeys(false).isEmpty())) return null;
        if (section == null || !section.isString("server") || section.getString("server").isBlank()) {
            throw new IllegalArgumentException("Missing server for spawn route " + key);
        }
        keys(section, Set.of("server", "spawn"));
        String id = section.getString("spawn");
        return new SpawnRoute(section.getString("server"), id == null ? null : SpawnEntry.normalise(id));
    }

    public SpawnRoute resolve(String key, String name) {
        return enabled ? worlds.getOrDefault(key, worlds.getOrDefault(name, fallback)) : null;
    }
}
