package com.telehop.velocity.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PendingAction {
    private final UUID playerUuid;
    private final String targetServer;
    private final String action;
    private final Map<String, String> payload = new HashMap<>();

    public PendingAction(UUID playerUuid, String targetServer, String action) {
        this.playerUuid = playerUuid;
        this.targetServer = targetServer;
        this.action = action;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public String targetServer() {
        return targetServer;
    }

    public String action() {
        return action;
    }

    public PendingAction put(String key, String value) {
        payload.put(key, value);
        return this;
    }

    public Map<String, String> payload() {
        return payload;
    }
}
