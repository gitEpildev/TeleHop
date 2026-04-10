package com.telehop.paper.service;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Material;

import java.util.concurrent.ExecutionException;
import java.util.Random;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class RtpManager {
    private final JavaPlugin plugin;
    private final Random random = new Random();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public RtpManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean onCooldown(Player player) {
        long now = System.currentTimeMillis();
        long next = cooldowns.getOrDefault(player.getUniqueId().toString(), 0L);
        return now < next;
    }

    /** Returns seconds remaining on the cooldown, or 0 if none. */
    public int remainingCooldown(Player player) {
        long diff = cooldowns.getOrDefault(player.getUniqueId().toString(), 0L) - System.currentTimeMillis();
        return diff > 0 ? (int) Math.ceil(diff / 1000.0) : 0;
    }

    public void markCooldown(Player player, int cooldownSeconds) {
        cooldowns.put(player.getUniqueId().toString(), System.currentTimeMillis() + cooldownSeconds * 1000L);
    }

    public CompletableFuture<Location> findSafeLocation(World world, int radius) {
        return CompletableFuture.supplyAsync(() -> {
            for (int i = 0; i < 300; i++) {
                int x = random.nextInt(radius * 2 + 1) - radius;
                int z = random.nextInt(radius * 2 + 1) - radius;
                try {
                    Location safe = Bukkit.getScheduler().callSyncMethod(plugin, () -> findSafeAtSync(world, x, z)).get();
                    if (safe != null) {
                        return safe;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                } catch (ExecutionException e) {
                    return null;
                }
            }
            return null;
        });
    }

    private Location findSafeAtSync(World world, int x, int z) {
        int minY = Math.max(world.getMinHeight() + 1, -63);
        int maxY = world.getMaxHeight() - 2;
        boolean isNether = world.getEnvironment() == World.Environment.NETHER;
        boolean isOverworld = world.getEnvironment() == World.Environment.NORMAL;

        if (isNether) {
            maxY = Math.min(maxY, 120);
        }

        for (int y = maxY; y >= minY; y--) {
            Block floor = world.getBlockAt(x, y - 1, z);
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);

            if (!isSafeFloor(world, floor, y)) continue;
            if (!feet.getType().isAir() || !head.getType().isAir()) continue;

            // Overworld: must have sky access (rejects caves, overhangs, and underground pockets)
            if (isOverworld && feet.getLightFromSky() < 10) continue;

            // Reject if floor is ice/frosted ice (oceans, frozen biomes over water)
            Material floorType = floor.getType();
            if (floorType == Material.ICE || floorType == Material.PACKED_ICE
                    || floorType == Material.BLUE_ICE || floorType == Material.FROSTED_ICE) continue;

            // Reject waterlogged floor blocks
            if (floor.getBlockData() instanceof org.bukkit.block.data.Waterlogged wl && wl.isWaterlogged()) continue;

            // Reject locations near water/lava (check 4 adjacent blocks at feet level)
            if (hasAdjacentLiquid(world, x, y, z)) continue;

            // Minimum Y threshold for overworld to avoid deep caves
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

    public void teleport(Player player, Location location) {
        Bukkit.getScheduler().runTask(plugin, () -> player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.PLUGIN));
    }
}
