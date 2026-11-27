package com.telehop.paper.service;

import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/** Samples one column per scheduled attempt, inside the radius and the world's current border. */
final class SafeColumnSearch {
    @FunctionalInterface
    interface Column {
        Location inspect(World world, int x, int z);
    }

    private SafeColumnSearch() {}

    static CompletableFuture<Location> find(NetworkPaperPlugin plugin, World world, int radius, int attempts, Column column) {
        if (radius < 0) throw new IllegalArgumentException("The teleport radius must be non-negative.");
        var result = new CompletableFuture<Location>();
        attempt(plugin, world, radius, attempts, column, result);
        return result;
    }

    private static void attempt(NetworkPaperPlugin plugin, World world, int radius, int attempts, Column column,
                                CompletableFuture<Location> result) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (result.isDone()) return;
            try {
                WorldBorder border = world.getWorldBorder();
                Location centre = border.getCenter();
                double half = border.getSize() / 2;
                int x = coordinate(radius, centre.getX(), half);
                int z = coordinate(radius, centre.getZ(), half);
                if (attempts == 0 || x == Integer.MIN_VALUE || z == Integer.MIN_VALUE) {
                    result.complete(null);
                    return;
                }
                plugin.platform().chunk(world, x >> 4, z >> 4).whenComplete((chunk, failure) -> {
                    if (failure != null) {
                        result.completeExceptionally(failure);
                        return;
                    }
                    // The native future can complete off-thread. Never inspect blocks from that callback.
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            Location safe = column.inspect(world, x, z);
                            if (safe != null && world.getWorldBorder().isInside(safe)) result.complete(safe);
                            else attempt(plugin, world, radius, attempts - 1, column, result);
                        } catch (RuntimeException error) {
                            result.completeExceptionally(error);
                        }
                    });
                });
            } catch (RuntimeException error) {
                result.completeExceptionally(error);
            }
        });
    }

    static int coordinate(int radius, double centre, double halfSize) {
        // Stay one block inside the border so the entire player can stand on the candidate.
        long min = Math.max(-(long) radius, (long) Math.ceil(centre - halfSize + 0.5));
        long max = Math.min((long) radius, (long) Math.floor(centre + halfSize - 1.5));
        return min > max ? Integer.MIN_VALUE : (int) ThreadLocalRandom.current().nextLong(min, max + 1);
    }
}
