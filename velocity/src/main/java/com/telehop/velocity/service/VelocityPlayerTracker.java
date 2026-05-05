package com.telehop.velocity.service;

import com.telehop.common.service.PlayerService;
import com.telehop.velocity.messaging.RedisCrossProxyBridge;
import com.velocitypowered.api.proxy.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class VelocityPlayerTracker {
    private final PlayerService playerService;
    private final Map<UUID, String> liveMap = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameMap = new ConcurrentHashMap<>();
    private volatile RedisCrossProxyBridge redisBridge;

    public VelocityPlayerTracker(PlayerService playerService) {
        this.playerService = playerService;
    }

    public void setRedisBridge(RedisCrossProxyBridge bridge) {
        this.redisBridge = bridge;
    }

    public CompletableFuture<Void> update(Player player, String serverName) {
        liveMap.put(player.getUniqueId(), serverName);
        nameMap.put(player.getUsername().toLowerCase(), player.getUniqueId());
        if (redisBridge != null) {
            redisBridge.broadcastPlayerUpdate(player.getUniqueId(), player.getUsername(), serverName, "JOIN");
        }
        return playerService.updateServer(player.getUniqueId(), serverName);
    }

    public void remove(Player player) {
        liveMap.remove(player.getUniqueId());
        nameMap.remove(player.getUsername().toLowerCase());
        if (redisBridge != null) {
            redisBridge.broadcastPlayerUpdate(player.getUniqueId(), player.getUsername(), null, "LEAVE");
        }
        playerService.remove(player.getUniqueId());
    }

    /**
     * Resolves a player UUID by name, checking local players first then the Redis global name map.
     */
    public Optional<UUID> resolveUuidByName(String name) {
        UUID local = nameMap.get(name.toLowerCase());
        if (local != null) return Optional.of(local);
        if (redisBridge != null) {
            UUID remote = redisBridge.globalPlayerNameMap().get(name.toLowerCase());
            if (remote != null) return Optional.of(remote);
        }
        return Optional.empty();
    }

    public CompletableFuture<Optional<String>> resolveServer(UUID uuid) {
        String local = liveMap.get(uuid);
        if (local != null) {
            return CompletableFuture.completedFuture(Optional.of(local));
        }
        if (redisBridge != null) {
            String remote = redisBridge.globalPlayerMap().get(uuid);
            if (remote != null) {
                return CompletableFuture.completedFuture(Optional.of(remote));
            }
        }
        return playerService.getServer(uuid);
    }

    /**
     * Checks if a player is on THIS proxy (local live map only).
     */
    public boolean isLocal(UUID uuid) {
        return liveMap.containsKey(uuid);
    }
}
