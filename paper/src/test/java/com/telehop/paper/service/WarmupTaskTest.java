package com.telehop.paper.service;

import com.telehop.paper.NetworkPaperPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WarmupTaskTest {
    @Test
    void countdownUsesTitlesWithoutChatOrActionBarAndClearsOnCompletion() throws Exception {
        NetworkPaperPlugin plugin = mock(NetworkPaperPlugin.class);
        MessageService messages = mock(MessageService.class);
        Player player = mock(Player.class);
        Runnable complete = mock(Runnable.class);
        when(player.getLocation()).thenReturn(new Location(null, 0, 64, 0));
        when(player.isOnline()).thenReturn(true);
        when(plugin.messageService()).thenReturn(messages);
        when(messages.rawFormat(anyString(), anyMap())).thenReturn(Component.text("countdown"));
        WarmupTask task = new WarmupTask(plugin, player, 5, false, true, complete, null);
        for (int i = 0; i < 6; i++) tick(task);
        for (int i = 5; i > 0; i--) {
            verify(messages).rawFormat("countdown-title", Map.of("seconds", String.valueOf(i)));
        }
        verify(player, times(5)).showTitle(any(Title.class));
        verify(player, never()).sendMessage(any(Component.class));
        verify(player, never()).sendActionBar(any(Component.class));
        verify(player).clearTitle();
        verify(complete).run();
    }

    @Test
    void disabledCountdownDoesNotDisplayOrClearTitles() throws Exception {
        NetworkPaperPlugin plugin = mock(NetworkPaperPlugin.class);
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(null, 0, 64, 0));
        when(player.isOnline()).thenReturn(true);
        WarmupTask task = new WarmupTask(plugin, player, 1, false, false, () -> {}, null);
        tick(task);
        tick(task);
        verify(player, never()).showTitle(any(Title.class));
        verify(player, never()).clearTitle();
        verify(player, never()).sendMessage(any(Component.class));
    }

    @Test
    void movementClearsTitleAndCancelsWithoutTeleporting() throws Exception {
        NetworkPaperPlugin plugin = mock(NetworkPaperPlugin.class);
        Player player = mock(Player.class);
        Runnable complete = mock(Runnable.class);
        Runnable cancel = mock(Runnable.class);
        when(player.getLocation()).thenReturn(new Location(null, 0, 64, 0), new Location(null, 1, 64, 0));
        when(player.isOnline()).thenReturn(true);
        WarmupTask task = new WarmupTask(plugin, player, 5, true, true, complete, cancel);
        tick(task);
        verify(player).clearTitle();
        verify(cancel).run();
        verify(complete, never()).run();
        verify(player, never()).showTitle(any(Title.class));
    }

    private static void tick(WarmupTask task) throws Exception {
        Method method = WarmupTask.class.getDeclaredMethod("tick");
        method.setAccessible(true);
        method.invoke(task);
    }
}
