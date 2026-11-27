package com.telehop.paper.command;

import com.telehop.paper.platform.PlatformText;
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

@CommandAlias("%{cmd-lastlocation}")
public class LastLocationCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public LastLocationCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    public void execute(Player player) {
        if (!plugin.isFeatureEnabled("last-location")) {
            PlatformText.send(player, plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(player, PermissionNodes.LAST_LOCATION)) {
            PlatformText.send(player, plugin.msg("no-permission"));
            return;
        }

        String uuid = player.getUniqueId().toString();
        plugin.services().lastLocationService().find(uuid).thenAccept(opt -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (opt.isEmpty()) {
                    PlatformText.send(player, plugin.msg("lastloc-no-location"));
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
                    plugin.messaging().send(player, packet);
                    PlatformText.send(player, plugin.msg("lastloc-teleporting"));
                    return;
                }

                World world = plugin.versionAdapter().resolveWorld(record.world());
                if (world == null) {
                    PlatformText.send(player, plugin.msg("lastloc-world-missing"));
                    return;
                }
                Location target = new Location(world, record.x(), record.y(), record.z(),
                        record.yaw(), record.pitch());
                PlatformText.send(player, plugin.msg("lastloc-teleporting"));
                plugin.services().teleportService().teleportBack(player, target);
            });
        });
    }
}
