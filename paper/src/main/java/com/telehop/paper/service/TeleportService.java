package com.telehop.paper.service;

import com.telehop.common.model.WarpRecord;
import com.telehop.paper.config.StorageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;

/**
 * Single point of truth for all teleport operations — spawn, warps,
 * pending cross-server teleports, and local RTP execution.
 */
public final class TeleportService {
    private final JavaPlugin plugin;
    private final PendingTeleportManager pendingManager;
    private final StorageManager storageManager;
    private MessageService messageService;
    private AuditLogger auditLogger;
    private RtpManager rtpManager;

    public TeleportService(JavaPlugin plugin, PendingTeleportManager pendingManager, StorageManager storageManager) {
        this.plugin = plugin;
        this.pendingManager = pendingManager;
        this.storageManager = storageManager;
    }

    /** Called once by Bootstrap after all services are created. */
    public void wire(MessageService messageService, AuditLogger auditLogger, RtpManager rtpManager) {
        this.messageService = messageService;
        this.auditLogger = auditLogger;
        this.rtpManager = rtpManager;
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

    /**
     * Executes a local RTP for the given player, resolving the world from the
     * dimension name and reading radius from config.
     */
    public void executeLocalRtp(Player player, String region, String dimension) {
        String worldName = resolveRtpWorldName(dimension);
        int maxRadius = plugin.getConfig().getInt("rtp.max-radius", 25000);
        int radius = Math.min(plugin.getConfig().getInt("rtp.regions." + region + ".radius", 25000), maxRadius);
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(messageService.format("rtp-failed"));
            auditLogger.log("rtp-failed-world player=" + player.getName()
                    + " region=" + region + " dimension=" + dimension + " worldName=" + worldName);
            return;
        }
        auditLogger.log("rtp-start player=" + player.getName()
                + " region=" + region + " dimension=" + dimension
                + " world=" + world.getName() + " radius=" + radius);
        player.sendMessage(messageService.format("rtp-searching"));
        rtpManager.findSafeLocation(world, radius).thenAccept(location -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (location == null) {
                    player.sendMessage(messageService.format("rtp-failed"));
                    auditLogger.log("rtp-failed-safe-spot player=" + player.getName()
                            + " world=" + world.getName());
                    return;
                }
                player.sendMessage(messageService.format("rtp-teleporting"));
                auditLogger.log("rtp-teleport player=" + player.getName()
                        + " world=" + world.getName()
                        + " x=" + location.getBlockX()
                        + " y=" + location.getBlockY()
                        + " z=" + location.getBlockZ());
                rtpManager.teleport(player, location);
            });
        });
    }

    private String resolveRtpWorldName(String dimension) {
        String key = "rtp.dimensions." + dimension;
        String configured = plugin.getConfig().getString(key);
        if (configured != null && Bukkit.getWorld(configured) != null) {
            return configured;
        }
        return switch (dimension.toLowerCase()) {
            case "nether" -> firstWorldByEnvironment(World.Environment.NETHER).orElse("world_nether");
            case "end" -> firstWorldByEnvironment(World.Environment.THE_END).orElse("world_the_end");
            default -> firstWorldByEnvironment(World.Environment.NORMAL).orElse("world");
        };
    }

    private Optional<String> firstWorldByEnvironment(World.Environment environment) {
        return Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == environment)
                .map(World::getName)
                .findFirst();
    }
}
