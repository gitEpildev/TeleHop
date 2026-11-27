package com.telehop.velocity.handler;

import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.velocity.model.PendingAction;
import com.telehop.velocity.service.PendingActionManager;
import com.telehop.velocity.service.VelocityServiceRegistry;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransferCleanupTest {
    private final UUID id = UUID.randomUUID();
    private final PendingActionManager pending = new PendingActionManager();
    private final CompletableFuture<Boolean> connection = new CompletableFuture<>();

    private void startSpawnTransfer() {
        var services = new VelocityServiceRegistry();
        services.setPendingActionManager(pending);
        var proxy = mock(ProxyServer.class);
        var player = mock(Player.class);
        var destination = mock(RegisteredServer.class);
        var request = mock(ConnectionRequestBuilder.class);
        when(player.getUniqueId()).thenReturn(id);
        when(proxy.getPlayer(id)).thenReturn(Optional.of(player));
        when(proxy.getServer("hub")).thenReturn(Optional.of(destination));
        when(player.createConnectionRequest(destination)).thenReturn(request);
        when(request.connectWithIndication()).thenReturn(connection);
        var packet = NetworkPacket.request(PacketType.TRANSFER_PLAYER, "survival", "velocity")
                .put("uuid", id.toString()).put("targetServer", "hub").put("postAction", "SPAWN");
        new VelocityPacketHandler(this, proxy, mock(Logger.class), services).handle(packet);
        assertEquals("SPAWN", pending.get(id).action());
    }

    @Test
    void rejectedTransferLeavesNoSpawnForALaterManualVisit() {
        startSpawnTransfer();
        connection.complete(false);
        assertNull(pending.get(id));
    }

    @Test
    void oldConnectionFailureCannotDiscardANewerTravelRequest() {
        startSpawnTransfer();
        var replacement = new PendingAction(id, "hub", "HOME");
        pending.put(replacement);
        connection.completeExceptionally(new IllegalStateException("Backend closed"));
        assertSame(replacement, pending.get(id));
    }
}
