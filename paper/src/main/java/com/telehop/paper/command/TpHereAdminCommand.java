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

@CommandAlias("tphere")
public class TpHereAdminCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public TpHereAdminCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    @co.aikar.commands.annotation.CommandCompletion("@networkplayers")
    public void execute(Player sender, String targetName) {
        if (!plugin.isFeatureEnabled("admin-tp")) {
            sender.sendMessage(plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(sender, PermissionNodes.TPHERE)) {
            sender.sendMessage(plugin.msg("no-permission"));
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target != null) {
            target.teleportAsync(sender.getLocation());
            plugin.auditLogger().log(sender.getName() + " tphere <- " + target.getName());
            return;
        }
        NetworkPacket packet = NetworkPacket.request(PacketType.ADMIN_TP_REQUEST, plugin.settings().serverName(), "velocity")
                .put("mode", "TARGET_TO_SENDER")
                .put("senderUuid", sender.getUniqueId().toString())
                .put("targetName", targetName);
        plugin.messaging().send(packet);
    }
}
