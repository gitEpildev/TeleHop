package com.telehop.paper.handler;

import com.telehop.paper.platform.PlatformText;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.common.model.TpaRequestRecord;
import com.telehop.common.model.TpaType;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.service.ServiceRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles every inbound cross-server packet dispatched by {@link com.telehop.paper.messaging.PaperMessagingManager}.
 * Keeps packet-processing logic out of the plugin main class.
 */
public final class PacketHandler implements com.telehop.paper.messaging.PaperMessagingManager.PacketHandler {
    private final NetworkPaperPlugin plugin;
    private final ServiceRegistry services;

    public PacketHandler(NetworkPaperPlugin plugin, ServiceRegistry services) {
        this.plugin = plugin;
        this.services = services;
    }

    @Override
    public java.util.concurrent.CompletableFuture<Boolean> handleVariant(NetworkPacket packet, Player carrier) {
        return com.telehop.paper.service.VariantTravel.arrive(plugin, carrier, packet);
    }

    @Override
    public void handle(NetworkPacket packet, Player carrier) {
        switch (packet.getType()) {
            case EXECUTE_POST_JOIN_TELEPORT -> handlePostJoinTeleport(packet);
            case WARP_TELEPORT              -> handleWarpTeleport(packet);
            case NAMED_SPAWN_ARRIVAL         -> services.spawns().arrive(carrier, packet);
            case SPAWN_REQUEST              -> handleSpawnRequest(packet);
            case TPA_CREATE                 -> handleTpaCreate(packet);
            case TPA_DENY                   -> handleTpaDeny(packet);
            case TPA_CANCEL                 -> handleTpaCancel(packet);
            case RTP_REQUEST                -> handleRtpRequest(packet);
            case TELEPORT_TO_PLAYER         -> handleTeleportToPlayer(packet);
            case ADMIN_TP_TO_COORDS         -> handleAdminTpToCoords(packet);
            case PWARP_TELEPORT             -> handlePwarpTeleport(packet);
            case HOME_TELEPORT              -> handleHomeTeleport(packet);
            case BACK_TELEPORT              -> handleBackTeleport(packet);
            case TPA_TOGGLE_DENY            -> handleTpaToggleDeny(packet);
            case PLAYER_LIST_RESPONSE       -> handlePlayerListResponse(packet);
            case SERVER_PING_UPDATE         -> handleServerPingUpdate(packet);
            default -> {}
        }
    }

    private void handleServerPingUpdate(NetworkPacket packet) {
        String encoded = packet.getOrDefault("rtts", "");
        if (encoded.isBlank()) return;
        Map<String, Long> rtts = new java.util.HashMap<>();
        for (String pair : encoded.split(",")) {
            int idx = pair.indexOf('=');
            if (idx <= 0) continue;
            try {
                rtts.put(pair.substring(0, idx), Long.parseLong(pair.substring(idx + 1)));
            } catch (NumberFormatException ignored) {
            }
        }
        long measuredAt;
        try {
            measuredAt = Long.parseLong(packet.getOrDefault("measuredAt", "0"));
        } catch (NumberFormatException e) {
            measuredAt = System.currentTimeMillis();
        }
        services.pingService().update(rtts, measuredAt);
    }

