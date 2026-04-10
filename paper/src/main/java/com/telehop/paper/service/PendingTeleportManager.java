package com.telehop.paper.service;

import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PendingTeleportManager {
    private final Map<UUID, Location> pendingLocations = new ConcurrentHashMap<>();

    public void setPending(UUID playerUuid, Location location) {
        pendingLocations.put(playerUuid, location);
    }

    public Location take(UUID playerUuid) {
        return pendingLocations.remove(playerUuid);
    }
}
