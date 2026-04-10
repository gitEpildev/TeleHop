package com.telehop.velocity.service;

import com.telehop.common.service.PlayerService;
import com.velocitypowered.api.proxy.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class VelocityPlayerTracker {
    private final PlayerService playerService;
    private final Map<UUID, String> liveMap = new ConcurrentHashMap<>();

    public VelocityPlayerTracker(PlayerService playerService) {
        this.playerService = playerService;
    }

    public CompletableFuture<Void> update(Player player, String serverName) {
        liveMap.put(player.getUniqueId(), serverName);
        return playerService.updateServer(player.getUniqueId(), serverName);
    }

    public void remove(Player player) {
        liveMap.remove(player.getUniqueId());
        playerService.remove(player.getUniqueId());
    }

    public CompletableFuture<Optional<String>> resolveServer(UUID uuid) {
        String local = liveMap.get(uuid);
        if (local != null) {
            return CompletableFuture.completedFuture(Optional.of(local));
        }
        return playerService.getServer(uuid);
    }
}
