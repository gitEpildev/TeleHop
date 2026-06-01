package com.telehop.velocity.handler;

import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.velocity.messaging.RedisCrossProxyBridge;
import com.telehop.velocity.messaging.VelocityMessagingManager;
import com.telehop.velocity.model.PendingAction;
import com.telehop.velocity.service.VelocityServiceRegistry;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles every inbound cross-server packet on the Velocity proxy.
 *
 * <p>When multi-proxy is enabled, methods fall back to forwarding packets
 * via the Redis bridge when the target player is not on this proxy.
 * A {@code _forwarded} flag prevents infinite ping-pong between proxies.</p>
 */
public final class VelocityPacketHandler implements VelocityMessagingManager.PacketHandler {
    private static final String FORWARDED_KEY = "_forwarded";

    private final Object pluginInstance;
    private final ProxyServer proxy;
    private final Logger logger;
    private final VelocityServiceRegistry services;

    public VelocityPacketHandler(Object pluginInstance, ProxyServer proxy, Logger logger,
                                 VelocityServiceRegistry services) {
        this.pluginInstance = pluginInstance;
        this.proxy = proxy;
        this.logger = logger;
        this.services = services;
    }

    @Override
    public void handle(NetworkPacket packet) {
        switch (packet.getType()) {
            case PLAYER_SERVER_UPDATE -> {
                UUID uuid = UUID.fromString(packet.get("uuid"));
                String server = packet.get("server");
                proxy.getPlayer(uuid).ifPresent(player -> services.playerTracker().update(player, server));
            }
            case TRANSFER_PLAYER      -> routeTransfer(packet);
            case TPA_CREATE            -> routeToTarget(packet, packet.get("targetUuid"));
            case TPA_DENY              -> routeToTarget(packet, packet.get("senderUuid"));
            case TPA_TOGGLE_DENY       -> routeToTarget(packet, packet.get("senderUuid"));
            case TPA_CANCEL            -> {
                String targetUuid = packet.get("targetUuid");
                if (targetUuid != null) routeToTarget(packet, targetUuid);
            }
            case PLAYER_LIST_REQUEST   -> handlePlayerListRequest(packet);
            case TPA_ACCEPT            -> handleTpaAccept(packet);
            case ADMIN_TP_REQUEST      -> handleAdminTeleport(packet);
            case ADMIN_TP_TO_COORDS    -> handleAdminTpToCoords(packet);
            case CROSS_PROXY_PLAYER_LIST -> handleCrossProxyPlayerListRequest(packet);
            default -> {}
        }
    }

