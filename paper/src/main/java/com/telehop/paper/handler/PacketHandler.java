package com.telehop.paper.handler;

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
    public void handle(NetworkPacket packet) {
        switch (packet.getType()) {
            case EXECUTE_POST_JOIN_TELEPORT -> handlePostJoinTeleport(packet);
            case WARP_TELEPORT              -> handleWarpTeleport(packet);
            case SPAWN_REQUEST              -> handleSpawnRequest(packet);
            case TPA_CREATE                 -> handleTpaCreate(packet);
            case TPA_DENY                   -> handleTpaDeny(packet);
            case TPA_CANCEL                 -> handleTpaCancel(packet);
            case RTP_REQUEST                -> handleRtpRequest(packet);
            case TELEPORT_TO_PLAYER         -> handleTeleportToPlayer(packet);
            case PWARP_TELEPORT             -> handlePwarpTeleport(packet);
            case HOME_TELEPORT              -> handleHomeTeleport(packet);
            case BACK_TELEPORT              -> handleBackTeleport(packet);
            case TPA_TOGGLE_DENY            -> handleTpaToggleDeny(packet);
            case PLAYER_LIST_RESPONSE       -> handlePlayerListResponse(packet);
            default -> {}
        }
    }

    private void handlePostJoinTeleport(NetworkPacket packet) {
        UUID playerId = UUID.fromString(packet.get("uuid"));
        String worldName = packet.get("world");
        World world = Bukkit.getWorld(worldName);
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
                    .thenAccept(warp -> warp.ifPresent(w -> services.teleportService().teleportToWarp(player, w)));
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
                target.sendMessage(plugin.msg(type == TpaType.TPA ? "request-received" : "request-received-here",
                        Map.of("sender", packet.get("senderName"))));
                target.sendMessage(services.messageService().raw("request-actions"));
            }
        }
    }

    private void handleTpaDeny(NetworkPacket packet) {
        Player sender = Bukkit.getPlayer(UUID.fromString(packet.get("senderUuid")));
        if (sender != null) {
            sender.sendMessage(plugin.msg("request-denied"));
        }
    }

    private void handleTpaCancel(NetworkPacket packet) {
        Player target = Bukkit.getPlayer(UUID.fromString(packet.get("targetUuid")));
        if (target != null) {
            target.sendMessage(plugin.msg("request-cancelled"));
        }
    }

    private void handleRtpRequest(NetworkPacket packet) {
        Player player = Bukkit.getPlayer(UUID.fromString(packet.get("uuid")));
        if (player != null) {
            services.teleportService().executeLocalRtp(player,
                    packet.getOrDefault("region", "default"),
                    packet.getOrDefault("dimension", "overworld"));
        }
    }

    private void handleTeleportToPlayer(NetworkPacket packet) {
        Player actor = Bukkit.getPlayer(UUID.fromString(packet.get("actorUuid")));
        Player target = Bukkit.getPlayer(UUID.fromString(packet.get("targetUuid")));
        if (actor != null && target != null) {
            actor.teleportAsync(target.getLocation());
        }
    }

    private void handlePwarpTeleport(NetworkPacket packet) {
        Player player = Bukkit.getPlayer(UUID.fromString(packet.get("uuid")));
        if (player == null) return;

        String ownerUuid = packet.get("pwarpOwner");
        String pwarpName = packet.get("pwarpName");
        services.playerWarpService().find(ownerUuid, pwarpName).thenAccept(opt -> opt.ifPresent(warp -> {
            World world = Bukkit.getWorld(warp.world());
            if (world != null) {
                Location loc = new Location(world, warp.x(), warp.y(), warp.z(), warp.yaw(), warp.pitch());
                Bukkit.getScheduler().runTask(plugin, () -> player.teleportAsync(loc));
            }
        }));
    }

    private void handleHomeTeleport(NetworkPacket packet) {
        Player player = Bukkit.getPlayer(UUID.fromString(packet.get("uuid")));
        if (player == null) return;
        int slot = Integer.parseInt(packet.get("homeSlot"));
        String homeUuid = packet.get("homeUuid");
        services.homeService().find(homeUuid, slot).thenAccept(opt -> opt.ifPresent(home -> {
            World world = Bukkit.getWorld(home.world());
            if (world != null) {
                Location loc = new Location(world, home.x(), home.y(), home.z(), home.yaw(), home.pitch());
                Bukkit.getScheduler().runTask(plugin, () ->
                        services.teleportService().teleportToHome(player, loc));
            }
        }));
    }

    private void handleBackTeleport(NetworkPacket packet) {
        Player player = Bukkit.getPlayer(UUID.fromString(packet.get("uuid")));
        if (player == null) return;
        World world = Bukkit.getWorld(packet.get("world"));
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
            sender.sendMessage(plugin.msg("player-tpa-disabled", Map.of("target", targetName)));
        }
    }

    private void handlePlayerListResponse(NetworkPacket packet) {
        String csv = packet.getOrDefault("names", "");
        List<String> names = csv.isBlank() ? List.of() : List.of(csv.split(","));
        services.networkPlayerNameCache().replace(
                names.stream().map(String::trim).filter(s -> !s.isBlank()).toList());
    }
}
