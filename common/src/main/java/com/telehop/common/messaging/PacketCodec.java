package com.telehop.common.messaging;

import com.telehop.common.model.NetworkPacket;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.charset.StandardCharsets;

public final class PacketCodec {
    private static final Gson GSON = new GsonBuilder().create();

    private PacketCodec() {
    }

    public static byte[] encode(NetworkPacket packet) {
        return GSON.toJson(packet).getBytes(StandardCharsets.UTF_8);
    }

    public static NetworkPacket decode(byte[] data) {
        return GSON.fromJson(new String(data, StandardCharsets.UTF_8), NetworkPacket.class);
    }
}
