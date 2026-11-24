package com.telehop.common.messaging;

import com.telehop.common.NetworkConstants;
import com.telehop.common.model.NetworkPacket;

import java.util.concurrent.CompletableFuture;

/**
 * High-level API for sending request packets and awaiting responses.
 * Combines {@link RequestTracker} (future management and deduplication)
 * with {@link PacketTransport} (delivery).
 */
public class RequestBroker {
    private final RequestTracker tracker;
    private final PacketTransport transport;

    public RequestBroker(RequestTracker tracker, PacketTransport transport) {
        this.tracker = tracker;
        this.transport = transport;
    }

    public CompletableFuture<NetworkPacket> sendRequest(NetworkPacket packet) {
        return sendRequest(packet, NetworkConstants.DEFAULT_REQUEST_TIMEOUT_MS);
    }

    public CompletableFuture<NetworkPacket> sendRequest(NetworkPacket packet, long timeoutMs) {
        return sendRequest(packet, timeoutMs, null, transport);
    }

    /** Select both the outbound transport and the connection allowed to answer this request. */
    public CompletableFuture<NetworkPacket> sendRequest(NetworkPacket packet, long timeoutMs,
                                                        Object carrier, PacketTransport delivery) {
        CompletableFuture<NetworkPacket> future = tracker.track(packet, timeoutMs, carrier);
        try {
            if (!delivery.send(packet)) {
                future.completeExceptionally(new IllegalStateException("Failed to send packet: " + packet.getType()));
            }
        } catch (RuntimeException failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    public boolean handleIncoming(NetworkPacket packet, long dedupeWindowMs) {
        return handleIncoming(packet, dedupeWindowMs, null);
    }

    public boolean handleIncoming(NetworkPacket packet, long dedupeWindowMs, Object carrier) {
        if (packet.isResponse()) {
            tracker.complete(packet, carrier);
            return false;
        }
        boolean fresh = tracker.markSeen(packet.getRequestId(), dedupeWindowMs);
        tracker.cleanupSeen(dedupeWindowMs);
        return fresh;
    }
}
