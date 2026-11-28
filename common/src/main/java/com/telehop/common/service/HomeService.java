package com.telehop.common.service;

import com.telehop.common.db.DatabaseManager;
import com.telehop.common.db.HomeRepository;
import com.telehop.common.model.HomeRecord;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Business-logic layer for player homes. All operations are executed
 * asynchronously on the {@link DatabaseManager} thread pool and return
 * {@link CompletableFuture} to keep the server thread free.
 */
public class HomeService {
    private final DatabaseManager databaseManager;
    private final HomeRepository repository;
    private final PlayerNameSuggestions suggestions;

    public PlayerNameSuggestions suggestions() { return suggestions; }

    public HomeService(DatabaseManager databaseManager, HomeRepository repository) {
        this.databaseManager = databaseManager;
        this.repository = repository;
        suggestions = new PlayerNameSuggestions(id -> databaseManager.supplyAsync(() -> repository.names(id)));
    }

    public CompletableFuture<List<HomeRecord>> listByPlayer(String uuid) {
        return databaseManager.supplyAsync(() -> repository.listByPlayer(uuid));
    }

    public CompletableFuture<Optional<HomeRecord>> find(String uuid, String name) {
        return databaseManager.supplyAsync(() -> repository.find(uuid, name));
    }

    public CompletableFuture<Void> upsert(HomeRecord home) {
        return databaseManager.runAsync(() -> repository.upsert(home)).thenRun(() -> suggestions.refresh(home.uuid()));
    }

    /** Creates a home within the member's allowance, optionally updating an existing name. */
    public CompletableFuture<Boolean> saveWithinLimit(HomeRecord home, int allowance, boolean replace) {
        return databaseManager.supplyAsync(() -> repository.saveWithinLimit(home, allowance, replace))
                .thenApply(saved -> {
                    if (saved) suggestions.refresh(home.uuid());
                    return saved;
                });
    }

    public CompletableFuture<Void> delete(String uuid, String name) {
        return databaseManager.runAsync(() -> repository.delete(uuid, name)).thenRun(() -> suggestions.refresh(uuid));
    }

    public CompletableFuture<Integer> countByPlayer(String uuid) {
        return databaseManager.supplyAsync(() -> repository.countByPlayer(uuid));
    }
}
