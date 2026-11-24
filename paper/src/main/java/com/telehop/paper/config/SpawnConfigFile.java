package com.telehop.paper.config;

import com.telehop.common.model.SpawnPoint;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/** Prepares a reversible edit of the same YAML operators use for manual configuration. */
public final class SpawnConfigFile {
    private final Path file;
    private final byte[] previous;
    private final byte[] updated;
    private final SpawnRoutes original;
    private final SpawnRoutes configuration;

    private SpawnConfigFile(Path file, byte[] previous, byte[] updated, SpawnRoutes original, SpawnRoutes configuration) {
        this.file = file;
        this.previous = previous;
        this.updated = updated;
        this.original = original;
        this.configuration = configuration;
    }

    public SpawnRoutes original() { return original; }
    public SpawnRoutes configuration() { return configuration; }

    public static SpawnConfigFile edit(File folder, String name, SpawnPoint point) throws Exception {
        Path path = folder.toPath().resolve("config/spawns.yml");
        byte[] old = Files.readAllBytes(path);
        var yaml = SpawnRoutes.decode(new String(old, java.nio.charset.StandardCharsets.UTF_8));
        var original = SpawnRoutes.parse(yaml);
        var definitions = yaml.getConfigurationSection("destinations");
        if (definitions != null) {
            for (String key : definitions.getKeys(false)) {
                if (key.equalsIgnoreCase(name)) definitions.set(key, null);
            }
        }
        if (point != null) {
            var entry = yaml.createSection(SpawnRoutes.path("destinations/") + name);
            entry.set("world", point.world());
            entry.set("x", point.x());
            entry.set("y", point.y());
            entry.set("z", point.z());
            entry.set("yaw", point.yaw());
            entry.set("pitch", point.pitch());
            entry.set("access", point.restricted() ? "restricted" : "public");
        }
        return new SpawnConfigFile(path, old, yaml.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8), original, SpawnRoutes.parse(yaml));
    }

    public void write(boolean undo) throws IOException {
        if (!Arrays.equals(Files.readAllBytes(file), undo ? updated : previous)) {
            throw new IOException("spawns.yml changed during the operation");
        }
        Path staging = Files.createTempFile(file.getParent(), ".spawns-", ".tmp");
        try {
            Files.write(staging, undo ? previous : updated);
            Files.move(staging, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(staging); }
    }
}
