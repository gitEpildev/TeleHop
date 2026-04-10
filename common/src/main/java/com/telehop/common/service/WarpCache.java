package com.telehop.common.service;

import com.telehop.common.model.WarpRecord;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WarpCache {
    private final Map<String, WarpRecord> cache = new ConcurrentHashMap<>();

    public void put(WarpRecord warp) {
        cache.put(warp.name().toLowerCase(), warp);
    }

    public Optional<WarpRecord> get(String name) {
        return Optional.ofNullable(cache.get(name.toLowerCase()));
    }

    public void remove(String name) {
        cache.remove(name.toLowerCase());
    }

    public void replaceAll(Collection<WarpRecord> warps) {
        cache.clear();
        for (WarpRecord warp : warps) {
            put(warp);
        }
    }

    public List<WarpRecord> list() {
        return cache.values().stream()
                .sorted(Comparator.comparing(WarpRecord::name))
                .toList();
    }
}
