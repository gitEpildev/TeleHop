package com.telehop.paper.version;

import org.bukkit.World;

/**
 * Abstracts Paper API differences between Minecraft versions so that
 * the shared paper code compiles against a single target but runs
 * correctly on any supported server (1.21.x through 26.1.x).
 */
public interface VersionAdapter {

    /**
     * Returns the world name suitable for storage and cross-server packets.
     */
    String getWorldName(World world);

    /**
     * Resolves a world by name or key, returning {@code null} if not found.
     */
    World resolveWorld(String name);
}
