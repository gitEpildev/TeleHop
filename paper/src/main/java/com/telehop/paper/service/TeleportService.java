package com.telehop.paper.service;

import com.telehop.common.model.WarpRecord;
import com.telehop.paper.config.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Optional;
import java.util.UUID;

/**
 * Single point of truth for all teleport operations -- spawn, warps,
 * pending cross-server teleports, homes, back, and local RTP execution.
 * Also stores the player's previous location for /back.
 */
public final class TeleportService {
    private final JavaPlugin plugin;
    private final PendingTeleportManager pendingManager;
    private final StorageManager storageManager;
    private MessageService messageService;
    private AuditLogger auditLogger;
    private RtpManager rtpManager;
    private TeleportEffectPlayer effectPlayer;
    private BackLocationManager backManager;
    private String serverName;

    public TeleportService(JavaPlugin plugin, PendingTeleportManager pendingManager, StorageManager storageManager) {
        this.plugin = plugin;
        this.pendingManager = pendingManager;
        this.storageManager = storageManager;
    }

    public void wire(MessageService messageService, AuditLogger auditLogger,
                     RtpManager rtpManager, TeleportEffectPlayer effectPlayer,
                     BackLocationManager backManager, String serverName) {
        this.messageService = messageService;
        this.auditLogger = auditLogger;
        this.rtpManager = rtpManager;
        this.effectPlayer = effectPlayer;
        this.backManager = backManager;
        this.serverName = serverName;
    }

    public void teleportToSpawn(Player player) {
        Location loc = storageManager.getSpawnLocation();
        if (loc != null) {
            doTeleport(player, loc, "spawn");
        }
    }

    public Location getSpawnLocation() {
        return storageManager.getSpawnLocation();
    }

    public void teleportToWarp(Player player, WarpRecord warp) {
        World world = Bukkit.getWorld(warp.world());
        if (world == null) return;
        Location target = new Location(world, warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch());
        doTeleport(player, target, "warp");
    }

    public void teleportToHome(Player player, Location target) {
        doTeleport(player, target, "home");
    }

    public void teleportBack(Player player, Location target) {
        doTeleport(player, target, "back");
    }

    public void teleportTpa(Player player, Location target) {
        doTeleport(player, target, "tpa");
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

    public void executeLocalRtp(Player player, String region, String dimension) {
        String worldName = resolveRtpWorldName(dimension);

        File rtpFile = new File(plugin.getDataFolder(), "config/rtp.yml");
        FileConfiguration rtpCfg = rtpFile.exists()
                ? YamlConfiguration.loadConfiguration(rtpFile)
                : plugin.getConfig();

        int maxRadius = rtpCfg.getInt("rtp.max-radius", 25000);
        int radius = Math.min(rtpCfg.getInt("rtp.regions." + region + ".radius", 25000), maxRadius);
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
                doTeleport(player, location, "rtp");
            });
        });
    }

    /**
     * Core teleport with back-location save and effect playback.
     */
    private void doTeleport(Player player, Location target, String type) {
        Location from = player.getLocation();
        if (backManager != null && serverName != null) {
            backManager.saveLastTeleport(player.getUniqueId(), from, serverName);
        }
        player.teleportAsync(target).thenAccept(success -> {
            if (success && effectPlayer != null) {
                Bukkit.getScheduler().runTask(plugin, () -> effectPlayer.play(player, from, target, type));
            }
        });
    }

    private String resolveRtpWorldName(String dimension) {
        File rtpFile = new File(plugin.getDataFolder(), "config/rtp.yml");
        FileConfiguration rtpCfg = rtpFile.exists()
                ? YamlConfiguration.loadConfiguration(rtpFile)
                : plugin.getConfig();

        String key = "rtp.dimensions." + dimension;
        String configured = rtpCfg.getString(key);
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
