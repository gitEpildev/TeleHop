package com.telehop.paper.platform;

import com.telehop.paper.v1_21.Paper121Adapter;
import com.telehop.paper.v1_21.PaperWorldOperations;
import com.telehop.paper.v26_1.Paper261Adapter;
import com.telehop.paper.version.VersionAdapter;
import com.telehop.paper.version.WorldOperations;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Startup-selected runtime operations. Platform features and versioned storage rules are independent. */
public final class BackendPlatform {
    private static final Pattern GAME_VERSION = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.\\d+)?");
    private final JavaPlugin plugin;
    private final WorldOperations nativeOperations;
    private final PlatformCapabilities capabilities;
    private final VersionAdapter version;
    private final String implementation;
    private final String description;

    private BackendPlatform(JavaPlugin plugin, WorldOperations nativeOperations, PlatformCapabilities capabilities,
                            VersionAdapter version, String implementation, String description) {
        this.plugin = plugin;
        this.nativeOperations = nativeOperations;
        this.capabilities = capabilities;
        this.version = version;
        this.implementation = implementation;
        this.description = description;
    }

    public static BackendPlatform detect(JavaPlugin plugin) {
        PlatformCapabilities capabilities = PlatformCapabilities.detect();
        String api = plugin.getServer().getBukkitVersion();
        String game = gameVersion(api);
        VersionAdapter version = select(game, Runtime.version().feature(), capabilities);
        boolean nativeAsync = capabilities.asynchronousTeleport() && capabilities.asynchronousChunks();
        String name = plugin.getServer().getName();
        if (name.equalsIgnoreCase("CraftBukkit") && capabilities.spigotChat()) name = "Spigot";
        String description = name + ", Minecraft " + game + ", Bukkit API " + api
                + ", Java " + Runtime.version().feature() + ", " + version.getClass().getSimpleName()
                + ", " + (nativeAsync ? "Paper asynchronous teleport and chunks" : "Bukkit main-thread teleport and chunks")
                + ", entity scheduler API=" + capabilities.entityScheduler() + ", region scheduler API=" + capabilities.regionScheduler()
                + ", native Adventure=" + capabilities.nativeAdventure() + ", MiniMessage=bundled"
                + ", chat=" + (capabilities.spigotChat() ? "Spigot component bridge" : "Bukkit and vanilla component chat");
        return new BackendPlatform(plugin, nativeAsync ? new PaperWorldOperations() : null, capabilities,
                version, name, description);
    }

    /** Modern Paper appends the distribution build number before its release channel. */
    static String gameVersion(String bukkitVersion) {
        return bukkitVersion.split("-", 2)[0].replaceFirst("\\.build\\.\\d+$", "");
    }

    static VersionAdapter select(String game, int javaVersion, PlatformCapabilities capabilities) {
        if (capabilities.regionThreaded()) {
            throw new IllegalStateException("Folia region ownership is not supported. Use a single-tick-thread backend.");
        }
        Matcher match = GAME_VERSION.matcher(game);
        if (!match.matches()) throw new IllegalStateException("Unrecognised Minecraft release: " + game);
        int generation = Integer.parseInt(match.group(1));
        int minor = Integer.parseInt(match.group(2));
        if (generation < 1 || generation == 1 && minor < 21 || generation > 1 && generation < 26) {
            throw new IllegalStateException("Minecraft " + game + " is outside the 1.21.x and 26.1+ compatibility range.");
        }
        if (generation == 26 && minor < 1) throw new IllegalStateException("Minecraft 26.1 or later is required.");
        int requiredJava = generation >= 26 ? 25 : 21;
        if (javaVersion < requiredJava) throw new IllegalStateException("Minecraft " + game + " requires Java " + requiredJava + " or later.");
        if (!capabilities.keyedWorlds() || !capabilities.worldHeights()) {
            throw new IllegalStateException("This server must expose Bukkit world keys and minimum and maximum heights.");
        }
        return generation >= 26 ? new Paper261Adapter() : new Paper121Adapter();
    }

    public String implementation() { return implementation; }
    public String description() { return description; }
    public VersionAdapter version() { return version; }
    public PlatformCapabilities capabilities() { return capabilities; }

    /** Entry can originate at a database completion. Resolve the player state on its server thread. */
    public CompletableFuture<Boolean> teleport(Player player, Location destination) {
        return onMain(() -> {
            if (!player.isOnline() || destination.getWorld() == null) return CompletableFuture.completedFuture(false);
            if (nativeOperations != null) return nativeOperations.teleport(player, destination);
            return CompletableFuture.completedFuture(player.teleport(destination, PlayerTeleportEvent.TeleportCause.PLUGIN));
        }).thenCompose(result -> result);
    }

    /** Bukkit chunk generation is scheduled one request at a time; Paper can prepare chunks asynchronously. */
    public CompletableFuture<Chunk> chunk(World world, int x, int z) {
        if (nativeOperations != null) {
            return onMain(() -> nativeOperations.chunk(world, x, z)).thenCompose(result -> result);
        }
        CompletableFuture<Chunk> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> complete(result, () -> world.getChunkAt(x, z)));
        return result;
    }

    private <T> CompletableFuture<T> onMain(Supplier<T> operation) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (Bukkit.isPrimaryThread()) complete(future, operation);
        else Bukkit.getScheduler().runTask(plugin, () -> complete(future, operation));
        return future;
    }

    private static <T> void complete(CompletableFuture<T> future, Supplier<T> operation) {
        try {
            future.complete(operation.get());
        } catch (RuntimeException error) {
            future.completeExceptionally(error);
        }
    }
}
