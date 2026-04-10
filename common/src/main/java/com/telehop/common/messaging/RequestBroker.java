package com.telehop.common.messaging;

import com.telehop.common.NetworkConstants;
import com.telehop.common.model.NetworkPacket;

import java.util.concurrent.CompletableFuture;

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
        CompletableFuture<NetworkPacket> future = tracker.track(packet, timeoutMs);
        if (!transport.send(packet)) {
            future.completeExceptionally(new IllegalStateException("Failed to send packet: " + packet.getType()));
        }
        return future;
    }

    public void handleIncoming(NetworkPacket packet, long dedupeWindowMs) {
        if (packet.isResponse()) {
            tracker.complete(packet);
            return;
        }
        tracker.markSeen(packet.getRequestId(), dedupeWindowMs);
        tracker.cleanupSeen(dedupeWindowMs);
    }
}
