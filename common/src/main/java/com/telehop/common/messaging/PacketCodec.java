package com.telehop.common.messaging;

import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.nio.charset.StandardCharsets;

/**
 * Encodes and decodes {@link NetworkPacket} instances to/from UTF-8 JSON bytes
 * for transmission over the plugin messaging channel.
 */
public final class PacketCodec {
    /** Bukkit's plugin message payload limit also bounds Redis-forwarded packets. */
    public static final int MAX_BYTES = 32766;

    private static final Gson GSON = new GsonBuilder().create();

    private PacketCodec() {
    }

    public static byte[] encode(NetworkPacket packet) {
        byte[] data = GSON.toJson(packet).getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_BYTES) throw new IllegalArgumentException("TeleHop packet exceeds the channel limit");
        return data;
    }

    /** Returns null for unrecognised or malformed packets, including future protocol types. */
    public static NetworkPacket decode(byte[] data) {
        if (data == null || data.length == 0 || data.length > MAX_BYTES) return null;
        return decodeJson(new String(data, StandardCharsets.UTF_8));
    }

    /** Redis carries the same bounded JSON envelope as the plugin channel. */
    public static NetworkPacket decode(String json) {
        if (json == null || json.length() > MAX_BYTES) return null;
        if (json.length() > MAX_BYTES / 3 && json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) return null;
        return decodeJson(json);
    }

    private static NetworkPacket decodeJson(String json) {
        try {
            var packet = GSON.fromJson(json, NetworkPacket.class);
            if (packet == null || packet.getRequestId() == null || packet.getType() == null
                    || !endpoint(packet.getOriginServer()) || !endpoint(packet.getTargetServer())
                    || packet.getTimestamp() <= 0 || packet.getPayload() == null || packet.getPayload().size() > 64
                    || packet.isResponse() != (packet.getType() == PacketType.RESPONSE
                    || packet.getType() == PacketType.ERROR)) return null;
            for (var field : packet.getPayload().entrySet()) {
                if (field.getKey().isBlank() || field.getKey().length() > 64 || field.getValue() == null) return null;
            }
            return packet;
        } catch (JsonParseException | IllegalArgumentException invalid) {
            return null;
        }
    }

    private static boolean endpoint(String name) {
        return name != null && !name.isBlank() && name.length() <= 128 && name.chars().noneMatch(Character::isISOControl);
    }
}
