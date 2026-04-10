package com.telehop.common.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NetworkPacket {
    private UUID requestId;
    private PacketType type;
    private String originServer;
    private String targetServer;
    private long timestamp;
    private boolean response;
    private boolean success;
    private String errorMessage;
    private Map<String, String> payload = new HashMap<>();

    public static NetworkPacket request(PacketType type, String originServer, String targetServer) {
        NetworkPacket packet = new NetworkPacket();
        packet.requestId = UUID.randomUUID();
        packet.type = type;
        packet.originServer = originServer;
        packet.targetServer = targetServer;
        packet.timestamp = System.currentTimeMillis();
        packet.response = false;
        packet.success = true;
        return packet;
    }

    public static NetworkPacket response(NetworkPacket request, boolean success, String errorMessage) {
        NetworkPacket packet = new NetworkPacket();
        packet.requestId = request.requestId;
        packet.type = PacketType.RESPONSE;
        packet.originServer = request.targetServer;
        packet.targetServer = request.originServer;
        packet.timestamp = System.currentTimeMillis();
        packet.response = true;
        packet.success = success;
        packet.errorMessage = errorMessage;
        return packet;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }

    public PacketType getType() {
        return type;
    }

    public void setType(PacketType type) {
        this.type = type;
    }

    public String getOriginServer() {
        return originServer;
    }

    public void setOriginServer(String originServer) {
        this.originServer = originServer;
    }

    public String getTargetServer() {
        return targetServer;
    }

    public void setTargetServer(String targetServer) {
        this.targetServer = targetServer;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isResponse() {
        return response;
    }

    public void setResponse(boolean response) {
        this.response = response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Map<String, String> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, String> payload) {
        this.payload = payload;
    }

    public NetworkPacket put(String key, String value) {
        this.payload.put(key, value);
        return this;
    }

    public String get(String key) {
        return this.payload.get(key);
    }

    public String getOrDefault(String key, String defaultValue) {
        return this.payload.getOrDefault(key, defaultValue);
    }
}
