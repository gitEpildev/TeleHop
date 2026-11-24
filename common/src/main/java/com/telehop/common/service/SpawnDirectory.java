package com.telehop.common.service;

import com.telehop.common.db.DatabaseManager;
import com.telehop.common.db.SpawnRepository;
import com.telehop.common.model.SpawnEntry;
import java.util.List;
import java.util.Optional;
import com.telehop.common.model.BackendSpawns;
import java.util.concurrent.CompletableFuture;

/** Asynchronous access to network destination ownership. */
public record SpawnDirectory(DatabaseManager database, SpawnRepository repository) {
    public CompletableFuture<Optional<SpawnEntry>> find(String id) {
        return database.supplyAsync(() -> repository.find(id));
    }

    public CompletableFuture<List<SpawnEntry>> list() {
        return database.supplyAsync(repository::list);
    }

    public CompletableFuture<Void> configure(BackendSpawns backend, SpawnEntry expected) {
        return database.runAsync(() -> repository.configure(backend, expected));
    }
}
