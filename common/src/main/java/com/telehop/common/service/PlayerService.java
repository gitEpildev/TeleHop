package com.telehop.common.service;

import com.telehop.common.db.DatabaseManager;
import com.telehop.common.db.PlayerRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerService {
    private final DatabaseManager databaseManager;
    private final PlayerRepository repository;
    private final PlayerServerCache cache;

    public PlayerService(DatabaseManager databaseManager, PlayerRepository repository, PlayerServerCache cache) {
        this.databaseManager = databaseManager;
        this.repository = repository;
        this.cache = cache;
    }

    public CompletableFuture<Void> updateServer(UUID uuid, String server) {
        cache.put(uuid, server);
        return databaseManager.runAsync(() -> repository.upsert(uuid, server, System.currentTimeMillis()));
    }

    public CompletableFuture<Optional<String>> getServer(UUID uuid) {
        Optional<String> cached = cache.get(uuid);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }
        return databaseManager.supplyAsync(() -> repository.getCurrentServer(uuid))
                .thenApply(server -> {
                    server.ifPresent(s -> cache.put(uuid, s));
                    return server;
                });
    }

    public void remove(UUID uuid) {
        cache.remove(uuid);
    }
}
