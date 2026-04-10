package com.telehop.common.service;

import com.telehop.common.db.DatabaseManager;
import com.telehop.common.db.WarpRepository;
import com.telehop.common.model.WarpRecord;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class WarpService {
    private final DatabaseManager databaseManager;
    private final WarpRepository repository;
    private final WarpCache cache;

    public WarpService(DatabaseManager databaseManager, WarpRepository repository, WarpCache cache) {
        this.databaseManager = databaseManager;
        this.repository = repository;
        this.cache = cache;
    }

    public CompletableFuture<Void> refreshCache() {
        return databaseManager.supplyAsync(repository::listAll).thenAccept(cache::replaceAll);
    }

    public CompletableFuture<Void> upsert(WarpRecord warp) {
        cache.put(warp);
        return databaseManager.runAsync(() -> repository.upsert(warp));
    }

    public CompletableFuture<Optional<WarpRecord>> find(String name) {
        Optional<WarpRecord> cached = cache.get(name);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }
        return databaseManager.supplyAsync(() -> repository.findByName(name))
                .thenApply(warp -> {
                    warp.ifPresent(cache::put);
                    return warp;
                });
    }

    public CompletableFuture<Void> delete(String name) {
        cache.remove(name);
        return databaseManager.runAsync(() -> repository.delete(name));
    }

    public List<WarpRecord> listCached() {
        return cache.list();
    }
}
