package com.telehop.common.service;

import com.telehop.common.model.TpaRequestRecord;
import com.telehop.common.model.TpaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TpaRequestCacheTest {

    private TpaRequestCache cache;

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID carol = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cache = new TpaRequestCache();
    }

    // ------------------------------------------------------------------
    // put / get
    // ------------------------------------------------------------------

    @Test
    void putAndGetReturnRecord() {
        TpaRequestRecord request = request(alice, bob, TpaType.TPA, Instant.now());
        cache.put(request);

        Optional<TpaRequestRecord> result = cache.get(alice, bob);

        assertTrue(result.isPresent());
        assertEquals(alice, result.get().senderUuid());
        assertEquals(bob, result.get().targetUuid());
    }

    @Test
    void getReturnsEmptyWhenAbsent() {
        assertTrue(cache.get(alice, bob).isEmpty());
    }

    @Test
    void putOverwritesExistingEntry() {
        cache.put(request(alice, bob, TpaType.TPA, Instant.now()));
        TpaRequestRecord updated = request(alice, bob, TpaType.TPA_HERE, Instant.now());
        cache.put(updated);

        assertEquals(TpaType.TPA_HERE, cache.get(alice, bob).get().type());
    }

    @Test
    void keyIsDirectionalAliceToAliceAndBobToAliceAreDistinct() {
        cache.put(request(alice, bob, TpaType.TPA, Instant.now()));

        assertTrue(cache.get(alice, bob).isPresent());
        assertTrue(cache.get(bob, alice).isEmpty(), "Reversed key must not match");
    }

    @Test
    void multipleDistinctRequestsAreStoredIndependently() {
        cache.put(request(alice, bob, TpaType.TPA, Instant.now()));
        cache.put(request(bob, carol, TpaType.TPA_HERE, Instant.now()));

        assertEquals(TpaType.TPA, cache.get(alice, bob).get().type());
        assertEquals(TpaType.TPA_HERE, cache.get(bob, carol).get().type());
    }

    // ------------------------------------------------------------------
    // remove
    // ------------------------------------------------------------------

    @Test
    void removeDeletesEntry() {
        cache.put(request(alice, bob, TpaType.TPA, Instant.now()));
        cache.remove(alice, bob);

        assertTrue(cache.get(alice, bob).isEmpty());
    }

    @Test
    void removeDoesNotAffectOtherEntries() {
        cache.put(request(alice, bob, TpaType.TPA, Instant.now()));
        cache.put(request(bob, carol, TpaType.TPA, Instant.now()));
        cache.remove(alice, bob);

        assertTrue(cache.get(bob, carol).isPresent());
    }

    @Test
    void removeNonExistentEntryDoesNotThrow() {
        assertDoesNotThrow(() -> cache.remove(UUID.randomUUID(), UUID.randomUUID()));
    }

    // ------------------------------------------------------------------
    // expired()
    // ------------------------------------------------------------------

    @Test
    void expiredReturnsRequestsOlderThanTimeout() {
        // sentAt 10 seconds in the past, timeout is 5 seconds → expired
        Instant old = Instant.now().minusSeconds(10);
        cache.put(request(alice, bob, TpaType.TPA, old));

        List<TpaRequestRecord> expired = cache.expired(5L);

        assertEquals(1, expired.size());
        assertEquals(alice, expired.get(0).senderUuid());
    }

    @Test
    void expiredDoesNotReturnFreshRequests() {
        // sentAt now, timeout is 60 seconds → not expired
        cache.put(request(alice, bob, TpaType.TPA, Instant.now()));

        assertTrue(cache.expired(60L).isEmpty());
    }

    @Test
    void expiredFiltersCorrectlyWithMixedRequests() {
        Instant old = Instant.now().minusSeconds(20);
        cache.put(request(alice, bob, TpaType.TPA, old));          // expired
        cache.put(request(bob, carol, TpaType.TPA_HERE, Instant.now())); // fresh

        List<TpaRequestRecord> expired = cache.expired(10L);

        assertEquals(1, expired.size());
        assertEquals(alice, expired.get(0).senderUuid());
    }

    @Test
    void expiredReturnsEmptyWhenCacheIsEmpty() {
        assertTrue(cache.expired(30L).isEmpty());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static TpaRequestRecord request(UUID sender, UUID target, TpaType type, Instant sentAt) {
        return new TpaRequestRecord(sender, target, type, sentAt);
    }
}
