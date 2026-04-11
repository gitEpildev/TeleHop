package com.telehop.common.model;

/**
 * Immutable snapshot of a player home stored in the {@code homes} table.
 *
 * @param uuid   owning player's UUID string
 * @param slot   home slot number (1-based, up to {@code max-slots})
 * @param server server name where the home is located
 * @param world  Bukkit world name
 */
public record HomeRecord(
        String uuid,
        int slot,
        String server,
        String world,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {}
