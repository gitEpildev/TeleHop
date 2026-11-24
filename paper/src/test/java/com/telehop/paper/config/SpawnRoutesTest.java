package com.telehop.paper.config;

import com.telehop.common.model.SpawnEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SpawnRoutesTest {
    @TempDir Path root;

    private SpawnRoutes parse(String yaml) throws Exception {
        Files.createDirectories(root.resolve("config"));
        Files.writeString(root.resolve("config/spawns.yml"), yaml);
        return SpawnRoutes.load(root.toFile());
    }

    @Test
    void exactWorldKeyWinsOverDisplayNameAndBackendDefault() throws Exception {
        var routes = parse("""
                routing:
                  enabled: true
                  default: {server: hub}
                  worlds:
                    'custom:arenas/arena.one': {server: games, spawn: gardens}
                    arena: {server: games, spawn: BUILD}
                """);
        assertEquals(new com.telehop.common.model.SpawnRoute("games", "gardens"), routes.resolve("custom:arenas/arena.one", "arena"));
        assertEquals(new com.telehop.common.model.SpawnRoute("games", "build"), routes.resolve("custom:other", "arena"));
        assertEquals(new com.telehop.common.model.SpawnRoute("hub", null), routes.resolve("custom:other", "other"));
    }

    @Test
    void disabledAndEmptyRoutesKeepRegionalFallback() throws Exception {
        assertNull(parse("routing: {enabled: false, default: {server: survival}}").resolve("key", "world"));
        assertNull(parse("routing: {enabled: true, default: {}}").resolve("key", "world"));
    }

    @Test
    void malformedConfigurationCannotBecomeAnUnintendedDefault() {
        assertThrows(IllegalArgumentException.class, () -> parse("routing: {enabled: maybe}"));
        assertThrows(IllegalArgumentException.class, () -> parse("destinatons: {}"));
        assertThrows(IllegalArgumentException.class, () -> parse("destinations: {}\ndestinations: {}"));
        assertThrows(IllegalArgumentException.class, () -> parse("routing: {worlds: [one]}"));
        assertThrows(IllegalArgumentException.class, () -> parse("routing: {worlds: {arena: {spawn: training}}}"));
    }

    @Test
    void idsRemainStableAndCannotContainYamlPaths() {
        assertEquals("01", SpawnEntry.normalise("01"));
        assertEquals("garden-2", SpawnEntry.normalise("GARDEN-2"));
        for (String invalid : java.util.List.of("", "-hub", "../hub", "a.b", "a".repeat(33))) {
            assertThrows(IllegalArgumentException.class, () -> SpawnEntry.normalise(invalid));
        }
    }
}
