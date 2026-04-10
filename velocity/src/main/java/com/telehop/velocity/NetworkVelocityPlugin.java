package com.telehop.velocity;

import com.telehop.common.db.DatabaseManager;
import com.telehop.common.db.PlayerRepository;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.common.service.PlayerServerCache;
import com.telehop.common.service.PlayerService;
import com.telehop.velocity.config.VelocitySettings;
import com.telehop.velocity.messaging.VelocityMessagingManager;
import com.telehop.velocity.model.PendingAction;
import com.telehop.velocity.service.PendingActionManager;
import com.telehop.velocity.service.VelocityPlayerTracker;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;

@Plugin(
        id = "telehop-velocity",
        name = "TeleHop-Velocity",
        version = "1.0.0",
        url = "https://developer.epildevconnect.uk/myhub/home",
        description = "Cross-server teleportation proxy bridge for TeleHop",
        authors = {"Epildev"}
)
public class NetworkVelocityPlugin {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private VelocitySettings settings;
    private DatabaseManager databaseManager;
    private VelocityPlayerTracker playerTracker;
    private VelocityMessagingManager messaging;
    private PendingActionManager pendingActionManager;

    @Inject
    public NetworkVelocityPlugin(ProxyServer proxy, Logger logger, @com.velocitypowered.api.plugin.annotation.DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        event.getPlayer().getCurrentServer().ifPresent(conn -> {
            String serverName = conn.getServerInfo().getName();
            playerTracker.update(event.getPlayer(), serverName);
        });
    }

