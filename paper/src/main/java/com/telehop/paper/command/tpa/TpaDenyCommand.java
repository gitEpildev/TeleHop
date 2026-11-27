package com.telehop.paper.command.tpa;

import com.telehop.paper.platform.PlatformText;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.telehop.common.PermissionNodes;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.paper.NetworkPaperPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@CommandAlias("%{cmd-tpadeny}")
public class TpaDenyCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public TpaDenyCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    public void execute(Player target) {
        if (!plugin.isFeatureEnabled("tpa")) {
            PlatformText.send(target, plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(target, PermissionNodes.TPA_DENY)) {
            PlatformText.send(target, plugin.msg("no-permission"));
            return;
        }
        var requestOpt = plugin.tpaRuntimeManager().getIncoming(target.getUniqueId());
        if (requestOpt.isEmpty()) {
            PlatformText.send(target, plugin.msg("request-none-pending"));
            return;
        }
        var request = requestOpt.get();
        plugin.tpaRuntimeManager().removeRequest(request);
        plugin.tpaService().delete(request);

        Player sender = Bukkit.getPlayer(request.senderUuid());
        if (sender != null) {
            PlatformText.send(sender, plugin.msg("request-denied"));
        } else {
            NetworkPacket packet = NetworkPacket.request(PacketType.TPA_DENY, plugin.settings().serverName(), "velocity")
                    .put("senderUuid", request.senderUuid().toString())
                    .put("targetUuid", request.targetUuid().toString());
            plugin.messaging().send(packet);
        }
        PlatformText.send(target, plugin.msg("request-denied"));
    }
}
