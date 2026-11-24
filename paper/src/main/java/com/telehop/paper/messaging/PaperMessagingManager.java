package com.telehop.paper.messaging;

import com.telehop.common.NetworkConstants;
import com.telehop.common.messaging.PacketCodec;
import com.telehop.common.messaging.RequestBroker;
import com.telehop.common.messaging.RequestTracker;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

public class PaperMessagingManager implements PluginMessageListener {
    private final JavaPlugin plugin;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final RequestTracker requestTracker = new RequestTracker(scheduler);
    private final RequestBroker requestBroker;
    private final long dedupeWindowMs;
    private final long timeoutMs;
    private final Supplier<String> serverName;
    private PacketHandler handler;

    public interface PacketHandler {
        void handle(NetworkPacket packet, Player carrier);
    }

    public PaperMessagingManager(JavaPlugin plugin, long dedupeWindowMs, long timeoutMs, Supplier<String> serverName) {
        this.plugin = plugin;
        this.serverName = serverName;
        this.dedupeWindowMs = dedupeWindowMs;
        this.timeoutMs = timeoutMs;
        this.requestBroker = new RequestBroker(requestTracker, this::sendInternal);
    }

    public void register() {
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, NetworkConstants.CHANNEL_ID);
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, NetworkConstants.CHANNEL_ID, this);
    }

    public void setHandler(PacketHandler handler) {
        this.handler = handler;
    }

    public CompletableFuture<NetworkPacket> sendRequest(NetworkPacket packet) {
        return requestBroker.sendRequest(packet, timeoutMs);
    }

    /** A shared backend can have players connected through several different proxies. */
    public CompletableFuture<NetworkPacket> sendRequest(Player carrier, NetworkPacket packet) {
        return requestBroker.sendRequest(packet, timeoutMs, carrier, outgoing -> send(carrier, outgoing));
    }

    public boolean send(Player carrier, NetworkPacket packet) {
        if (!carrier.isOnline() || Bukkit.getPlayer(carrier.getUniqueId()) != carrier) return false;
        carrier.sendPluginMessage(plugin, NetworkConstants.CHANNEL_ID, PacketCodec.encode(packet));
        return true;
    }

    public void send(NetworkPacket packet) {
        sendInternal(packet);
    }

    private boolean sendInternal(NetworkPacket packet) {
        var carrier = Bukkit.getOnlinePlayers().stream().findFirst();
        if (carrier.isEmpty()) {
            return false;
        }
        carrier.get().sendPluginMessage(plugin, NetworkConstants.CHANNEL_ID, PacketCodec.encode(packet));
        return true;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!NetworkConstants.CHANNEL_ID.equals(channel)) {
            return;
        }
        NetworkPacket packet = PacketCodec.decode(message);
        boolean probe = packet.getType() == PacketType.PING && packet.supportsSpawns();
        boolean arrival = packet.getType() == PacketType.NAMED_SPAWN_ARRIVAL;
        if ((probe || arrival) && (!"velocity".equalsIgnoreCase(packet.getOriginServer())
                || !serverName.get().equalsIgnoreCase(packet.getTargetServer())
                || !player.getUniqueId().toString().equals(packet.get("uuid")))) return;
        if (!requestBroker.handleIncoming(packet, dedupeWindowMs, player) || handler == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (probe || arrival) {
                NetworkPacket reply = NetworkPacket.response(packet, true, null);
                if (probe) reply.put("capability", packet.get("capability"));
                if (!send(player, reply) || probe) return;
            }
            handler.handle(packet, player);
        });
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
