package com.telehop.paper.config;

import com.telehop.common.PermissionNodes;
import com.telehop.paper.service.PermissionService;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** The active profile's RTP catalogue. Menus, local commands and remote arrivals use the same choices. */
public final class RtpPolicy {
    public record Region(String world, int radius, String material, String title, List<String> lore) {}
    public record Choice(String region, String dimension, String server, String world, int radius) {}
    public record Menu(String title, int rows) {}
    public record Item(String material, String title, List<String> lore) {}

    private final Map<String, String> servers;
    private final Map<String, PaperSettings.RegionConfig> networkRegions;
    private final String local;
    private final int cap;
    private final Map<String, Region> regions;
    private final Map<String, String> dimensions;
    private final String defaultRegion;
    private final String defaultDimension;
    private final Map<String, Item> dimensionItems;
    private final Menu regionMenu;
    private final Menu dimensionMenu;
    private final boolean selectDimensionWithoutRegion;
    private final boolean ping;
    private final boolean proxyPing;

    RtpPolicy(ConfigurationSection cfg, PaperSettings settings, Supplier<List<String>> loadedWorlds) {
        local = settings.serverName();
        servers = settings.servers();
        networkRegions = settings.regions();
        cap = Objects.requireNonNullElse(TravelVariant.number(cfg, "rtp.max-radius", 1, 29999984), 25000);
        var defined = new LinkedHashMap<String, Region>();
        var section = TravelVariant.optionalSection(cfg, "rtp.regions");
        if (section != null) for (String id : section.getKeys(false)) {
            TravelVariant.identifier(id);
            var value = TravelVariant.section(section, id);
            defined.put(id, new Region(value.getString("world"), Math.min(cap,
                    Objects.requireNonNullElse(TravelVariant.number(value, "radius", 1, 29999984), cap)),
                    value.getString("gui.material", "GRASS_BLOCK"), value.getString("gui.name", id),
                    List.copyOf(value.getStringList("gui.lore"))));
        }
        regions = Collections.unmodifiableMap(defined);
        var worlds = new LinkedHashMap<String, String>();
        var dims = TravelVariant.optionalSection(cfg, "rtp.dimensions");
        if (dims != null) for (String id : dims.getKeys(false)) {
            TravelVariant.identifier(id);
            if (!dims.isString(id) || dims.getString(id).isBlank()) throw TravelVariant.invalid(dims, "world missing for " + id);
            worlds.put(id, dims.getString(id));
        }
        if (worlds.isEmpty()) for (String name : loadedWorlds.get()) {
            String id = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
            if (id.isBlank() || id.length() > 64 || worlds.putIfAbsent(id, name) != null)
                throw new IllegalArgumentException("Set rtp.dimensions explicitly for world " + name + " to give it a unique ID");
        }
        dimensions = Collections.unmodifiableMap(worlds);
        defaultRegion = defaultId(cfg, "rtp.default-region", regions);
        defaultDimension = defaultId(cfg, "rtp.default-dimension", dimensions);
        var items = new LinkedHashMap<String, Item>();
        var configuredItems = TravelVariant.optionalSection(cfg, "rtp.gui.dimension-items");
        if (configuredItems != null) for (String id : configuredItems.getKeys(false)) {
            if (!dimensions.containsKey(id)) throw TravelVariant.invalid(configuredItems, "unknown dimension " + id);
            var item = TravelVariant.section(configuredItems, id);
            items.put(id, new Item(item.getString("material", "GRASS_BLOCK"), item.getString("name", id), List.copyOf(item.getStringList("lore"))));
        }
        dimensionItems = Map.copyOf(items);
        regionMenu = menu(cfg, "region-menu", "<dark_purple>Select Region</dark_purple>");
        dimensionMenu = menu(cfg, "dimension-menu", "<gold>Select Dimension</gold>");
        selectDimensionWithoutRegion = TravelVariant.flag(cfg, "rtp.gui.select-dimension-without-region", false);
        ping = TravelVariant.flag(cfg, "rtp.gui.ping.enabled", true);
        proxyPing = TravelVariant.flag(cfg, "rtp.gui.ping.proxy-ping", true);
    }

    private static String defaultId(ConfigurationSection config, String key, Map<String, ?> choices) {
        if (!config.contains(key)) return choices.keySet().stream().findFirst().orElse("");
        if (!config.isString(key) || !choices.containsKey(config.getString(key))) throw TravelVariant.invalid(config, "unknown ID at " + key);
        return config.getString(key);
    }

    public Item dimensionItem(String id, String fallbackName) {
        return dimensionItems.getOrDefault(id, new Item("GRASS_BLOCK", fallbackName, List.of()));
    }

    private static Menu menu(ConfigurationSection cfg, String key, String fallback) {
        String path = "rtp.gui." + key;
        return new Menu(cfg.getString(path + ".title", fallback),
                Objects.requireNonNullElse(TravelVariant.number(cfg, path + ".rows", 1, 6), 3));
    }

    public Menu regionMenu() { return regionMenu; }
    public Menu dimensionMenu() { return dimensionMenu; }
    public boolean selectDimensionWithoutRegion() { return selectDimensionWithoutRegion; }
    public boolean ping() { return ping; }
    public boolean proxyPing() { return proxyPing; }
    public Map<String, Region> regions() { return regions; }
    public Map<String, String> dimensions() { return dimensions; }

