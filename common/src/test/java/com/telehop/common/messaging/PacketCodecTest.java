package com.telehop.common.messaging;

import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PacketCodecTest {

    @Test
    void encodeAndDecodePreservesFields() {
        NetworkPacket original = NetworkPacket.request(PacketType.TPA_CREATE, "eu", "velocity");
        original.put("senderUuid", "abc-123");
        original.put("targetUuid", "def-456");

        byte[] encoded = PacketCodec.encode(original);
        NetworkPacket decoded = PacketCodec.decode(encoded);

        assertEquals(original.getRequestId(), decoded.getRequestId());
        assertEquals(original.getType(), decoded.getType());
        assertEquals(original.getOriginServer(), decoded.getOriginServer());
        assertEquals(original.getTargetServer(), decoded.getTargetServer());
        assertEquals("abc-123", decoded.get("senderUuid"));
        assertEquals("def-456", decoded.get("targetUuid"));
        assertFalse(decoded.isResponse());
        assertTrue(decoded.isSuccess());
    }

    @Test
    void responsePacketPreservesSuccessAndError() {
        NetworkPacket request = NetworkPacket.request(PacketType.WARP_TELEPORT, "eu", "lobby");
        NetworkPacket response = NetworkPacket.response(request, false, "Warp not found");

        byte[] encoded = PacketCodec.encode(response);
        NetworkPacket decoded = PacketCodec.decode(encoded);

        assertTrue(decoded.isResponse());
        assertFalse(decoded.isSuccess());
        assertEquals("Warp not found", decoded.getErrorMessage());
        assertEquals(PacketType.RESPONSE, decoded.getType());
        assertEquals(request.getRequestId(), decoded.getRequestId());
    }

    @Test
    void emptyPayloadRoundTrips() {
        NetworkPacket packet = NetworkPacket.request(PacketType.PING, "server1", "server2");
        byte[] encoded = PacketCodec.encode(packet);
        NetworkPacket decoded = PacketCodec.decode(encoded);

        assertTrue(decoded.getPayload().isEmpty());
    }

    @Test
    void specialCharactersInPayload() {
        NetworkPacket packet = NetworkPacket.request(PacketType.WARP_CREATE, "eu", "velocity");
        packet.put("name", "warp with spaces & <special> chars!");

        byte[] encoded = PacketCodec.encode(packet);
        NetworkPacket decoded = PacketCodec.decode(encoded);

        assertEquals("warp with spaces & <special> chars!", decoded.get("name"));
    }
}
