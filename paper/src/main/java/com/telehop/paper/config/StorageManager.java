package com.telehop.paper.config;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Manages runtime-mutable values persisted in {@code storage.yml}.
 * <p>
 * Static settings (MySQL, server name, features, etc.) stay in {@code config.yml}
 * and are never written by the plugin at runtime. Values that can change in-game
 * (spawn location, future per-server state) live here so they survive reloads
 * without being overwritten by the default config template.
 */
public final class StorageManager {
    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration data;

    public StorageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "storage.yml");
    }

    /** Loads storage.yml from disk, creating it if absent. */
    public void load() {
        if (!file.exists()) {
            migrateFromConfig();
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    /** Writes current in-memory values to storage.yml. */
    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save storage.yml", e);
        }
    }

    public Location getSpawnLocation() {
        String worldName = data.getString("spawn.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) return null;
        return new Location(
                world,
                data.getDouble("spawn.x", 0.5),
                data.getDouble("spawn.y", 100.0),
                data.getDouble("spawn.z", 0.5),
                (float) data.getDouble("spawn.yaw", 0.0),
                (float) data.getDouble("spawn.pitch", 0.0)
        );
    }

    public void setSpawnLocation(Location loc) {
        data.set("spawn.world", loc.getWorld().getName());
        data.set("spawn.x", loc.getX());
        data.set("spawn.y", loc.getY());
        data.set("spawn.z", loc.getZ());
        data.set("spawn.yaw", (double) loc.getYaw());
        data.set("spawn.pitch", (double) loc.getPitch());
        save();
    }

    /**
     * One-time migration: copies spawn values from config.yml into a fresh storage.yml
     * so existing servers keep their spawn location after the config split.
     */
    private void migrateFromConfig() {
        FileConfiguration cfg = plugin.getConfig();
        data = new YamlConfiguration();

        String world = cfg.getString("spawn.location.world",
                cfg.getString("spawn.world", "world"));
        double x = cfg.getDouble("spawn.location.x", cfg.getDouble("spawn.x", 0.5));
        double y = cfg.getDouble("spawn.location.y", cfg.getDouble("spawn.y", 100.0));
        double z = cfg.getDouble("spawn.location.z", cfg.getDouble("spawn.z", 0.5));
        double yaw = cfg.getDouble("spawn.location.yaw", cfg.getDouble("spawn.yaw", 0.0));
        double pitch = cfg.getDouble("spawn.location.pitch", cfg.getDouble("spawn.pitch", 0.0));

        data.set("spawn.world", world);
        data.set("spawn.x", x);
        data.set("spawn.y", y);
        data.set("spawn.z", z);
        data.set("spawn.yaw", yaw);
        data.set("spawn.pitch", pitch);

        save();
        plugin.getLogger().info("Migrated spawn location from config.yml to storage.yml");
    }
}
