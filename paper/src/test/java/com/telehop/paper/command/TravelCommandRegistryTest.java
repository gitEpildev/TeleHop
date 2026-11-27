package com.telehop.paper.command;

import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.config.CommandNames;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TravelCommandRegistryTest {
    @Test void foreignOwnerPreventsRegisteringAnyOfTheProposedAliases() {
        var plugin = mock(NetworkPaperPlugin.class);
        var rival = mock(Plugin.class);
        var claimed = mock(PluginCommand.class);
        var map = mock(CommandMap.class);
        var proposal = mock(CommandNames.class);
        when(proposal.labels()).thenReturn(Map.of("spawn", List.of("lobby", "networkspawn")));
        when(map.getCommand("networkspawn")).thenReturn(claimed);
        when(claimed.getPlugin()).thenReturn(rival);
        when(rival.getName()).thenReturn("ExistingTravel");
        var failure = assertThrows(IllegalArgumentException.class, () -> TravelCommandRegistry.checkOwnership(plugin, proposal, map));
        assertTrue(failure.getMessage().contains("networkspawn"));
        assertTrue(failure.getMessage().contains("ExistingTravel"));
        verify(map, never()).register(anyString(), any());
    }

    @Test void inspectingOwnLiveAliasesAllowsARulesOnlyReload() {
        var plugin = mock(NetworkPaperPlugin.class);
        var current = mock(PluginCommand.class);
        var map = mock(CommandMap.class);
        var proposal = mock(CommandNames.class);
        when(proposal.labels()).thenReturn(Map.of("spawn", List.of("spawn")));
        when(map.getCommand("spawn")).thenReturn(current);
        when(current.getPlugin()).thenReturn(plugin);
        TravelCommandRegistry.checkOwnership(plugin, proposal, map);
        verify(map).getCommand("spawn");
        verify(map, never()).register(anyString(), any());
    }
}
