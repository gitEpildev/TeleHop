package com.telehop.paper.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** A fixed TeleHop operation, addressed by an ID that survives changes to its public labels. */
public record TravelVariant(String id, String kind, List<String> labels, String spawn, String region,
                            String server, String world, String dimension, boolean gui, Integer cooldown,
                            Integer delay, Boolean cancelOnMove, Integer maxRadius, Set<String> required) {
    public TravelVariant {
        labels = List.copyOf(labels);
        required = Set.copyOf(required);
    }

    static TravelVariant read(String id, String kind, ConfigurationSection config) {
        identifier(id);
        keys(config, Set.of("enabled", "name", "aliases", "destination", "gui", "settings", "permissions"));
        List<String> labels = labels(config);
        var dest = section(config, "destination");
        var gui = optionalSection(config, "gui");
        var settings = optionalSection(config, "settings");
        var permissions = optionalSection(config, "permissions");
        if (gui != null) keys(gui, Set.of("enabled"));
        if (settings != null) keys(settings, Set.of("inherit-defaults", "overrides"));
        if (settings != null && !flag(settings, "inherit-defaults", true))
            throw invalid(settings, "settings must inherit the active RTP configuration");
        var overrides = settings == null ? null : optionalSection(settings, "overrides");
        if (overrides != null) keys(overrides, Set.of("cooldown-seconds", "delay-seconds", "cancel-on-move", "max-radius"));
        boolean rtp = kind.equals("rtp");
        if (!rtp && (gui != null || overrides != null)) throw invalid(config, "GUI and RTP overrides require an RTP command");
        Set<String> allowed = rtp ? Set.of("command", "region", "server", "world", "dimension") : kind.equals("hub") ? Set.of("command") : Set.of("command", "destination");
        var required = new java.util.HashSet<String>();
        if (permissions != null) {
            keys(permissions, allowed.stream().map(k -> "require-" + k + "-permission").collect(java.util.stream.Collectors.toSet()));
            for (String key : allowed) {
                if (flag(permissions, "require-" + key + "-permission", false)) required.add(key);
            }
        }
        String spawn = null;
        String region = null;
        String server = null;
        String world = null;
        String dimension = null;
        if (rtp) {
            keys(dest, Set.of("region", "server", "world", "dimension"));
            region = selector(dest, "region");
            server = destinationName(dest, "server");
            world = destinationName(dest, "world");
            dimension = selector(dest, "dimension");
            if (region != null && server != null || world != null && dimension != null || dest.getKeys(false).isEmpty())
                throw invalid(dest, "select one of region/server and one of world/dimension, or a single selector");
        } else {
            keys(dest, Set.of("type", "target"));
            if (kind.equals("spawn") && "NAMED_SPAWN".equals(dest.getString("type")))
                spawn = com.telehop.common.model.SpawnEntry.normalise(text(dest, "target"));
            else if (!kind.equals("hub") || !"HUB".equals(dest.getString("type")) || dest.contains("target"))
                throw invalid(dest, "use NAMED_SPAWN with a target for spawn, or HUB for hub");
        }
        return new TravelVariant(id, kind, labels, spawn, region, server, world, dimension, flag(gui, "enabled", false),
                number(overrides, "cooldown-seconds", 0, 604800), number(overrides, "delay-seconds", 0, 3600),
                overrides != null && overrides.contains("cancel-on-move") ? flag(overrides, "cancel-on-move", true) : null,
                number(overrides, "max-radius", 1, 29999984), required);
    }

    static List<String> labels(ConfigurationSection cfg) {
        var names = new ArrayList<String>();
        names.add(identifier(text(cfg, "name")));
        if (cfg.contains("aliases") && !(cfg.get("aliases") instanceof List<?>)) throw invalid(cfg, "aliases must be a list");
        for (Object value : cfg.getList("aliases", List.of())) {
            if (!(value instanceof String s)) throw invalid(cfg, "each alias must be a command name");
            names.add(identifier(s));
        }
        return List.copyOf(names);
    }

    static String identifier(String value) {
        if (value == null || !value.matches("[a-z0-9_-]{1,64}"))
            throw new IllegalArgumentException("Invalid command or destination identifier: " + value);
        return value;
    }

    static ConfigurationSection section(ConfigurationSection cfg, String key) {
        var child = cfg.getConfigurationSection(key);
        if (child == null) throw invalid(cfg, "expected a mapping at " + key);
        return child;
    }

    static ConfigurationSection optionalSection(ConfigurationSection cfg, String key) {
        return cfg.contains(key) ? section(cfg, key) : null;
    }

    static boolean flag(ConfigurationSection cfg, String key, boolean fallback) {
        if (cfg == null || !cfg.contains(key)) return fallback;
        if (!cfg.isBoolean(key)) throw invalid(cfg, key + " must be true or false");
        return cfg.getBoolean(key);
    }

    static Integer number(ConfigurationSection cfg, String key, int min, int max) {
        if (cfg == null || !cfg.contains(key)) return null;
        if (!cfg.isInt(key) || cfg.getInt(key) < min || cfg.getInt(key) > max)
            throw invalid(cfg, key + " must be an integer between " + min + " and " + max);
        return cfg.getInt(key);
    }

    private static String text(ConfigurationSection cfg, String key) {
        if (!cfg.isString(key) || cfg.getString(key).isBlank()) throw invalid(cfg, "expected text at " + key);
        return cfg.getString(key);
    }

    private static String selector(ConfigurationSection cfg, String key) {
        return cfg.contains(key) ? identifier(text(cfg, key)) : null;
    }

    private static String destinationName(ConfigurationSection cfg, String key) {
        if (!cfg.contains(key)) return null;
        String name = text(cfg, key);
        if (name.length() > 255 || name.chars().anyMatch(Character::isISOControl)) throw invalid(cfg, "invalid destination name at " + key);
        return name;
    }

    static void keys(ConfigurationSection cfg, Set<String> allowed) {
        for (String key : cfg.getKeys(false)) if (!allowed.contains(key)) throw invalid(cfg, "unknown key " + key);
    }

    static IllegalArgumentException invalid(ConfigurationSection cfg, String issue) {
        return new IllegalArgumentException(cfg.getCurrentPath() + ": " + issue);
    }
}
