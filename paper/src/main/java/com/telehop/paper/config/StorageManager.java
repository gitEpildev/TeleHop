package com.telehop.paper.config;

import com.telehop.paper.version.VersionAdapter;
import com.telehop.common.model.SpawnPoint;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/**
 * Manages runtime-mutable values persisted in {@code storage.yml}.
 * <p>
 * Static settings stay in the shared and profile configuration files
 * and are never written by the plugin at runtime. Values that can change in-game
 * (spawn location, future per-server state) live here so they survive reloads
 * without being overwritten by the default config template.
 */
public final class StorageManager {
    private final JavaPlugin plugin;
    private final File file;
    private final VersionAdapter versionAdapter;
    private FileConfiguration data;

    public StorageManager(JavaPlugin plugin, VersionAdapter versionAdapter) {
        this.plugin = plugin;
        this.versionAdapter = versionAdapter;
        this.file = new File(plugin.getDataFolder(), "storage.yml");
    }

    /** Loads storage.yml from disk, creating it if absent. */
    public void load() {
        if (!file.exists()) {
            migrateFromConfig();
        }
        var loaded = new YamlConfiguration();
        try { loaded.load(file); }
        catch (Exception ex) { throw new IllegalStateException("Cannot load storage.yml", ex); }
        data = loaded;
    }

    /** Writes current in-memory values to storage.yml. */
    public void save() {
        var path = file.toPath();
        try {
            var temp = java.nio.file.Files.createTempFile(path.getParent(), ".spawn-", ".tmp");
            try {
                data.save(temp.toFile());
                java.nio.file.Files.move(temp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } finally { java.nio.file.Files.deleteIfExists(temp); }
        } catch (IOException ex) { throw new IllegalStateException("Cannot save storage.yml", ex); }
    }

    public Location getSpawnLocation() {
        return spawn(true);
    }

    public Location spawn(boolean useLegacyFallback) {
        String worldName = data.getString("spawn.world", "world");
        World world = versionAdapter.resolveWorld(worldName);
        if (world == null && useLegacyFallback) {
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

    /** Retain the configured world even when it is currently unloaded. */
    public SpawnPoint defaultPoint() {
        return new SpawnPoint(data.getString("spawn.world", "world"), data.getDouble("spawn.x", 0.5),
                data.getDouble("spawn.y", 100), data.getDouble("spawn.z", 0.5), (float) data.getDouble("spawn.yaw"),
                (float) data.getDouble("spawn.pitch"), false);
    }

    public void setSpawnLocation(Location loc) {
        setDefaultPoint(new SpawnPoint(versionAdapter.getWorldName(loc.getWorld()), loc.getX(), loc.getY(),
                loc.getZ(), loc.getYaw(), loc.getPitch(), false));
    }

    public void setDefaultPoint(SpawnPoint point) {
        data.set("spawn.world", point.world());
        data.set("spawn.x", point.x());
        data.set("spawn.y", point.y());
        data.set("spawn.z", point.z());
        data.set("spawn.yaw", point.yaw());
        data.set("spawn.pitch", point.pitch());
        try { save(); }
        catch (IllegalStateException ex) { load(); throw ex; }
    }

    /** On first installation use the real primary world spawn, retaining archived legacy values on upgrades. */
    private void migrateFromConfig() {
        File oldConfig = new File(plugin.getDataFolder(), "config.yml.old");
        if (oldConfig.isFile()) {
            data = legacySpawn(PaperProfiles.yaml(oldConfig.toPath()));
            save();
            return;
        }
        var worlds = plugin.getServer().getWorlds();
        if (worlds.isEmpty()) throw new IllegalStateException("Load a backend world before initialising storage.yml");
        data = new YamlConfiguration();
        setSpawnLocation(worlds.getFirst().getSpawnLocation());
    }

    static YamlConfiguration legacySpawn(FileConfiguration cfg) {
        var data = new YamlConfiguration();
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
        return data;
    }
}
