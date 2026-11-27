package com.telehop.common.messaging;

import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class WireValidationTest {
    @Test
    void ignoresUnknownEnvelopesAndCapsInputBeforeParsing() {
        for (String invalid : new String[]{"null", "[]", "{}", "{", "{\"type\":\"FUTURE_TYPE\"}"}) {
            assertNull(PacketCodec.decode(invalid.getBytes(StandardCharsets.UTF_8)));
        }
        assertNull(PacketCodec.decode(new byte[PacketCodec.MAX_BYTES + 1]));
        var packet = NetworkPacket.request(PacketType.PING, "first", "second");
        packet.setPayload(null);
        packet.setTargetServer(null);
        assertNull(PacketCodec.decode(PacketCodec.encode(packet)));
    }

    @Test
    void redisAndThePluginChannelShareOneUtf8ByteLimit() {
        var packet = NetworkPacket.request(PacketType.PING, "west", "east").put("note", "replace-me");
        String json = new String(PacketCodec.encode(packet), StandardCharsets.UTF_8);
        String oversized = json.replace("replace-me", "界".repeat(PacketCodec.MAX_BYTES / 3));
        assertTrue(oversized.length() < PacketCodec.MAX_BYTES);
        assertNull(PacketCodec.decode(oversized));
        assertNull(PacketCodec.decode(oversized.getBytes(StandardCharsets.UTF_8)));
        assertEquals("café", PacketCodec.decode(json.replace("replace-me", "café")).get("note"));
    }

    @Test
    void variantsRequireTheirOwnCapabilityAtBothEnds() {
        var traveller = UUID.randomUUID();
        var probe = NetworkPacket.variantProbe("entry", "velocity", traveller);
        var received = PacketCodec.decode(PacketCodec.encode(probe));
        assertEquals(traveller.toString(), received.get("uuid"));
        assertTrue(received.supportsVariants());
        assertFalse(received.supportsSpawns());
        assertFalse(NetworkPacket.spawnProbe("entry", "velocity", traveller).supportsVariants());
    }

    @Test
    void replayWindowRefusesOverflowUntilExpiryWithoutEvictingLiveEntries() {
        var window = new RecentRequests(1);
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        assertTrue(window.accept(first, 1000, 500));
        assertFalse(window.accept(second, 1200, 500));
        assertFalse(window.accept(first, 1300, 500));
        assertTrue(window.accept(second, 2500, 500));
    }

    @Test
    void allResponsesMatchEndpointsAndShutdownReleasesAwaitingCallers() {
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        try {
            var tracker = new RequestTracker(scheduler);
            var packet = NetworkPacket.request(PacketType.PING, "first", "second");
            var pending = tracker.track(packet, 60_000);
            var reply = NetworkPacket.response(packet, true, null);
            reply.setOriginServer("other");
            tracker.complete(reply);
            assertFalse(pending.isDone());
            tracker.close();
            assertTrue(pending.isCancelled());
        } finally {
            scheduler.shutdownNow();
        }
    }
}
