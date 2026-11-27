package com.telehop.paper.config;

import co.aikar.commands.CommandReplacements;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    static final List<String> GROUPS = List.of("admin", "back", "homes", "hub", "lastlocation", "playerinfo",
            "player-warps", "rtp", "spawn", "teleport", "tpa", "warps");
    private final Map<String, List<String>> labels;
    private final Map<String, TravelVariant> variants;
    private final RtpPolicy rtp;

    CommandNames(YamlConfiguration defaults, YamlConfiguration configured) {
        this(defaults, configured, true);
    }

    CommandNames(YamlConfiguration defaults, YamlConfiguration configured, boolean hubEnabled) {
        this(defaults, configured, hubEnabled, Map.of(), null);
    }

    private CommandNames(YamlConfiguration defaults, YamlConfiguration configured, boolean hubEnabled,
                         Map<String, TravelVariant> variants, RtpPolicy rtp) {
        this.variants = Map.copyOf(variants);
        this.rtp = rtp;
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
        if (names.containsKey("telehop") && !names.get("telehop").contains("telehop")) {
            if (!used.add("telehop")) throw new IllegalArgumentException("/telehop is reserved for administrative recovery");
            var recovery = new ArrayList<>(names.get("telehop"));
            recovery.add("telehop");
            names.put("telehop", List.copyOf(recovery));
        }
        for (var variant : variants.values()) {
            if (defaults.contains(variant.id())) throw new IllegalArgumentException("Reserved command ID: " + variant.id());
            for (String label : variant.labels()) if (!used.add(label))
                throw new IllegalArgumentException("Duplicate command label /" + label + " for " + variant.id());
            names.put(variant.id(), variant.labels());
        }
        labels = Map.copyOf(names);
    }

    /** Load only recognised action groups and validate every active label before touching the command map. */
    public static CommandNames load(JavaPlugin plugin, PaperSettings settings, File profile, boolean custom, boolean additional) {
        return load(plugin, settings, profile, custom, additional, settings.isFeatureEnabled("hub"));
    }

    public static CommandNames load(JavaPlugin plugin, PaperSettings settings, File profile, boolean custom, boolean additional,
                                    boolean hubRegistered) {
        return loadAt(plugin, plugin.getDataFolder().toPath(), settings, profile, custom, additional, hubRegistered);
    }

    /** Stage command migration in a private directory before the profile transaction is committed. */
    public static CommandNames loadAt(JavaPlugin plugin, Path data, PaperSettings settings, File profile, boolean custom,
                                      boolean additional, boolean hubRegistered) {
        try {
            var defaults = resource(plugin, RESOURCE);
            var overrides = new YamlConfiguration();
            var variants = new LinkedHashMap<String, TravelVariant>();
            Path directory = data.resolve("commands");
            if (Files.isSymbolicLink(directory)) throw new IllegalArgumentException("Expected a command directory inside TeleHop");
            Files.createDirectories(directory);
            var source = data.resolve(RESOURCE).toFile();
            boolean needsImport = GROUPS.stream().anyMatch(group -> !Files.exists(directory.resolve(group + ".yml")));
            var legacy = needsImport && source.isFile() ? read(source) : new YamlConfiguration();
            var rtp = new RtpPolicy(read(new File(profile, "rtp.yml")), settings,
                    () -> plugin.getServer().getWorlds().stream().map(org.bukkit.World::getName).toList());
            for (String group : GROUPS) {
                String name = group + ".yml";
                var template = resource(plugin, "commands/" + name);
                var file = directory.resolve(name);
                if (!Files.exists(file)) install(file, template, defaults, legacy);
                var configured = read(file.toFile());
                TravelVariant.keys(configured, template.getKeys(false));
                for (String id : template.getKeys(false)) {
                    if (!configured.contains(id)) continue;
                    var root = TravelVariant.section(configured, id);
                    boolean travel = Set.of("spawn", "hub", "rtp").contains(id);
                    TravelVariant.keys(root, travel ? Set.of("custom-name", "additional-commands") : Set.of("custom-name"));
                    var naming = TravelVariant.optionalSection(root, "custom-name");
                    if (naming != null) TravelVariant.keys(naming, Set.of("enabled", "name", "aliases"));
                    if (custom && TravelVariant.flag(naming, "enabled", false)) overrides.set(id, TravelVariant.labels(naming));
                    var extra = TravelVariant.optionalSection(root, "additional-commands");
                    if (extra != null) TravelVariant.keys(extra, Set.of("enabled", "commands"));
                    if (!additional || !TravelVariant.flag(extra, "enabled", false)) continue;
                    var definitions = TravelVariant.section(extra, "commands");
                    for (String key : definitions.getKeys(false)) {
                        var def = TravelVariant.section(definitions, key);
                        if (!TravelVariant.flag(def, "enabled", true)) continue;
                        if (!settings.isFeatureEnabled(id) || id.equals("hub") && !settings.isFeatureEnabled("spawn"))
                            throw new IllegalArgumentException("Disabled feature for command " + key + ": " + id);
                        var variant = TravelVariant.read(key, id, def);
                        rtp.validate(variant);
                        if (variants.put(key, variant) != null) throw new IllegalArgumentException("Duplicate command ID: " + key);
                    }
                }
            }
            return new CommandNames(defaults, overrides, hubRegistered, variants, rtp);
        } catch (IOException | InvalidConfigurationException ex) {
            throw new IllegalArgumentException("Cannot load the command configuration: " + ex.getMessage(), ex);
        }
    }

    private static YamlConfiguration resource(JavaPlugin plugin, String name) throws IOException, InvalidConfigurationException {
        try (var input = Objects.requireNonNull(plugin.getResource(name), name);
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            var yaml = new YamlConfiguration();
            yaml.load(reader);
            return yaml;
        }
    }

    private static YamlConfiguration read(File file) throws IOException, InvalidConfigurationException {
        var path = file.toPath();
        if (!Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 2_000_000)
            throw new IllegalArgumentException("Expected a regular configuration file of up to 2 MB at " + path);
        return PaperProfiles.yaml(path);
    }

    private static void install(Path file, YamlConfiguration template, YamlConfiguration defaults,
                                YamlConfiguration legacy) throws IOException {
        for (String id : template.getKeys(false)) {
            if (!legacy.contains(id) || Objects.equals(legacy.get(id), defaults.get(id))) continue;
            var value = legacy.get(id);
            if (!(value instanceof List<?> old) || old.isEmpty() || old.stream().anyMatch(v -> !(v instanceof String)))
                throw new IllegalArgumentException("Invalid legacy command labels for " + id);
            template.set(id + ".custom-name.enabled", true);
            template.set(id + ".custom-name.name", old.getFirst());
            template.set(id + ".custom-name.aliases", old.subList(1, old.size()));
        }
        Path tmp = Files.createTempFile(file.getParent(), ".command-", ".tmp");
        try {
            Files.writeString(tmp, template.saveToString(), StandardCharsets.UTF_8);
            Files.move(tmp, file);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    public Map<String, List<String>> labels() { return labels; }
    public Map<String, TravelVariant> variants() { return variants; }
    public RtpPolicy rtp() { return rtp; }
    public boolean sameRegistration(CommandNames next) { return labels.equals(next.labels); }

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