    public void validate(TravelVariant variant) {
        if (!"rtp".equals(variant.kind())) return;
        if (variant.required().contains("region") && variant.region() == null
                || variant.required().contains("dimension") && variant.world() != null)
            throw new IllegalArgumentException("Required permission scope has no selected ID for " + variant.id());
        if (variant.region() != null && !regions.containsKey(variant.region())) throw new IllegalArgumentException("Unknown RTP region for " + variant.id());
        if (variant.dimension() != null && !dimensions.containsKey(variant.dimension())) throw new IllegalArgumentException("Unknown RTP dimension for " + variant.id());
        if (variant.server() != null && !local.equalsIgnoreCase(variant.server()) && !servers.containsValue(variant.server())
                && networkRegions.values().stream().noneMatch(region -> region.servers().contains(variant.server())))
            throw new IllegalArgumentException("Unknown backend for " + variant.id());
        if (variant.world() != null && !dimensions.containsValue(variant.world())
                && regions.values().stream().noneMatch(region -> variant.world().equals(region.world())))
            throw new IllegalArgumentException("Unknown configured RTP world for " + variant.id());
    }

    public String defaultDimension() { return defaultDimension; }

    /** Resolve all coordinates on the receiving backend. Wire fields can only narrow configured choices. */
    public Choice resolve(TravelVariant variant, String region, String dimension) {
        String selected = variant == null && (region == null || region.isEmpty()) ? defaultRegion : Objects.requireNonNullElse(region, "");
        String dim = variant == null && (dimension == null || dimension.isEmpty()) ? defaultDimension : Objects.requireNonNullElse(dimension, "");
        if (variant != null && !variant.gui()) {
            String expectedDim = variant.world() == null ? Objects.requireNonNullElse(variant.dimension(), defaultDimension()) : "";
            if (!Objects.requireNonNullElse(variant.region(), "").equals(selected) || !expectedDim.equals(dim)) return null;
        }
        if (variant != null && (variant.region() != null && !variant.region().equals(selected)
                || variant.dimension() != null && !variant.dimension().equals(dim)
                || variant.world() != null && !dim.isEmpty()
                || variant.region() == null && !selected.isEmpty())) return null;
        if (!selected.isEmpty() && !regions.containsKey(selected)) return null;
        if (selected.isEmpty() && variant == null && !regions.isEmpty()) return null;
        Region cfg = regions.get(selected);
        String world = variant != null && variant.world() != null ? variant.world() : dimensions.get(dim);
        if (world == null) return null;
        if (defaultDimension.equals(dim) && cfg != null && cfg.world() != null) world = cfg.world();
        String server = variant != null && variant.server() != null ? variant.server() : server(selected);
        int radius = cfg == null ? cap : cfg.radius();
        if (variant != null && variant.maxRadius() != null) radius = Math.min(cap, variant.maxRadius());
        return new Choice(selected, dim, server, world, radius);
    }

    /** An explicit region mapping wins; a topology-only region uses its first configured backend. */
    public String server(String region) {
        if (servers.containsKey(region)) return servers.get(region);
        var topology = networkRegions.get(region);
        return topology == null || topology.servers().isEmpty() ? local : topology.servers().getFirst();
    }

    public static boolean allowed(Player player, TravelVariant variant, Choice choice, PermissionService perms) {
        if (choice == null || !perms.has(player, PermissionNodes.RTP)) return false;
        if (variant != null && !variantAccess(player, variant, perms)) return false;
        return destination(player, variant, "region", choice.region(), perms)
                && destination(player, variant, "server", choice.server(), perms)
                && destination(player, variant, "world", choice.world(), perms)
                && destination(player, variant, "dimension", choice.dimension(), perms);
    }

    public List<String> eligibleDimensions(Player player, TravelVariant variant, String region, PermissionService permissions) {
        List<String> options;
        if (variant != null && variant.world() != null) options = List.of("");
        else if (variant != null && variant.dimension() != null) options = List.of(variant.dimension());
        else if (variant != null && !variant.gui()) options = List.of(defaultDimension);
        else options = List.copyOf(dimensions.keySet());
        return options.stream().filter(id -> allowedHere(player, variant, resolve(variant, region, id), permissions)).toList();
    }

    public boolean allowedHere(Player player, TravelVariant variant, Choice choice, PermissionService perms) {
        if (!allowed(player, variant, choice, perms)) return false;
        if (!local.equalsIgnoreCase(choice.server())) return true;
        String world = choice.world();
        return perms.inWorld(player, world, PermissionNodes.RTP, true)
                && (variant == null || perms.inWorld(player, world, PermissionNodes.COMMAND_PREFIX + variant.id(), !variant.required().contains("command")))
                && inWorld(player, variant, "region", choice.region(), world, perms)
                && inWorld(player, variant, "server", choice.server(), world, perms)
                && inWorld(player, variant, "dimension", choice.dimension(), world, perms)
                && inWorld(player, variant, "world", world, world, perms);
    }

    private static boolean inWorld(Player player, TravelVariant variant, String scope, String value, String world, PermissionService perms) {
        return value.isEmpty() || perms.inWorld(player, world, PermissionNodes.rtpDestination(scope, value),
                variant == null || !variant.required().contains(scope));
    }

    public static boolean variantAccess(Player player, TravelVariant variant, PermissionService perms) {
        return perms.destination(player, PermissionNodes.COMMAND_PREFIX + variant.id(), variant.required().contains("command"));
    }

    private static boolean destination(Player player, TravelVariant variant, String scope, String value, PermissionService perms) {
        boolean required = variant != null && variant.required().contains(scope);
        if (value.isEmpty()) return !required;
        return perms.destination(player, PermissionNodes.rtpDestination(scope, value), required);
    }
}
