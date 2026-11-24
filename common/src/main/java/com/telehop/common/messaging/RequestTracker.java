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
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> seenRequests = new ConcurrentHashMap<>();

    public RequestTracker(ScheduledExecutorService scheduler) {
        this.scheduler = scheduler;
    }

    public CompletableFuture<NetworkPacket> track(NetworkPacket request, long timeoutMs) {
        return track(request, timeoutMs, null);
    }

    /** Pin a reply to the very same live connection, even across reconnects with the same UUID. */
    public CompletableFuture<NetworkPacket> track(NetworkPacket request, long timeoutMs, Object carrier) {
        var future = new CompletableFuture<NetworkPacket>();
        UUID id = request.getRequestId();
        var entry = new Pending(carrier, request.getTargetServer(), request.getOriginServer(), future);
        if (pending.putIfAbsent(id, entry) != null) {
            throw new IllegalArgumentException("Request is already pending: " + id);
        }
        var timeout = scheduler.schedule(() -> future.completeExceptionally(
                new TimeoutException("Request timed out: " + id)), timeoutMs, TimeUnit.MILLISECONDS);
        future.whenComplete((reply, error) -> {
            pending.remove(id, entry);
            timeout.cancel(false);
        });
        return future;
    }

    public void complete(NetworkPacket response) {
        complete(response, null);
    }

    public void complete(NetworkPacket response, Object carrier) {
        Pending entry = pending.get(response.getRequestId());
        if (entry != null && entry.accepts(response, carrier)) entry.future().complete(response);
    }

    private record Pending(Object carrier, String origin, String target, CompletableFuture<NetworkPacket> future) {
        boolean accepts(NetworkPacket reply, Object inbound) {
            return carrier == null || (carrier == inbound && origin.equalsIgnoreCase(reply.getOriginServer())
                    && target.equalsIgnoreCase(reply.getTargetServer()));
        }
    }

    public boolean markSeen(UUID requestId, long dedupeWindowMs) {
        long now = System.currentTimeMillis();
        Long firstSeen = seenRequests.putIfAbsent(requestId, now);
        if (firstSeen == null) {
            return true;
        }
        if (now - firstSeen > dedupeWindowMs) {
            return seenRequests.replace(requestId, firstSeen, now);
        }
        return false;
    }

    public void cleanupSeen(long dedupeWindowMs) {
        long now = System.currentTimeMillis();
        seenRequests.entrySet().removeIf(e -> now - e.getValue() > dedupeWindowMs);
    }
}
