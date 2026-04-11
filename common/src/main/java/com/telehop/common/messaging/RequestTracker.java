package com.telehop.common.messaging;

import com.telehop.common.model.NetworkPacket;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tracks in-flight request/response pairs and deduplicates incoming packets.
 *
 * <p>Each outbound request is tracked by its {@code requestId}. When a matching
 * response arrives, the corresponding {@link CompletableFuture} is completed.
 * Requests that are not answered within the configured timeout are completed
 * exceptionally with a {@link java.util.concurrent.TimeoutException}.</p>
 *
 * <p>Incoming request IDs are recorded in a seen-set to prevent duplicate
 * processing during retries or network hiccups.</p>
 */
public class RequestTracker {
    private final ScheduledExecutorService scheduler;
    private final Map<UUID, CompletableFuture<NetworkPacket>> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> seenRequests = new ConcurrentHashMap<>();

    public RequestTracker(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    public CompletableFuture<NetworkPacket> track(NetworkPacket request, long timeoutMs) {
        CompletableFuture<NetworkPacket> future = new CompletableFuture<>();
        pending.put(request.getRequestId(), future);
        scheduler.schedule(() -> {
            CompletableFuture<NetworkPacket> pendingFuture = pending.remove(request.getRequestId());
            if (pendingFuture != null && !pendingFuture.isDone()) {
                pendingFuture.completeExceptionally(new TimeoutException("Request timed out: " + request.getRequestId()));
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);
        return future;
    }

    public void complete(NetworkPacket response) {
        CompletableFuture<NetworkPacket> future = pending.remove(response.getRequestId());
        if (future != null) {
            future.complete(response);
        }
    }

    public boolean markSeen(UUID requestId, long dedupeWindowMs) {
        long now = System.currentTimeMillis();
        Long firstSeen = seenRequests.putIfAbsent(requestId, now);
        if (firstSeen == null) {
            return true;
        }
        if (now - firstSeen > dedupeWindowMs) {
            seenRequests.put(requestId, now);
            return true;
        }
        return false;
    }

    public void cleanupSeen(long dedupeWindowMs) {
        long now = System.currentTimeMillis();
        seenRequests.entrySet().removeIf(e -> now - e.getValue() > dedupeWindowMs);
    }
}
