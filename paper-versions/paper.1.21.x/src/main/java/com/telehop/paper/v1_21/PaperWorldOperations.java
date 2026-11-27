package com.telehop.paper.v1_21;

import com.telehop.paper.version.WorldOperations;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.concurrent.CompletableFuture;

/** Loaded only when the server exposes both Paper asynchronous methods. */
public final class PaperWorldOperations implements WorldOperations {
    @Override
    public CompletableFuture<Boolean> teleport(Player player, Location destination) {
        return player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
    }

    @Override
    public CompletableFuture<Chunk> chunk(World world, int chunkX, int chunkZ) {
        return world.getChunkAtAsync(chunkX, chunkZ);
    }
}