    /** Called by the plugin on {@code ServerConnectedEvent} to fire queued post-transfer actions. */
    public void executePendingAction(Player player, String currentServer) {
        PendingAction action = services.pendingActionManager().get(player.getUniqueId());
        if (action == null) return;
        if (!action.targetServer().equalsIgnoreCase(currentServer)) {
            logger.info("Pending action wait for {}: action={}, target={}, current={}",
                    player.getUniqueId(), action.action(), action.targetServer(), currentServer);
            return;
        }
        services.pendingActionManager().remove(player.getUniqueId());
        logger.info("Executing pending action for {}: action={}, server={}",
                player.getUniqueId(), action.action(), currentServer);

        switch (action.action()) {
            case "SPAWN" -> sendWithRetry(currentServer,
                    NetworkPacket.request(PacketType.SPAWN_REQUEST, "velocity", currentServer)
                            .put("uuid", player.getUniqueId().toString()),
                    player.getUniqueId());
            case "WARP" -> sendWithRetry(currentServer,
                    NetworkPacket.request(PacketType.WARP_TELEPORT, "velocity", currentServer)
                            .put("uuid", player.getUniqueId().toString())
                            .put("warp", action.payload().get("warp")),
                    player.getUniqueId());
            case "RTP", "RTP_OVERWORLD", "RTP_NETHER", "RTP_END" -> {
                String dimension = action.payload().getOrDefault("dimension", "overworld");
                if (action.action().startsWith("RTP_")) {
                    String suffix = action.action().substring("RTP_".length()).toLowerCase();
                    if (!suffix.isBlank()) dimension = suffix;
                }
                sendWithRetry(currentServer,
                        NetworkPacket.request(PacketType.RTP_REQUEST, "velocity", currentServer)
                                .put("uuid", player.getUniqueId().toString())
                                .put("region", action.payload().getOrDefault("region", "default"))
                                .put("dimension", dimension),
                        player.getUniqueId());
            }
            case "TELEPORT_TO_PLAYER" -> sendWithRetry(currentServer,
                    NetworkPacket.request(PacketType.TELEPORT_TO_PLAYER, "velocity", currentServer)
                            .put("actorUuid", action.payload().get("actorUuid"))
                            .put("targetUuid", action.payload().get("targetUuid")),
                    player.getUniqueId());
            case "PWARP" -> sendWithRetry(currentServer,
                    NetworkPacket.request(PacketType.PWARP_TELEPORT, "velocity", currentServer)
                            .put("uuid", player.getUniqueId().toString())
                            .put("pwarpOwner", action.payload().get("pwarpOwner"))
                            .put("pwarpName", action.payload().get("pwarpName")),
                    player.getUniqueId());
            case "HOME" -> sendWithRetry(currentServer,
                    NetworkPacket.request(PacketType.HOME_TELEPORT, "velocity", currentServer)
                            .put("uuid", player.getUniqueId().toString())
                            .put("homeName", action.payload().get("homeName"))
                            .put("homeUuid", action.payload().get("homeUuid")),
                    player.getUniqueId());
            case "LASTLOC" -> sendWithRetry(currentServer,
                    NetworkPacket.request(PacketType.EXECUTE_POST_JOIN_TELEPORT, "velocity", currentServer)
                            .put("uuid", player.getUniqueId().toString())
                            .put("world", action.payload().get("world"))
                            .put("x", action.payload().get("x"))
                            .put("y", action.payload().get("y"))
                            .put("z", action.payload().get("z"))
                            .put("yaw", action.payload().get("yaw"))
                            .put("pitch", action.payload().get("pitch")),
                    player.getUniqueId());
            case "BACK" -> sendWithRetry(currentServer,
                    NetworkPacket.request(PacketType.BACK_TELEPORT, "velocity", currentServer)
                            .put("uuid", player.getUniqueId().toString())
                            .put("world", action.payload().get("world"))
                            .put("x", action.payload().get("x"))
                            .put("y", action.payload().get("y"))
                            .put("z", action.payload().get("z"))
                            .put("yaw", action.payload().get("yaw"))
                            .put("pitch", action.payload().get("pitch")),
                    player.getUniqueId());
            default -> {}
        }
    }

    // ── private helpers ─────────────────────────────────────────────

    private boolean isForwarded(NetworkPacket packet) {
        return "true".equals(packet.getOrDefault(FORWARDED_KEY, ""));
    }

    private void forwardOnce(NetworkPacket packet) {
        if (!hasBridge() || isForwarded(packet)) return;
        packet.put(FORWARDED_KEY, "true");
        bridge().forwardPacket(packet);
    }

    private void routeToTarget(NetworkPacket packet, String targetUuidString) {
        String targetName = packet.getOrDefault("targetName", "");
        if (!targetName.isBlank()) {
            Optional<Player> byName = proxy.getPlayer(targetName);
            if (byName.isPresent()) {
                Player player = byName.get();
                String server = player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(null);
                if (server != null) {
                    packet.put("targetUuid", player.getUniqueId().toString());
                    packet.setTargetServer(server);
                    services.messaging().sendToServer(server, packet);
                    return;
                }
            }
            // Try resolving by name via Redis for cross-proxy
            if (targetUuidString == null && hasBridge()) {
                Optional<UUID> resolved = services.playerTracker().resolveUuidByName(targetName);
                if (resolved.isPresent()) {
                    targetUuidString = resolved.get().toString();
                    packet.put("targetUuid", targetUuidString);
                }
            }
        }
        if (targetUuidString == null) {
            notifySender(packet, "Player not found.");
            return;
        }
        UUID targetUuid = UUID.fromString(targetUuidString);

        if (services.playerTracker().isLocal(targetUuid)) {
            services.playerTracker().resolveServer(targetUuid).thenAccept(server -> server.ifPresent(s -> {
                packet.setTargetServer(s);
                services.messaging().sendToServer(s, packet);
            }));
        } else {
            forwardOnce(packet);
        }
    }

