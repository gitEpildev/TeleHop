package com.telehop.paper.service;

import com.telehop.common.PermissionNodes;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.common.model.SpawnEntry;
import com.telehop.common.service.SpawnDirectory;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.config.PaperSettings;
import com.telehop.paper.config.SpawnRoutes;
import com.telehop.paper.config.StorageManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.UUID;

class SpawnAccessTest {
    @Test
    void aDirectoryTimeoutAfterLeavingDoesNotReportAnotherSessionsFailure() {
        var plugin = mock(NetworkPaperPlugin.class);
        var settings = mock(PaperSettings.class);
        when(plugin.settings()).thenReturn(settings);
        when(settings.serverName()).thenReturn("hub");
        var directory = mock(SpawnDirectory.class);
        when(directory.configure(any(), isNull())).thenReturn(CompletableFuture.completedFuture(null));
        when(directory.list()).thenReturn(CompletableFuture.completedFuture(List.of()));
        var lookup = new CompletableFuture<Optional<SpawnEntry>>();
        when(directory.find("orchard")).thenReturn(lookup);
        var routes = new SpawnRoutes(false, false, null, Map.of(), Map.of());
        var service = new SpawnService(plugin, directory, routes, mock(StorageManager.class));
        var player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class)) {
            var scheduler = mock(BukkitScheduler.class);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
                invocation.getArgument(1, Runnable.class).run();
                return null;
            });
            service.apply("hub", routes, null).join();
            var arrival = NetworkPacket.request(PacketType.NAMED_SPAWN_ARRIVAL, "velocity", "hub")
                    .put("uuid", id.toString()).put("spawn", "orchard");
            service.arrive(player, arrival);
            service.forget(id);
            lookup.completeExceptionally(new java.util.concurrent.TimeoutException());
            verify(directory).find("orchard");
            verify(plugin, never()).getLogger();
            verify(plugin, never()).msg("spawn-unavailable");
        }
    }

    @Test
    void ordinaryRegionalSpawnWorksBeforeTheNamedDirectoryIsReady() {
        var plugin = mock(NetworkPaperPlugin.class);
        var settings = mock(PaperSettings.class);
        var services = mock(ServiceRegistry.class);
        var teleport = mock(TeleportService.class);
        var player = mock(Player.class);
        var world = mock(World.class);
        when(plugin.isFeatureEnabled("spawn")).thenReturn(true);
        when(plugin.settings()).thenReturn(settings);
        when(settings.serverName()).thenReturn("eu");
        when(settings.getRegionalHub("eu")).thenReturn("eu");
        when(plugin.services()).thenReturn(services);
        when(services.teleportService()).thenReturn(teleport);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(PermissionNodes.SPAWN)).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(world.getKey()).thenReturn(NamespacedKey.minecraft("overworld"));
        var routes = new SpawnRoutes(false, false, null, Map.of(), Map.of());
        new SpawnService(plugin, null, routes, null).travel(player, null, false);
        verify(teleport).teleportToSpawn(player);
    }

    @Test
    void bukkitDefaultsAndEffectiveOverridesApplyToEachDestination() {
        var permissions = new PermissionService();
        var member = mock(Player.class);
        String node = com.telehop.common.PermissionNodes.SPAWN_DESTINATION + "garden";
        assertTrue(permissions.destination(member, node, false));
        assertFalse(permissions.destination(member, node, true));
        when(member.isPermissionSet(node)).thenReturn(true);
        assertFalse(permissions.destination(member, node, false));
        when(member.hasPermission(node)).thenReturn(true);
        assertTrue(permissions.destination(member, node, true));
    }
}
