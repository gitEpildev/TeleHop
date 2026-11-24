package com.telehop.paper.config;

import co.aikar.commands.CommandReplacements;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Validated startup command names shared by registration, help text and clickable actions. */
public final class CommandNames {
    private static final String RESOURCE = "config/commands.yml";
    private static final Pattern COMMAND = Pattern.compile("(?<![\\w</:.-])/([a-z0-9_-]+)(?![\\w:/-])");
    private final Map<String, List<String>> labels;

    CommandNames(YamlConfiguration defaults, YamlConfiguration configured) {
        this(defaults, configured, true);
    }

    CommandNames(YamlConfiguration defaults, YamlConfiguration configured, boolean hubEnabled) {
        Map<String, List<String>> names = new HashMap<>();
        Set<String> used = new HashSet<>();
        for (String key : configured.getKeys(false)) {
            if (!defaults.contains(key)) throw new IllegalArgumentException("Unknown command in " + RESOURCE + ": " + key);
        }
        for (String key : defaults.getKeys(false)) {
            if (key.equals("hub") && !hubEnabled) continue;
            Object value = configured.get(key, defaults.get(key));
            if (!(value instanceof List<?> list) || list.isEmpty()) {
                throw new IllegalArgumentException(RESOURCE + ": " + key + " needs a non-empty list of names");
            }
            for (Object item : list) {
                if (!(item instanceof String name) || !name.matches("[a-z0-9_-]+") || !used.add(name)) {
                    throw new IllegalArgumentException(RESOURCE + ": invalid or duplicate label '" + item + "' for " + key);
                }
            }
            names.put(key, list.stream().map(String.class::cast).toList());
        }
        labels = Map.copyOf(names);
    }

    public static CommandNames load(JavaPlugin plugin, boolean hubEnabled) {
        try (var reader = new InputStreamReader(Objects.requireNonNull(plugin.getResource(RESOURCE)), StandardCharsets.UTF_8)) {
            var configured = new YamlConfiguration();
            configured.load(new File(plugin.getDataFolder(), RESOURCE));
            return new CommandNames(YamlConfiguration.loadConfiguration(reader), configured, hubEnabled);
        } catch (IOException | InvalidConfigurationException e) {
            throw new IllegalStateException("Cannot load " + RESOURCE, e);
        }
    }

    public void register(CommandReplacements replacements) {
        labels.forEach((key, names) -> replacements.addReplacement("cmd-" + key, String.join("|", names)));
    }

    public String primary(String key) {
        return labels.get(key).getFirst();
    }

    /** Resolve standalone slash commands before inserting user-supplied message parameters. */
    public String inMessage(String template) {
        return COMMAND.matcher(template).replaceAll(match -> labels.containsKey(match.group(1))
                ? "/" + primary(match.group(1)) : match.group());
    }
}
