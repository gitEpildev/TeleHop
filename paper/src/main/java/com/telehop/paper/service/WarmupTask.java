package com.telehop.paper.service;

import com.telehop.paper.NetworkPaperPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Reusable countdown warmup that ticks once per second. Optionally cancels
 * if the player moves, and optionally shows a live action-bar countdown.
 */
public class WarmupTask {
    private final NetworkPaperPlugin plugin;
    private final Player player;
    private final int totalSeconds;
    private final boolean cancelOnMove;
    private final boolean showActionBar;
    private final Location startLocation;
    private final Runnable onComplete;
    private final Runnable onCancel;
    private int remaining;
    private int taskId = -1;

    public WarmupTask(NetworkPaperPlugin plugin, Player player, int seconds,
                      boolean cancelOnMove, boolean showActionBar,
                      Runnable onComplete, Runnable onCancel) {
        this.plugin = plugin;
        this.player = player;
        this.totalSeconds = seconds;
        this.cancelOnMove = cancelOnMove;
        this.showActionBar = showActionBar;
        this.startLocation = player.getLocation().clone();
        this.onComplete = onComplete;
        this.onCancel = onCancel != null ? onCancel : () -> {};
        this.remaining = seconds;
    }

    public void start() {
        if (totalSeconds <= 0) {
            onComplete.run();
            return;
        }
        taskId = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 20L).getTaskId();
    }

    private void tick() {
        if (!player.isOnline()) {
            stop();
            return;
        }
        if (cancelOnMove && hasMoved()) {
            clearActionBar();
            stop();
            onCancel.run();
            return;
        }
        if (remaining <= 0) {
            clearActionBar();
            stop();
            onComplete.run();
            return;
        }
        Map<String, String> countdownReplacements = Map.of("seconds", String.valueOf(remaining));
        if (showActionBar) {
            player.sendActionBar(plugin.messageService().rawFormat("countdown-actionbar", countdownReplacements));
        }
        player.sendMessage(plugin.messageService().rawFormat("countdown-chat", countdownReplacements));
        remaining--;
    }

    private boolean hasMoved() {
        Location current = player.getLocation();
        return current.getBlockX() != startLocation.getBlockX()
                || current.getBlockY() != startLocation.getBlockY()
                || current.getBlockZ() != startLocation.getBlockZ();
    }

    private void clearActionBar() {
        if (showActionBar) player.sendActionBar(Component.empty());
    }

    private void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }
}
