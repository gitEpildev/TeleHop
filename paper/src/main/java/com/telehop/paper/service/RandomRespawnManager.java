package com.telehop.paper.service;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds one pre-computed safe {@link Location} per player UUID, staged
 * asynchronously at the moment of death and consumed exactly once at respawn.
 *
 * <p>The one-shot contract (stage → consume) means stale entries never linger:
 * a consumed or cleared location is removed immediately, so the next death
 * cycle starts fresh.
 */
public final class RandomRespawnManager {

    private final Map<UUID, Location> staged = new ConcurrentHashMap<>();

    /**
     * Stores a pre-found safe location for the given player.
     * Replaces any previously staged location for the same UUID.
     */
    public void stage(UUID playerId, Location location) {
        staged.put(playerId, location);
    }

    /**
     * Retrieves and removes the staged location for the given player.
     *
     * @return the staged {@link Location}, or {@code null} if none was ready
     */
    public Location consume(UUID playerId) {
        return staged.remove(playerId);
    }

    /**
     * Discards any staged location for the given player without consuming it.
     * Called on disconnect to prevent memory leaks.
     */
    public void clear(UUID playerId) {
        staged.remove(playerId);
    }

    /** Returns {@code true} if a location has already been staged for this player. */
    public boolean hasStaged(UUID playerId) {
        return staged.containsKey(playerId);
    }
}
