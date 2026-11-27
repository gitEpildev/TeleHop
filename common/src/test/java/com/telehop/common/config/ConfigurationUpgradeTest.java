package com.telehop.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    @Test void replaysAnInterruptedCommitBeforeRebuilding() throws Exception {
        Path pending = Files.createDirectories(root.resolve("migrations/work"));
        Path original = Files.createDirectories(pending.resolve("original"));
        Path stage = Files.createDirectories(pending.resolve("next"));
        Files.writeString(original.resolve("config.yml"), "mode: SINGLE");
        Files.writeString(stage.resolve("config.yml"), "mode: CUSTOM");
        Files.writeString(root.resolve("config.yml"), "mode: SINGLE");
        Files.writeString(pending.resolve("changes"), "config.yml\n");
        Files.writeString(pending.resolve("id"), "paper-interruption-test");
        Files.createFile(pending.resolve("ready"));
        ConfigurationUpgrade.run(root, "paper", ignored -> fail("Sealed journal must be replayed"));
        assertEquals(DeploymentConfig.Mode.CUSTOM, DeploymentConfig.read(root).mode());
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
