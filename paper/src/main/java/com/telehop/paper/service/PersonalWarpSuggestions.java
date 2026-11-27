package com.telehop.paper.service;

import com.telehop.common.model.PlayerWarpRecord;
import com.telehop.common.service.PlayerWarpService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Refreshes an online player's own tab suggestions without stalling the server thread on JDBC. */
public final class PersonalWarpSuggestions {
    private record Entry(List<String> previous, CompletableFuture<List<String>> latest, long refreshAt) {
        List<String> available() { return latest.getNow(previous); }
    }
    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final PlayerWarpService service;

    public PersonalWarpSuggestions(PlayerWarpService service) { this.service = service; }

    public List<String> list(UUID member) {
        long now = System.nanoTime();
        return entries.compute(member, (id, old) -> {
            if (old != null && (!old.latest().isDone() || now - old.refreshAt() < 0)) return old;
            List<String> previous = old == null ? List.of() : old.available();
            var read = service.listByOwner(id.toString()).thenApply(records -> records.stream().map(PlayerWarpRecord::name).toList())
                    .exceptionally(error -> previous);
            return new Entry(previous, read, now + TimeUnit.SECONDS.toNanos(2));
        }).available();
    }

    public void forget(UUID member) { entries.remove(member); }
}