    private void handlePlayerListRequest(NetworkPacket packet) {
        String originServer = packet.getOriginServer();
        if (originServer == null || originServer.isBlank()) return;

        String localNames = proxy.getAllPlayers().stream()
                .map(Player::getUsername)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        if (hasBridge() && services.settings().globalPlayerList()) {
            bridge().requestRemotePlayerList(services.settings().crossProxyTimeoutMs())
                    .thenAccept(remoteNames -> {
                        String combined = mergeNameLists(localNames, remoteNames);
                        NetworkPacket response = NetworkPacket.request(
                                PacketType.PLAYER_LIST_RESPONSE, "velocity", originServer)
                                .put("names", combined);
                        services.messaging().sendToServer(originServer, response);
                    });
        } else {
            NetworkPacket response = NetworkPacket.request(
                    PacketType.PLAYER_LIST_RESPONSE, "velocity", originServer)
                    .put("names", localNames);
            services.messaging().sendToServer(originServer, response);
        }
    }

    private void handleCrossProxyPlayerListRequest(NetworkPacket packet) {
        String correlationId = packet.get("correlationId");
        if (correlationId == null || !hasBridge()) return;

        String names = proxy.getAllPlayers().stream()
                .map(Player::getUsername)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        bridge().sendPlayerListResponse(correlationId, names);
    }

    private void routeTransfer(NetworkPacket packet) {
        UUID uuid = UUID.fromString(packet.get("uuid"));
        String targetServer = packet.get("targetServer");
        String postAction = packet.getOrDefault("postAction", "");

        Optional<Player> playerOpt = proxy.getPlayer(uuid);
        if (playerOpt.isEmpty()) {
            forwardOnce(packet);
            return;
        }
        Optional<RegisteredServer> destination = proxy.getServer(targetServer);
        if (destination.isEmpty()) return;
        Player player = playerOpt.get();

        if (!postAction.isBlank()) {
            PendingAction action = new PendingAction(uuid, targetServer, postAction);
            action.payload().putAll(packet.getPayload());
            services.pendingActionManager().put(action);
            logger.info("Queued pending action for {}: action={}, targetServer={}", uuid, postAction, targetServer);
        }
        player.createConnectionRequest(destination.get()).fireAndForget();
    }

    private void handleTpaAccept(NetworkPacket packet) {
        UUID senderUuid = UUID.fromString(packet.get("senderUuid"));
        UUID targetUuid = UUID.fromString(packet.get("targetUuid"));
        String type = packet.get("type");

        Optional<Player> senderOpt = proxy.getPlayer(senderUuid);
        Optional<Player> targetOpt = proxy.getPlayer(targetUuid);

        if (senderOpt.isPresent() && targetOpt.isPresent()) {
            Player sender = senderOpt.get();
            Player target = targetOpt.get();
            String hubServer = services.settings().hubServer();
            String targetServer = target.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(hubServer);
            String senderServer = sender.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(hubServer);

            if ("TPA".equalsIgnoreCase(type)) {
                ensureTeleport(sender, target, senderServer, targetServer);
            } else {
                ensureTeleport(target, sender, targetServer, senderServer);
            }
            return;
        }

        // Cross-proxy: one player is local, the other is remote.
        // The local player (actor) needs to be transferred to the remote player's server.
        if (senderOpt.isPresent() || targetOpt.isPresent()) {
            Player localPlayer;
            UUID remoteUuid;
            if ("TPA".equalsIgnoreCase(type)) {
                // TPA = sender moves to target
                if (senderOpt.isPresent()) {
                    localPlayer = senderOpt.get();
                    remoteUuid = targetUuid;
                } else {
                    // Target is here but sender isn't -- forward so sender's proxy handles it
                    forwardOnce(packet);
                    return;
                }
            } else {
                // TPAHERE = target moves to sender
                if (targetOpt.isPresent()) {
                    localPlayer = targetOpt.get();
                    remoteUuid = senderUuid;
                } else {
                    forwardOnce(packet);
                    return;
                }
            }

            services.playerTracker().resolveServer(remoteUuid).thenAccept(serverOpt -> {
                if (serverOpt.isEmpty()) {
                    localPlayer.sendMessage(Component.text("Could not locate the other player's server.", NamedTextColor.RED));
                    return;
                }
                String remoteServer = serverOpt.get();
                Optional<RegisteredServer> dest = proxy.getServer(remoteServer);
                if (dest.isEmpty()) {
                    localPlayer.sendMessage(Component.text("Server '" + remoteServer + "' is not available.", NamedTextColor.RED));
                    return;
                }

                PendingAction action = new PendingAction(localPlayer.getUniqueId(), remoteServer, "TELEPORT_TO_PLAYER")
                        .put("actorUuid", localPlayer.getUniqueId().toString())
                        .put("targetUuid", remoteUuid.toString());
                services.pendingActionManager().put(action);
                logger.info("Cross-proxy TPA: transferring {} to server {} to meet {}",
                        localPlayer.getUsername(), remoteServer, remoteUuid);
                localPlayer.sendMessage(Component.text("Teleporting...", NamedTextColor.GREEN));
                localPlayer.createConnectionRequest(dest.get()).fireAndForget();
            });
            return;
        }

        // Neither player is on this proxy -- don't forward (prevents loop)
        logger.debug("TPA_ACCEPT: neither player is on this proxy, ignoring (sender={}, target={})",
                senderUuid, targetUuid);
    }

