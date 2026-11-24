package com.telehop.velocity.messaging;

import com.telehop.common.NetworkConstants;
import com.telehop.common.messaging.PacketCodec;
import com.telehop.common.messaging.RequestBroker;
import com.telehop.common.messaging.RequestTracker;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class VelocityMessagingManager {
    public interface PacketHandler {
        void handle(NetworkPacket packet, ServerConnection carrier);
    }

    private final ProxyServer proxyServer;
    private final ChannelIdentifier identifier = MinecraftChannelIdentifier.from(NetworkConstants.CHANNEL_ID);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final RequestTracker requestTracker = new RequestTracker(scheduler);
    private final RequestBroker requestBroker;
    private final long dedupeWindowMs;
    private final long timeoutMs;
    private PacketHandler packetHandler;

    public VelocityMessagingManager(ProxyServer proxyServer, long dedupeWindowMs, long timeoutMs) {
        this.proxyServer = proxyServer;
        this.dedupeWindowMs = dedupeWindowMs;
        this.timeoutMs = timeoutMs;
        this.requestBroker = new RequestBroker(requestTracker, packet -> {
            if (packet.getTargetServer() == null) {
                return false;
            }
            return sendToServer(packet.getTargetServer(), packet);
        });
    }

    public void register() {
        proxyServer.getChannelRegistrar().register(identifier);
    }

    public void setHandler(PacketHandler packetHandler) {
        this.packetHandler = packetHandler;
    }

    public CompletableFuture<NetworkPacket> request(ServerConnection carrier, NetworkPacket packet) {
        return requestBroker.sendRequest(packet, timeoutMs, carrier, outgoing -> send(carrier, outgoing));
    }

    /** The registered server may choose another player. A spawn transaction owns one connection. */
    public boolean send(ServerConnection carrier, NetworkPacket packet) {
        return current(carrier) && carrier.getServerInfo().getName().equalsIgnoreCase(packet.getTargetServer())
                && carrier.sendPluginMessage(identifier, PacketCodec.encode(packet));
    }

    private boolean current(ServerConnection carrier) {
        return carrier.getPlayer().isActive() && carrier.getPlayer().getCurrentServer().orElse(null) == carrier;
    }

    public boolean sendToServer(String serverName, NetworkPacket packet) {
        Optional<RegisteredServer> target = proxyServer.getServer(serverName);
        if (target.isEmpty()) return false;
        return target.get().sendPluginMessage(identifier, PacketCodec.encode(packet));
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().getId().equals(identifier.getId())) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        // Only a backend connection may originate network commands.
        if (!(event.getSource() instanceof ServerConnection connection)) return;
        NetworkPacket packet = PacketCodec.decode(event.getData());
        boolean probe = packet.getType() == PacketType.PING && packet.supportsSpawns();
        if ((probe || packet.getType() == PacketType.NAMED_SPAWN_TRANSFER)
                && (!current(connection) || !connection.getServerInfo().getName().equalsIgnoreCase(packet.getOriginServer())
                || !"velocity".equalsIgnoreCase(packet.getTargetServer())
                || !connection.getPlayer().getUniqueId().toString().equals(packet.get("uuid")))) return;
        if (packet.isResponse() && !current(connection)) return;
        if (!requestBroker.handleIncoming(packet, dedupeWindowMs, connection)) return;
        if (probe) {
            send(connection, NetworkPacket.response(packet, true, null).put("capability", packet.get("capability")));
        } else if (packetHandler != null) {
            packetHandler.handle(packet, connection);
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
