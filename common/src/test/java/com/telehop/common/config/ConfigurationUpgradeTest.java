package com.telehop.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ConfigurationUpgradeTest {
    @TempDir Path root;

    @Test void rejectsInvalidModeAndTypeWithoutEchoingValues() {
        assertThrows(IllegalArgumentException.class, () -> DeploymentConfig.parse(Map.of("mode", "../other")));
        assertThrows(IllegalArgumentException.class, () -> DeploymentConfig.parse(Map.of("mode", "MULTI", "config-version", 2)));
        var ex = assertThrows(IllegalArgumentException.class, () -> ConfigYaml.parse("mysql: [secret-token\n", "database.yml"));
        assertFalse(ex.getMessage().contains("secret-token"));
        assertThrows(IllegalArgumentException.class, () -> ConfigYaml.parse("mode: MULTI\nmode: SINGLE\n", "config.yml"));
    }

    @Test void stagingFailureLeavesOriginalBytesAndNoSuccessMarker() throws Exception {
        Path old = root.resolve("config.properties");
        Files.writeString(old, "private settings");
        assertThrows(IllegalArgumentException.class, () -> ConfigurationUpgrade.run(root, "velocity", stage -> {
            Files.writeString(stage.resolve("config.properties"), "replacement");
            throw new IllegalArgumentException("Invalid candidate");
        }));
        assertEquals("private settings", Files.readString(old));
        assertFalse(Files.exists(root.resolve("migrations/state.yml")));
        assertFalse(Files.exists(root.resolve("migrations/work")));
    }

    @Test void protectsOriginalBytesAndNeverDeletesItsLastBackup() throws Exception {
        Files.writeString(root.resolve("config.properties"), "kept");
        ConfigurationUpgrade.run(root, "velocity", stage -> Files.writeString(stage.resolve("config.yml"),
                "mode: SINGLE\nmigration: {backup-retention: 1}\n"));
        Path backup;
        try (var entries = Files.list(root.resolve("migrations/backups"))) { backup = entries.findFirst().orElseThrow(); }
        assertEquals("kept", Files.readString(backup.resolve("original/config.properties")));
        assertTrue(Files.readString(backup.resolve("SHA256SUMS")).endsWith("  config.properties\n"));
        if (root.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(backup));
            assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(backup.resolve("original/config.properties")));
        }
        ConfigurationUpgrade.run(root, "velocity", stage -> {});
        try (var entries = Files.list(root.resolve("migrations/backups"))) { assertEquals(1, entries.count()); }
        assertEquals(1, Files.readAllLines(root.resolve("migrations/history.log")).size());
    }

    @Test void emptyInstallationCanDisableAutomaticUpdatesFromItsFirstBoot() throws Exception {
        ConfigurationUpgrade.run(root, "paper", stage -> Files.writeString(stage.resolve("config.yml"),
                "mode: SINGLE\nmigration: {automatic: false}\n"));
        assertTrue(ConfigurationUpgrade.tracked(root, "paper"));
        assertFalse(DeploymentConfig.read(root).automaticMigration());
    }

    @Test void trackedRepairRequiresConsentButAnUnchangedConfigurationStillLoads() throws Exception {
        String selector = "mode: MULTI\nmigration: {automatic: false}\n";
        Files.writeString(root.resolve("config.yml"), selector);
        Path proxy = Path.of("config/multi/proxy.yml");
        Path servers = Path.of("config/multi/servers.yml");
        ConfigurationUpgrade.run(root, "velocity", stage -> {
            Files.createDirectories(stage.resolve(proxy).getParent());
            Files.writeString(stage.resolve(proxy), "# Owner settings\nproxy: {}\n");
        });
        assertEquals(selector, Files.readString(root.resolve("config.yml")));
        var state = Files.readString(root.resolve("migrations/state.yml"));
        var history = Files.readString(root.resolve("migrations/history.log"));
        var backups = root.resolve("migrations/backups");
        long backupCount;
        try (var entries = Files.list(backups)) { backupCount = entries.count(); }
        ConfigurationUpgrade.run(root, "velocity", stage -> {});

        var failure = assertThrows(IOException.class, () -> ConfigurationUpgrade.run(root, "velocity", stage -> {
            Files.writeString(stage.resolve(proxy), "proxy: {}\n");
            Files.writeString(stage.resolve(servers), "servers: {}\n");
        }));
        assertTrue(failure.getMessage().contains("migration.automatic"));
        assertEquals(state, Files.readString(root.resolve("migrations/state.yml")));
        assertEquals(history, Files.readString(root.resolve("migrations/history.log")));
        assertEquals("# Owner settings\nproxy: {}\n", Files.readString(root.resolve(proxy)));
        assertFalse(Files.exists(root.resolve(servers)));
        assertFalse(Files.exists(root.resolve("migrations/work")));
        try (var entries = Files.list(backups)) { assertEquals(backupCount, entries.count()); }

        Files.writeString(root.resolve("config.yml"), "mode: MULTI\nmigration: {automatic: true}\n");
        ConfigurationUpgrade.run(root, "velocity", stage -> Files.writeString(stage.resolve(servers), "servers: {}\n"));
        assertEquals("servers: {}\n", Files.readString(root.resolve(servers)));
        assertEquals(2, Files.readAllLines(root.resolve("migrations/history.log")).size());
    }

    @Test void untrackedExistingConfigurationStillRequiresConsentToMigrate() throws Exception {
        Files.writeString(root.resolve("config.properties"), "owned");
        Files.writeString(root.resolve("config.yml"), "mode: MULTI\nmigration: {automatic: false}\n");
        assertThrows(IOException.class, () -> ConfigurationUpgrade.run(root, "velocity",
                stage -> Files.delete(stage.resolve("config.properties"))));
        assertEquals("owned", Files.readString(root.resolve("config.properties")));
        assertFalse(Files.exists(root.resolve("migrations/state.yml")));
    }

    @Test void completeManuallyInstalledConfigurationCanBeAdoptedWithoutAutomaticUpdates() throws Exception {
        Files.writeString(root.resolve("config.yml"), "mode: SINGLE\nmigration: {automatic: false}\n");
        var commands = Files.createDirectories(root.resolve("commands")).resolve("spawn.yml");
        Files.writeString(commands, "# Owner settings\n");
        ConfigurationUpgrade.run(root, "paper", stage -> {});
        assertTrue(ConfigurationUpgrade.tracked(root, "paper"));
        assertEquals("# Owner settings\n", Files.readString(commands));
    }

    @Test void replaysAnInterruptedCommitBeforeRebuilding() throws Exception {
        Path pending = Files.createDirectories(root.resolve("migrations/work"));
        Path original = Files.createDirectories(pending.resolve("original"));
        Path stage = Files.createDirectories(pending.resolve("next"));
        String selector = "mode: SINGLE\nmigration: {automatic: false}\n";
        Files.writeString(original.resolve("config.yml"), selector);
        Files.writeString(stage.resolve("config.yml"), "mode: CUSTOM\nmigration: {automatic: false}\n");
        Files.writeString(root.resolve("config.yml"), selector);
        Files.writeString(pending.resolve("changes"), "config.yml\n");
        Files.writeString(pending.resolve("id"), "paper-interruption-test");
        Files.createFile(pending.resolve("ready"));
        ConfigurationUpgrade.run(root, "paper", ignored -> fail("Sealed journal must be replayed"));
        assertEquals(DeploymentConfig.Mode.CUSTOM, DeploymentConfig.read(root).mode());
        assertFalse(DeploymentConfig.read(root).automaticMigration());
        assertFalse(Files.exists(pending));
        assertTrue(Files.readString(root.resolve("migrations/state.yml")).contains("paper-interruption-test"));
    }

    @Test void recognisesAllProfilesAndRestrictsSelectionToTheDataFolder() throws Exception {
        for (var mode : DeploymentConfig.Mode.values()) {
            Files.writeString(root.resolve("config.yml"), DeploymentConfig.initial(mode, true));
            var config = DeploymentConfig.read(root);
            assertEquals(mode, config.mode());
            assertTrue(config.customNames());
            assertTrue(config.profileDirectory(root).startsWith(root.resolve("config")));
        }
    }
}
