package com.telehop.paper.listener;

import com.telehop.common.PermissionNodes;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.config.PaperSettings;
import com.telehop.paper.service.RandomRespawnManager;
import com.telehop.paper.service.RandomRespawnService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.concurrent.CompletableFuture;

/**
 * Handles random respawn logic for the {@code random-respawn} feature.
 *
 * <p><b>Flow:</b>
 * <ol>
 *   <li>{@link #onDeath} fires at {@link EventPriority#MONITOR MONITOR} and starts an
 *       async safe-location search via {@link RandomRespawnService}. The future is stored
 *       in {@link RandomRespawnManager}.</li>
 *   <li>{@link #onRespawn} fires at {@link EventPriority#HIGH HIGH}. Two cases:
 *       <ul>
 *         <li><b>Future done</b> — location is applied immediately via
 *             {@code event.setRespawnLocation()}.</li>
 *         <li><b>Future pending</b> — the player respawns at the default location first,
 *             then is teleported on the next tick once the search completes. A brief
 *             flicker is preferable to blocking the main thread.</li>
 *       </ul>
 *   </li>
 * </ol>
 */
public final class RespawnListener implements Listener {

    private final NetworkPaperPlugin plugin;
    private final RandomRespawnManager respawnManager;
    private final RandomRespawnService respawnService;

    public RespawnListener(NetworkPaperPlugin plugin,
                           RandomRespawnManager respawnManager,
                           RandomRespawnService respawnService) {
        this.plugin = plugin;
        this.respawnManager = respawnManager;
        this.respawnService = respawnService;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        plugin.getLogger().info("[RandomRespawn] " + player.getName() + " died — processing...");

        if (!plugin.isFeatureEnabled("random-respawn")) {
            plugin.getLogger().info("[RandomRespawn] Feature disabled — skipping.");
            return;
        }

        if (plugin.permissionService().hasExplicit(player, PermissionNodes.RESPAWN_BYPASS)) {
            plugin.getLogger().info("[RandomRespawn] " + player.getName()
                    + " has explicit bypass permission — skipping.");
            return;
        }

        PaperSettings settings = plugin.settings();
        if (settings.serverName().equalsIgnoreCase(settings.hubServer())) {
            plugin.getLogger().info("[RandomRespawn] Hub server ("
                    + settings.serverName() + " == " + settings.hubServer() + ") — skipping.");
            return;
        }

        World world = Bukkit.getWorld(settings.respawnWorld());
        if (world == null) {
            plugin.getLogger().warning("[RandomRespawn] World '" + settings.respawnWorld()
                    + "' not found — falling back to default respawn.");
            return;
        }

        plugin.getLogger().info("[RandomRespawn] Starting async search for "
                + player.getName() + " in world '" + settings.respawnWorld()
                + "' radius " + settings.respawnRadius());
        CompletableFuture<Location> future = respawnService.findSafeLocation(world, settings.respawnRadius());
        respawnManager.stage(player.getUniqueId(), future);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getLogger().info("[RandomRespawn] " + player.getName() + " respawning...");

        if (!plugin.isFeatureEnabled("random-respawn")) return;
        if (plugin.permissionService().hasExplicit(player, PermissionNodes.RESPAWN_BYPASS)) return;

        PaperSettings settings = plugin.settings();
        if (settings.serverName().equalsIgnoreCase(settings.hubServer())) return;

        if (settings.respawnRespectBed() && event.isBedSpawn()) {
            plugin.getLogger().info("[RandomRespawn] " + player.getName()
                    + " has bed spawn — respecting bed.");
            return;
        }
        if (settings.respawnRespectAnchor() && event.isAnchorSpawn()) {
            plugin.getLogger().info("[RandomRespawn] " + player.getName()
                    + " has anchor spawn — respecting anchor.");
            return;
        }

        CompletableFuture<Location> future = respawnManager.consume(player.getUniqueId());
        if (future == null) {
            plugin.getLogger().warning("[RandomRespawn] No staged future for "
                    + player.getName() + " — falling back to default.");
            return;
        }

        if (future.isDone() && !future.isCompletedExceptionally()) {
            Location location = future.getNow(null);
            if (location != null) {
                plugin.getLogger().info("[RandomRespawn] Applying location for "
                        + player.getName() + ": " + formatLoc(location));
                event.setRespawnLocation(location);
            } else {
                plugin.getLogger().warning("[RandomRespawn] Search returned null for "
                        + player.getName() + " — no safe location found.");
            }
        } else {
            plugin.getLogger().info("[RandomRespawn] Future still pending for "
                    + player.getName() + " — will teleport after respawn.");
            future.thenAccept(location -> {
                if (location != null && player.isOnline()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getLogger().info("[RandomRespawn] Late teleport for "
                                + player.getName() + ": " + formatLoc(location));
                        player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
                    });
                }
            });
        }
    }

    private static String formatLoc(Location loc) {
        return String.format("%.0f, %.0f, %.0f in %s",
                loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        respawnManager.clear(event.getPlayer().getUniqueId());
    }
}
