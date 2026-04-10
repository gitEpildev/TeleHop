package com.telehop.common.service;

import com.telehop.common.model.TpaRequestRecord;

import java.time.Instant;
import java.util.*;
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

    public void remove(UUID sender, UUID target) {
        cache.remove(key(sender, target));
    }

    public List<TpaRequestRecord> expired(long timeoutSeconds) {
        return cache.values().stream()
                .filter(r -> r.isExpired(timeoutSeconds))
                .toList();
    }
}
