package com.telehop.paper.service;

import com.telehop.common.model.WarpRecord;
import com.telehop.paper.config.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;

/**
 * Single point of truth for all teleport operations — spawn, warps,
 * and pending cross-server teleports. Removes duplicated location-building
 * code that previously lived directly in the plugin main class.
 */
public final class TeleportService {
    private final JavaPlugin plugin;
    private final PendingTeleportManager pendingManager;
    private final StorageManager storageManager;

    public TeleportService(JavaPlugin plugin, PendingTeleportManager pendingManager, StorageManager storageManager) {
        this.plugin = plugin;
        this.pendingManager = pendingManager;
        this.storageManager = storageManager;
    }

    public void teleportToSpawn(Player player) {
        Location loc = storageManager.getSpawnLocation();
        if (loc != null) {
            player.teleportAsync(loc);
        }
    }

    public Location getSpawnLocation() {
        return storageManager.getSpawnLocation();
    }

    public void teleportToWarp(Player player, WarpRecord warp) {
        World world = Bukkit.getWorld(warp.world());
        if (world == null) return;
        Location target = new Location(world, warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch());
        player.teleportAsync(target);
    }

    public void executePendingTeleport(Player player) {
        Location location = pendingManager.take(player.getUniqueId());
        if (location != null) {
            player.teleportAsync(location);
        }
    }

    public void clearPending(UUID playerUuid) {
        pendingManager.take(playerUuid);
    }
}
