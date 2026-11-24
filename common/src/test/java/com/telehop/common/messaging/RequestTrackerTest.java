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
    void spawnRequestOnlyAcceptsItsOwnConnectionAndReversedEndpoints() {
        var broker = new RequestBroker(tracker, ignored -> {
            throw new AssertionError("A player-bound request used the default transport");
        });
        var carrier = new Object();
        var traveller = UUID.randomUUID();
        var probe = NetworkPacket.spawnProbe("hub", "velocity", traveller);
        var future = broker.sendRequest(probe, 5000, carrier, outgoing -> {
            assertSame(probe, outgoing);
            assertTrue(outgoing.supportsSpawns());
            assertEquals(traveller.toString(), outgoing.get("uuid"));
            return true;
        });
        var reply = NetworkPacket.response(probe, true, null);
        broker.handleIncoming(reply, 5000, new Object());
        assertFalse(future.isDone());
        reply.setOriginServer("second-proxy");
        broker.handleIncoming(reply, 5000, carrier);
        assertFalse(future.isDone());
        reply.setOriginServer("velocity");
        reply.setTargetServer("other-backend");
        broker.handleIncoming(reply, 5000, carrier);
        assertFalse(future.isDone());
        reply.setTargetServer("hub");
        broker.handleIncoming(reply, 5000, carrier);
        assertSame(reply, future.join());
    }

    @Test
    void unsuccessfulBoundSendReleasesTheRequestImmediately() {
        var broker = new RequestBroker(tracker, ignored -> false);
        var probe = NetworkPacket.spawnProbe("hub", "velocity", UUID.randomUUID());
        var carrier = new Object();
        assertTrue(broker.sendRequest(probe, 5000, carrier, ignored -> false).isCompletedExceptionally());
        var retry = broker.sendRequest(probe, 5000, carrier, ignored -> true);
        var reply = NetworkPacket.response(probe, true, null);
        broker.handleIncoming(reply, 5000, carrier);
        assertSame(reply, retry.join());
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
