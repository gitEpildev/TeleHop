package com.telehop.common.model;

/** The complete location and access policy declared by a backend. */
public record SpawnPoint(String world, double x, double y, double z, float yaw, float pitch, boolean restricted) {
    public SpawnPoint {
        if (world == null || world.isBlank() || world.length() > 255 || !Double.isFinite(x)
                || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Invalid spawn location");
        }
    }
}