    private void handleAdminTpToCoords(NetworkPacket packet) {
        String targetName = packet.get("targetName");
        Optional<Player> targetOpt = proxy.getPlayer(targetName);
        if (targetOpt.isEmpty()) {
            forwardOnce(packet);
            return;
        }

        Player target = targetOpt.get();
        String targetServer = target.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(null);
        if (targetServer == null) return;

        NetworkPacket forward = NetworkPacket.request(PacketType.ADMIN_TP_TO_COORDS, "velocity", targetServer)
                .put("targetUuid", target.getUniqueId().toString())
                .put("world", packet.get("world"))
                .put("x", packet.get("x"))
                .put("y", packet.get("y"))
                .put("z", packet.get("z"));
        sendWithRetry(targetServer, forward, target.getUniqueId());
    }

    private void handleAdminTeleport(NetworkPacket packet) {
        String mode = packet.get("mode");
        String hubServer = services.settings().hubServer();
        switch (mode) {
            case "SELF_TO_TARGET" -> {
                Player sender = proxy.getPlayer(UUID.fromString(packet.get("senderUuid"))).orElse(null);
                String tgtName = packet.get("targetName");
                Player target = proxy.getPlayer(tgtName).orElse(null);
                if (sender != null && target != null) {
                    ensureTeleport(sender, target,
                            sender.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(hubServer),
                            target.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(hubServer));
                } else if (sender != null) {
                    resolveAndTransfer(sender, tgtName);
                } else {
                    forwardOnce(packet);
                }
            }
            case "TARGET_TO_SENDER" -> {
                Player sender = proxy.getPlayer(UUID.fromString(packet.get("senderUuid"))).orElse(null);
                Player target = proxy.getPlayer(packet.get("targetName")).orElse(null);
                if (sender != null && target != null) {
                    ensureTeleport(target, sender,
                            target.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(hubServer),
                            sender.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(hubServer));
                } else {
                    forwardOnce(packet);
                }
            }
            case "PLAYER_TO_PLAYER" -> {
                Player player = proxy.getPlayer(packet.get("playerName")).orElse(null);
                String tgtName2 = packet.get("targetName");
                Player target = proxy.getPlayer(tgtName2).orElse(null);
                if (player != null && target != null) {
                    ensureTeleport(player, target,
                            player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(hubServer),
                            target.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(hubServer));
                } else if (player != null) {
                    resolveAndTransfer(player, tgtName2);
                } else {
                    forwardOnce(packet);
                }
            }
            default -> {}
        }
    }

