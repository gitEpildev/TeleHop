package com.telehop.common.service;

import java.util.function.Function;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Refreshes an online player's own tab suggestions without stalling the server thread on JDBC. */
public final class PlayerNameSuggestions {
    private record Entry(List<String> previous, CompletableFuture<List<String>> latest, long refreshAt) {
        List<String> available() { return latest.getNow(previous); }
    }
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Function<String, CompletableFuture<List<String>>> loader;

    public PlayerNameSuggestions(Function<String, CompletableFuture<List<String>>> loader) { this.loader = loader; }

    public List<String> list(String member) {
        long now = System.nanoTime();
        return entries.compute(member, (id, old) -> {
            if (old != null && (!old.latest().isDone() || now - old.refreshAt() < 0)) return old;
            List<String> previous = old == null ? List.of() : old.available();
            var read = loader.apply(id)
                    .exceptionally(error -> previous);
            return new Entry(previous, read, now + TimeUnit.SECONDS.toNanos(2));
        }).available();
    }

    /** Refresh an active session after a local write without retaining offline players. */
    public void refresh(String member) {
        entries.computeIfPresent(member, (id, old) -> {
            var previous = old.available();
            return new Entry(previous, loader.apply(id).exceptionally(error -> previous),
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(2));
        });
    }

    public void forget(String member) { entries.remove(member); }
}
