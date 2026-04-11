package com.telehop.paper.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * One-time migration: reads the legacy monolithic {@code config.yml},
 * writes split YAML files into {@code config/}, and renames the old
 * file to {@code config.yml.old}.
 */
public final class ConfigMigrator {

    private ConfigMigrator() {}

    public static boolean needsMigration(JavaPlugin plugin) {
        File configDir = new File(plugin.getDataFolder(), "config");
        File general = new File(configDir, "general.yml");
        File legacy = new File(plugin.getDataFolder(), "config.yml");
        return !general.exists() && legacy.exists();
    }

    public static void migrate(JavaPlugin plugin) {
        File legacy = new File(plugin.getDataFolder(), "config.yml");
        FileConfiguration old = YamlConfiguration.loadConfiguration(legacy);
        File configDir = new File(plugin.getDataFolder(), "config");
        configDir.mkdirs();

        try {
            writeGeneral(old, new File(configDir, "general.yml"));
            writeDatabase(old, new File(configDir, "database.yml"));
            writeFeatures(old, new File(configDir, "features.yml"));
            writeTeleport(old, new File(configDir, "teleport.yml"));
            writeTpa(old, new File(configDir, "tpa.yml"));
            writeRtp(old, new File(configDir, "rtp.yml"));
            writeHome(new File(configDir, "home.yml"));

            File backup = new File(plugin.getDataFolder(), "config.yml.old");
            if (backup.exists()) backup.delete();
            legacy.renameTo(backup);

            plugin.getLogger().info("Migrated config.yml -> config/ (7 files). Old file renamed to config.yml.old");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Config migration failed", e);
        }
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
        cfg.set("features.spawn", old.getBoolean("features.spawn", true));
        cfg.set("features.rtp", old.getBoolean("features.rtp", true));
        cfg.set("features.tpa", old.getBoolean("features.tpa", true));
        cfg.set("features.warps", old.getBoolean("features.warps", true));
        cfg.set("features.player-warps", old.getBoolean("features.player-warps", true));
        cfg.set("features.admin-tp", old.getBoolean("features.admin-tp", true));
        cfg.set("features.homes", true);
        cfg.set("features.back", true);
        cfg.set("features.tpa-toggle", true);
        cfg.save(target);
    }

    private static void writeTeleport(FileConfiguration old, File target) throws IOException {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("show-countdown", old.getBoolean("teleport.show-countdown", true));
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

        cfg.set("rtp.dimensions.overworld", old.getString("rtp.dimensions.overworld", "world"));
        cfg.set("rtp.dimensions.nether", old.getString("rtp.dimensions.nether", "world_nether"));
        cfg.set("rtp.dimensions.end", old.getString("rtp.dimensions.end", "world_the_end"));

        cfg.set("rtp.gui.region-menu.title", old.getString("rtp.gui.region-menu.title", "<dark_purple>Select Region</dark_purple>"));
        cfg.set("rtp.gui.region-menu.rows", old.getInt("rtp.gui.region-menu.rows", 3));
        cfg.set("rtp.gui.dimension-menu.title", old.getString("rtp.gui.dimension-menu.title", "<gold>Select Dimension</gold>"));
        cfg.set("rtp.gui.dimension-menu.rows", old.getInt("rtp.gui.dimension-menu.rows", 3));
        cfg.save(target);
    }

    private static void writeHome(File target) throws IOException {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("homes.max-slots", 5);
        cfg.set("homes.confirm-set", true);
        cfg.set("homes.gui-title", "<gradient:red:gold>Your Homes</gradient>");
        cfg.save(target);
    }
}
