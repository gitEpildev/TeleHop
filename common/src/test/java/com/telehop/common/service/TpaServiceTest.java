package com.telehop.common.service;

import com.telehop.common.db.DatabaseManager;
import com.telehop.common.db.TpaRepository;
import com.telehop.common.model.TpaRequestRecord;
import com.telehop.common.model.TpaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TpaServiceTest {

    private DatabaseManager databaseManager;
    private TpaRepository repository;
    private TpaRequestCache cache;
    private TpaService service;

    private final UUID senderUuid = UUID.randomUUID();
    private final UUID targetUuid = UUID.randomUUID();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        databaseManager = mock(DatabaseManager.class);
        repository = mock(TpaRepository.class);
        cache = new TpaRequestCache();

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

        service = new TpaService(databaseManager, repository, cache);
    }

    // ------------------------------------------------------------------
    // upsert()
    // ------------------------------------------------------------------

    @Test
    void upsertWritesToCacheAndDb() throws Exception {
        TpaRequestRecord request = request(TpaType.TPA);

        service.upsert(request).get();

        assertTrue(cache.get(senderUuid, targetUuid).isPresent());
        verify(repository).upsert(request);
    }

    @Test
    void upsertOverwritesExistingCacheEntry() throws Exception {
        TpaRequestRecord first = request(TpaType.TPA);
        TpaRequestRecord second = request(TpaType.TPA_HERE);

        service.upsert(first).get();
        service.upsert(second).get();

        assertEquals(TpaType.TPA_HERE, cache.get(senderUuid, targetUuid).get().type());
    }

    // ------------------------------------------------------------------
    // find()
    // ------------------------------------------------------------------

    @Test
    void findReturnsCachedRequestWithoutHittingDb() throws Exception {
        TpaRequestRecord request = request(TpaType.TPA);
        cache.put(request);

        Optional<TpaRequestRecord> result = service.find(senderUuid, targetUuid).get();

        assertTrue(result.isPresent());
        assertEquals(TpaType.TPA, result.get().type());
        verifyNoInteractions(databaseManager, repository);
    }

    @Test
    void findQueriesDbOnCacheMissAndPopulatesCache() throws Exception {
        TpaRequestRecord request = request(TpaType.TPA_HERE);
        when(repository.find(senderUuid, targetUuid)).thenReturn(Optional.of(request));

        Optional<TpaRequestRecord> result = service.find(senderUuid, targetUuid).get();

        assertTrue(result.isPresent());
        verify(repository).find(senderUuid, targetUuid);
        // Second call must hit cache, not DB
        service.find(senderUuid, targetUuid).get();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void findReturnsEmptyWhenDbHasNoRecord() throws Exception {
        when(repository.find(senderUuid, targetUuid)).thenReturn(Optional.empty());

        Optional<TpaRequestRecord> result = service.find(senderUuid, targetUuid).get();

        assertTrue(result.isEmpty());
    }

    @Test
    void findDoesNotCacheEmptyResultFromDb() throws Exception {
        when(repository.find(senderUuid, targetUuid)).thenReturn(Optional.empty());

        service.find(senderUuid, targetUuid).get();
        service.find(senderUuid, targetUuid).get();

        // Both calls must go to DB because nothing was cached.
        verify(repository, times(2)).find(senderUuid, targetUuid);
    }

    // ------------------------------------------------------------------
    // delete()
    // ------------------------------------------------------------------

    @Test
    void deleteRemovesFromCacheAndCallsDb() throws Exception {
        TpaRequestRecord request = request(TpaType.TPA);
        cache.put(request);

        service.delete(senderUuid, targetUuid).get();

        assertTrue(cache.get(senderUuid, targetUuid).isEmpty());
        verify(repository).delete(senderUuid, targetUuid);
    }

    @Test
    void deleteOnNonExistentEntryDoesNotThrow() {
        assertDoesNotThrow(() -> service.delete(UUID.randomUUID(), UUID.randomUUID()).get());
    }

    // ------------------------------------------------------------------
    // expiredNow()
    // ------------------------------------------------------------------

    @Test
    void expiredNowDelegatesToRepositoryWithConvertedMillis() throws Exception {
        List<TpaRequestRecord> expired = List.of(request(TpaType.TPA));
        // The service multiplies timeoutSeconds by 1000 before calling the repo.
        when(repository.findExpired(30_000L)).thenReturn(expired);

        List<TpaRequestRecord> result = service.expiredNow(30L).get();

        assertEquals(1, result.size());
        verify(repository).findExpired(30_000L);
    }

    @Test
    void expiredNowReturnsEmptyListWhenNoneExpired() throws Exception {
        when(repository.findExpired(anyLong())).thenReturn(List.of());

        List<TpaRequestRecord> result = service.expiredNow(60L).get();

        assertTrue(result.isEmpty());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private TpaRequestRecord request(TpaType type) {
        return new TpaRequestRecord(senderUuid, targetUuid, type, Instant.now());
    }
}