    private void resolveAndTransfer(Player actor, String targetName) {
        Optional<UUID> targetUuid = services.playerTracker().resolveUuidByName(targetName);
        if (targetUuid.isEmpty()) {
            actor.sendMessage(Component.text("Player '" + targetName + "' is not online.", NamedTextColor.RED));
            return;
        }

        services.playerTracker().resolveServer(targetUuid.get()).thenAccept(serverOpt -> {
            if (serverOpt.isEmpty()) {
                actor.sendMessage(Component.text("Could not find the server for '" + targetName + "'.", NamedTextColor.RED));
                return;
            }
            String remoteServer = serverOpt.get();
            Optional<RegisteredServer> dest = proxy.getServer(remoteServer);
            if (dest.isEmpty()) {
                actor.sendMessage(Component.text("Server '" + remoteServer + "' is not registered on this proxy.", NamedTextColor.RED));
                return;
            }

            PendingAction action = new PendingAction(actor.getUniqueId(), remoteServer, "TELEPORT_TO_PLAYER")
                    .put("actorUuid", actor.getUniqueId().toString())
                    .put("targetUuid", targetUuid.get().toString());
            services.pendingActionManager().put(action);
            logger.info("Cross-proxy admin TP: transferring {} to server {} to reach {}",
                    actor.getUsername(), remoteServer, targetName);
            actor.sendMessage(Component.text("Teleporting to " + targetName + "...", NamedTextColor.GREEN));
            actor.createConnectionRequest(dest.get()).fireAndForget();
        });
    }

    private void ensureTeleport(Player actor, Player target, String actorServer, String targetServer) {
        if (actorServer.equalsIgnoreCase(targetServer)) {
            NetworkPacket localTeleport = NetworkPacket.request(PacketType.TELEPORT_TO_PLAYER, "velocity", targetServer)
                    .put("actorUuid", actor.getUniqueId().toString())
                    .put("targetUuid", target.getUniqueId().toString());
            sendWithRetry(targetServer, localTeleport, actor.getUniqueId(), 8);
            return;
        }
        PendingAction action = new PendingAction(actor.getUniqueId(), targetServer, "TELEPORT_TO_PLAYER")
                .put("actorUuid", actor.getUniqueId().toString())
                .put("targetUuid", target.getUniqueId().toString());
        services.pendingActionManager().put(action);
        proxy.getServer(targetServer).ifPresent(server -> actor.createConnectionRequest(server).fireAndForget());
    }

    private void sendWithRetry(String serverName, NetworkPacket packet, UUID playerUuid) {
        sendWithRetry(serverName, packet, playerUuid, 8);
    }

    private void sendWithRetry(String serverName, NetworkPacket packet, UUID playerUuid, int attemptsLeft) {
        boolean sent = services.messaging().sendToServer(serverName, packet);
        if (sent) {
            logger.info("Delivered {} packet to {} for player {} (requestId={})",
                    packet.getType(), serverName, playerUuid, packet.getRequestId());
            return;
        }
        if (attemptsLeft <= 1) {
            logger.warn("Failed to deliver {} packet to {} for player {} after retries (requestId={})",
                    packet.getType(), serverName, playerUuid, packet.getRequestId());
            return;
        }
        proxy.getScheduler().buildTask(pluginInstance,
                () -> sendWithRetry(serverName, packet, playerUuid, attemptsLeft - 1))
                .delay(Duration.ofMillis(500))
                .schedule();
    }

    private void notifySender(NetworkPacket packet, String message) {
        String senderUuidStr = packet.getOrDefault("senderUuid", "");
        if (senderUuidStr.isBlank()) return;
        try {
            proxy.getPlayer(UUID.fromString(senderUuidStr))
                    .ifPresent(p -> p.sendMessage(Component.text(message, NamedTextColor.RED)));
        } catch (Exception ignored) {}
    }

    private String mergeNameLists(String local, String remote) {
        if (remote == null || remote.isBlank()) return local;
        if (local.isBlank()) return remote;
        return local + "," + remote;
    }

    private boolean hasBridge() {
        return services.redisBridge() != null;
    }

    private RedisCrossProxyBridge bridge() {
        return services.redisBridge();
    }
}
