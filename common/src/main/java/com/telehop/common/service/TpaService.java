package com.telehop.common.service;

import com.telehop.common.db.DatabaseManager;
import com.telehop.common.db.TpaRepository;
import com.telehop.common.model.TpaRequestRecord;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class TpaService {
    private final DatabaseManager databaseManager;
    private final TpaRepository repository;
    private final TpaRequestCache cache;

    public TpaService(DatabaseManager databaseManager, TpaRepository repository, TpaRequestCache cache) {
        this.databaseManager = databaseManager;
        this.repository = repository;
        this.cache = cache;
    }

    public CompletableFuture<Void> upsert(TpaRequestRecord request) {
        cache.put(request);
        return databaseManager.runAsync(() -> repository.upsert(request));
    }

    public CompletableFuture<Optional<TpaRequestRecord>> find(UUID sender, UUID target) {
        Optional<TpaRequestRecord> cached = cache.get(sender, target);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }
        return databaseManager.supplyAsync(() -> repository.find(sender, target))
                .thenApply(request -> {
                    request.ifPresent(cache::put);
                    return request;
                });
    }

    public CompletableFuture<Void> delete(UUID sender, UUID target) {
        cache.remove(sender, target);
        return databaseManager.runAsync(() -> repository.delete(sender, target));
    }

    /**
     * Finds all requests that have exceeded the given timeout.
     *
     * @param timeoutSeconds the configured TPA timeout in seconds
     */
    public CompletableFuture<List<TpaRequestRecord>> expiredNow(long timeoutSeconds) {
        return databaseManager.supplyAsync(() -> repository.findExpired(timeoutSeconds * 1000L));
    }
}
