package com.telehop.paper.listener;

import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.paper.NetworkPaperPlugin;
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
        plugin.executePendingTeleport(event.getPlayer());
        NetworkPacket packet = NetworkPacket.request(PacketType.PLAYER_SERVER_UPDATE, plugin.settings().serverName(), "velocity");
        packet.put("uuid", event.getPlayer().getUniqueId().toString());
        packet.put("server", plugin.settings().serverName());
        plugin.messaging().send(packet);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.playerService().remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.clearPendingTeleport(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.clearPendingTeleport(event.getPlayer().getUniqueId());
        if (plugin.settings().serverName().equalsIgnoreCase(plugin.settings().hubServer())) {
            event.setRespawnLocation(plugin.getSpawnLocation());
        }
    }
}
