package com.telehop.common.service;

import com.telehop.common.db.DatabaseManager;
import com.telehop.common.db.WarpRepository;
import com.telehop.common.model.WarpRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WarpServiceTest {

    private DatabaseManager databaseManager;
    private WarpRepository repository;
    private WarpCache cache;
    private WarpService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        databaseManager = mock(DatabaseManager.class);
        repository = mock(WarpRepository.class);
        cache = new WarpCache();

        when(databaseManager.supplyAsync(any(Supplier.class)))
                .thenAnswer(inv -> {
                    Supplier<?> s = inv.getArgument(0);
                    return CompletableFuture.completedFuture(s.get());
                });
        when(databaseManager.runAsync(any(Runnable.class)))
                .thenAnswer(inv -> {
                    Runnable r = inv.getArgument(0);
                    r.run();
                    return CompletableFuture.completedFuture(null);
                });

        service = new WarpService(databaseManager, repository, cache);
    }

    // ------------------------------------------------------------------
    // find()
    // ------------------------------------------------------------------

    @Test
    void findReturnsCachedValueWithoutQueryingDb() throws Exception {
        WarpRecord spawn = warp("spawn");
        cache.put(spawn);

        Optional<WarpRecord> result = service.find("spawn").get();

        assertTrue(result.isPresent());
        assertEquals("spawn", result.get().name());
        verifyNoInteractions(databaseManager, repository);
    }

    @Test
    void findQueriesDbAndPopulatesCacheOnCacheMiss() throws Exception {
        WarpRecord hub = warp("hub");
        when(repository.findByName("hub")).thenReturn(Optional.of(hub));

        Optional<WarpRecord> result = service.find("hub").get();

        assertTrue(result.isPresent());
        assertEquals("hub", result.get().name());
        verify(repository).findByName("hub");
        // Subsequent call must hit cache, not DB again
        service.find("hub").get();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void findReturnsEmptyWhenDbHasNoRecord() throws Exception {
        when(repository.findByName("unknown")).thenReturn(Optional.empty());

        Optional<WarpRecord> result = service.find("unknown").get();

        assertTrue(result.isEmpty());
    }

    // ------------------------------------------------------------------
    // upsert()
    // ------------------------------------------------------------------

    @Test
    void upsertWritesToCacheBeforeDb() throws Exception {
        WarpRecord warp = warp("pvp");

        service.upsert(warp).get();

        // Cache should be populated synchronously before DB call completes.
        assertTrue(cache.get("pvp").isPresent());
        verify(repository).upsert(warp);
    }

    @Test
    void upsertUpdatesExistingCacheEntry() throws Exception {
        WarpRecord original = warp("pvp");
        WarpRecord updated = new WarpRecord("pvp", "us", "world", 20, 64, 30, 0f, 0f);
        service.upsert(original).get();
        service.upsert(updated).get();

        assertEquals("us", cache.get("pvp").get().server());
    }

    // ------------------------------------------------------------------
    // delete()
    // ------------------------------------------------------------------

    @Test
    void deleteRemovesFromCacheAndCallsDb() throws Exception {
        WarpRecord warp = warp("farm");
        cache.put(warp);

        service.delete("farm").get();

        assertTrue(cache.get("farm").isEmpty());
        verify(repository).delete("farm");
    }

    @Test
    void deleteOnNonExistentNameDoesNotThrow() {
        assertDoesNotThrow(() -> service.delete("does-not-exist").get());
    }

    // ------------------------------------------------------------------
    // refreshCache()
    // ------------------------------------------------------------------

    @Test
    void refreshCacheReplacesAllEntries() throws Exception {
        cache.put(warp("old-warp"));
        List<WarpRecord> fresh = List.of(warp("spawn"), warp("hub"));
        when(repository.listAll()).thenReturn(fresh);

        service.refreshCache().get();

        assertTrue(cache.get("old-warp").isEmpty(), "Stale warp should be evicted");
        assertTrue(cache.get("spawn").isPresent());
        assertTrue(cache.get("hub").isPresent());
    }

    @Test
    void refreshCacheWithEmptyListClearsCache() throws Exception {
        cache.put(warp("spawn"));
        when(repository.listAll()).thenReturn(List.of());

        service.refreshCache().get();

        assertTrue(cache.get("spawn").isEmpty());
    }

    // ------------------------------------------------------------------
    // listCached()
    // ------------------------------------------------------------------

    @Test
    void listCachedReturnsSortedNames() throws Exception {
        service.upsert(warp("zebra")).get();
        service.upsert(warp("alpha")).get();
        service.upsert(warp("middle")).get();

        List<String> names = service.listCached().stream().map(WarpRecord::name).toList();

        assertEquals(List.of("alpha", "middle", "zebra"), names);
    }

    @Test
    void listCachedReturnsEmptyListWhenCacheEmpty() {
        assertTrue(service.listCached().isEmpty());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static WarpRecord warp(String name) {
        return new WarpRecord(name, "eu", "world", 0, 64, 0, 0f, 0f);
    }

    private static <T> ArgumentCaptor<T> captor(Class<T> type) {
        return ArgumentCaptor.forClass(type);
    }
}
