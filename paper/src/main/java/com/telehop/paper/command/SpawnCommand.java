package com.telehop.paper.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import com.telehop.common.PermissionNodes;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@CommandAlias("spawn")
public class SpawnCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;
    private final ConcurrentHashMap<UUID, Long> spawnDebounceMs = new ConcurrentHashMap<>();

    public SpawnCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    @Description("Send player to network spawn on lobby")
    public void execute(Player player) {
        if (!plugin.isFeatureEnabled("spawn")) {
            player.sendMessage(plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(player, PermissionNodes.SPAWN)) {
            player.sendMessage(plugin.msg("no-permission"));
            return;
        }
        long now = System.currentTimeMillis();
        long last = spawnDebounceMs.getOrDefault(player.getUniqueId(), 0L);
        if ((now - last) < 1000L) {
            return;
        }
        spawnDebounceMs.put(player.getUniqueId(), now);

        player.sendMessage(plugin.msg("spawn-sent"));

        if (plugin.settings().serverName().equalsIgnoreCase(plugin.settings().hubServer())) {
            plugin.services().teleportService().teleportToSpawn(player);
        } else {
            NetworkPacket packet = NetworkPacket.request(PacketType.TRANSFER_PLAYER, plugin.settings().serverName(), "velocity")
                    .put("uuid", player.getUniqueId().toString())
                    .put("targetServer", plugin.settings().hubServer())
                    .put("postAction", "SPAWN");
            plugin.messaging().send(packet);
        }
        plugin.auditLogger().log("spawn command by " + player.getName());
    }
}
