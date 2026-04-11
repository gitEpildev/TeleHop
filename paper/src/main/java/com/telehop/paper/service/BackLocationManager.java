package com.telehop.paper.service;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for /back locations. Cleared on quit, not persisted.
 */
public final class BackLocationManager {

    public record BackEntry(Location location, String server) {}

    private final Map<UUID, BackEntry> lastTeleport = new ConcurrentHashMap<>();
    private final Map<UUID, BackEntry> lastDeath = new ConcurrentHashMap<>();

    public void saveLastTeleport(UUID uuid, Location location, String server) {
        lastTeleport.put(uuid, new BackEntry(location, server));
    }

    public void saveLastDeath(UUID uuid, Location location, String server) {
        lastDeath.put(uuid, new BackEntry(location, server));
    }

    public BackEntry getLastTeleport(UUID uuid) {
        return lastTeleport.get(uuid);
    }

    public BackEntry getLastDeath(UUID uuid) {
        return lastDeath.get(uuid);
    }

    public void remove(UUID uuid) {
        lastTeleport.remove(uuid);
        lastDeath.remove(uuid);
    }
}
