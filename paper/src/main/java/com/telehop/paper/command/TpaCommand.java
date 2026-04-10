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
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@CommandAlias("tpa")
public class TpaCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public TpaCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Default
    @co.aikar.commands.annotation.CommandCompletion("@networkplayers")
    public void execute(Player sender, String targetName) {
        createRequest(sender, targetName, TpaType.TPA);
    }

    void createRequest(Player sender, String targetName, TpaType type) {
        if (!plugin.isFeatureEnabled("tpa")) {
            sender.sendMessage(plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(sender, type == TpaType.TPA ? PermissionNodes.TPA : PermissionNodes.TPA_HERE)) {
            sender.sendMessage(plugin.msg("no-permission"));
            return;
        }
        if (!plugin.permissionService().has(sender, PermissionNodes.TPA_BYPASS_COOLDOWN)
                && plugin.tpaRuntimeManager().onCooldown(sender.getUniqueId())) {
            sender.sendMessage(plugin.msg("tpa-cooldown"));
            return;
        }
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        UUID targetUuid = onlineTarget != null ? onlineTarget.getUniqueId() : Bukkit.getOfflinePlayer(targetName).getUniqueId();
        if (targetUuid.equals(sender.getUniqueId())) {
            sender.sendMessage(plugin.msg("player-not-found"));
            return;
        }

        Instant expiry = Instant.now().plusSeconds(plugin.settings().tpaTimeoutSeconds());
        TpaRequestRecord request = new TpaRequestRecord(sender.getUniqueId(), targetUuid, type, expiry);
        plugin.tpaRuntimeManager().setIncoming(request);
        plugin.tpaService().upsert(request);
        plugin.tpaRuntimeManager().markCooldown(sender.getUniqueId(), plugin.settings().tpaCooldownSeconds());

        sender.sendMessage(plugin.msg("request-sent", Map.of("target", targetName)));
        Player target = onlineTarget != null ? onlineTarget : Bukkit.getPlayer(targetUuid);
        if (target != null) {
            target.sendMessage(plugin.msg(type == TpaType.TPA ? "request-received" : "request-received-here",
                    Map.of("sender", sender.getName())));
            target.sendMessage(plugin.messageService().raw("request-actions"));
        }

        NetworkPacket packet = NetworkPacket.request(PacketType.TPA_CREATE, plugin.settings().serverName(), "velocity")
                .put("senderUuid", sender.getUniqueId().toString())
                .put("senderName", sender.getName())
                .put("targetUuid", targetUuid.toString())
                .put("targetName", targetName)
                .put("type", type.name())
                .put("expiry", String.valueOf(expiry.toEpochMilli()));
        plugin.messaging().send(packet);
    }
}
