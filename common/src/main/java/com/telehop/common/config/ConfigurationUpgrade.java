package com.telehop.common.config;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Publishes a validated configuration from private staging. A sealed journal can resume after a crash. */
public final class ConfigurationUpgrade {
    private ConfigurationUpgrade() {}

    /** Check the platform-specific schema before repairing or adopting a selected profile. */
    public static boolean tracked(Path data, String platform) throws IOException {
        Path state = data.resolve("migrations/state.yml");
        if (Files.notExists(state)) return false;
        var values = ConfigYaml.read(state);
        if (!Integer.valueOf(1).equals(values.get("schema")) || !platform.equals(values.get("platform"))) {
            throw new IOException("Unsupported migration state in " + state + "; install the matching plugin or restore the backup");
        }
        return true;
    }

    @FunctionalInterface
    public interface Prepare {
        void build(Path staged) throws Exception;
    }

    public static void run(Path data, String platform, Prepare prepare) throws Exception {
        Path migrations = data.resolve("migrations");
        privateDirectory(migrations);
        privateDirectory(migrations.resolve("backups"));
        try (var channel = FileChannel.open(migrations.resolve("lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Another TeleHop configuration migration is running");
            Path work = migrations.resolve("work");
            if (Files.exists(work.resolve("ready"))) {
                publish(data, work, platform);
                return;
            }
            remove(work);
            privateDirectory(work);
            Path original = work.resolve("original");
            Path stage = work.resolve("next");
            privateDirectory(original);
            privateDirectory(stage);
            try {
                try (var children = Files.list(data)) {
                    for (Path child : children.toList()) {
                        String name = child.getFileName().toString();
                        if (managed(name)) {
                            copyTree(child, original.resolve(name));
                            copyTree(child, stage.resolve(name));
                        }
                    }
                }
                prepare.build(stage);
                DeploymentConfig config = DeploymentConfig.read(stage);
                boolean previous = !files(original).isEmpty();
                boolean first = !Files.exists(migrations.resolve("state.yml"));
                List<String> changed = new ArrayList<>();
                for (Path path : files(stage)) {
                    Path rel = stage.relativize(path);
                    Path before = original.resolve(rel);
                    if (!Files.exists(before) || Files.mismatch(path, before) != -1) {
                        changed.add(rel.toString());
                    }
                }
                for (Path path : files(original)) {
                    Path rel = original.relativize(path);
                    if (!Files.exists(stage.resolve(rel))) changed.add(rel.toString());
                }
                if (!first && changed.isEmpty()) { remove(work); return; }
                if (first && previous && !config.automaticMigration()) {
                    throw new IOException("Automatic migration is disabled in config.yml. Enable it or restore the matching configuration");
                }
                String id = platform + "-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
                Files.writeString(work.resolve("id"), id);
                Files.write(work.resolve("changes"), changed);
                if (previous && config.backups()) {
                    Path backups = migrations.resolve("backups");
                    privateDirectory(backups);
                    Path backup = backups.resolve(id);
                    privateDirectory(backup);
                    copyTree(original, backup.resolve("original"));
                    List<String> hashes = new ArrayList<>();
                    for (Path file : files(original)) hashes.add(digest(file) + "  " + original.relativize(file));
                    Files.write(backup.resolve("SHA256SUMS"), hashes);
                    Files.writeString(backup.resolve("recovery.yml"), "id: " + id + "\nplatform: " + platform
                            + "\noriginal-schema: " + (first ? "legacy-or-untracked" : "1") + "\ntarget-schema: 1\ntime: '" + Instant.now() + "'\n");
                }
                Files.createFile(work.resolve("ready"));
                publish(data, work, platform);
            } catch (Exception ex) {
                if (!Files.exists(work.resolve("ready"))) remove(work);
                throw ex;
            }
        }
    }

    /** Replay only staged files; an administrator's independent edit stops recovery before overwriting it. */
    private static void publish(Path data, Path work, String platform) throws IOException {
        var paths = Files.readAllLines(work.resolve("changes"));
        var applied = new ArrayList<Path>();
        try {
            for (String line : paths) {
                Path rel = Path.of(line);
                if (rel.isAbsolute() || !rel.equals(rel.normalize()) || rel.startsWith("..") || !managed(rel.getName(0).toString())) {
                    throw new IOException("Invalid path in TeleHop migration journal");
                }
                Path target = data.resolve(rel);
                verifyParents(data, target);
                Path original = work.resolve("original").resolve(rel);
                Path next = work.resolve("next").resolve(rel);
                boolean deleting = Files.notExists(next);
                if (deleting && Files.notExists(target) || !deleting && Files.exists(target) && Files.mismatch(target, next) == -1) continue;
                if (Files.exists(target) != Files.exists(original)
                        || Files.exists(target) && Files.mismatch(target, original) != -1) {
                    throw new IOException("Configuration changed during migration: " + rel + ". Review migrations/work and its backup");
                }
                if (deleting) Files.delete(target);
                else atomicCopy(next, target);
                applied.add(rel);
            }
        } catch (IOException problem) {
            for (Path rel : applied.reversed()) {
                Path old = work.resolve("original").resolve(rel);
                if (Files.exists(old)) atomicCopy(old, data.resolve(rel));
                else Files.delete(data.resolve(rel));
            }
            throw problem;
        }
        String id = Files.readString(work.resolve("id"));
        String report = "schema: 1\nplatform: " + platform + "\nmigration: " + id + "\n";
        Path migrations = data.resolve("migrations");
        Files.writeString(work.resolve("state.yml"), report);
        atomicCopy(work.resolve("state.yml"), migrations.resolve("state.yml"));
        Path history = migrations.resolve("history.log");
        String entry = Instant.now() + " " + id + " completed; " + paths.size() + " configuration files updated\n";
        if (!Files.exists(history) || !Files.readString(history).contains(id)) {
            Files.writeString(history, entry, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        int retention = DeploymentConfig.read(work.resolve("next")).retention();
        remove(work);
        Path backups = migrations.resolve("backups");
        if (Files.isDirectory(backups)) {
            try (var stream = Files.list(backups)) {
                for (Path old : stream.filter(p -> p.getFileName().toString().matches(platform + "-[0-9]+-[a-f0-9-]{36}"))
                        .sorted(Comparator.reverseOrder()).skip(retention).toList()) remove(old);
            }
        }
    }

    private static boolean managed(String name) {
        return name.equals("config.yml") || name.equals("config.properties") || name.equals("config")
                || name.equals("commands") || name.equals("languages") || name.equals("storage.yml")
                || name.equals(".telehop-legacy-migration") || name.matches("config\\.yml\\.old(\\.[0-9]+)?");
    }

    private static void verifyParents(Path root, Path file) throws IOException {
        for (Path path = file; path != null && !path.equals(root); path = path.getParent()) {
            if (Files.isSymbolicLink(path)) throw new IOException("Configuration contains a symbolic link: " + path);
        }
    }

    private static String digest(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static List<Path> files(Path dir) throws IOException {
        try (var stream = Files.walk(dir)) {
            return stream.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).sorted().toList();
        }
    }

    private static void privateDirectory(Path dir) throws IOException {
        if (Files.isSymbolicLink(dir)) throw new IOException("Symbolic link at " + dir);
        Files.createDirectories(dir);
        if (dir.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
        }
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        if (source.getFileName().toString().chars().anyMatch(Character::isISOControl)) throw new IOException("A configuration filename contains a control character");
        if (Files.isSymbolicLink(source)) throw new IOException("Cannot migrate a symbolic link at " + source);
        if (Files.isDirectory(source)) {
            privateDirectory(destination);
            try (var children = Files.list(source)) {
                for (Path child : children.toList()) copyTree(child, destination.resolve(child.getFileName()));
            }
        } else if (Files.isRegularFile(source)) {
            Files.copy(source, destination);
            if (destination.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(destination, PosixFilePermissions.fromString("rw-------"));
            }
        } else throw new IOException("Cannot back up a special file at " + source);
    }

    private static void atomicCopy(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".telehop-", ".tmp");
        try {
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            if (temp.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------"));
            }
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }

    private static void remove(Path dir) throws IOException {
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) return;
        try (var stream = Files.walk(dir)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
