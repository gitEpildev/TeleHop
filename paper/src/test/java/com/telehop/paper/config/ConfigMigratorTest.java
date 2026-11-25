package com.telehop.paper.config;

import com.telehop.common.model.SpawnPoint;
import com.telehop.paper.version.VersionAdapter;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigMigratorTest {
    @TempDir Path root;
    private final JavaPlugin plugin = mock(JavaPlugin.class);

    @BeforeEach
    void useTemporaryPluginDirectory() {
        when(plugin.getDataFolder()).thenReturn(root.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
    }

    @Test
    void preSplitSettingsAndSpawnSurviveMigrationAndTheNextStart() throws Exception {
        // Match the original 1.0.0 layout. Non-default values expose a silent fallback to a new template.
        var legacy = """
                # Our original network configuration
                language: pl
                server-name: mines
                hub-server: central
                servers: {quarry: mines, lobby: central}
                mysql: {host: db.internal, port: 43306, database: game, username: hop, password: sample, pool-size: 7}
                messaging: {dedupe-window-ms: 42000, request-timeout-ms: 13000}
                audit: {enabled: true}
                features: {spawn: false, admin-tp: false}
                teleport: {show-countdown: false}
                tpa: {timeout-seconds: 45, cooldown-seconds: 12, delay-seconds: 3, cancel-on-move: false}
                rtp:
                  max-radius: 3200
                  dimensions: {overworld: quarry}
                  regions:
                    north: {world: quarry, radius: 700, gui: {material: STONE, name: Northern, lore: [Remote]}}
                  gui:
                    region-menu: {title: Regions, rows: 2}
                spawn:
                  location: {world: quarry, x: 1.5, y: 80, z: -21.25, yaw: -40, pitch: 14}
                """;
        Path source = root.resolve("config.yml");
        Files.writeString(source, legacy);

        assertTrue(ConfigMigrator.needsMigration(plugin));
        ConfigMigrator.migrate(plugin);
        var storage = new StorageManager(plugin, mock(VersionAdapter.class));
        storage.load();

        assertFalse(Files.exists(source));
        assertEquals(legacy, Files.readString(root.resolve("config.yml.old")));
        var general = settings("general");
        assertEquals("pl", general.getString("language"));
        assertEquals("mines", general.getString("server-name"));
        assertEquals("central", general.getString("hub-server"));
        assertEquals("mines", general.getString("servers.quarry"));
        assertEquals(42000, general.getLong("messaging.dedupe-window-ms"));
        assertTrue(general.getBoolean("audit.enabled"));
        var database = settings("database");
        assertEquals("db.internal", database.getString("mysql.host"));
        assertEquals(43306, database.getInt("mysql.port"));
        assertEquals("sample", database.getString("mysql.password"));
        assertEquals(7, database.getInt("mysql.pool-size"));
        assertEquals(false, settings("features").get("features.spawn"));
        assertEquals(false, settings("teleport").get("show-countdown"));
        assertEquals(3, settings("tpa").getInt("tpa.delay-seconds"));
        var rtp = settings("rtp");
        assertEquals(3200, rtp.getInt("rtp.max-radius"));
        assertEquals("quarry", rtp.getString("rtp.dimensions.overworld"));
        assertEquals(700, rtp.getInt("rtp.regions.north.radius"));
        assertEquals(List.of("Remote"), rtp.getStringList("rtp.regions.north.gui.lore"));
        assertEquals("Regions", rtp.getString("rtp.gui.region-menu.title"));
        assertEquals(5, settings("home").getInt("homes.max-slots"));
        assertEquals(new SpawnPoint("quarry", 1.5, 80, -21.25, -40, 14, false), storage.defaultPoint());

        var updated = new SpawnPoint("new_lobby", 70, 65, 18, 90, 0, false);
        storage.setDefaultPoint(updated);
        assertFalse(ConfigMigrator.needsMigration(plugin));
        var restarted = new StorageManager(plugin, mock(VersionAdapter.class));
        restarted.load();
        assertEquals(updated, restarted.defaultPoint());
        assertEquals(legacy, Files.readString(root.resolve("config.yml.old")));
    }

    @Test
    void existingSplitInstallationKeepsItsOwnSettings() throws Exception {
        Path general = root.resolve("config/general.yml");
        Files.createDirectories(general.getParent());
        Files.writeString(general, "server-name: live\n");
        Files.writeString(root.resolve("config.yml"), "server-name: retired\n");

        assertFalse(ConfigMigrator.needsMigration(plugin));
        assertEquals("live", settings("general").getString("server-name"));
    }

    @Test
    void retryAfterFailedStoragePublicationKeepsCustomFilesAndOldBackup() throws Exception {
        String source = "server-name: mines\nspawn: {location: {world: quarry, x: 17.25}}\n";
        Path config = Files.createDirectory(root.resolve("config"));
        Files.writeString(root.resolve("config.yml"), source);
        Files.writeString(root.resolve("config.yml.old"), "spawn: {world: earlier}\n");
        Files.writeString(config.resolve("database.yml"), "# customised\nmysql: {host: existing-db}\n");
        Path blocked = root.resolve("storage.yml");
        Files.createDirectory(blocked);

        assertThrows(IOException.class, () -> ConfigMigrator.migrate(plugin));
        assertTrue(ConfigMigrator.needsMigration(plugin));
        assertEquals(source, Files.readString(root.resolve("config.yml")));
        assertEquals("mines", settings("general").getString("server-name"));

        Files.delete(blocked);
        Files.writeString(config.resolve("general.yml"), "server-name: custom-mines\n");
        ConfigMigrator.migrate(plugin);

        assertFalse(ConfigMigrator.needsMigration(plugin));
        assertFalse(Files.exists(root.resolve("config.yml")));
        assertEquals("# customised\nmysql: {host: existing-db}\n", Files.readString(config.resolve("database.yml")));
        assertEquals("custom-mines", settings("general").getString("server-name"));
        assertEquals("spawn: {world: earlier}\n", Files.readString(root.resolve("config.yml.old")));
        assertEquals(source, Files.readString(root.resolve("config.yml.old.1")));
        var storage = new StorageManager(plugin, mock(VersionAdapter.class));
        storage.load();
        assertEquals("quarry", storage.defaultPoint().world());
        assertEquals(17.25, storage.defaultPoint().x());
    }

    @Test
    void existingStorageTakesPrecedenceOverTheLegacySpawn() throws Exception {
        Files.writeString(root.resolve("config.yml"), "spawn: {world: retired}\n");
        Path saved = root.resolve("storage.yml");
        String original = "# Saved in game\nspawn: {world: lobby, x: 21.25}\n";
        Files.writeString(saved, original);

        ConfigMigrator.migrate(plugin);

        assertEquals(original, Files.readString(saved));
        assertEquals("spawn: {world: retired}\n", Files.readString(root.resolve("config.yml.old")));
    }

    @Test
    void malformedLegacyYamlMustBeCorrectedBeforeMigration() throws Exception {
        Path source = root.resolve("config.yml");
        Files.writeString(source, "server-name: [unfinished\n");

        assertThrows(InvalidConfigurationException.class, () -> ConfigMigrator.migrate(plugin));
        assertFalse(Files.exists(root.resolve("config/general.yml")));
        assertEquals("server-name: [unfinished\n", Files.readString(source));
        assertTrue(ConfigMigrator.needsMigration(plugin));

        Files.writeString(source, "server-name: corrected\n");
        ConfigMigrator.migrate(plugin);
        assertEquals("corrected", settings("general").getString("server-name"));
    }

    @Test
    void upgradeFillsBothListsAndRetainsOldChoicesOnRepeatedStarts() throws Exception {
        Path config = Files.createDirectories(root.resolve("config"));
        Path features = config.resolve("features.yml");
        Path commands = config.resolve("commands.yml");
        Files.writeString(features, "# Operator's settings\nfeatures: {rtp: false}\n");
        Files.writeString(commands, "tp: [networktp, ntp]\n");
        for (String resource : List.of("config/features.yml", "config/commands.yml")) {
            when(plugin.getResource(resource)).thenAnswer(call -> getClass().getResourceAsStream("/" + resource));
        }

        ConfigMigrator.completeCommandSettings(plugin, true);
        assertEquals(true, settings("features").get("features.hub"));
        assertEquals(false, settings("features").get("features.rtp"));
        assertEquals(List.of("networktp", "ntp"), settings("commands").getStringList("tp"));
        assertEquals(List.of("hub"), settings("commands").getStringList("hub"));
        assertTrue(Files.readString(features).contains("# Operator's settings"));

        String acceptedFeatures = Files.readString(features);
        String acceptedCommands = Files.readString(commands);
        ConfigMigrator.completeCommandSettings(plugin, false);
        assertEquals(acceptedFeatures, Files.readString(features));
        assertEquals(acceptedCommands, Files.readString(commands));
    }

    @Test
    void explicitDisabledHubWinsOverAnEnabledLegacySwitch() throws Exception {
        Path config = Files.createDirectories(root.resolve("config"));
        Files.writeString(config.resolve("features.yml"), "features: {hub: false}\n");
        Files.writeString(config.resolve("commands.yml"), "hub: [lobby, hub]\n");
        for (String resource : List.of("config/features.yml", "config/commands.yml")) {
            when(plugin.getResource(resource)).thenAnswer(call -> getClass().getResourceAsStream("/" + resource));
        }
        ConfigMigrator.completeCommandSettings(plugin, true);
        assertEquals(false, settings("features").get("features.hub"));
        assertEquals(List.of("lobby", "hub"), settings("commands").getStringList("hub"));
    }

    @Test
    void invalidHubFlagLeavesTheOriginalFileIntact() throws Exception {
        Path features = root.resolve("features.yml");
        String malformed = "features: {hub: 'false'}\n";
        Files.writeString(features, malformed);
        var defaults = new YamlConfiguration();
        defaults.set("features.hub", false);
        assertThrows(InvalidConfigurationException.class,
                () -> ConfigMigrator.completeSettings(features, defaults, true));
        assertEquals(malformed, Files.readString(features));
    }

    private YamlConfiguration settings(String name) throws Exception {
        var result = new YamlConfiguration();
        result.load(root.resolve("config/" + name + ".yml").toFile());
        return result;
    }
}