    private void handlePostJoinTeleport(NetworkPacket packet) {
        UUID playerId = UUID.fromString(packet.get("uuid"));
        String worldName = packet.get("world");
        World world = plugin.versionAdapter().resolveWorld(worldName);
        if (world == null) return;

        Location location = new Location(
                world,
                Double.parseDouble(packet.get("x")),
                Double.parseDouble(packet.get("y")),
                Double.parseDouble(packet.get("z")),
                Float.parseFloat(packet.getOrDefault("yaw", "0")),
                Float.parseFloat(packet.getOrDefault("pitch", "0"))
        );
        services.pendingTeleportManager().setPending(playerId, location);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            services.teleportService().executePendingTeleport(player);
        }
    }

    private void handleWarpTeleport(NetworkPacket packet) {
        String warpName = packet.get("warp");
        Player player = Bukkit.getPlayer(UUID.fromString(packet.get("uuid")));
        if (player != null) {
            services.warpService().find(warpName)
                    .thenAccept(warp -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline() && plugin.isFeatureEnabled("warps") && plugin.permissionService().warp(player, warpName)) {
                            warp.filter(w -> w.server().equalsIgnoreCase(plugin.settings().serverName())
                                    && plugin.permissionService().warp(player, w, plugin.settings().serverName()))
                                    .ifPresent(w -> services.teleportService().teleportToWarp(player, w));
                        }
                    }));
        }
    }

    private void handleSpawnRequest(NetworkPacket packet) {
        Player player = Bukkit.getPlayer(UUID.fromString(packet.get("uuid")));
        if (player != null) {
            services.teleportService().teleportToSpawn(player);
        }
    }

    private void handleTpaCreate(NetworkPacket packet) {
        UUID senderUuid = UUID.fromString(packet.get("senderUuid"));
        UUID targetUuid = UUID.fromString(packet.get("targetUuid"));
        TpaType type = TpaType.valueOf(packet.get("type"));
        Instant sentAt = Instant.ofEpochMilli(Long.parseLong(packet.get("sentAt")));

        if (!packet.getOriginServer().equalsIgnoreCase(services.settings().serverName())) {
            Player target = Bukkit.getPlayer(targetUuid);
            if (target != null && services.tpaRuntimeManager().isTpaDisabled(targetUuid)) {
                NetworkPacket deny = NetworkPacket.request(
                        PacketType.TPA_TOGGLE_DENY, services.settings().serverName(), "velocity");
                deny.put("senderUuid", senderUuid.toString());
                deny.put("senderTargetName", target.getName());
                services.messaging().send(deny);
                return;
            }
        }

        TpaRequestRecord request = new TpaRequestRecord(senderUuid, targetUuid, type, sentAt);
        services.tpaRuntimeManager().setIncoming(request);
        services.tpaService().upsert(request);

        if (!packet.getOriginServer().equalsIgnoreCase(services.settings().serverName())) {
            Player target = Bukkit.getPlayer(targetUuid);
            if (target != null) {
                PlatformText.send(target, plugin.msg(type == TpaType.TPA ? "request-received" : "request-received-here",
                        Map.of("sender", packet.get("senderName"))));
                PlatformText.send(target, services.messageService().raw("request-actions"));
            }
        }
    }

    private void handleTpaDeny(NetworkPacket packet) {
        Player sender = Bukkit.getPlayer(UUID.fromString(packet.get("senderUuid")));
        if (sender != null) {
            PlatformText.send(sender, plugin.msg("request-denied"));
        }
    }

    private void handleTpaCancel(NetworkPacket packet) {
        Player target = Bukkit.getPlayer(UUID.fromString(packet.get("targetUuid")));
        if (target != null) {
            PlatformText.send(target, plugin.msg("request-cancelled"));
        }
    }

    private void handleRtpRequest(NetworkPacket packet) {
        Player player = Bukkit.getPlayer(UUID.fromString(packet.get("uuid")));
        if (player != null) {
            new com.telehop.paper.service.RtpTravel(plugin).arrive(player, null,
                    packet.get("region"), packet.get("dimension"));
        }
    }

    private void handleTeleportToPlayer(NetworkPacket packet) {
        Player actor = Bukkit.getPlayer(UUID.fromString(packet.get("actorUuid")));
        Player target = Bukkit.getPlayer(UUID.fromString(packet.get("targetUuid")));
        if (actor != null && target != null) {
            plugin.platform().teleport(actor, target.getLocation());
        }
    }

    private void handleAdminTpToCoords(NetworkPacket packet) {
        Player target = Bukkit.getPlayer(UUID.fromString(packet.get("targetUuid")));
        if (target == null) return;
        World world = plugin.versionAdapter().resolveWorld(packet.get("world"));
        if (world == null) world = target.getWorld();
        Location dest = new Location(world,
                Double.parseDouble(packet.get("x")),
                Double.parseDouble(packet.get("y")),
                Double.parseDouble(packet.get("z")));
        plugin.platform().teleport(target, dest);
    }

    private void handlePwarpTeleport(NetworkPacket packet) {
        Player player = Bukkit.getPlayer(UUID.fromString(packet.get("uuid")));
        if (player == null) return;

        String ownerUuid = packet.get("pwarpOwner");
        String pwarpName = packet.get("pwarpName");
        services.playerWarpService().find(ownerUuid, pwarpName).thenAccept(opt -> Bukkit.getScheduler().runTask(plugin, () -> opt.ifPresent(warp -> {
            if (!player.isOnline()) return;
            World world = plugin.versionAdapter().resolveWorld(warp.world());
            if (world != null) {
                Location loc = new Location(world, warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch());
                plugin.platform().teleport(player, loc);
            }
        })));
    }

    private void handleHomeTeleport(NetworkPacket packet) {
        Player player = Bukkit.getPlayer(UUID.fromString(packet.get("uuid")));
        if (player == null) return;
        String homeName = packet.get("homeName");
        String homeUuid = packet.get("homeUuid");
        services.homeService().find(homeUuid, homeName).thenAccept(found -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || found.isEmpty()) return;
            if ("true".equals(packet.get("staffVisit")) && !player.hasPermission(
                    com.telehop.common.PermissionNodes.ADMIN_HOMES_VISIT)) {
                PlatformText.send(player, plugin.msg("no-permission"));
                return;
            }
            var home = found.get();
            World world = plugin.versionAdapter().resolveWorld(home.world());
            if (world == null || !home.server().equalsIgnoreCase(plugin.settings().serverName())) return;
            services.teleportService().teleportToHome(player,
                    new Location(world, home.x(), home.y(), home.z(), home.yaw(), home.pitch()));
        }));
    }

    private void handleBackTeleport(NetworkPacket packet) {
        Player player = Bukkit.getPlayer(UUID.fromString(packet.get("uuid")));
        if (player == null) return;
        World world = plugin.versionAdapter().resolveWorld(packet.get("world"));
        if (world == null) return;
        Location loc = new Location(world,
                Double.parseDouble(packet.get("x")), Double.parseDouble(packet.get("y")),
                Double.parseDouble(packet.get("z")),
                Float.parseFloat(packet.getOrDefault("yaw", "0")),
                Float.parseFloat(packet.getOrDefault("pitch", "0")));
        services.teleportService().teleportBack(player, loc);
    }

    private void handleTpaToggleDeny(NetworkPacket packet) {
        Player sender = Bukkit.getPlayer(UUID.fromString(packet.get("senderUuid")));
        if (sender != null) {
            String targetName = packet.getOrDefault("senderTargetName", "Unknown");
            PlatformText.send(sender, plugin.msg("player-tpa-disabled", Map.of("target", targetName)));
        }
    }

    private void handlePlayerListResponse(NetworkPacket packet) {
        String csv = packet.getOrDefault("names", "");
        List<String> names = csv.isBlank() ? List.of() : List.of(csv.split(","));
        services.networkPlayerNameCache().replace(
                names.stream().map(String::trim).filter(s -> !s.isBlank()).toList());
    }
}
