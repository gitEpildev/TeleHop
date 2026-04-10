package com.telehop.paper.service;

import com.telehop.common.model.TpaRequestRecord;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TpaRuntimeManager {
    private final Map<UUID, TpaRequestRecord> incomingByTarget = new ConcurrentHashMap<>();
    private final Map<UUID, TpaRequestRecord> outgoingBySender = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public void setIncoming(TpaRequestRecord request) {
        incomingByTarget.put(request.targetUuid(), request);
        outgoingBySender.put(request.senderUuid(), request);
    }

    public Optional<TpaRequestRecord> getIncoming(UUID targetUuid) {
        return Optional.ofNullable(incomingByTarget.get(targetUuid));
    }

    public void removeIncoming(UUID targetUuid) {
        TpaRequestRecord removed = incomingByTarget.remove(targetUuid);
        if (removed != null) {
            outgoingBySender.remove(removed.senderUuid());
        }
    }

    public Optional<TpaRequestRecord> getOutgoing(UUID senderUuid) {
        return Optional.ofNullable(outgoingBySender.get(senderUuid));
    }

    public void removeOutgoing(UUID senderUuid) {
        TpaRequestRecord removed = outgoingBySender.remove(senderUuid);
        if (removed != null) {
            incomingByTarget.remove(removed.targetUuid());
        }
    }

    public boolean onCooldown(UUID playerUuid) {
        return System.currentTimeMillis() < cooldowns.getOrDefault(playerUuid, 0L);
    }

    public void markCooldown(UUID playerUuid, int cooldownSeconds) {
        cooldowns.put(playerUuid, System.currentTimeMillis() + cooldownSeconds * 1000L);
    }
}
