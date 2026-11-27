package com.telehop.paper.command.tpa;

import com.telehop.paper.platform.PlatformText;
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

@CommandAlias("%{cmd-tpa}")
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
            PlatformText.send(sender, plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(sender, type == TpaType.TPA ? PermissionNodes.TPA : PermissionNodes.TPA_HERE)) {
            PlatformText.send(sender, plugin.msg("no-permission"));
            return;
        }
        if (!plugin.permissionService().has(sender, PermissionNodes.TPA_BYPASS_COOLDOWN)
                && plugin.tpaRuntimeManager().onCooldown(sender.getUniqueId())) {
            int remaining = plugin.tpaRuntimeManager().remainingCooldown(sender.getUniqueId());
            PlatformText.send(sender, plugin.msg("tpa-cooldown", Map.of("seconds", String.valueOf(remaining))));
            return;
        }
        Player onlineTarget = Bukkit.getPlayerExact(targetName);
        UUID targetUuid = onlineTarget != null ? onlineTarget.getUniqueId() : Bukkit.getOfflinePlayer(targetName).getUniqueId();
        if (targetUuid.equals(sender.getUniqueId())) {
            PlatformText.send(sender, plugin.msg("player-not-found"));
            return;
        }
        if (onlineTarget != null && plugin.tpaRuntimeManager().isTpaDisabled(targetUuid)) {
            PlatformText.send(sender, plugin.msg("player-tpa-disabled", Map.of("target", targetName)));
            return;
        }

        Instant sentAt = Instant.now();
        TpaRequestRecord request = new TpaRequestRecord(sender.getUniqueId(), targetUuid, type, sentAt);
        plugin.tpaRuntimeManager().setIncoming(request);
        plugin.tpaService().upsert(request);
        plugin.tpaRuntimeManager().markCooldown(sender.getUniqueId(), plugin.settings().tpaCooldownSeconds());

        PlatformText.send(sender, plugin.msg("request-sent", Map.of("target", targetName)));
        Player target = onlineTarget != null ? onlineTarget : Bukkit.getPlayer(targetUuid);
        if (target != null) {
            PlatformText.send(target, plugin.msg(type == TpaType.TPA ? "request-received" : "request-received-here",
                    Map.of("sender", sender.getName())));
            PlatformText.send(target, plugin.messageService().raw("request-actions"));
        }

        NetworkPacket packet = NetworkPacket.request(PacketType.TPA_CREATE, plugin.settings().serverName(), "velocity")
                .put("senderUuid", sender.getUniqueId().toString())
                .put("senderName", sender.getName())
                .put("targetUuid", targetUuid.toString())
                .put("targetName", targetName)
                .put("type", type.name())
                .put("sentAt", String.valueOf(sentAt.toEpochMilli()));
        plugin.messaging().send(packet);
    }
}
