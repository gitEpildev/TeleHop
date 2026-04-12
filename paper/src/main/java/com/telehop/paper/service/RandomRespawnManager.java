package com.telehop.paper.service;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds one pre-computed safe {@link Location} future per player UUID, staged
 * asynchronously at the moment of death and consumed at respawn.
 *
 * <p>Stores a {@link CompletableFuture} rather than a bare {@code Location} so
 * the respawn handler can deal with both the "ready" case (future already done)
 * and the "still searching" case (future pending) without blocking the main thread.
 */
public final class RandomRespawnManager {

    private final Map<UUID, CompletableFuture<Location>> staged = new ConcurrentHashMap<>();

    /**
     * Stores a pending safe-location search for the given player.
     * Replaces any previously staged future for the same UUID.
     */
    public void stage(UUID playerId, CompletableFuture<Location> future) {
        staged.put(playerId, future);
    }

    /**
     * Retrieves and removes the staged future for the given player.
     *
     * @return the staged future, or {@code null} if none exists
     */
    public CompletableFuture<Location> consume(UUID playerId) {
        return staged.remove(playerId);
    }

    /**
     * Discards any staged future for the given player without consuming it.
     * Called on disconnect to prevent memory leaks.
     */
    public void clear(UUID playerId) {
        staged.remove(playerId);
    }
}
