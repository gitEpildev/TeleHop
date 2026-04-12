package com.telehop.paper.listener;

import com.telehop.common.PermissionNodes;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.config.PaperSettings;
import com.telehop.paper.service.RandomRespawnManager;
import com.telehop.paper.service.RtpManager;
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

/**
 * Handles random respawn logic for the {@code random-respawn} feature.
 *
 * <p><b>Flow:</b>
 * <ol>
 *   <li>{@link #onDeath} fires at {@link EventPriority#MONITOR MONITOR} — after all
 *       other death handlers have run — and kicks off an <em>async</em> safe-location
 *       search via {@link RtpManager#findSafeLocation}. The result is stored in
 *       {@link RandomRespawnManager} keyed by the player's UUID.</li>
 *   <li>{@link #onRespawn} fires at {@link EventPriority#HIGH HIGH}. If the feature
 *       is active and the player qualifies, the pre-staged location is consumed and
 *       applied via {@link PlayerRespawnEvent#setRespawnLocation}. Because the location
 *       was found asynchronously during the death screen, the main thread is never
 *       blocked.</li>
 * </ol>
 *
 * <p><b>Priority reasoning:</b>
 * <ul>
 *   <li>{@code MONITOR} on death — we only observe, never cancel; MONITOR guarantees
 *       we see the final state of the event before triggering background work.</li>
 *   <li>{@code HIGH} on respawn — below {@code HIGHEST}/{@code MONITOR} so other
 *       plugins (e.g. bed-respawn overrides) can still win if needed, but high enough
 *       to override {@code NORMAL}-priority handlers.</li>
 * </ul>
 */
public final class RespawnListener implements Listener {

    private final NetworkPaperPlugin plugin;
    private final RandomRespawnManager respawnManager;

    public RespawnListener(NetworkPaperPlugin plugin, RandomRespawnManager respawnManager) {
        this.plugin = plugin;
        this.respawnManager = respawnManager;
    }

    /**
     * On player death: asynchronously pre-compute a safe random location so it is
     * ready by the time the player clicks "Respawn".
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.isFeatureEnabled("random-respawn")) return;

        Player player = event.getEntity();
        if (plugin.permissionService().has(player, PermissionNodes.RESPAWN_BYPASS)) return;

        PaperSettings settings = plugin.settings();

        // Never pre-stage a location on the hub server — it sends players to spawn anyway.
        if (settings.serverName().equalsIgnoreCase(settings.hubServer())) return;

        World world = Bukkit.getWorld(settings.respawnWorld());
        if (world == null) {
            plugin.getLogger().warning("[RandomRespawn] World '" + settings.respawnWorld()
                    + "' not found — falling back to default respawn.");
            return;
        }

        plugin.services().rtpManager()
                .findSafeLocation(world, settings.respawnRadius())
                .thenAccept(location -> {
                    if (location != null) {
                        respawnManager.stage(player.getUniqueId(), location);
                    } else {
                        plugin.getLogger().warning("[RandomRespawn] Could not find a safe location for "
                                + player.getName() + " — falling back to default respawn.");
                    }
                });
    }

    /**
     * On player respawn: apply the pre-staged random location if one is ready and
     * the player hasn't opted out (bed/anchor/bypass).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!plugin.isFeatureEnabled("random-respawn")) return;

        Player player = event.getPlayer();
        if (plugin.permissionService().has(player, PermissionNodes.RESPAWN_BYPASS)) return;

        PaperSettings settings = plugin.settings();

        // Never interfere on the hub server — PaperPlayerListener handles spawn there.
        if (settings.serverName().equalsIgnoreCase(settings.hubServer())) return;

        if (settings.respawnRespectBed() && event.isBedSpawn()) return;
        if (settings.respawnRespectAnchor() && event.isAnchorSpawn()) return;

        Location staged = respawnManager.consume(player.getUniqueId());
        if (staged == null) return;

        event.setRespawnLocation(staged);
    }

    /**
     * On player quit: clear any staged location to prevent memory leaks for
     * players who disconnect on the death screen without respawning.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        respawnManager.clear(event.getPlayer().getUniqueId());
    }
}
