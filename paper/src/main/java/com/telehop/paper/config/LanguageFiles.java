package com.telehop.paper.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Installs missing language entries while preserving custom text and MiniMessage placeholders. */
public final class LanguageFiles {
    private static final List<String> CODES = List.of("en", "nl", "de", "es", "zh", "pl");
    private static final Pattern PLACEHOLDER = Pattern.compile("<([a-z][a-z0-9_-]*)>");
    private static final Set<String> MARKUP = Set.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
            "dark_purple", "gold", "gray", "grey", "dark_gray", "dark_grey", "blue", "green", "aqua", "red",
            "light_purple", "yellow", "white", "b", "bold", "i", "em", "italic", "u", "underlined", "st",
            "strikethrough", "obf", "obfuscated", "reset", "br", "newline", "rainbow");

    private LanguageFiles() {}

    public static boolean incomplete(JavaPlugin plugin, Path data) throws Exception {
        for (String code : CODES) {
            Path path = data.resolve("languages/" + code + ".yml");
            if (!Files.isRegularFile(path)) return true;
            var defaults = new YamlConfiguration();
            defaults.loadFromString(PaperProfiles.resource(plugin, "languages/" + code + ".yml"));
            if (!PaperProfiles.yaml(path).getKeys(true).containsAll(defaults.getKeys(true))) return true;
        }
        return false;
    }

    public static void complete(JavaPlugin plugin, Path data) throws Exception {
        Path languages = data.resolve("languages");
        Files.createDirectories(languages);
        for (String code : CODES) {
            String resource = PaperProfiles.resource(plugin, "languages/" + code + ".yml");
            Path target = languages.resolve(code + ".yml");
            if (Files.notExists(target)) {
                write(target, resource);
                continue;
            }
            var defaults = new YamlConfiguration();
            defaults.loadFromString(resource);
            var existing = PaperProfiles.yaml(target);
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (!defaults.isString(key)) continue;
                if (!existing.contains(key)) {
                    existing.set(key, defaults.getString(key));
                    changed = true;
                } else if (!existing.isString(key)) throw new IllegalArgumentException(target + ": expected text for " + key);
            }
            if (changed) write(target, existing.saveToString());
        }
    }

    private static void write(Path target, String text) throws IOException {
        Path temp = Files.createTempFile(target.getParent(), ".language-", ".tmp");
        try {
            Files.writeString(temp, text);
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }

    public static YamlConfiguration load(Path data, String language, JavaPlugin plugin) {
        Path dir = data.resolve("languages");
        Path source = dir.resolve(language + ".yml");
        var active = PaperProfiles.yaml(Files.isRegularFile(source) ? source : dir.resolve("en.yml"));
        try {
            var defaults = new YamlConfiguration();
            String template = CODES.contains(language) ? language : "en";
            defaults.loadFromString(PaperProfiles.resource(plugin, "languages/" + template + ".yml"));
            for (String key : defaults.getKeys(true)) {
                if (!active.contains(key) || !defaults.isString(key)) continue;
                if (!active.isString(key)) throw new IllegalArgumentException(source + ": expected text for " + key);
                var matcher = PLACEHOLDER.matcher(defaults.getString(key));
                while (matcher.find()) {
                    if (!MARKUP.contains(matcher.group(1)) && !active.getString(key).contains(matcher.group())) {
                        throw new IllegalArgumentException(source + ": missing " + matcher.group() + " in " + key);
                    }
                }
            }
            active.setDefaults(defaults);
            return active;
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException ex) {
            throw new IllegalStateException("Cannot read the bundled English language file");
        }
    }
}
