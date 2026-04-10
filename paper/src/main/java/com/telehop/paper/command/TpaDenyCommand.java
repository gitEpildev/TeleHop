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

@CommandAlias("tpadeny")
public class TpaDenyCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public TpaDenyCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    public void execute(Player target) {
        if (!plugin.isFeatureEnabled("tpa")) {
            target.sendMessage(plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(target, PermissionNodes.TPA_DENY)) {
            target.sendMessage(plugin.msg("no-permission"));
            return;
        }
        var requestOpt = plugin.tpaRuntimeManager().getIncoming(target.getUniqueId());
        if (requestOpt.isEmpty()) {
            target.sendMessage(plugin.msg("request-none-pending"));
            return;
        }
        var request = requestOpt.get();
        plugin.tpaRuntimeManager().removeIncoming(target.getUniqueId());
        plugin.tpaService().delete(request.senderUuid(), request.targetUuid());

        Player sender = Bukkit.getPlayer(request.senderUuid());
        if (sender != null) {
            sender.sendMessage(plugin.msg("request-denied"));
        } else {
            NetworkPacket packet = NetworkPacket.request(PacketType.TPA_DENY, plugin.settings().serverName(), "velocity")
                    .put("senderUuid", request.senderUuid().toString())
                    .put("targetUuid", request.targetUuid().toString());
            plugin.messaging().send(packet);
        }
        target.sendMessage(plugin.msg("request-denied"));
    }
}
