package com.telehop.common.model;

/** Final target of a backend or world route. An absent ID selects the backend's default spawn. */
public record SpawnRoute(String server, String id) {
    public SpawnRoute {
        if (server == null || server.isBlank() || server.length() > 64) throw new IllegalArgumentException("Invalid server");
        if (id != null) id = SpawnEntry.normalise(id);
    }
}
