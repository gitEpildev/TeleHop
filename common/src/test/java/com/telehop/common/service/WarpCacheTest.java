package com.telehop.common.service;

import com.telehop.common.model.WarpRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WarpCacheTest {

    private WarpCache cache;

    @BeforeEach
    void setUp() {
        cache = new WarpCache();
    }

    // ------------------------------------------------------------------
    // put / get
    // ------------------------------------------------------------------

    @Test
    void putAndGetReturnRecord() {
        cache.put(warp("spawn"));
        Optional<WarpRecord> result = cache.get("spawn");
        assertTrue(result.isPresent());
        assertEquals("spawn", result.get().name());
    }

    @Test
    void getIsCaseInsensitive() {
        cache.put(warp("Spawn"));
        assertTrue(cache.get("spawn").isPresent());
        assertTrue(cache.get("SPAWN").isPresent());
        assertTrue(cache.get("SpAwN").isPresent());
    }

    @Test
    void getReturnsEmptyForUnknownName() {
        assertTrue(cache.get("does-not-exist").isEmpty());
    }

    @Test
    void putOverwritesExistingEntry() {
        cache.put(warp("hub"));
        WarpRecord updated = new WarpRecord("hub", "us", "world", 10, 64, 10, 0f, 0f);
        cache.put(updated);
        assertEquals("us", cache.get("hub").get().server());
    }

    // ------------------------------------------------------------------
    // remove
    // ------------------------------------------------------------------

    @Test
    void removeDeletesEntry() {
        cache.put(warp("farm"));
        cache.remove("farm");
        assertTrue(cache.get("farm").isEmpty());
    }

    @Test
    void removeIsCaseInsensitive() {
        cache.put(warp("Farm"));
        cache.remove("FARM");
        assertTrue(cache.get("farm").isEmpty());
    }

    @Test
    void removeNonExistentNameDoesNotThrow() {
        assertDoesNotThrow(() -> cache.remove("ghost"));
    }

    // ------------------------------------------------------------------
    // replaceAll
    // ------------------------------------------------------------------

    @Test
    void replaceAllClearsOldEntriesAndAddsNew() {
        cache.put(warp("old"));
        cache.replaceAll(List.of(warp("spawn"), warp("hub")));

        assertTrue(cache.get("old").isEmpty(), "old entry should be evicted");
        assertTrue(cache.get("spawn").isPresent());
        assertTrue(cache.get("hub").isPresent());
    }

    @Test
    void replaceAllWithEmptyListClearsCache() {
        cache.put(warp("spawn"));
        cache.replaceAll(List.of());
        assertTrue(cache.get("spawn").isEmpty());
    }

    @Test
    void replaceAllAcceptsEntriesWithMixedCase() {
        cache.replaceAll(List.of(warp("Spawn"), warp("HUB")));
        assertTrue(cache.get("spawn").isPresent());
        assertTrue(cache.get("hub").isPresent());
    }

    // ------------------------------------------------------------------
    // list
    // ------------------------------------------------------------------

    @Test
    void listReturnsSortedByName() {
        cache.put(warp("zebra"));
        cache.put(warp("alpha"));
        cache.put(warp("middle"));

        List<String> names = cache.list().stream().map(WarpRecord::name).toList();
        assertEquals(List.of("alpha", "middle", "zebra"), names);
    }

    @Test
    void listReturnsEmptyWhenCacheIsEmpty() {
        assertTrue(cache.list().isEmpty());
    }

    @Test
    void listDoesNotLeakInternalState() {
        cache.put(warp("hub"));
        List<WarpRecord> first = cache.list();
        cache.put(warp("spawn"));
        // The list returned before the second put must not change.
        assertEquals(1, first.size());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static WarpRecord warp(String name) {
        return new WarpRecord(name, "eu", "world", 0, 64, 0, 0f, 0f);
    }
}
