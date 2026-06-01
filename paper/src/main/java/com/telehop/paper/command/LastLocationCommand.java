package com.telehop.paper.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.telehop.common.PermissionNodes;
import com.telehop.common.model.LastLocationRecord;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

@CommandAlias("lastlocation|lastloc|backlast|ll")
public class LastLocationCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public LastLocationCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    public void execute(Player player) {
        if (!plugin.isFeatureEnabled("last-location")) {
            player.sendMessage(plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(player, PermissionNodes.LAST_LOCATION)) {
            player.sendMessage(plugin.msg("no-permission"));
            return;
        }

        String uuid = player.getUniqueId().toString();
        plugin.services().lastLocationService().find(uuid).thenAccept(opt -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (opt.isEmpty()) {
                    player.sendMessage(plugin.msg("lastloc-no-location"));
                    return;
                }
                LastLocationRecord record = opt.get();
                String currentServer = plugin.settings().serverName();

                if (!record.server().equalsIgnoreCase(currentServer)) {
                    NetworkPacket packet = NetworkPacket.request(
                            PacketType.TRANSFER_PLAYER, currentServer, "velocity");
                    packet.put("uuid", uuid);
                    packet.put("targetServer", record.server());
                    packet.put("postAction", "LASTLOC");
                    packet.put("world", record.world());
                    packet.put("x", String.valueOf(record.x()));
                    packet.put("y", String.valueOf(record.y()));
                    packet.put("z", String.valueOf(record.z()));
                    packet.put("yaw", String.valueOf(record.yaw()));
                    packet.put("pitch", String.valueOf(record.pitch()));
                    plugin.messaging().send(packet);
                    player.sendMessage(plugin.msg("lastloc-teleporting"));
                    return;
                }

                World world = Bukkit.getWorld(record.world());
                if (world == null) {
                    player.sendMessage(plugin.msg("lastloc-world-missing"));
                    return;
                }
                Location target = new Location(world, record.x(), record.y(), record.z(),
                        record.yaw(), record.pitch());
                player.sendMessage(plugin.msg("lastloc-teleporting"));
                plugin.services().teleportService().teleportBack(player, target);
            });
        });
    }
}
