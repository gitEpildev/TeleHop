package com.telehop.common.model;

/**
 * Immutable snapshot of a player's last known location before disconnecting.
 * Stored in the {@code last_locations} table, keyed by UUID.
 *
 * @param uuid   player's UUID string
 * @param server server name where the player logged out
 * @param world  Bukkit world name
 */
public record LastLocationRecord(
        String uuid,
        String server,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {}
