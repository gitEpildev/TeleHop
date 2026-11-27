package com.telehop.paper.command.tpa;

import com.telehop.common.PermissionNodes;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.common.model.TpaRequestRecord;
import com.telehop.common.model.TpaType;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.platform.PlatformText;
import com.telehop.paper.service.TpaRuntimeManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TpaAcceptCommandTest {
    @ParameterizedTest
    @EnumSource(TpaType.class)
    void acceptingRemoteRequestUsesTheAcceptersOwnProxyConnection(TpaType type) {
        var plugin = mock(NetworkPaperPlugin.class, RETURNS_DEEP_STUBS);
        var accepter = mock(Player.class);
        var senderId = UUID.randomUUID();
        var accepterId = UUID.randomUUID();
        var pending = new TpaRuntimeManager();
        pending.setIncoming(new TpaRequestRecord(senderId, accepterId, type, Instant.now()));
        when(accepter.getUniqueId()).thenReturn(accepterId);
        when(plugin.isFeatureEnabled("tpa")).thenReturn(true);
        when(plugin.permissionService().has(accepter, PermissionNodes.TPA_ACCEPT)).thenReturn(true);
        when(plugin.tpaRuntimeManager()).thenReturn(pending);
        when(plugin.settings().serverName()).thenReturn("orchard");
        when(plugin.settings().tpaTimeoutSeconds()).thenReturn(60);
        var messaging = plugin.messaging();

        try (var bukkit = mockStatic(Bukkit.class); var text = mockStatic(PlatformText.class)) {
            new TpaAcceptCommand(plugin).execute(accepter);
        }

        var packet = ArgumentCaptor.forClass(NetworkPacket.class);
        verify(messaging).send(same(accepter), packet.capture());
        verify(messaging, never()).send(any(NetworkPacket.class));
        assertEquals(PacketType.TPA_ACCEPT, packet.getValue().getType());
        assertEquals(Map.of("senderUuid", senderId.toString(), "targetUuid", accepterId.toString(),
                "type", type.name(), "targetServer", "orchard"), packet.getValue().getPayload());
    }

    @Test
    void expiredLocallyCachedRequestCannotTeleportEvenAfterAnotherBackendDeletedTheRow() {
        var plugin = mock(NetworkPaperPlugin.class, RETURNS_DEEP_STUBS);
        var accepter = mock(Player.class);
        var pending = new TpaRuntimeManager();
        var expired = new TpaRequestRecord(UUID.randomUUID(), UUID.randomUUID(), TpaType.TPA, Instant.EPOCH);
        pending.setIncoming(expired);
        when(accepter.getUniqueId()).thenReturn(expired.targetUuid());
        when(plugin.isFeatureEnabled("tpa")).thenReturn(true);
        when(plugin.permissionService().has(accepter, PermissionNodes.TPA_ACCEPT)).thenReturn(true);
        when(plugin.tpaRuntimeManager()).thenReturn(pending);
        when(plugin.settings().tpaTimeoutSeconds()).thenReturn(60);
        var notice = plugin.msg("request-expired");
        try (var text = mockStatic(PlatformText.class)) {
            new TpaAcceptCommand(plugin).execute(accepter);
            text.verify(() -> PlatformText.send(accepter, notice));
        }
        verify(plugin, never()).messaging();
        verify(plugin, never()).platform();
        assertEquals(Optional.empty(), pending.getIncoming(expired.targetUuid()));
    }

    @Test
    void aDelayedExpiryCannotRemoveANewerRequestForTheSamePlayers() {
        var pending = new TpaRuntimeManager();
        var old = new TpaRequestRecord(UUID.randomUUID(), UUID.randomUUID(), TpaType.TPA, Instant.EPOCH);
        var replacement = new TpaRequestRecord(old.senderUuid(), old.targetUuid(), old.type(), Instant.ofEpochMilli(1000));
        pending.setIncoming(old);
        pending.setIncoming(replacement);

        assertFalse(pending.removeRequest(old));
        assertEquals(Optional.of(replacement), pending.getIncoming(old.targetUuid()));
        assertEquals(Optional.of(replacement), pending.getOutgoing(old.senderUuid()));
    }
}
