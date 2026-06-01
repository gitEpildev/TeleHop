package com.telehop.common.model;

/**
 * Immutable snapshot of a player home stored in the {@code homes} table.
 *
 * @param uuid   owning player's UUID string
 * @param name   user-chosen home name (case-insensitive lookup, original casing preserved)
 * @param server server name where the home is located
 * @param world  Bukkit world name
 */
public record HomeRecord(
        String uuid,
        String name,
        String server,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {}
