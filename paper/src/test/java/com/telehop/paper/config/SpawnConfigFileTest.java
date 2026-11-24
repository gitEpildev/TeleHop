package com.telehop.paper.config;

import com.telehop.common.model.SpawnPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpawnConfigFileTest {
    @TempDir Path root;

    @Test
    void savingAndRemovingAnOperatorChosenNameRoundTripsAllFields() throws Exception {
        Files.createDirectories(root.resolve("config"));
        Files.writeString(root.resolve("config/spawns.yml"), "destinations: {}\n");
        var point = new SpawnPoint("custom:garden", 12.5, 70, -9.5, 33, -12, true);
        var change = SpawnConfigFile.edit(root.toFile(), "gardens", point);
        change.write(false);
        assertEquals(point, SpawnRoutes.load(root.toFile()).destinations().get("gardens"));
        SpawnConfigFile.edit(root.toFile(), "gardens", null).write(false);
        assertFalse(SpawnRoutes.load(root.toFile()).destinations().containsKey("gardens"));
    }

    @Test
    void editsDuringPublicationArePreserved() throws Exception {
        Path file = root.resolve("config/spawns.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "destinations: {}\n");
        var change = SpawnConfigFile.edit(root.toFile(), "gardens", new SpawnPoint("world", 0, 64, 0, 0, 0, false));
        change.write(false);
        Files.writeString(file, "# operator edit\ndestinations: {}\n");
        assertThrows(java.io.IOException.class, () -> change.write(true));
        assertEquals("# operator edit\ndestinations: {}\n", Files.readString(file));
    }
}
