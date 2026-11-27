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
    private final java.util.Set<UUID> tpaDisabled = ConcurrentHashMap.newKeySet();

    public void setIncoming(TpaRequestRecord request) {
        incomingByTarget.put(request.targetUuid(), request);
        outgoingBySender.put(request.senderUuid(), request);
    }

    public Optional<TpaRequestRecord> getIncoming(UUID targetUuid) {
        return Optional.ofNullable(incomingByTarget.get(targetUuid));
    }

    /** Remove the observed request without consuming a newer request for either player. */
    public boolean removeRequest(TpaRequestRecord request) {
        boolean removed = incomingByTarget.remove(request.targetUuid(), request);
        outgoingBySender.remove(request.senderUuid(), request);
        return removed;
    }

    public Optional<TpaRequestRecord> getOutgoing(UUID senderUuid) {
        return Optional.ofNullable(outgoingBySender.get(senderUuid));
    }

    public boolean onCooldown(UUID playerUuid) {
        return System.currentTimeMillis() < cooldowns.getOrDefault(playerUuid, 0L);
    }

    /** Returns seconds remaining on the cooldown, or 0 if none. */
    public int remainingCooldown(UUID playerUuid) {
        long diff = cooldowns.getOrDefault(playerUuid, 0L) - System.currentTimeMillis();
        return diff > 0 ? (int) Math.ceil(diff / 1000.0) : 0;
    }

    public void markCooldown(UUID playerUuid, int cooldownSeconds) {
        cooldowns.put(playerUuid, System.currentTimeMillis() + cooldownSeconds * 1000L);
    }

    /** @return true if TPA is now disabled, false if re-enabled */
    public boolean toggleTpa(UUID playerUuid) {
        if (tpaDisabled.contains(playerUuid)) {
            tpaDisabled.remove(playerUuid);
            return false;
        } else {
            tpaDisabled.add(playerUuid);
            return true;
        }
    }

    public boolean isTpaDisabled(UUID playerUuid) {
        return tpaDisabled.contains(playerUuid);
    }

    public void clearToggle(UUID playerUuid) {
        tpaDisabled.remove(playerUuid);
    }
}