    @Subscribe
    public void onServerSwitch(ServerConnectedEvent event) {
        String serverName = event.getServer().getServerInfo().getName();
        playerTracker.update(event.getPlayer(), serverName);
        executePendingAction(event.getPlayer(), serverName);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        playerTracker.remove(event.getPlayer());
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        try {
            settings = loadSettings();
            databaseManager = new DatabaseManager(settings.databaseConfig());
            databaseManager.initSchema();
            playerTracker = new VelocityPlayerTracker(
                    new PlayerService(databaseManager, new PlayerRepository(databaseManager.dataSource()), new PlayerServerCache())
            );
            pendingActionManager = new PendingActionManager();
            messaging = new VelocityMessagingManager(proxy, settings.dedupeWindowMs(), settings.requestTimeoutMs());
            messaging.register();
            messaging.setHandler(this::handlePacket);
            proxy.getEventManager().register(this, messaging);
            logger.info("TeleHop-Velocity enabled.");
        } catch (Exception e) {
            logger.error("Failed to initialize TeleHop-Velocity", e);
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (messaging != null) messaging.shutdown();
        if (databaseManager != null) databaseManager.shutdown();
    }

    private void handlePacket(NetworkPacket packet) {
        switch (packet.getType()) {
            case PLAYER_SERVER_UPDATE -> {
                UUID uuid = UUID.fromString(packet.get("uuid"));
                String server = packet.get("server");
                proxy.getPlayer(uuid).ifPresent(player -> playerTracker.update(player, server));
            }
            case TRANSFER_PLAYER -> routeTransfer(packet);
            case TPA_CREATE -> routeToTarget(packet, packet.get("targetUuid"));
            case TPA_DENY -> routeToTarget(packet, packet.get("senderUuid"));
            case TPA_CANCEL -> {
                String targetUuid = packet.get("targetUuid");
                if (targetUuid != null) routeToTarget(packet, targetUuid);
            }
            case PLAYER_LIST_REQUEST -> handlePlayerListRequest(packet);
            case TPA_ACCEPT -> handleTpaAccept(packet);
            case ADMIN_TP_REQUEST -> handleAdminTeleport(packet);
            default -> {
            }
        }
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
                    messaging.sendToServer(server, packet);
                    return;
                }
            }
        }
        if (targetUuidString == null) return;
        UUID targetUuid = UUID.fromString(targetUuidString);
        playerTracker.resolveServer(targetUuid).thenAccept(server -> server.ifPresent(s -> {
            packet.setTargetServer(s);
            messaging.sendToServer(s, packet);
        }));
    }

    private void handlePlayerListRequest(NetworkPacket packet) {
        String originServer = packet.getOriginServer();
        if (originServer == null || originServer.isBlank()) return;
        String names = proxy.getAllPlayers().stream()
                .map(Player::getUsername)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        NetworkPacket response = NetworkPacket.request(PacketType.PLAYER_LIST_RESPONSE, "velocity", originServer)
                .put("names", names);
        messaging.sendToServer(originServer, response);
    }

    private void routeTransfer(NetworkPacket packet) {
        UUID uuid = UUID.fromString(packet.get("uuid"));
        String targetServer = packet.get("targetServer");
        String postAction = packet.getOrDefault("postAction", "");

        Optional<Player> playerOpt = proxy.getPlayer(uuid);
        if (playerOpt.isEmpty()) return;
        Optional<RegisteredServer> destination = proxy.getServer(targetServer);
        if (destination.isEmpty()) return;
        Player player = playerOpt.get();

        if (!postAction.isBlank()) {
            PendingAction action = new PendingAction(uuid, targetServer, postAction);
            action.payload().putAll(packet.getPayload());
            pendingActionManager.put(action);
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
        if (senderOpt.isEmpty() || targetOpt.isEmpty()) return;

        Player sender = senderOpt.get();
        Player target = targetOpt.get();
        String targetServer = target.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(settings.hubServer());
        String senderServer = sender.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(settings.hubServer());

        if ("TPA".equalsIgnoreCase(type)) {
            ensureTeleport(sender, target, senderServer, targetServer);
        } else {
            ensureTeleport(target, sender, targetServer, senderServer);
        }
    }

    private void handleAdminTeleport(NetworkPacket packet) {
        String mode = packet.get("mode");
        switch (mode) {
            case "SELF_TO_TARGET" -> {
                Player sender = proxy.getPlayer(UUID.fromString(packet.get("senderUuid"))).orElse(null);
                Player target = proxy.getPlayer(packet.get("targetName")).orElse(null);
                if (sender != null && target != null) {
                    ensureTeleport(sender, target,
                            sender.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(settings.hubServer()),
                            target.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(settings.hubServer()));
                }
            }
            case "TARGET_TO_SENDER" -> {
                Player sender = proxy.getPlayer(UUID.fromString(packet.get("senderUuid"))).orElse(null);
                Player target = proxy.getPlayer(packet.get("targetName")).orElse(null);
                if (sender != null && target != null) {
                    ensureTeleport(target, sender,
                            target.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(settings.hubServer()),
                            sender.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(settings.hubServer()));
                }
            }
            case "PLAYER_TO_PLAYER" -> {
                Player player = proxy.getPlayer(packet.get("playerName")).orElse(null);
                Player target = proxy.getPlayer(packet.get("targetName")).orElse(null);
                if (player != null && target != null) {
                    ensureTeleport(player, target,
                            player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(settings.hubServer()),
                            target.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse(settings.hubServer()));
                }
            }
            default -> {
            }
        }
    }

    private void ensureTeleport(Player actor, Player target, String actorServer, String targetServer) {
        if (actorServer.equalsIgnoreCase(targetServer)) {
            NetworkPacket localTeleport = NetworkPacket.request(PacketType.TELEPORT_TO_PLAYER, "velocity", targetServer)
                    .put("actorUuid", actor.getUniqueId().toString())
                    .put("targetUuid", target.getUniqueId().toString());
            sendToPaperWithRetry(targetServer, localTeleport, actor.getUniqueId(), 8);
            return;
        }
        PendingAction action = new PendingAction(actor.getUniqueId(), targetServer, "TELEPORT_TO_PLAYER")
                .put("actorUuid", actor.getUniqueId().toString())
                .put("targetUuid", target.getUniqueId().toString());
        pendingActionManager.put(action);
        proxy.getServer(targetServer).ifPresent(server -> actor.createConnectionRequest(server).fireAndForget());
    }

    private void executePendingAction(Player player, String currentServer) {
        PendingAction action = pendingActionManager.get(player.getUniqueId());
        if (action == null) return;
        if (!action.targetServer().equalsIgnoreCase(currentServer)) {
            logger.info("Pending action wait for {}: action={}, target={}, current={}",
                    player.getUniqueId(), action.action(), action.targetServer(), currentServer);
            return;
        }
        pendingActionManager.remove(player.getUniqueId());
        logger.info("Executing pending action for {}: action={}, server={}",
                player.getUniqueId(), action.action(), currentServer);

        switch (action.action()) {
            case "SPAWN" -> {
                NetworkPacket packet = NetworkPacket.request(PacketType.SPAWN_REQUEST, "velocity", currentServer)
                        .put("uuid", player.getUniqueId().toString());
                sendToPaperWithRetry(currentServer, packet, player.getUniqueId(), 8);
            }
            case "WARP" -> {
                NetworkPacket packet = NetworkPacket.request(PacketType.WARP_TELEPORT, "velocity", currentServer)
                        .put("uuid", player.getUniqueId().toString())
                        .put("warp", action.payload().get("warp"));
                sendToPaperWithRetry(currentServer, packet, player.getUniqueId(), 8);
            }
            case "RTP", "RTP_OVERWORLD", "RTP_NETHER", "RTP_END" -> {
                String dimension = action.payload().getOrDefault("dimension", "overworld");
                if (action.action().startsWith("RTP_")) {
                    String suffix = action.action().substring("RTP_".length()).toLowerCase();
                    if (!suffix.isBlank()) {
                        dimension = suffix;
                    }
                }
                NetworkPacket packet = NetworkPacket.request(PacketType.RTP_REQUEST, "velocity", currentServer)
                        .put("uuid", player.getUniqueId().toString())
                        .put("region", action.payload().getOrDefault("region", "default"))
                        .put("dimension", dimension);
                sendToPaperWithRetry(currentServer, packet, player.getUniqueId(), 8);
            }
            case "TELEPORT_TO_PLAYER" -> {
                NetworkPacket packet = NetworkPacket.request(PacketType.TELEPORT_TO_PLAYER, "velocity", currentServer)
                        .put("actorUuid", action.payload().get("actorUuid"))
                        .put("targetUuid", action.payload().get("targetUuid"));
                sendToPaperWithRetry(currentServer, packet, player.getUniqueId(), 8);
            }
            case "PWARP" -> {
                NetworkPacket packet = NetworkPacket.request(PacketType.PWARP_TELEPORT, "velocity", currentServer)
                        .put("uuid", player.getUniqueId().toString())
                        .put("pwarpOwner", action.payload().get("pwarpOwner"))
                        .put("pwarpName", action.payload().get("pwarpName"));
                sendToPaperWithRetry(currentServer, packet, player.getUniqueId(), 8);
            }
            default -> {
            }
        }
    }

    private void sendToPaperWithRetry(String serverName, NetworkPacket packet, UUID playerUuid, int attemptsLeft) {
        boolean sent = messaging.sendToServer(serverName, packet);
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
        proxy.getScheduler().buildTask(this,
                () -> sendToPaperWithRetry(serverName, packet, playerUuid, attemptsLeft - 1))
                .delay(Duration.ofMillis(500))
                .schedule();
    }

    private VelocitySettings loadSettings() throws IOException {
        Files.createDirectories(dataDirectory);
        Path config = dataDirectory.resolve("config.properties");
        if (Files.notExists(config)) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.properties")) {
                if (in != null) {
                    Files.copy(in, config);
                }
            }
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(config)) {
            props.load(in);
        }
        return VelocitySettings.from(props);
    }
}
