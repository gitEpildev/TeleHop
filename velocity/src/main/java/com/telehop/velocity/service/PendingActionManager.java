package com.telehop.velocity.service;

import com.telehop.velocity.model.PendingAction;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PendingActionManager {
    private final Map<UUID, PendingAction> pendingByPlayer = new ConcurrentHashMap<>();

    public void put(PendingAction action) {
        pendingByPlayer.put(action.playerUuid(), action);
    }

    public PendingAction take(UUID playerUuid) {
        return pendingByPlayer.remove(playerUuid);
    }

    public PendingAction get(UUID playerUuid) {
        return pendingByPlayer.get(playerUuid);
    }

    public void remove(UUID playerUuid) {
        pendingByPlayer.remove(playerUuid);
    }
}
