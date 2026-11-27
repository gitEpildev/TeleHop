package com.telehop.paper.version;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

/** Operations whose native asynchronous implementation is optional on Bukkit. */
public interface WorldOperations {
    CompletableFuture<Boolean> teleport(Player player, Location destination);
    CompletableFuture<Chunk> chunk(World world, int chunkX, int chunkZ);
}
