package com.telehop.common.service;

import com.telehop.common.model.TpaRequestRecord;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TpaRequestCache {
    private final Map<String, TpaRequestRecord> cache = new ConcurrentHashMap<>();

    private String key(UUID sender, UUID target) {
        return sender + ":" + target;
    }

    public void put(TpaRequestRecord request) {
        cache.put(key(request.senderUuid(), request.targetUuid()), request);
    }

    public Optional<TpaRequestRecord> get(UUID sender, UUID target) {
        return Optional.ofNullable(cache.get(key(sender, target)));
    }

    public void remove(TpaRequestRecord request) {
        cache.remove(key(request.senderUuid(), request.targetUuid()), request);
    }

    public List<TpaRequestRecord> expired(long timeoutSeconds) {
        return cache.values().stream()
                .filter(r -> r.isExpired(timeoutSeconds))
                .toList();
    }
}
