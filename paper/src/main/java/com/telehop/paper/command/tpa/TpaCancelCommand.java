package com.telehop.paper.command.tpa;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.telehop.common.PermissionNodes;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.entity.Player;

@CommandAlias("tpacancel")
public class TpaCancelCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public TpaCancelCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    public void execute(Player sender) {
        if (!plugin.isFeatureEnabled("tpa")) {
            sender.sendMessage(plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(sender, PermissionNodes.TPA_CANCEL)) {
            sender.sendMessage(plugin.msg("no-permission"));
            return;
        }
        var outgoing = plugin.tpaRuntimeManager().getOutgoing(sender.getUniqueId()).orElse(null);
        if (outgoing == null) {
            sender.sendMessage(plugin.msg("request-none-outgoing"));
            return;
        }
        plugin.tpaRuntimeManager().removeOutgoing(sender.getUniqueId());
        plugin.tpaService().delete(outgoing.senderUuid(), outgoing.targetUuid());

        NetworkPacket packet = NetworkPacket.request(PacketType.TPA_CANCEL, plugin.settings().serverName(), "velocity")
                .put("senderUuid", sender.getUniqueId().toString())
                .put("targetUuid", outgoing.targetUuid().toString());
        plugin.messaging().send(packet);
        sender.sendMessage(plugin.msg("request-cancelled"));
    }
}
