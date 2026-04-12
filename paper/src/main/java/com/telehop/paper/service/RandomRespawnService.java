package com.telehop.paper.service;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Finds safe random locations for death respawn — fully independent of {@link RtpManager}.
 *
 * <p>Key differences from {@code RtpManager}:
 * <ul>
 *   <li>Uses Paper's async chunk loading ({@code World#getChunkAtAsync}) instead of
 *       bouncing each check through {@code callSyncMethod().get()}, which eliminates
 *       the main-thread bottleneck that caused the race condition with fast respawns.</li>
 *   <li>No cooldowns, regions, dimensions, or GUI — single-purpose for respawn.</li>
 *   <li>Returns a {@link CompletableFuture} so the caller can handle both the "ready"
 *       and "still searching" states gracefully.</li>
 * </ul>
 */
public final class RandomRespawnService {

    private static final int MAX_ATTEMPTS = 200;

    private final JavaPlugin plugin;
    private final Random random = new Random();

    public RandomRespawnService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Asynchronously searches for a safe surface location within the given radius.
     * The returned future completes with the location, or {@code null} if none was
     * found within {@value #MAX_ATTEMPTS} attempts.
     */
    public CompletableFuture<Location> findSafeLocation(World world, int radius) {
        CompletableFuture<Location> result = new CompletableFuture<>();
        searchNext(world, radius, 0, result);
        return result;
    }

    /**
     * Recursive async search: pick a random X/Z, load the chunk asynchronously via
     * Paper's API, then check the column on the main thread (chunk is guaranteed
     * loaded at that point). If safe, complete the future; otherwise recurse.
     */
    private void searchNext(World world, int radius, int attempt, CompletableFuture<Location> result) {
        if (attempt >= MAX_ATTEMPTS) {
            result.complete(null);
            return;
        }

        int x = random.nextInt(radius * 2 + 1) - radius;
        int z = random.nextInt(radius * 2 + 1) - radius;

        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            Location safe = checkColumn(world, x, z);
            if (safe != null) {
                result.complete(safe);
            } else {
                searchNext(world, radius, attempt + 1, result);
            }
        }).exceptionally(ex -> {
            searchNext(world, radius, attempt + 1, result);
            return null;
        });
    }

    /**
     * Checks a single X/Z column top-down for a safe standing position.
     * Must be called when the chunk at (x, z) is loaded.
     */
    private Location checkColumn(World world, int x, int z) {
        int minY = Math.max(world.getMinHeight() + 1, -63);
        int maxY = world.getMaxHeight() - 2;
        boolean isNether = world.getEnvironment() == World.Environment.NETHER;
        boolean isOverworld = world.getEnvironment() == World.Environment.NORMAL;

        if (isNether) maxY = Math.min(maxY, 120);

        for (int y = maxY; y >= minY; y--) {
            Block floor = world.getBlockAt(x, y - 1, z);
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);

            if (!isSafeFloor(world, floor, y)) continue;
            if (!feet.getType().isAir() || !head.getType().isAir()) continue;

            if (isOverworld && feet.getLightFromSky() < 10) continue;

            Material floorType = floor.getType();
            if (floorType == Material.ICE || floorType == Material.PACKED_ICE
                    || floorType == Material.BLUE_ICE || floorType == Material.FROSTED_ICE) continue;

            if (floor.getBlockData() instanceof org.bukkit.block.data.Waterlogged wl && wl.isWaterlogged()) continue;
            if (hasAdjacentLiquid(world, x, y, z)) continue;
            if (isOverworld && y < 50) continue;

            return new Location(world, x + 0.5, y, z + 0.5);
        }
        return null;
    }

    private boolean hasAdjacentLiquid(World world, int x, int y, int z) {
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] off : offsets) {
            Material m = world.getBlockAt(x + off[0], y, z + off[1]).getType();
            if (m == Material.WATER || m == Material.LAVA) return true;
            Material below = world.getBlockAt(x + off[0], y - 1, z + off[1]).getType();
            if (below == Material.WATER || below == Material.LAVA) return true;
        }
        return false;
    }

    private boolean isSafeFloor(World world, Block floor, int y) {
        Material type = floor.getType();
        if (!type.isSolid()) return false;

        if (type == Material.LAVA || type == Material.WATER
                || type == Material.MAGMA_BLOCK
                || type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE
                || type == Material.CACTUS
                || type == Material.FIRE || type == Material.SOUL_FIRE
                || type == Material.SWEET_BERRY_BUSH
                || type == Material.POWDER_SNOW
                || type == Material.POINTED_DRIPSTONE) {
            return false;
        }

        if (world.getEnvironment() == World.Environment.NETHER) {
            if (type == Material.BEDROCK || y > 120) return false;
        }
        return true;
    }
}
