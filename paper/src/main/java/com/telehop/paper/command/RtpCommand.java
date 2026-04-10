package com.telehop.paper.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.telehop.common.PermissionNodes;
import com.telehop.common.model.NetworkPacket;
import com.telehop.common.model.PacketType;
import com.telehop.paper.NetworkPaperPlugin;
import com.telehop.paper.gui.RtpGui;
import com.telehop.paper.service.WarmupTask;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

public class RtpCommand extends BaseCommand {
    private final NetworkPaperPlugin plugin;

    public RtpCommand(NetworkPaperPlugin plugin) {
        this.plugin = plugin;
    }

    @CommandAlias("rtp")
    @Default
    public void execute(Player player) {
        if (!plugin.isFeatureEnabled("rtp")) {
            player.sendMessage(plugin.msg("feature-disabled"));
            return;
        }
        if (!plugin.permissionService().has(player, PermissionNodes.RTP)) {
            player.sendMessage(plugin.msg("no-permission"));
            return;
        }
        if (!plugin.permissionService().has(player, PermissionNodes.RTP_BYPASS_COOLDOWN)
                && plugin.rtpManager().onCooldown(player)) {
            player.sendMessage(plugin.msg("rtp-cooldown"));
            return;
        }
        plugin.rtpManager().markCooldown(player, plugin.settings().rtpCooldownSeconds());
        player.sendMessage(plugin.msg("rtp-opening"));
        new RtpGui(plugin, (region, dimension) -> {
            int delay = plugin.settings().rtpDelaySeconds();
            boolean bypass = plugin.permissionService().has(player, PermissionNodes.RTP_BYPASS_DELAY);
            if (delay > 0 && !bypass) {
                player.sendMessage(plugin.msg("rtp-delay", Map.of("seconds", String.valueOf(delay))));
                new WarmupTask(plugin, player, delay,
                        plugin.settings().rtpCancelOnMove(),
                        plugin.settings().showCountdown(),
                        () -> startRtp(player, region, dimension),
                        () -> player.sendMessage(plugin.msg("rtp-cancelled"))
                ).start();
            } else {
                startRtp(player, region, dimension);
            }
        }).openRegion(player);
    }

    private void startRtp(Player player, String region, String dimension) {
        String normalizedRegion = region.toLowerCase(Locale.ROOT);
        String normalizedDimension = dimension.toLowerCase(Locale.ROOT);
        String targetServer = plugin.regionServer(region).orElse(plugin.settings().serverName());
        plugin.auditLogger().log("rtp-select player=" + player.getName() + " region=" + normalizedRegion + " dimension=" + normalizedDimension + " targetServer=" + targetServer);
        if (!targetServer.equalsIgnoreCase(plugin.settings().serverName())) {
            NetworkPacket packet = NetworkPacket.request(PacketType.TRANSFER_PLAYER, plugin.settings().serverName(), "velocity")
                    .put("uuid", player.getUniqueId().toString())
                    .put("targetServer", targetServer)
                    .put("postAction", "RTP_" + normalizedDimension.toUpperCase(Locale.ROOT))
                    .put("dimension", normalizedDimension)
                    .put("region", normalizedRegion);
            plugin.messaging().send(packet);
            return;
        }
        plugin.executeLocalRtp(player, normalizedRegion, normalizedDimension);
    }
}
