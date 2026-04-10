package com.telehop.common.service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerServerCache {
    private final Map<UUID, String> cache = new ConcurrentHashMap<>();

    public void put(UUID uuid, String server) {
        cache.put(uuid, server);
    }

    public Optional<String> get(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    public void remove(UUID uuid) {
        cache.remove(uuid);
    }
}
