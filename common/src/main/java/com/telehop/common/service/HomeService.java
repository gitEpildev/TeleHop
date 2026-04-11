package com.telehop.common.service;

import com.telehop.common.db.DatabaseManager;
import com.telehop.common.db.HomeRepository;
import com.telehop.common.model.HomeRecord;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class HomeService {
    private final DatabaseManager databaseManager;
    private final HomeRepository repository;

    public HomeService(DatabaseManager databaseManager, HomeRepository repository) {
        this.databaseManager = databaseManager;
        this.repository = repository;
    }

    public CompletableFuture<List<HomeRecord>> listByPlayer(String uuid) {
        return databaseManager.supplyAsync(() -> repository.listByPlayer(uuid));
    }

    public CompletableFuture<Optional<HomeRecord>> find(String uuid, int slot) {
        return databaseManager.supplyAsync(() -> repository.find(uuid, slot));
    }

    public CompletableFuture<Void> upsert(HomeRecord home) {
        return databaseManager.runAsync(() -> repository.upsert(home));
    }

    public CompletableFuture<Void> delete(String uuid, int slot) {
        return databaseManager.runAsync(() -> repository.delete(uuid, slot));
    }
}
