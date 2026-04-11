package com.telehop.paper.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Optional;
import com.telehop.common.PermissionNodes;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.service.BackLocationManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

@CommandAlias("back")
public class BackCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public BackCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    public void execute(Player player, @Optional String subcommand) {
        if (!plugin.isFeatureEnabled("back")) {
            player.sendMessage(plugin.msg("feature-disabled"));
            return;
        }

        BackLocationManager backManager = plugin.services().backLocationManager();

        if ("death".equalsIgnoreCase(subcommand)) {
            if (!plugin.permissionService().has(player, PermissionNodes.BACK_DEATH)) {
                player.sendMessage(plugin.msg("no-permission"));
                return;
            }
            BackLocationManager.BackEntry entry = backManager.getLastDeath(player.getUniqueId());
            if (entry == null) {
                player.sendMessage(plugin.msg("back-death-no-location"));
                return;
            }
            teleportBack(player, entry, "back-death-teleporting");
        } else {
            if (!plugin.permissionService().has(player, PermissionNodes.BACK)) {
                player.sendMessage(plugin.msg("no-permission"));
                return;
            }
            BackLocationManager.BackEntry entry = backManager.getLastTeleport(player.getUniqueId());
            if (entry == null) {
                player.sendMessage(plugin.msg("back-no-location"));
                return;
            }
            teleportBack(player, entry, "back-teleporting");
        }
    }

    private void teleportBack(Player player, BackLocationManager.BackEntry entry, String messageKey) {
        String currentServer = plugin.settings().serverName();
        if (!entry.server().equalsIgnoreCase(currentServer)) {
            NetworkPacket packet = NetworkPacket.request(PacketType.TRANSFER_PLAYER, currentServer, "velocity");
            packet.put("uuid", player.getUniqueId().toString());
            packet.put("targetServer", entry.server());
            packet.put("postAction", "BACK");
            Location loc = entry.location();
            packet.put("world", loc.getWorld().getName());
            packet.put("x", String.valueOf(loc.getX()));
            packet.put("y", String.valueOf(loc.getY()));
            packet.put("z", String.valueOf(loc.getZ()));
            packet.put("yaw", String.valueOf(loc.getYaw()));
            packet.put("pitch", String.valueOf(loc.getPitch()));
            plugin.messaging().send(packet);
            player.sendMessage(plugin.msg(messageKey));
            return;
        }
        player.sendMessage(plugin.msg(messageKey));
        plugin.services().teleportService().teleportBack(player, entry.location());
    }
}
