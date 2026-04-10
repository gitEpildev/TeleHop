package com.telehop.paper;

import com.telehop.common.model.WarpRecord;
import com.telehop.paper.config.PaperSettings;
import com.telehop.paper.service.ServiceRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Plugin entry point. Lifecycle only — all wiring lives in {@link Bootstrap},
 * all services in {@link ServiceRegistry}.
 */
public class NetworkPaperPlugin extends JavaPlugin {
    private ServiceRegistry services;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        services = Bootstrap.init(this);
    }

    @Override
    public void onDisable() {
        Bootstrap.shutdown(services);
    }

    // ── public API used by commands, listeners, and the packet handler ──

    public ServiceRegistry services() { return services; }

    public PaperSettings settings()                        { return services.settings(); }
    public com.telehop.paper.service.MessageService messageService() { return services.messageService(); }
    public com.telehop.paper.service.PermissionService permissionService() { return services.permissionService(); }
    public com.telehop.paper.service.AuditLogger auditLogger()   { return services.auditLogger(); }
    public com.telehop.common.service.PlayerService playerService() { return services.playerService(); }
    public com.telehop.common.service.WarpService warpService()     { return services.warpService(); }
    public com.telehop.common.service.PlayerWarpService playerWarpService() { return services.playerWarpService(); }
    public com.telehop.common.service.TpaService tpaService()       { return services.tpaService(); }
    public com.telehop.paper.service.RtpManager rtpManager()         { return services.rtpManager(); }
    public com.telehop.paper.service.TpaRuntimeManager tpaRuntimeManager() { return services.tpaRuntimeManager(); }
    public com.telehop.paper.messaging.PaperMessagingManager messaging() { return services.messaging(); }

    public Component msg(String key) {
        return services.messageService().format(key);
    }

    public Component msg(String key, Map<String, String> replacements) {
        return services.messageService().format(key, replacements);
    }

    public Component mm(String mini) {
        return miniMessage.deserialize(mini);
    }

    public boolean isFeatureEnabled(String feature) {
        return services.settings().isFeatureEnabled(feature);
    }

    public void reload() {
        Bootstrap.reload(this, services);
    }

    public Optional<String> regionServer(String region) {
        String mapped = services.settings().servers().get(region.toLowerCase());
        return mapped != null ? Optional.of(mapped) : Optional.empty();
    }

    public List<String> listWarpNames() {
        return services.warpService().listCached().stream().map(WarpRecord::name).toList();
    }

    public List<String> listNetworkPlayers() {
        return services.networkPlayerNameCache().list();
    }

    // ── teleport helpers ──────────────────────────────────────────────

    public void executeLocalRtp(Player player, String region, String dimension) {
        String worldName = resolveRtpWorldName(dimension);
        int maxRadius = getConfig().getInt("rtp.max-radius", 25000);
        int radius = Math.min(getConfig().getInt("rtp.regions." + region + ".radius", 25000), maxRadius);
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage(msg("rtp-failed"));
            services.auditLogger().log("rtp-failed-world player=" + player.getName()
                    + " region=" + region + " dimension=" + dimension + " worldName=" + worldName);
            return;
        }
        services.auditLogger().log("rtp-start player=" + player.getName()
                + " region=" + region + " dimension=" + dimension
                + " world=" + world.getName() + " radius=" + radius);
        player.sendMessage(msg("rtp-searching"));
        services.rtpManager().findSafeLocation(world, radius).thenAccept(location -> {
            Bukkit.getScheduler().runTask(this, () -> {
                if (location == null) {
                    player.sendMessage(msg("rtp-failed"));
                    services.auditLogger().log("rtp-failed-safe-spot player=" + player.getName()
                            + " world=" + world.getName());
                    return;
                }
                player.sendMessage(msg("rtp-teleporting"));
                services.auditLogger().log("rtp-teleport player=" + player.getName()
                        + " world=" + world.getName()
                        + " x=" + location.getBlockX()
                        + " y=" + location.getBlockY()
                        + " z=" + location.getBlockZ());
                services.rtpManager().teleport(player, location);
            });
        });
    }

    public void executePendingTeleport(Player player) {
        services.teleportService().executePendingTeleport(player);
    }

    public void clearPendingTeleport(UUID playerUuid) {
        services.teleportService().clearPending(playerUuid);
    }

    public Location getSpawnLocation() {
        return services.teleportService().getSpawnLocation();
    }

    public void teleportToWarp(Player player, WarpRecord warp) {
        services.teleportService().teleportToWarp(player, warp);
    }

    public void teleportToSpawn(Player player) {
        services.teleportService().teleportToSpawn(player);
    }

    // ── private helpers ─────────────────────────────────────────────

    private String resolveRtpWorldName(String dimension) {
        String key = "rtp.dimensions." + dimension;
        String configured = getConfig().getString(key);
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
