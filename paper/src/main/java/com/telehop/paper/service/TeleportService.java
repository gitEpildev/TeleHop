package com.telehop.paper.service;

import com.telehop.paper.platform.PlatformText;
import com.telehop.common.model.WarpRecord;
import com.telehop.paper.config.StorageManager;
import com.telehop.paper.version.VersionAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import com.telehop.paper.config.RtpPolicy;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;
import com.telehop.paper.NetworkPaperPlugin;

import java.util.UUID;

/**
 * Single point of truth for all teleport operations -- spawn, warps,
 * pending cross-server teleports, homes, back, and local RTP execution.
 * Also stores the player's previous location for /back.
 */
public final class TeleportService {
    private final NetworkPaperPlugin plugin;
    private final PendingTeleportManager pendingManager;
    private final StorageManager storageManager;
    private final VersionAdapter versionAdapter;
    private MessageService messageService;
    private AuditLogger auditLogger;
    private RtpManager rtpManager;
    private TeleportEffectPlayer effectPlayer;
    private BackLocationManager backManager;
    private String serverName;

    public TeleportService(NetworkPaperPlugin plugin, PendingTeleportManager pendingManager,
                           StorageManager storageManager, VersionAdapter versionAdapter) {
        this.plugin = plugin;
        this.pendingManager = pendingManager;
        this.storageManager = storageManager;
        this.versionAdapter = versionAdapter;
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

    /** Apply the validated language catalogue to future teleport messages on the server thread. */
    public void setMessageService(MessageService messages) { this.messageService = messages; }

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
        World world = versionAdapter.resolveWorld(warp.world());
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
            plugin.platform().teleport(player, location);
        }
    }

    public void clearPending(UUID playerUuid) {
        pendingManager.take(playerUuid);
    }

    /** Resolve safe ground on this backend and acknowledge only a completed teleport. */
    public CompletableFuture<Boolean> executeLocalRtp(Player player, RtpPolicy.Choice choice, BooleanSupplier authorised) {
        World world = versionAdapter.resolveWorld(choice.world());
        if (world == null || !authorised.getAsBoolean()) {
            PlatformText.send(player, messageService.format("rtp-failed"));
            return CompletableFuture.completedFuture(false);
        }
        auditLogger.log("rtp-start player=" + player.getName() + " region=" + choice.region() + " dimension=" + choice.dimension()
                + " world=" + versionAdapter.getWorldName(world) + " radius=" + choice.radius());
        PlatformText.send(player, messageService.format("rtp-searching"));
        return rtpManager.findSafeLocation(world, choice.radius()).thenComposeAsync(location -> {
            if (!player.isOnline()) return CompletableFuture.completedFuture(false);
            if (location == null || !authorised.getAsBoolean()) {
                PlatformText.send(player, messageService.format("rtp-failed"));
                return CompletableFuture.completedFuture(false);
            }
            PlatformText.send(player, messageService.format("rtp-teleporting"));
            return doTeleport(player, location, "rtp");
        }, task -> Bukkit.getScheduler().runTask(plugin, task));
    }

    /**
     * Core teleport with back-location save and effect playback.
     */
    public java.util.concurrent.CompletableFuture<Boolean> teleportToNamedSpawn(Player player, Location location) {
        return doTeleport(player, location, "spawn");
    }

    private java.util.concurrent.CompletableFuture<Boolean> doTeleport(Player player, Location target, String type) {
        Location from = player.getLocation();
        if (backManager != null && serverName != null) {
            backManager.saveLastTeleport(player.getUniqueId(), from, serverName);
        }
        return plugin.platform().teleport(player, target).thenApply(success -> {
            if (success && effectPlayer != null) {
                Bukkit.getScheduler().runTask(plugin, () -> effectPlayer.play(player, from, target, type));
            }
            return success;
        });
    }

}
