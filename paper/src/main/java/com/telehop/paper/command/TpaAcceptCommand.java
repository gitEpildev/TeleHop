package com.telehop.paper.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.telehop.common.PermissionNodes;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.common.model.TpaRequestRecord;
import com.telehop.common.model.TpaType;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.service.WarmupTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

@CommandAlias("tpaaccept")
public class TpaAcceptCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public TpaAcceptCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    public void execute(Player target) {
        if (!plugin.isFeatureEnabled("tpa")) {
            target.sendMessage(plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(target, PermissionNodes.TPA_ACCEPT)) {
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
        target.sendMessage(plugin.msg("request-accepted"));

        int delay = plugin.settings().tpaDelaySeconds();
        if (delay > 0) {
            target.sendMessage(plugin.msg("tpa-warmup", Map.of("seconds", String.valueOf(delay))));
            new WarmupTask(plugin, target, delay,
                    plugin.settings().tpaCancelOnMove(),
                    plugin.settings().showCountdown(),
                    () -> executeTeleport(target, request),
                    () -> target.sendMessage(plugin.msg("tpa-warmup-cancelled"))
            ).start();
        } else {
            executeTeleport(target, request);
        }
    }

    private void executeTeleport(Player target, TpaRequestRecord request) {
        Player sender = Bukkit.getPlayer(request.senderUuid());
        if (sender != null) {
            if (request.type() == TpaType.TPA) {
                sender.teleportAsync(target.getLocation());
            } else {
                target.teleportAsync(sender.getLocation());
            }
        } else {
            NetworkPacket packet = NetworkPacket.request(PacketType.TPA_ACCEPT, plugin.settings().serverName(), "velocity")
                    .put("senderUuid", request.senderUuid().toString())
                    .put("targetUuid", request.targetUuid().toString())
                    .put("type", request.type().name())
                    .put("targetServer", plugin.settings().serverName());
            plugin.messaging().send(packet);
        }
    }
}
