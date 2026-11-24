package com.telehop.paper.command.admin;

import com.telehop.common.PermissionNodes;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.config.PaperSettings;
import com.telehop.paper.messaging.PaperMessagingManager;
import com.telehop.paper.service.AuditLogger;
import com.telehop.paper.version.VersionAdapter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminTeleportCommandTest {
    @Test
    void visitOnlyModeratorCannotMoveAnotherPlayer() {
        var plugin = mock(NetworkPaperPlugin.class);
        var sender = mock(Player.class);
        when(plugin.isFeatureEnabled("admin-tp")).thenReturn(true);
        when(sender.hasPermission(PermissionNodes.TP_PLAYER_SELF)).thenReturn(true);
        var denied = Component.text("Denied");
        when(plugin.msg("no-permission")).thenReturn(denied);
        new AdminTeleportCommand(plugin).execute(sender, "Alex", "Sam", null, null);
        verify(sender).sendMessage(denied);
        verify(plugin, never()).messaging();
    }

    @Test
    void staffCanTeleportSelfWithOnlySelfCoordinateAccess() {
        var plugin = mock(NetworkPaperPlugin.class);
        var sender = mock(Player.class);
        when(plugin.isFeatureEnabled("admin-tp")).thenReturn(true);
        when(sender.hasPermission(PermissionNodes.TP_COORDINATES_SELF)).thenReturn(true);
        when(plugin.auditLogger()).thenReturn(mock(AuditLogger.class));

        new AdminTeleportCommand(plugin).execute(sender, "10", "64", "-3", null);

        verify(sender).teleportAsync(new Location(null, 10, 64, -3));
        verify(plugin, never()).messaging();
    }

    @Test
    void otherCoordinatePermissionSendsTheNamedPlayerAndDestinationToVelocity() {
        var plugin = mock(NetworkPaperPlugin.class);
        var sender = mock(Player.class);
        var world = mock(World.class);
        var actor = UUID.randomUUID();
        var settings = mock(PaperSettings.class);
        var adapter = mock(VersionAdapter.class);
        var messaging = mock(PaperMessagingManager.class);
        when(plugin.isFeatureEnabled("admin-tp")).thenReturn(true);
        when(sender.hasPermission(PermissionNodes.TP_COORDINATES_OTHERS)).thenReturn(true);
        when(sender.getName()).thenReturn("Alex");
        when(sender.getUniqueId()).thenReturn(actor);
        when(sender.getWorld()).thenReturn(world);
        when(plugin.settings()).thenReturn(settings);
        when(settings.serverName()).thenReturn("lobby");
        when(plugin.versionAdapter()).thenReturn(adapter);
        when(adapter.getWorldName(world)).thenReturn("overworld");
        when(plugin.messaging()).thenReturn(messaging);

        try (var bukkit = mockStatic(Bukkit.class)) {
            new AdminTeleportCommand(plugin).execute(sender, "Steve", "-10.5", "64", "3");
            bukkit.verify(() -> Bukkit.getPlayerExact("Steve"));
        }

        var packet = ArgumentCaptor.forClass(NetworkPacket.class);
        verify(messaging).send(packet.capture());
        assertEquals(PacketType.ADMIN_TP_TO_COORDS, packet.getValue().getType());
        assertEquals("lobby", packet.getValue().getOriginServer());
        assertEquals("velocity", packet.getValue().getTargetServer());
        assertEquals(Map.of("targetName", "Steve", "actorUuid", actor.toString(), "world", "overworld",
                "x", "-10.5", "y", "64.0", "z", "3.0"), packet.getValue().getPayload());
    }

    @ParameterizedTest
    @CsvSource({"self,1", "self,Alex", "others,Steve"})
    void deniedCoordinatesCannotTeleportOrSendProxyRequests(String permission, String first) {
        var plugin = mock(NetworkPaperPlugin.class);
        var sender = mock(Player.class);
        var denied = Component.text("Denied");
        when(plugin.isFeatureEnabled("admin-tp")).thenReturn(true);
        when(plugin.msg("no-permission")).thenReturn(denied);
        when(sender.getName()).thenReturn("Alex");

        new AdminTeleportCommand(plugin).execute(sender, first, "2", "3", first.equals("1") ? null : "4");

        verify(sender).hasPermission(permission.equals("self") ? PermissionNodes.TP_COORDINATES_SELF : PermissionNodes.TP_COORDINATES_OTHERS);
        verify(sender).sendMessage(denied);
        verify(sender, never()).teleportAsync(any(Location.class));
        verify(plugin, never()).messaging();
    }
}
