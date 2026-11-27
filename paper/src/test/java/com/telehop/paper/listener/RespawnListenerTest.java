package com.telehop.paper.listener;

import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.config.PaperSettings;
import com.telehop.paper.messaging.PaperMessagingManager;
import com.telehop.paper.service.RandomRespawnManager;
import com.telehop.paper.service.RandomRespawnService;
import com.telehop.paper.version.VersionAdapter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RespawnListenerTest {
    private final NetworkPaperPlugin plugin = mock(NetworkPaperPlugin.class);
    private final PaperSettings settings = mock(PaperSettings.class);
    private final VersionAdapter adapter = mock(VersionAdapter.class);
    private final Player player = mock(Player.class);
    private final World deathWorld = mock(World.class);
    private final RandomRespawnService search = mock(RandomRespawnService.class);
    private final RandomRespawnManager manager = new RandomRespawnManager();
    private final RespawnListener listener = new RespawnListener(plugin, manager, search);
    private final UUID id = UUID.randomUUID();
    private final PlayerDeathEvent death = mock(PlayerDeathEvent.class);

    @BeforeEach void configure() {
        when(death.getEntity()).thenReturn(player);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getWorld()).thenReturn(deathWorld);
        when(plugin.isFeatureEnabled("random-respawn")).thenReturn(true);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.settings()).thenReturn(settings);
        when(plugin.versionAdapter()).thenReturn(adapter);
        when(settings.serverName()).thenReturn("gardens");
        when(settings.hubServer()).thenReturn("entrance");
        when(settings.respawnWorld()).thenReturn("");
        when(settings.respawnRadius()).thenReturn(250);
        when(adapter.getWorldName(deathWorld)).thenReturn("private_gardens");
    }

    @Test void emptySelectorSearchesTheActualWorldOfDeath() {
        var future = new CompletableFuture<Location>();
        when(search.findSafeLocation(deathWorld, 250)).thenReturn(future);
        listener.onDeath(death);
        assertSame(future, manager.pending(id));
        verify(adapter, never()).resolveWorld(anyString());
    }

    @Test void missingExplicitWorldKeepsOrdinaryRespawn() {
        when(settings.respawnWorld()).thenReturn("unloaded_garden");
        listener.onDeath(death);
        assertNull(manager.pending(id));
        verify(adapter).resolveWorld("unloaded_garden");
        verifyNoInteractions(search);
    }

    @Test void standaloneIdentityMayMatchTheHubNameWhileANetworkHubKeepsNormalRespawn() {
        var transport = mock(PaperMessagingManager.class);
        when(plugin.messaging()).thenReturn(transport);
        when(settings.hubServer()).thenReturn("gardens");
        when(transport.enabled()).thenReturn(false, true);
        var future = new CompletableFuture<Location>();
        when(search.findSafeLocation(deathWorld, 250)).thenReturn(future);
        listener.onDeath(death);
        assertSame(future, manager.pending(id));
        listener.onDeath(death);
        assertNull(manager.pending(id));
        verify(search).findSafeLocation(deathWorld, 250);
    }

    @Test void lateSearchCannotPlaceAPlayerAfterTheNextDeath() {
        var oldSearch = new CompletableFuture<Location>();
        var newSearch = new CompletableFuture<Location>();
        manager.stage(id, oldSearch);
        var respawn = mock(PlayerRespawnEvent.class);
        when(respawn.getPlayer()).thenReturn(player);
        listener.onRespawn(respawn);
        oldSearch.complete(new Location(deathWorld, 0, 70, 0));
        var callback = ArgumentCaptor.forClass(Runnable.class);
        verify(plugin).onMainThread(callback.capture());
        when(search.findSafeLocation(deathWorld, 250)).thenReturn(newSearch);
        listener.onDeath(death);
        callback.getValue().run();
        assertSame(newSearch, manager.pending(id));
        verify(plugin, never()).platform();
    }
}
