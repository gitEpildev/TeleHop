package com.telehop.common.model;

import java.util.Map;

/** One atomic database snapshot of an operator's backend spawn configuration. */
public record BackendSpawns(String server, SpawnPoint defaultSpawn, boolean routingEnabled, boolean hubCommand,
                            Map<String, SpawnPoint> destinations, Map<String, SpawnRoute> routes) {
    public BackendSpawns {
        if (server == null || server.isBlank() || server.length() > 64) throw new IllegalArgumentException("Invalid server name");
        destinations = Map.copyOf(destinations);
        routes = Map.copyOf(routes);
    }
}
