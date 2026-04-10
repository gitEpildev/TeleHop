package com.telehop.common.service;

import com.telehop.common.db.DatabaseManager;
import com.telehop.common.db.PlayerWarpRepository;
import com.telehop.common.model.PlayerWarpRecord;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class PlayerWarpService {
    private final DatabaseManager databaseManager;
    private final PlayerWarpRepository repository;

    public PlayerWarpService(DatabaseManager databaseManager, PlayerWarpRepository repository) {
        this.databaseManager = databaseManager;
        this.repository = repository;
    }

    public CompletableFuture<Void> upsert(PlayerWarpRecord warp) {
        return databaseManager.runAsync(() -> repository.upsert(warp));
    }

    public CompletableFuture<Optional<PlayerWarpRecord>> find(String ownerUuid, String name) {
        return databaseManager.supplyAsync(() -> repository.find(ownerUuid, name));
    }

    public CompletableFuture<Optional<PlayerWarpRecord>> findPublic(String ownerUuid, String name) {
        return databaseManager.supplyAsync(() -> repository.findPublic(ownerUuid, name));
    }

    public CompletableFuture<Void> delete(String ownerUuid, String name) {
        return databaseManager.runAsync(() -> repository.delete(ownerUuid, name));
    }

    public CompletableFuture<List<PlayerWarpRecord>> listByOwner(String ownerUuid) {
        return databaseManager.supplyAsync(() -> repository.listByOwner(ownerUuid));
    }

    public CompletableFuture<Integer> countByOwner(String ownerUuid) {
        return databaseManager.supplyAsync(() -> repository.countByOwner(ownerUuid));
    }

    public CompletableFuture<List<PlayerWarpRecord>> listAll() {
        return databaseManager.supplyAsync(repository::listAll);
    }

    public CompletableFuture<Void> setPublic(String ownerUuid, String name, boolean isPublic) {
        return databaseManager.runAsync(() -> repository.setPublic(ownerUuid, name, isPublic));
    }
}
