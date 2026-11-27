package com.telehop.paper.platform;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/** Startup probes for the server interfaces used by the backend. */
public record PlatformCapabilities(boolean asynchronousTeleport, boolean asynchronousChunks,
                                   boolean regionThreaded, boolean keyedWorlds, boolean worldHeights,
                                   boolean entityScheduler, boolean regionScheduler, boolean nativeAdventure,
                                   boolean spigotChat) {
    public static PlatformCapabilities detect() {
        return new PlatformCapabilities(
                method(Player.class, "teleportAsync", CompletableFuture.class, Location.class, PlayerTeleportEvent.TeleportCause.class),
                method(World.class, "getChunkAtAsync", CompletableFuture.class, int.class, int.class),
                hasRegionThreads(),
                method(World.class, "getKey", org.bukkit.NamespacedKey.class),
                method(World.class, "getMinHeight", int.class) && method(World.class, "getMaxHeight", int.class),
                method(Player.class, "getScheduler", Object.class), method(Server.class, "getRegionScheduler", Object.class),
                method(Player.class, "displayName", Object.class), hasSpigotChat());
    }

    static boolean hasSpigotChat() {
        return method(Player.class, "spigot", Object.class);
    }

    private static boolean hasRegionThreads() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false, World.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    private static boolean method(Class<?> api, String name, Class<?> result, Class<?>... parameterTypes) {
        try {
            Method method = api.getMethod(name, parameterTypes);
            return result.isAssignableFrom(method.getReturnType());
        } catch (NoSuchMethodException absent) {
            return false;
        }
    }
}
