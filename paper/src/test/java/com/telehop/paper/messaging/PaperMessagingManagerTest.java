package com.telehop.paper.messaging;

import com.telehop.common.NetworkConstants;
import com.telehop.common.messaging.PacketCodec;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaperMessagingManagerTest {
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final PaperMessagingManager messaging = new PaperMessagingManager(plugin, 5000, 5000, () -> "hub");
    private final PaperMessagingManager.PacketHandler handler = mock(PaperMessagingManager.PacketHandler.class);

    @AfterEach
    void shutdown() {
        messaging.shutdown();
    }

    @Test
    void directlyAuthenticatedStandaloneServerRegistersNoProxyChannel() {
        Server server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getOnlineMode()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        try (var bukkit = mockStatic(Bukkit.class)) {
            messaging.register(true);
            bukkit.verifyNoInteractions();
            assertFalse(messaging.send(mock(Player.class), NetworkPacket.spawnProbe("hub", "velocity", UUID.randomUUID())));
        }
    }

    @Test
    void probeRepliesOnTheTravellersConnectionEvenWithAnotherProxyOnTheBackend() {
        UUID id = UUID.randomUUID();
        Player traveller = mock(Player.class);
        Player otherProxy = mock(Player.class);
        var probe = NetworkPacket.spawnProbe("velocity", "hub", id);
        try (var bukkit = mockStatic(Bukkit.class)) {
            when(plugin.getServer()).thenReturn(mock(Server.class));
            bukkit.when(Bukkit::getMessenger).thenReturn(mock(Messenger.class));
            messaging.register(true);
            var scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
                invocation.getArgument(1, Runnable.class).run();
                return null;
            });
            when(traveller.getUniqueId()).thenReturn(id);
            when(traveller.isOnline()).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(traveller);
            when(otherProxy.getUniqueId()).thenReturn(UUID.randomUUID());
            messaging.setHandler(handler);
            byte[] payload = PacketCodec.encode(probe);
            messaging.onPluginMessageReceived(NetworkConstants.CHANNEL_ID, otherProxy, payload);
            messaging.onPluginMessageReceived(NetworkConstants.CHANNEL_ID, traveller, payload);
            messaging.onPluginMessageReceived(NetworkConstants.CHANNEL_ID, traveller, payload);
            var response = ArgumentCaptor.forClass(byte[].class);
            verify(traveller).sendPluginMessage(eq(plugin), eq(NetworkConstants.CHANNEL_ID), response.capture());
            verify(otherProxy, never()).sendPluginMessage(any(), anyString(), any(byte[].class));
            verifyNoInteractions(handler);
            NetworkPacket reply = PacketCodec.decode(response.getValue());
            assertTrue(reply.supportsSpawns());
            assertEquals(probe.getRequestId(), reply.getRequestId());
        }
    }

    @Test
    void aReconnectedPlayerCannotInheritAnEarlierSessionsArrival() {
        UUID id = UUID.randomUUID();
        Player departed = mock(Player.class);
        var arrival = NetworkPacket.request(PacketType.NAMED_SPAWN_ARRIVAL, "velocity", "hub").put("uuid", id.toString());
        try (var bukkit = mockStatic(Bukkit.class)) {
            when(plugin.getServer()).thenReturn(mock(Server.class));
            bukkit.when(Bukkit::getMessenger).thenReturn(mock(Messenger.class));
            messaging.register(true);
            var scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
                invocation.getArgument(1, Runnable.class).run();
                return null;
            });
            when(departed.getUniqueId()).thenReturn(id);
            when(departed.isOnline()).thenReturn(true);
            Player replacement = mock(Player.class);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(replacement);
            messaging.setHandler(handler);
            messaging.onPluginMessageReceived(NetworkConstants.CHANNEL_ID, departed, PacketCodec.encode(arrival));
            verify(departed, never()).sendPluginMessage(any(), anyString(), any(byte[].class));
            verifyNoInteractions(handler, replacement);
        }
    }
}
