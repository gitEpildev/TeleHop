package com.telehop.common.messaging;

import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class RequestTrackerTest {

    private ScheduledExecutorService scheduler;
    private RequestTracker tracker;

    @BeforeEach
    void setUp() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        tracker = new RequestTracker(scheduler);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void trackedRequestCompletesOnResponse() throws Exception {
        NetworkPacket request = NetworkPacket.request(PacketType.TPA_CREATE, "eu", "lobby");
        CompletableFuture<NetworkPacket> future = tracker.track(request, 5000);

        NetworkPacket response = NetworkPacket.response(request, true, null);
        tracker.complete(response);

        NetworkPacket result = future.get(1, TimeUnit.SECONDS);
        assertTrue(result.isSuccess());
    }

    @Test
    void trackedRequestTimesOut() {
        NetworkPacket request = NetworkPacket.request(PacketType.WARP_LIST, "eu", "velocity");
        CompletableFuture<NetworkPacket> future = tracker.track(request, 200);

        assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
    }

    @Test
    void duplicateRequestIsRejectedWithinWindow() {
        UUID requestId = UUID.randomUUID();
        assertTrue(tracker.markSeen(requestId, 5000));
        assertFalse(tracker.markSeen(requestId, 5000));
    }

    @Test
    void expiredSeenRequestsAreCleaned() throws InterruptedException {
        UUID requestId = UUID.randomUUID();
        tracker.markSeen(requestId, 100);
        Thread.sleep(200);
        tracker.cleanupSeen(100);
        assertTrue(tracker.markSeen(requestId, 5000));
    }

    @Test
    void completingUnknownResponseDoesNotThrow() {
        NetworkPacket request = NetworkPacket.request(PacketType.PING, "a", "b");
        NetworkPacket response = NetworkPacket.response(request, true, null);
        assertDoesNotThrow(() -> tracker.complete(response));
    }
}
