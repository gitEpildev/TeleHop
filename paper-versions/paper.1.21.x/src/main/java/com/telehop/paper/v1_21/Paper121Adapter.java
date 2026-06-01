package com.telehop.paper.v1_21;

import com.telehop.paper.version.VersionAdapter;
import org.bukkit.Bukkit;
import org.bukkit.World;

/**
 * Version adapter for Paper 1.21.x servers.
 * Uses the standard Bukkit {@link World#getName()} and {@link Bukkit#getWorld(String)} APIs.
 */
public final class Paper121Adapter implements VersionAdapter {

    @Override
    public String getWorldName(World world) {
        return world.getName();
    }

    @Override
    public World resolveWorld(String name) {
        return Bukkit.getWorld(name);
    }
}
