package com.telehop.paper.service;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import com.telehop.paper.NetworkPaperPlugin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class RtpManager {
    private final NetworkPaperPlugin plugin;
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, Long> pendingRtps = new ConcurrentHashMap<>();

    public RtpManager(NetworkPaperPlugin plugin) {
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

    /**
     * Whether the player has an RTP warmup or transfer in flight. Entries
     * expire automatically so a crash, quit, or server switch mid-warmup can
     * never permanently lock a player out.
     */
    public boolean hasPendingRtp(Player player) {
        Long expiry = pendingRtps.get(player.getUniqueId().toString());
        return expiry != null && expiry > System.currentTimeMillis();
    }

    /**
     * Marks an RTP as in progress. {@code expectedSeconds} should cover the
     * warmup delay; a buffer is added for the teleport/transfer itself.
     */
    public void markPendingRtp(Player player, int expectedSeconds) {
        pendingRtps.put(player.getUniqueId().toString(),
                System.currentTimeMillis() + (expectedSeconds + 15) * 1000L);
    }

    /** Expire disconnected players' state without shortening their cooldown when they return. */
    public void expire() {
        long now = System.currentTimeMillis();
        pendingRtps.values().removeIf(deadline -> deadline <= now);
        cooldowns.values().removeIf(deadline -> deadline <= now);
    }

    public void clearPendingRtp(Player player) {
        pendingRtps.remove(player.getUniqueId().toString());
    }

    public CompletableFuture<Location> findSafeLocation(World world, int radius) {
        return SafeColumnSearch.find(plugin, world, radius, 300, this::findSafeAtSync);
    }

    private Location findSafeAtSync(World world, int x, int z) {
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        boolean isNether = world.getEnvironment() == World.Environment.NETHER;
        boolean isOverworld = world.getEnvironment() == World.Environment.NORMAL;

        if (isNether) {
            maxY = Math.min(maxY, world.getMinHeight() + world.getLogicalHeight() - 8);
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
            if (isOverworld && y < world.getSeaLevel() - 13) continue;

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
            if (type == Material.BEDROCK) return false;
        }
        return true;
    }

    public void teleport(Player player, Location location) {
        plugin.platform().teleport(player, location);
    }
}
