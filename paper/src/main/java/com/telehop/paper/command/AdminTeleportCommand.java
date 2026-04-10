package com.telehop.paper.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.telehop.common.PermissionNodes;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@CommandAlias("tp")
public class AdminTeleportCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public AdminTeleportCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    @co.aikar.commands.annotation.CommandCompletion("@networkplayers @networkplayers")
    public void execute(Player sender, String arg1, @co.aikar.commands.annotation.Optional String arg2) {
        tp(sender, arg1, arg2);
    }

    private void tp(Player sender, String arg1, String arg2) {
        if (!plugin.isFeatureEnabled("admin-tp")) {
            sender.sendMessage(plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(sender, PermissionNodes.TP)) {
            sender.sendMessage(plugin.msg("no-permission"));
            return;
        }
        if (arg2 == null) {
            Player target = Bukkit.getPlayerExact(arg1);
            if (target != null) {
                sender.teleportAsync(target.getLocation());
                plugin.auditLogger().log(sender.getName() + " tp -> " + target.getName());
                return;
            }
            NetworkPacket packet = NetworkPacket.request(PacketType.ADMIN_TP_REQUEST, plugin.settings().serverName(), "velocity")
                    .put("mode", "SELF_TO_TARGET")
                    .put("senderUuid", sender.getUniqueId().toString())
                    .put("targetName", arg1);
            plugin.messaging().send(packet);
            return;
        }

        Player player1 = Bukkit.getPlayerExact(arg1);
        Player player2 = Bukkit.getPlayerExact(arg2);
        if (player1 != null && player2 != null) {
            player1.teleportAsync(player2.getLocation());
            plugin.auditLogger().log(sender.getName() + " tp " + player1.getName() + " -> " + player2.getName());
            return;
        }
        NetworkPacket packet = NetworkPacket.request(PacketType.ADMIN_TP_REQUEST, plugin.settings().serverName(), "velocity")
                .put("mode", "PLAYER_TO_PLAYER")
                .put("actorUuid", sender.getUniqueId().toString())
                .put("playerName", arg1)
                .put("targetName", arg2);
        plugin.messaging().send(packet);
    }

}
