package com.telehop.paper.messaging;

import com.telehop.common.NetworkConstants;
import com.telehop.common.messaging.PacketCodec;
import com.telehop.common.messaging.RequestBroker;
import com.telehop.common.messaging.RequestTracker;
import com.telehop.common.model.NetworkPacket;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class PaperMessagingManager implements PluginMessageListener {
    private final JavaPlugin plugin;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final RequestTracker requestTracker = new RequestTracker(scheduler);
    private final RequestBroker requestBroker;
    private final long dedupeWindowMs;
    private final long timeoutMs;
    private PacketHandler handler;

    public interface PacketHandler {
        void handle(NetworkPacket packet);
    }

    public PaperMessagingManager(JavaPlugin plugin, long dedupeWindowMs, long timeoutMs) {
        this.plugin = plugin;
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
        requestBroker.handleIncoming(packet, dedupeWindowMs);
        if (!packet.isResponse() && handler != null) {
            Bukkit.getScheduler().runTask(plugin, () -> handler.handle(packet));
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
