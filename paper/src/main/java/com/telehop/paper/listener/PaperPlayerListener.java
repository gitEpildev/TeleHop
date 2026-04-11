package com.telehop.paper.listener;

import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.service.BackLocationManager;
import com.telehop.paper.service.TeleportService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PaperPlayerListener implements Listener {
    private final NetworkPaperPlugin plugin;

    public PaperPlayerListener(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.services().teleportService().executePendingTeleport(event.getPlayer());
        NetworkPacket packet = NetworkPacket.request(PacketType.PLAYER_SERVER_UPDATE, plugin.settings().serverName(), "velocity");
        packet.put("uuid", event.getPlayer().getUniqueId().toString());
        packet.put("server", plugin.settings().serverName());
        plugin.messaging().send(packet);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.playerService().remove(event.getPlayer().getUniqueId());
        plugin.services().backLocationManager().remove(event.getPlayer().getUniqueId());
        plugin.tpaRuntimeManager().clearToggle(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.services().teleportService().clearPending(event.getEntity().getUniqueId());
        BackLocationManager backManager = plugin.services().backLocationManager();
        if (backManager != null && event.getEntity().getLocation().getWorld() != null) {
            backManager.saveLastDeath(event.getEntity().getUniqueId(),
                    event.getEntity().getLocation(), plugin.settings().serverName());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        TeleportService tp = plugin.services().teleportService();
        tp.clearPending(event.getPlayer().getUniqueId());
        if (plugin.settings().serverName().equalsIgnoreCase(plugin.settings().hubServer())) {
            event.setRespawnLocation(tp.getSpawnLocation());
        }
    }
}
