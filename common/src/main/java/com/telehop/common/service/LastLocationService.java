package com.telehop.common.service;

import com.telehop.common.db.DatabaseManager;
import com.telehop.common.db.LastLocationRepository;
import com.telehop.common.model.LastLocationRecord;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Business-logic layer for persistent player logout locations.
 * All operations are executed asynchronously on the {@link DatabaseManager}
 * thread pool.
 */
public class LastLocationService {
    private final DatabaseManager databaseManager;
    private final LastLocationRepository repository;

    public LastLocationService(DatabaseManager databaseManager, LastLocationRepository repository) {
        this.databaseManager = databaseManager;
        this.repository = repository;
    }

    public CompletableFuture<Void> upsert(LastLocationRecord record) {
        return databaseManager.runAsync(() -> repository.upsert(record));
    }

    public CompletableFuture<Optional<LastLocationRecord>> find(String uuid) {
        return databaseManager.supplyAsync(() -> repository.find(uuid));
    }

    public CompletableFuture<Void> delete(String uuid) {
        return databaseManager.runAsync(() -> repository.delete(uuid));
    }
}
