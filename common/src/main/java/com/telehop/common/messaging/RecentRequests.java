package com.telehop.common.messaging;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded replay window shared by the proxy, backend and Redis receivers. */
public final class RecentRequests {
    private final ConcurrentHashMap<UUID, Long> expiry = new ConcurrentHashMap<>();
    private final Semaphore slots;
    private final AtomicLong nextCleanup = new AtomicLong();

    public RecentRequests(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Invalid replay window capacity");
        slots = new Semaphore(capacity);
    }

    /** When full, refuse new requests until a slot expires. An active entry is never evicted early. */
    public boolean accept(UUID id, long now, long windowMs) {
        cleanup(now);
        Long existing = expiry.get(id);
        if (existing != null) return existing < now && expiry.replace(id, existing, now + windowMs);
        if (!slots.tryAcquire()) return false;
        if (expiry.putIfAbsent(id, now + windowMs) == null) return true;
        slots.release();
        return false;
    }

    void cleanup(long now) {
        long next = nextCleanup.get();
        if (now < next || !nextCleanup.compareAndSet(next, now + 1000)) return;
        expiry.forEach((id, deadline) -> {
            if (deadline < now && expiry.remove(id, deadline)) slots.release();
        });
    }
}
