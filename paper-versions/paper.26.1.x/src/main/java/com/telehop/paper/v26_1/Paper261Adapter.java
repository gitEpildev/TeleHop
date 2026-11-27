package com.telehop.paper.v26_1;

import com.telehop.paper.version.VersionAdapter;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

/**
 * Version adapter for Paper 26.1.x servers.
 * Prefers key-based world resolution. Falls back to name-based lookup
 * for backwards compatibility with existing database records.
 */
public final class Paper261Adapter implements VersionAdapter {

    @Override
    public String getWorldName(World world) {
        return world.getKey().toString();
    }

    @Override
    public World resolveWorld(String name) {
        NamespacedKey key = NamespacedKey.fromString(name);
        World world = key == null ? null : worldByKey(key);
        if (world != null) return world;
        return Bukkit.getWorld(name);
    }
}
